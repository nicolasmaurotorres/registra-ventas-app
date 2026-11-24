package com.marowark.registraventas.catalogo

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marowark.registraventas.RegistraVentasApp
import com.marowark.registraventas.formatearPesos
import com.marowark.registraventas.parsearPesos
import com.marowark.registraventas.registro.Canal
import com.marowark.registraventas.registro.Producto
import com.marowark.registraventas.registro.Resultado
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CatalogoViewModel(app: Application) : AndroidViewModel(app) {
    private val registro = (app as RegistraVentasApp).registro

    val productos = registro.productos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val canales = registro.canales().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Corre [op] y pasa a [listo] null si salió bien o el mensaje del error. */
    fun ejecutar(listo: (String?) -> Unit = {}, op: suspend () -> Resultado<*>) = viewModelScope.launch {
        listo((op() as? Resultado.Error)?.motivo?.mensaje)
    }

    /** [id] null crea; si no, edita. */
    fun guardarProducto(id: String?, nombre: String, precio: Long, listo: (String?) -> Unit) = ejecutar(listo) {
        if (id == null) registro.crearProducto(nombre, precio) else registro.editarProducto(id, nombre, precio)
    }

    fun guardarCanal(id: String?, nombre: String, listo: (String?) -> Unit) = ejecutar(listo) {
        if (id == null) registro.crearCanal(nombre) else registro.editarCanal(id, nombre)
    }

    fun alternarActivo(p: Producto) = viewModelScope.launch {
        if (p.activo) registro.desactivarProducto(p.id) else registro.reactivarProducto(p.id)
    }

    fun alternarActivo(c: Canal) = viewModelScope.launch {
        if (c.activo) registro.desactivarCanal(c.id) else registro.reactivarCanal(c.id)
    }

    fun borrar(p: Producto, listo: (String?) -> Unit) = ejecutar(listo) { registro.borrarProducto(p.id) }
    fun borrar(c: Canal, listo: (String?) -> Unit) = ejecutar(listo) { registro.borrarCanal(c.id) }
}

@Composable
fun CatalogoScreen(vm: CatalogoViewModel = viewModel()) {
    val productos by vm.productos.collectAsStateWithLifecycle()
    val canales by vm.canales.collectAsStateWithLifecycle()
    // null = cerrado; id "" = nuevo.
    var producto by remember { mutableStateOf<Producto?>(null) }
    var canal by remember { mutableStateOf<Canal?>(null) }
    val inactivo = { activo: Boolean -> if (activo) "" else "inactivo" }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Seccion("Productos") { producto = Producto("", "", 0, true, java.time.Instant.EPOCH) }
        }
        if (productos.isEmpty()) item { Text("Todavía no hay Productos", Modifier.padding(16.dp)) }
        items(productos, key = { it.id }) { p ->
            ListItem(
                headlineContent = { Text(p.nombre) },
                supportingContent = { Text(listOf(formatearPesos(p.precioReferencia), inactivo(p.activo)).filter(String::isNotEmpty).joinToString(" · ")) },
                modifier = Modifier.clickable { producto = p },
            )
        }
        item {
            Seccion("Canales") { canal = Canal("", "", true, java.time.Instant.EPOCH) }
        }
        items(canales, key = { it.id }) { c ->
            ListItem(
                headlineContent = { Text(c.nombre) },
                supportingContent = inactivo(c.activo).takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                modifier = Modifier.clickable { canal = c },
            )
        }
    }

    producto?.let { p ->
        ElementoDialogo(
            tipo = "Producto", nuevo = p.id.isEmpty(), nombreInicial = p.nombre, activo = p.activo,
            precioInicial = if (p.id.isEmpty()) "" else formatearPesos(p.precioReferencia),
            guardar = { nombre, precio, listo -> vm.guardarProducto(p.id.ifEmpty { null }, nombre, precio!!, listo) },
            alternarActivo = { vm.alternarActivo(p) },
            borrar = { vm.borrar(p, it) },
            cerrar = { producto = null },
        )
    }
    canal?.let { c ->
        ElementoDialogo(
            tipo = "Canal", nuevo = c.id.isEmpty(), nombreInicial = c.nombre, activo = c.activo,
            precioInicial = null,
            guardar = { nombre, _, listo -> vm.guardarCanal(c.id.ifEmpty { null }, nombre, listo) },
            alternarActivo = { vm.alternarActivo(c) },
            borrar = { vm.borrar(c, it) },
            cerrar = { canal = null },
        )
    }
}

@Composable
private fun Seccion(titulo: String, nuevo: () -> Unit) {
    ListItem(
        headlineContent = { Text(titulo, style = MaterialTheme.typography.titleMedium) },
        trailingContent = { IconButton(onClick = nuevo) { Icon(Icons.Filled.Add, contentDescription = "Nuevo en $titulo") } },
    )
}

/** Diálogo de alta/edición de Producto o Canal. [precioInicial] null oculta el precio (Canal). */
@Composable
internal fun ElementoDialogo(
    tipo: String,
    nuevo: Boolean,
    nombreInicial: String,
    activo: Boolean,
    precioInicial: String?,
    guardar: (nombre: String, centavos: Long?, listo: (String?) -> Unit) -> Unit,
    alternarActivo: () -> Unit,
    borrar: ((String?) -> Unit) -> Unit,
    cerrar: () -> Unit,
) {
    var nombre by remember { mutableStateOf(nombreInicial) }
    var precio by remember { mutableStateOf(precioInicial) }
    var error by remember { mutableStateOf<String?>(null) }
    val listo: (String?) -> Unit = { e -> if (e == null) cerrar() else error = e }

    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(if (nuevo) "Nuevo $tipo" else "Editar $tipo") },
        text = {
            Column {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true)
                precio?.let { texto ->
                    OutlinedTextField(
                        texto, { precio = it },
                        label = { Text("Precio de referencia") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!nuevo) {
                    TextButton(onClick = { alternarActivo(); cerrar() }) {
                        Text(if (activo) "Desactivar" else "Reactivar")
                    }
                    TextButton(onClick = { borrar(listo) }) { Text("Borrar") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val texto = precio
                val centavos = texto?.let(::parsearPesos)
                if (texto != null && centavos == null) error = "Precio inválido" else guardar(nombre, centavos, listo)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}
