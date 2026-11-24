package com.marowark.registraventas.ventas

import android.app.Application
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
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
import com.marowark.registraventas.registro.Producto
import com.marowark.registraventas.registro.Resultado
import com.marowark.registraventas.registro.Venta
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class VentasViewModel(app: Application) : AndroidViewModel(app) {
    private val registro = (app as RegistraVentasApp).registro

    val ventas = registro.ventasVigentes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val canalesActivos = registro.canalesActivos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val productosActivos = registro.productosActivos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun hoy(): LocalDate = registro.hoy()

    /** Corre [op] y pasa a [listo] null si salió bien o el mensaje del error. */
    fun ejecutar(listo: (String?) -> Unit = {}, op: suspend () -> Resultado<*>) = viewModelScope.launch {
        listo((op() as? Resultado.Error)?.motivo?.mensaje)
    }

    fun registrar(datos: DatosVenta, listo: (String?) -> Unit) = ejecutar(listo) { registro.registrarVenta(datos) }

    val canales = registro.canales().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val productos = registro.productos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    suspend fun venta(id: String) = registro.venta(id)
    fun editar(id: String, datos: DatosVenta, listo: (String?) -> Unit) = ejecutar(listo) { registro.editarVenta(id, datos) }

    val canceladas = registro.ventasCanceladas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun cancelar(id: String) = viewModelScope.launch { registro.cancelarVenta(id) }
    fun reactivar(id: String) = viewModelScope.launch { registro.reactivarVenta(id) }

    fun crearProducto(nombre: String, precio: Long, listo: (Resultado<Producto>) -> Unit) = viewModelScope.launch {
        listo(registro.crearProducto(nombre, precio))
    }
}

private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy")
fun formatearFecha(fecha: LocalDate): String = fecha.format(formatoFecha)

/** Pestaña Ventas: lista de Ventas vigentes, botón + para registrar y toque en una Venta para editarla. */
@Composable
fun VentasScreen(nueva: () -> Unit, editar: (String) -> Unit, vm: VentasViewModel = viewModel()) {
    val ventas by vm.ventas.collectAsStateWithLifecycle()
    var aCancelar by remember { mutableStateOf<Venta?>(null) }
    aCancelar?.let { v -> Confirmar("¿Cancelar esta Venta?", "Cancelar Venta", { vm.cancelar(v.id) }, { aCancelar = null }) }
    Box(Modifier.fillMaxSize()) {
        if (ventas.isEmpty()) Text("Todavía no hay Ventas", Modifier.align(Alignment.Center))
        LazyColumn(Modifier.fillMaxSize()) {
            items(ventas, key = { it.id }) { VentaFila(it, Modifier.combinedClickable(onClick = { editar(it.id) }, onLongClick = { aCancelar = it })) }
        }
        FloatingActionButton(onClick = nueva, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Registrar Venta")
        }
    }
}

/** Una Venta de la lista: fecha, Canal, Cliente y Monto. */
@Composable
fun VentaFila(venta: Venta, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(listOfNotNull(venta.canalNombre, venta.cliente).joinToString(" · ")) },
        supportingContent = { Text(formatearFecha(venta.fechaVenta)) },
        trailingContent = { Text(formatearPesos(venta.monto)) },
        modifier = modifier,
    )
}
