package com.marowark.registraventas

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import com.marowark.registraventas.catalogo.CatalogoScreen
import com.marowark.registraventas.ventas.EditarVentaScreen
import com.marowark.registraventas.estadisticas.EstadisticasScreen
import com.marowark.registraventas.ventas.RegistrarVentaScreen
import com.marowark.registraventas.ventas.VentasCanceladasScreen
import com.marowark.registraventas.ventas.VentasScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.TextButton
import com.marowark.registraventas.registro.Resultado
import com.marowark.registraventas.ventas.Confirmar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object VentasRuta
@Serializable object EstadisticasRuta
@Serializable object CatalogoRuta
@Serializable object RegistrarVentaRuta
@Serializable data class EditarVentaRuta(val id: String)
@Serializable object VentasCanceladasRuta

private data class Pestania(val ruta: Any, val clase: KClass<*>, val titulo: String, val icono: ImageVector)

private val pestanias = listOf(
    Pestania(VentasRuta, VentasRuta::class, "Ventas", Icons.Filled.ShoppingCart),
    Pestania(EstadisticasRuta, EstadisticasRuta::class, "Estadísticas", Icons.Filled.DateRange),
    Pestania(CatalogoRuta, CatalogoRuta::class, "Catálogo", Icons.AutoMirrored.Filled.List),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                App()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val exportar = exportarRespaldo()
    val importar = importarRespaldo()
    // Menú ⋮: agregar opciones acá (Exportar/Importar respaldo).
    val menu = listOf<Pair<String, () -> Unit>>(
        "Ventas canceladas" to { nav.navigate(VentasCanceladasRuta) { launchSingleTop = true } },
        "Exportar respaldo" to { exportar.launch("registra-ventas-${java.time.LocalDate.now()}.json") },
        "Importar respaldo" to importar,
    )
    Scaffold(
        topBar = { TopAppBar(title = { Text("Registra Ventas") }, actions = { MenuMas(menu) }) },
        bottomBar = {
            NavigationBar {
                pestanias.forEach { p ->
                    NavigationBarItem(
                        selected = entrada?.destination?.hasRoute(p.clase) == true,
                        onClick = {
                            nav.navigate(p.ruta) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(p.icono, contentDescription = null) },
                        label = { Text(p.titulo) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = VentasRuta, modifier = Modifier.padding(padding)) {
            composable<VentasRuta> { VentasScreen(nueva = { nav.navigate(RegistrarVentaRuta) }, editar = { nav.navigate(EditarVentaRuta(it)) }) }
            composable<RegistrarVentaRuta> { RegistrarVentaScreen(cerrar = { nav.popBackStack() }) }
            composable<EditarVentaRuta> { EditarVentaScreen(it.toRoute<EditarVentaRuta>().id, cerrar = { nav.popBackStack() }) }
            composable<EstadisticasRuta> { EstadisticasScreen() }
            composable<CatalogoRuta> { CatalogoScreen() }
            composable<VentasCanceladasRuta> { VentasCanceladasScreen() }
        }
    }
}

/** SAF: el usuario elige dónde guardar; el JSON lo arma Registro.exportar(). */
@Composable
private fun exportarRespaldo(): androidx.activity.result.ActivityResultLauncher<String> {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    return rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        alcance.launch {
            val mensaje = try {
                val json = (contexto.applicationContext as RegistraVentasApp).registro.exportar()
                withContext(Dispatchers.IO) {
                    checkNotNull(contexto.contentResolver.openOutputStream(uri, "wt")) { "No se pudo abrir el archivo" }.use { it.write(json.toByteArray()) }
                }
                "Respaldo guardado"
            } catch (e: Exception) {
                "No se pudo guardar el respaldo: ${e.message}"
            }
            Toast.makeText(contexto, mensaje, Toast.LENGTH_LONG).show()
        }
    }
}

/** Confirma, deja elegir el archivo (SAF), fusiona con Registro.importar() y muestra el resumen. */
@Composable
private fun importarRespaldo(): () -> Unit {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var confirmando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<String?>(null) }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        alcance.launch {
            resultado = try {
                val json = withContext(Dispatchers.IO) {
                    checkNotNull(contexto.contentResolver.openInputStream(uri)) { "No se pudo abrir el archivo" }.use { it.readBytes().decodeToString() }
                }
                when (val r = (contexto.applicationContext as RegistraVentasApp).registro.importar(json)) {
                    is Resultado.Ok -> with(r.valor) {
                        "Ventas: $ventasAgregadas agregadas, $ventasActualizadas actualizadas\n" +
                            "Productos: $productosAgregados agregados, $productosActualizados actualizados\n" +
                            "Canales: $canalesAgregados agregados, $canalesActualizados actualizados"
                    }
                    is Resultado.Error -> r.motivo.mensaje + ". No se modificó nada."
                }
            } catch (e: Exception) {
                "No se pudo leer el respaldo: ${e.message}"
            }
        }
    }
    if (confirmando) {
        Confirmar(
            "¿Importar un respaldo? Se fusiona con tus datos sin borrar nada; si un registro está en ambos lados gana el modificado más recientemente.",
            "Elegir archivo", confirmar = { abrir.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, cerrar = { confirmando = false },
        )
    }
    resultado?.let {
        AlertDialog(
            onDismissRequest = { resultado = null },
            title = { Text("Importar respaldo") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { resultado = null }) { Text("Aceptar") } },
        )
    }
    return { confirmando = true }
}

@Composable
private fun MenuMas(opciones: List<Pair<String, () -> Unit>>) {
    var abierto by remember { mutableStateOf(false) }
    IconButton(onClick = { abierto = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones") }
    DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
        opciones.forEach { (titulo, accion) ->
            DropdownMenuItem(text = { Text(titulo) }, onClick = { abierto = false; accion() })
        }
    }
}
