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
import com.marowark.registraventas.ventas.RegistrarVentaScreen
import com.marowark.registraventas.ventas.VentasCanceladasScreen
import com.marowark.registraventas.ventas.VentasScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.Alignment
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
    // Menú ⋮: agregar opciones acá (Exportar/Importar respaldo).
    val menu = listOf<Pair<String, () -> Unit>>(
        "Ventas canceladas" to { nav.navigate(VentasCanceladasRuta) { launchSingleTop = true } },
        "Exportar respaldo" to { exportar.launch("registra-ventas-${java.time.LocalDate.now()}.json") },
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
            composable<EstadisticasRuta> { Vacia("Estadísticas") }
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

// Placeholder hasta que cada ticket implemente su pestaña.
@Composable
private fun Vacia(titulo: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(titulo) }
}
