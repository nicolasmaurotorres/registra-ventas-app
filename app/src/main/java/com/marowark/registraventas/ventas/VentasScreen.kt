package com.marowark.registraventas.ventas

import android.app.Application
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
}

private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy")
fun formatearFecha(fecha: LocalDate): String = fecha.format(formatoFecha)

/** Pestaña Ventas: lista de Ventas vigentes y botón + para registrar. */
@Composable
fun VentasScreen(nueva: () -> Unit, vm: VentasViewModel = viewModel()) {
    val ventas by vm.ventas.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        if (ventas.isEmpty()) Text("Todavía no hay Ventas", Modifier.align(Alignment.Center))
        LazyColumn(Modifier.fillMaxSize()) {
            items(ventas, key = { it.id }) { VentaFila(it) }
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
