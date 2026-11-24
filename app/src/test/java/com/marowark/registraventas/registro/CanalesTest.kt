package com.marowark.registraventas.registro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CanalesTest {
    private val prueba = RegistroDePrueba()
    private val registro = prueba.registro
    private val precargados = listOf("En mano", "Facebook Marketplace", "Mercado Libre", "Tienda web")

    @After fun cerrar() = prueba.cerrar()

    @Test
    fun base_nueva_trae_los_canales_precargados_activos() = runBlocking {
        assertEquals(precargados, registro.canalesActivos().first().map { it.nombre })
    }

    @Test
    fun reabrir_la_base_no_duplica_los_precargados() = runBlocking {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        val primera = RegistroDatabase.crear(contexto, prueba.reloj)
        Registro(primera, prueba.reloj).crearCanal("Feria")
        primera.close()

        val segunda = RegistroDatabase.crear(contexto, prueba.reloj)
        try {
            assertEquals(
                listOf("En mano", "Facebook Marketplace", "Feria", "Mercado Libre", "Tienda web"),
                Registro(segunda, prueba.reloj).canales().first().map { it.nombre },
            )
        } finally {
            segunda.close()
        }
    }

    @Test
    fun crear_canal_lo_lista_y_rechaza_nombre_duplicado_o_vacio() = runBlocking {
        val feria = registro.crearCanal(" Feria ").valor()

        assertEquals("Feria", feria.nombre)
        assertEquals(Resultado.Error(Motivo.NOMBRE_DUPLICADO), registro.crearCanal("FERIA"))
        assertEquals(Resultado.Error(Motivo.NOMBRE_DUPLICADO), registro.crearCanal("mercado libre"))
        assertEquals(Resultado.Error(Motivo.NOMBRE_VACIO), registro.crearCanal("  "))
        assertEquals(5, registro.canales().first().size)
    }

    @Test
    fun editar_renombra_y_rechaza_nombre_de_otro_canal() = runBlocking {
        val feria = registro.crearCanal("Feria").valor()
        prueba.reloj.avanzarDias(1)

        val editada = registro.editarCanal(feria.id, "Feria del parque").valor()

        assertEquals("Feria del parque", editada.nombre)
        assertEquals(feria.ultimaModificacion.plusSeconds(86400), editada.ultimaModificacion)
        assertEquals(Resultado.Error(Motivo.NOMBRE_DUPLICADO), registro.editarCanal(feria.id, "En Mano"))
    }

    @Test
    fun desactivado_sale_de_activos_pero_sigue_en_todos_y_se_reactiva() = runBlocking {
        val enMano = registro.canales().first().single { it.nombre == "En mano" }

        registro.desactivarCanal(enMano.id)

        assertEquals(precargados - "En mano", registro.canalesActivos().first().map { it.nombre })
        assertEquals(false, registro.canales().first().single { it.id == enMano.id }.activo)

        registro.reactivarCanal(enMano.id)

        assertEquals(precargados, registro.canalesActivos().first().map { it.nombre })
    }

    @Test
    fun borrar_sin_ventas_lo_quita() = runBlocking {
        val feria = registro.crearCanal("Feria").valor()

        assertEquals(Resultado.Ok(Unit), registro.borrarCanal(feria.id))
        assertEquals(precargados, registro.canales().first().map { it.nombre })
    }
}
