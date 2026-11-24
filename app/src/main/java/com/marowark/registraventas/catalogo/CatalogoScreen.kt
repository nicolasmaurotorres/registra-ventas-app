package com.marowark.registraventas.catalogo

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
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
import com.marowark.registraventas.registro.Producto
import com.marowark.registraventas.registro.Resultado
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CatalogoViewModel(app: Application) : AndroidViewModel(app) {
    private val registro = (app as RegistraVentasApp).registro

    val productos = registro.productos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** [id] null crea; si no, edita. [listo] recibe null si salió bien o el mensaje del error. */
    fun guardar(id: String?, nombre: String, precio: Long, listo: (String?) -> Unit) = viewModelScope.launch {
        val r = if (id == null) registro.crearProducto(nombre, precio) else registro.editarProducto(id, nombre, precio)
        listo((r as? Resultado.Error)?.motivo?.mensaje)
    }

    fun alternarActivo(p: Producto) = viewModelScope.launch {
        if (p.activo) registro.desactivarProducto(p.id) else registro.reactivarProducto(p.id)
    }

    fun borrar(p: Producto, listo: (String?) -> Unit) = viewModelScope.launch {
        listo((registro.borrarProducto(p.id) as? Resultado.Error)?.motivo?.mensaje)
    }
}

// shortcut: sólo Productos; los Canales se suman a esta pestaña en su ticket.
@Composable
fun CatalogoScreen(vm: CatalogoViewModel = viewModel()) {
    val productos by vm.productos.collectAsStateWithLifecycle()
    // null = cerrado; Producto vacío (id "") = nuevo.
    var editando by remember { mutableStateOf<Producto?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (productos.isEmpty()) {
            Text("Todavía no hay Productos", Modifier.align(Alignment.Center))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(productos, key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text(p.nombre) },
                    supportingContent = { Text(formatearPesos(p.precioReferencia) + if (p.activo) "" else " · inactivo") },
                    modifier = Modifier.clickable { editando = p },
                )
            }
        }
        FloatingActionButton(
            onClick = { editando = Producto("", "", 0, true, java.time.Instant.EPOCH) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Nuevo Producto") }
    }

    editando?.let { p -> ProductoDialogo(p, vm, cerrar = { editando = null }) }
}

@Composable
private fun ProductoDialogo(p: Producto, vm: CatalogoViewModel, cerrar: () -> Unit) {
    val nuevo = p.id.isEmpty()
    var nombre by remember { mutableStateOf(p.nombre) }
    var precio by remember { mutableStateOf(if (nuevo) "" else formatearPesos(p.precioReferencia)) }
    var error by remember { mutableStateOf<String?>(null) }
    val listo: (String?) -> Unit = { e -> if (e == null) cerrar() else error = e }

    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(if (nuevo) "Nuevo Producto" else "Editar Producto") },
        text = {
            Column {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(
                    precio, { precio = it },
                    label = { Text("Precio de referencia") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!nuevo) {
                    TextButton(onClick = { vm.alternarActivo(p); cerrar() }) {
                        Text(if (p.activo) "Desactivar" else "Reactivar")
                    }
                    TextButton(onClick = { vm.borrar(p, listo) }) { Text("Borrar") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val centavos = parsearPesos(precio)
                if (centavos == null) error = "Precio inválido" else vm.guardar(p.id.ifEmpty { null }, nombre, centavos, listo)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}
