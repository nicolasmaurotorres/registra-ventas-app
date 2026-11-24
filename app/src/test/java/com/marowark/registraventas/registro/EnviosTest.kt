package com.marowark.registraventas.registro

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class EnviosTest {
    private val prueba = RegistroDePrueba(hoy = LocalDate.of(2026, 3, 10))
    private val registro = prueba.registro
    private val hoy = registro.hoy()

    @After fun cerrar() = prueba.cerrar()

    private val producto by lazy { runBlocking { registro.crearProducto("Cuaderno", 100).valor() } }
    private val canal by lazy { runBlocking { registro.canales().first().first() } }

    private suspend fun venta(fecha: LocalDate = hoy): Venta =
        registro.registrarVenta(DatosVenta(fecha, canal.id, null, listOf(DatosLinea(producto.id, 1, 100)))).valor()

    private suspend fun listada(id: String) = registro.ventasVigentes().first().single { it.id == id }

    @Test
    fun venta_nueva_esta_pendiente_de_despacho() = runBlocking {
        assertEquals(EstadoEnvio.PENDIENTE_DE_DESPACHO, venta().estadoEnvio)
    }

    @Test
    fun despachar_y_entregar_avanzan_el_estado() = runBlocking {
        val v = venta(hoy.minusDays(3))
        prueba.reloj.avanzarDias(1)

        val despachada = registro.despachar(v.id, hoy.minusDays(2)).valor()
        assertEquals(hoy.minusDays(2), listada(v.id).fechaDespacho)
        assertEquals(EstadoEnvio.EN_CAMINO, despachada.estadoEnvio)
        assertEquals(prueba.reloj.instant(), despachada.ultimaModificacion)

        val entregada = registro.entregar(v.id, hoy).valor()
        assertEquals(hoy, listada(v.id).fechaEntrega)
        assertEquals(EstadoEnvio.ENTREGADA, entregada.estadoEnvio)
    }

    @Test
    fun entrega_en_mano_tres_fechas_iguales_es_valida() = runBlocking {
        val v = venta()
        registro.despachar(v.id, hoy).valor()
        assertEquals(EstadoEnvio.ENTREGADA, registro.entregar(v.id, hoy).valor().estadoEnvio)
    }

    @Test
    fun rechaza_fechas_fuera_de_orden_sin_cambiar_nada() = runBlocking {
        val v = venta()

        assertEquals(Resultado.Error(Motivo.ENTREGA_SIN_DESPACHO), registro.entregar(v.id, hoy))
        assertEquals(Resultado.Error(Motivo.FECHAS_FUERA_DE_ORDEN), registro.despachar(v.id, hoy.minusDays(1)))
        registro.despachar(v.id, hoy.plusDays(1)).valor()
        assertEquals(Resultado.Error(Motivo.FECHAS_FUERA_DE_ORDEN), registro.entregar(v.id, hoy))
        registro.entregar(v.id, hoy.plusDays(2)).valor()
        assertEquals(Resultado.Error(Motivo.FECHAS_FUERA_DE_ORDEN), registro.despachar(v.id, hoy.plusDays(3)))
        assertEquals(Resultado.Error(Motivo.ENTREGA_SIN_DESPACHO), registro.quitarDespacho(v.id))

        val sinCambios = listada(v.id)
        assertEquals(hoy.plusDays(1), sinCambios.fechaDespacho)
        assertEquals(hoy.plusDays(2), sinCambios.fechaEntrega)
    }

    @Test
    fun quitar_fechas_vuelve_al_estado_anterior() = runBlocking {
        val v = venta()
        registro.despachar(v.id, hoy).valor()
        registro.entregar(v.id, hoy).valor()

        assertEquals(EstadoEnvio.EN_CAMINO, registro.quitarEntrega(v.id).valor().estadoEnvio)
        assertNull(listada(v.id).fechaEntrega)
        assertEquals(EstadoEnvio.PENDIENTE_DE_DESPACHO, registro.quitarDespacho(v.id).valor().estadoEnvio)
        assertNull(listada(v.id).fechaDespacho)
    }

    @Test
    fun lista_filtrable_por_estado_de_envio() = runBlocking {
        val pendiente = venta(hoy.minusDays(2))
        val enCamino = venta(hoy.minusDays(1))
        val entregada = venta(hoy)
        registro.despachar(enCamino.id, hoy).valor()
        registro.despachar(entregada.id, hoy).valor()
        registro.entregar(entregada.id, hoy).valor()

        fun ids(estado: EstadoEnvio?) = runBlocking { registro.ventasVigentes(estado).first().map { it.id } }
        assertEquals(listOf(pendiente.id), ids(EstadoEnvio.PENDIENTE_DE_DESPACHO))
        assertEquals(listOf(enCamino.id), ids(EstadoEnvio.EN_CAMINO))
        assertEquals(listOf(entregada.id), ids(EstadoEnvio.ENTREGADA))
        assertEquals(listOf(entregada.id, enCamino.id, pendiente.id), ids(null))
    }
}
