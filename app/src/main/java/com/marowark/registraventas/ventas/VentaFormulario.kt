package com.marowark.registraventas.ventas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marowark.registraventas.formatearPesos
import com.marowark.registraventas.parsearPesos
import com.marowark.registraventas.registro.Canal
import com.marowark.registraventas.registro.DatosLinea
import com.marowark.registraventas.registro.DatosVenta
import com.marowark.registraventas.registro.Producto
import com.marowark.registraventas.registro.Venta
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Pantalla "Registrar Venta": formulario vacío con Fecha de venta = hoy y sólo Canales/Productos activos. */
@Composable
fun RegistrarVentaScreen(cerrar: () -> Unit, vm: VentasViewModel = viewModel()) {
    val canales by vm.canalesActivos.collectAsStateWithLifecycle()
    val productos by vm.productosActivos.collectAsStateWithLifecycle()
    VentaFormulario(
        titulo = "Registrar Venta",
        inicial = remember { DatosVenta(vm.hoy(), canalId = null, cliente = null, lineas = emptyList()) },
        canales = canales,
        productos = productos,
        guardar = vm::registrar,
        cerrar = cerrar,
    )
}

/**
 * Pantalla "Editar Venta": carga la Venta y ofrece Canales/Productos activos más los inactivos
 * que la Venta ya usa, para que se sigan viendo seleccionados.
 */
@Composable
fun EditarVentaScreen(id: String, cerrar: () -> Unit, vm: VentasViewModel = viewModel()) {
    val venta by produceState<Venta?>(null, id) { value = vm.venta(id) }
    val canales by vm.canales.collectAsStateWithLifecycle()
    val productos by vm.productos.collectAsStateWithLifecycle()
    val v = venta ?: return
    val usados = v.lineas.map { it.productoId }.toSet()
    VentaFormulario(
        titulo = "Editar Venta",
        inicial = remember(v) {
            DatosVenta(
                v.fechaVenta, v.canalId, v.cliente, v.lineas.map { DatosLinea(it.productoId, it.cantidad, it.precioCobrado, it.id) },
                v.costoEnvio, v.comision, v.fechaDespacho, v.fechaEntrega,
            )
        },
        canales = canales.filter { it.activo || it.id == v.canalId },
        productos = productos.filter { it.activo || it.id in usados },
        guardar = { datos, listo -> vm.editar(id, datos, listo) },
        cerrar = cerrar,
        fechasDeEnvio = true,
    )
}

/** Línea en edición: textos tal como se tipean, se parsean al guardar. [id] null = Línea nueva. */
private class LineaForm(productoId: String?, cantidad: String, precio: String, val id: String? = null) {
    var productoId by mutableStateOf(productoId)
    var cantidad by mutableStateOf(cantidad)
    var precio by mutableStateOf(precio)
}

/**
 * Formulario de Venta reutilizable (registrar / editar). [canales] y [productos] son las opciones
 * a ofrecer; [guardar] llama a listo(null) si salió bien o listo(mensaje) con el error.
 */
@Composable
fun VentaFormulario(
    titulo: String,
    inicial: DatosVenta,
    canales: List<Canal>,
    productos: List<Producto>,
    guardar: (DatosVenta, listo: (String?) -> Unit) -> Unit,
    cerrar: () -> Unit,
    fechasDeEnvio: Boolean = false,
) {
    var fecha by remember { mutableStateOf(inicial.fechaVenta) }
    var despacho by remember { mutableStateOf(inicial.fechaDespacho) }
    var entrega by remember { mutableStateOf(inicial.fechaEntrega) }
    var canalId by remember { mutableStateOf(inicial.canalId) }
    var cliente by remember { mutableStateOf(inicial.cliente.orEmpty()) }
    val lineas = remember {
        mutableStateListOf<LineaForm>().apply {
            inicial.lineas.forEach { add(LineaForm(it.productoId, it.cantidad.toString(), formatearPesos(it.precioCobrado), it.id)) }
            if (isEmpty()) add(LineaForm(null, "1", ""))
        }
    }
    var envio by remember { mutableStateOf(formatearPesos(inicial.costoEnvio)) }
    var comision by remember { mutableStateOf(formatearPesos(inicial.comision)) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(titulo, style = MaterialTheme.typography.titleLarge)
        CampoFecha("Fecha de venta", fecha) { fecha = it }
        if (fechasDeEnvio) {
            CampoFechaOpcional("Fecha de despacho", despacho, fecha) { despacho = it }
            CampoFechaOpcional("Fecha de entrega", entrega, despacho ?: fecha) { entrega = it }
        }
        Selector("Canal", canales.map { it.id to it.nombre }, canalId) { canalId = it }
        OutlinedTextField(cliente, { cliente = it }, label = { Text("Cliente (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Text("Productos", style = MaterialTheme.typography.titleMedium)
        lineas.forEachIndexed { i, linea ->
            LineaFila(linea, productos, quitar = { lineas.removeAt(i); Unit }.takeIf { lineas.size > 1 })
        }
        OutlinedButton(onClick = { lineas.add(LineaForm(null, "1", "")) }) { Text("Agregar Producto") }

        CampoPesos("Costo de envío", envio) { envio = it }
        CampoPesos("Comisión", comision) { comision = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = cerrar) { Text("Cancelar") }
            Button(onClick = {
                val datosLineas = lineas.map { l ->
                    val productoId = l.productoId ?: return@Button run { error = "Elegí un Producto en cada línea" }
                    val precio = parsearPesos(l.precio) ?: return@Button run { error = "Precio inválido" }
                    DatosLinea(productoId, l.cantidad.trim().toIntOrNull() ?: 0, precio, l.id)
                }
                val costoEnvio = parsearPesos(envio) ?: return@Button run { error = "Costo de envío inválido" }
                val costoComision = parsearPesos(comision) ?: return@Button run { error = "Comisión inválida" }
                guardar(DatosVenta(fecha, canalId, cliente, datosLineas, costoEnvio, costoComision, despacho, entrega)) { e ->
                    if (e == null) cerrar() else error = e
                }
            }) { Text("Guardar") }
        }
    }
}

/** Una Línea: Producto (precarga el Precio cobrado con el Precio de referencia), cantidad y precio. */
@Composable
private fun LineaFila(linea: LineaForm, productos: List<Producto>, quitar: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Selector("Producto", productos.map { it.id to it.nombre }, linea.productoId, Modifier.weight(1f)) { id ->
                linea.productoId = id
                productos.firstOrNull { it.id == id }?.let { linea.precio = formatearPesos(it.precioReferencia) }
            }
            quitar?.let { IconButton(onClick = it) { Icon(Icons.Filled.Close, contentDescription = "Quitar Producto") } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                linea.cantidad, { linea.cantidad = it }, label = { Text("Cantidad") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
            CampoPesos("Precio cobrado", linea.precio, Modifier.weight(1f)) { linea.precio = it }
        }
    }
}

@Composable
private fun CampoPesos(etiqueta: String, valor: String, modifier: Modifier = Modifier, cambiar: (String) -> Unit) {
    OutlinedTextField(
        valor, cambiar, label = { Text(etiqueta) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier.fillMaxWidth(),
    )
}

/** Desplegable de opciones (id, nombre). [elegido] null = sin elegir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Selector(etiqueta: String, opciones: List<Pair<String, String>>, elegido: String?, modifier: Modifier = Modifier, elegir: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(abierto, { abierto = it }, modifier) {
        OutlinedTextField(
            opciones.firstOrNull { it.first == elegido }?.second.orEmpty(), {}, readOnly = true,
            label = { Text(etiqueta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(abierto, { abierto = false }) {
            opciones.forEach { (id, nombre) ->
                DropdownMenuItem(text = { Text(nombre) }, onClick = { elegir(id); abierto = false })
            }
        }
    }
}

/** Fecha que puede faltar: botón para cargarla ([sugerida] por defecto) o la fecha con un botón para quitarla. */
@Composable
private fun CampoFechaOpcional(etiqueta: String, fecha: LocalDate?, sugerida: LocalDate, cambiar: (LocalDate?) -> Unit) {
    if (fecha == null) {
        OutlinedButton(onClick = { cambiar(sugerida) }, modifier = Modifier.fillMaxWidth()) { Text("Agregar $etiqueta") }
        return
    }
    Row {
        Box(Modifier.weight(1f)) { CampoFecha(etiqueta, fecha) { cambiar(it) } }
        IconButton(onClick = { cambiar(null) }) { Icon(Icons.Filled.Close, contentDescription = "Quitar $etiqueta") }
    }
}

/** Campo de fecha que abre un DatePicker (reutilizable para despacho/entrega). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(etiqueta: String, fecha: LocalDate, cambiar: (LocalDate) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth()) { Text("$etiqueta: ${formatearFecha(fecha)}") }
    if (abierto) {
        // DatePicker trabaja en millis UTC.
        val estado = rememberDatePickerState(fecha.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { cambiar(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    abierto = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } },
        ) { DatePicker(estado) }
    }
}
