package com.marowark.registraventas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.marowark.registraventas.catalogo.CatalogoScreen
import com.marowark.registraventas.ventas.EditarVentaScreen
import com.marowark.registraventas.ventas.RegistrarVentaScreen
import com.marowark.registraventas.ventas.VentasScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
private fun App() {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    Scaffold(
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
        }
    }
}

// Placeholder hasta que cada ticket implemente su pestaña.
@Composable
private fun Vacia(titulo: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(titulo) }
}
