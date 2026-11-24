package com.marowark.registraventas.ventas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marowark.registraventas.registro.Venta

/** ⋮ → Ventas canceladas: tocar una Venta la reactiva (vuelve a la lista de Ventas). */
@Composable
fun VentasCanceladasScreen(vm: VentasViewModel = viewModel()) {
    val ventas by vm.canceladas.collectAsStateWithLifecycle()
    var aReactivar by remember { mutableStateOf<Venta?>(null) }
    aReactivar?.let { v -> Confirmar("¿Reactivar esta Venta?", "Reactivar", { vm.reactivar(v.id) }, { aReactivar = null }) }
    Box(Modifier.fillMaxSize()) {
        if (ventas.isEmpty()) Text("No hay Ventas canceladas", Modifier.align(Alignment.Center))
        LazyColumn(Modifier.fillMaxSize()) {
            items(ventas, key = { it.id }) { VentaFila(it, Modifier.clickable { aReactivar = it }) }
        }
    }
}

/** Diálogo sí/no; [cerrar] se llama siempre al salir. */
@Composable
fun Confirmar(pregunta: String, accion: String, confirmar: () -> Unit, cerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(pregunta) },
        confirmButton = { TextButton(onClick = { confirmar(); cerrar() }) { Text(accion) } },
        dismissButton = { TextButton(onClick = cerrar) { Text("Volver") } },
    )
}
