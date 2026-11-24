package com.marowark.registraventas.estadisticas

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marowark.registraventas.RegistraVentasApp
import com.marowark.registraventas.formatearPesos
import com.marowark.registraventas.registro.Estadisticas
import com.marowark.registraventas.registro.Periodo
import com.marowark.registraventas.registro.TipoPeriodo
import com.marowark.registraventas.ventas.formatearFecha
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import java.util.Locale

class EstadisticasViewModel(app: Application) : AndroidViewModel(app) {
    private val registro = (app as RegistraVentasApp).registro

    val periodo = MutableStateFlow(Periodo(TipoPeriodo.MES, registro.hoy()))

    @OptIn(ExperimentalCoroutinesApi::class)
    val estadisticas = periodo.flatMapLatest { registro.estadisticas(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Cambiar de tipo vuelve al Período actual. */
    fun tipo(tipo: TipoPeriodo) { periodo.value = Periodo(tipo, registro.hoy()) }
    fun mover(adelante: Boolean) { periodo.value = periodo.value.let { if (adelante) it.siguiente() else it.anterior() } }
}

private val formatoMes = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es-AR"))

private fun titulo(p: Periodo) = when (p.tipo) {
    TipoPeriodo.SEMANA -> "${formatearFecha(p.inicio)} – ${formatearFecha(p.fin)}"
    TipoPeriodo.MES -> p.inicio.format(formatoMes).replaceFirstChar { it.uppercase() }
    TipoPeriodo.ANIO -> p.inicio.year.toString()
}

/** Pestaña Estadísticas: elegir semana/mes/año, navegar ‹ › y ver totales, por Producto y por Canal. */
@Composable
fun EstadisticasScreen(vm: EstadisticasViewModel = viewModel()) {
    val periodo by vm.periodo.collectAsStateWithLifecycle()
    val e by vm.estadisticas.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TipoPeriodo.SEMANA to "Semana", TipoPeriodo.MES to "Mes", TipoPeriodo.ANIO to "Año").forEach { (t, nombre) ->
                FilterChip(selected = periodo.tipo == t, onClick = { vm.tipo(t) }, label = { Text(nombre) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.mover(false) }) { Text("‹", style = MaterialTheme.typography.titleLarge) }
            Text(titulo(periodo), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { vm.mover(true) }) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        val actual = e
        when {
            actual == null -> {}
            actual.cantidadVentas == 0 -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No hay Ventas en este período") }
            else -> Detalle(actual)
        }
    }
}

@Composable
private fun Detalle(e: Estadisticas) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Seccion("Totales")
        Par("Ventas", e.cantidadVentas.toString())
        Par("Unidades", e.unidades.toString())
        Par("Monto", formatearPesos(e.monto))
        Par("Costos de envío", formatearPesos(e.costosEnvio))
        Par("Comisiones", formatearPesos(e.comisiones))
        Par("Neto", formatearPesos(e.neto), negrita = true)

        Seccion("Por Producto")
        Fila("Producto", "Unid.", "Monto", encabezado = true)
        e.porProducto.forEach { Fila(it.nombre, it.unidades.toString(), formatearPesos(it.monto)) }

        Seccion("Por Canal")
        Fila("Canal", "Monto", "Envíos", "Comis.", "Neto", encabezado = true)
        e.porCanal.forEach {
            Fila(it.nombre, formatearPesos(it.monto), formatearPesos(it.costosEnvio), formatearPesos(it.comisiones), formatearPesos(it.neto))
        }
    }
}

@Composable
private fun Seccion(titulo: String) {
    Text(titulo, Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium)
    HorizontalDivider()
}

@Composable
private fun Par(nombre: String, valor: String, negrita: Boolean = false) {
    val peso = if (negrita) FontWeight.Bold else null
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(nombre, Modifier.weight(1f), fontWeight = peso)
        Text(valor, fontWeight = peso)
    }
}

/** Primera columna (nombre) más ancha; el resto alineado a la derecha. */
@Composable
private fun Fila(vararg celdas: String, encabezado: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        celdas.forEachIndexed { i, c -> Celda(c, i == 0, encabezado) }
    }
}

@Composable
private fun RowScope.Celda(texto: String, primera: Boolean, encabezado: Boolean) = Text(
    texto,
    Modifier.weight(if (primera) 1.6f else 1f),
    textAlign = if (primera) TextAlign.Start else TextAlign.End,
    fontWeight = if (encabezado) FontWeight.Bold else null,
    style = MaterialTheme.typography.bodySmall,
)
