package com.marowark.registraventas.ventas

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marowark.registraventas.RegistraVentasApp
import com.marowark.registraventas.formatearPesos
import com.marowark.registraventas.registro.DatosVenta
import com.marowark.registraventas.registro.EstadoEnvio
import com.marowark.registraventas.registro.Producto
import com.marowark.registraventas.registro.Resultado
import com.marowark.registraventas.registro.Venta
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class VentasViewModel(app: Application) : AndroidViewModel(app) {
    private val registro = (app as RegistraVentasApp).registro

    /** Filtro rápido por Estado de envío; null = todas. */
    val filtro = MutableStateFlow<EstadoEnvio?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ventas = filtro.flatMapLatest { registro.ventasVigentes(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val canalesActivos = registro.canalesActivos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val productosActivos = registro.productosActivos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun hoy(): LocalDate = registro.hoy()

    /** Corre [op] y pasa a [listo] null si salió bien o el mensaje del error. */
    fun ejecutar(listo: (String?) -> Unit = {}, op: suspend () -> Resultado<*>) = viewModelScope.launch {
        listo((op() as? Resultado.Error)?.motivo?.mensaje)
    }

    fun registrar(datos: DatosVenta, listo: (String?) -> Unit) = ejecutar(listo) { registro.registrarVenta(datos) }

    fun despachar(id: String, fecha: LocalDate, listo: (String?) -> Unit) = ejecutar(listo) { registro.despachar(id, fecha) }
    fun entregar(id: String, fecha: LocalDate, listo: (String?) -> Unit) = ejecutar(listo) { registro.entregar(id, fecha) }
    fun quitarDespacho(id: String, listo: (String?) -> Unit) = ejecutar(listo) { registro.quitarDespacho(id) }
    fun quitarEntrega(id: String, listo: (String?) -> Unit) = ejecutar(listo) { registro.quitarEntrega(id) }
    val canceladas = registro.ventasCanceladas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun cancelar(id: String) = viewModelScope.launch { registro.cancelarVenta(id) }
    fun reactivar(id: String) = viewModelScope.launch { registro.reactivarVenta(id) }

    fun crearProducto(nombre: String, precio: Long, listo: (Resultado<Producto>) -> Unit) = viewModelScope.launch {
        listo(registro.crearProducto(nombre, precio))
    }
}

private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy")
fun formatearFecha(fecha: LocalDate): String = fecha.format(formatoFecha)

/** Pestaña Ventas: filtros por Estado de envío, lista de Ventas vigentes y botón + para registrar. */
@Composable
fun VentasScreen(nueva: () -> Unit, vm: VentasViewModel = viewModel()) {
    val ventas by vm.ventas.collectAsStateWithLifecycle()
    val filtro by vm.filtro.collectAsStateWithLifecycle()
    var envio by remember { mutableStateOf<String?>(null) }
    var aCancelar by remember { mutableStateOf<Venta?>(null) }
    aCancelar?.let { v -> Confirmar("¿Cancelar esta Venta?", "Cancelar Venta", { vm.cancelar(v.id) }, { aCancelar = null }) }
    Box(Modifier.fillMaxSize()) {
        if (ventas.isEmpty()) Text("No hay Ventas", Modifier.align(Alignment.Center))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(EstadoEnvio.PENDIENTE_DE_DESPACHO to "Pendientes de despacho", EstadoEnvio.EN_CAMINO to "En camino").forEach { (estado, texto) ->
                    FilterChip(
                        selected = filtro == estado,
                        onClick = { vm.filtro.value = if (filtro == estado) null else estado },
                        label = { Text(texto) },
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(ventas, key = { it.id }) {
                    VentaFila(it, Modifier.combinedClickable(onClick = { envio = it.id }, onLongClick = { aCancelar = it }))
                }
            }
        }
        FloatingActionButton(onClick = nueva, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Registrar Venta")
        }
    }
    ventas.find { it.id == envio }?.let { EnvioDialogo(it, vm, cerrar = { envio = null }) }
}

/** Una Venta de la lista: fecha, Canal, Cliente, Monto y Estado de envío. */
@Composable
fun VentaFila(venta: Venta, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(listOfNotNull(venta.canalNombre, venta.cliente).joinToString(" · ")) },
        supportingContent = { Text("${formatearFecha(venta.fechaVenta)} · ${venta.estadoEnvio.nombre}") },
        trailingContent = { Text(formatearPesos(venta.monto)) },
        modifier = modifier,
    )
}

/** Despachar, entregar o quitar esas fechas de una Venta (fecha = hoy, editable). */
@Composable
private fun EnvioDialogo(venta: Venta, vm: VentasViewModel, cerrar: () -> Unit) {
    var fecha by remember { mutableStateOf(vm.hoy()) }
    var error by remember { mutableStateOf<String?>(null) }
    val listo: (String?) -> Unit = { error = it; if (it == null) cerrar() }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(venta.estadoEnvio.nombre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                venta.fechaDespacho?.let { Text("Despacho: ${formatearFecha(it)}") }
                venta.fechaEntrega?.let { Text("Entrega: ${formatearFecha(it)}") }
                if (venta.estadoEnvio != EstadoEnvio.ENTREGADA) CampoFecha("Fecha", fecha) { fecha = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when (venta.estadoEnvio) {
                EstadoEnvio.PENDIENTE_DE_DESPACHO -> TextButton(onClick = { vm.despachar(venta.id, fecha, listo) }) { Text("Despachar") }
                EstadoEnvio.EN_CAMINO -> TextButton(onClick = { vm.entregar(venta.id, fecha, listo) }) { Text("Entregar") }
                EstadoEnvio.ENTREGADA -> TextButton(onClick = { vm.quitarEntrega(venta.id, listo) }) { Text("Quitar entrega") }
            }
        },
        dismissButton = {
            if (venta.estadoEnvio == EstadoEnvio.EN_CAMINO) TextButton(onClick = { vm.quitarDespacho(venta.id, listo) }) { Text("Quitar despacho") }
            else TextButton(onClick = cerrar) { Text("Cerrar") }
        },
    )
}
