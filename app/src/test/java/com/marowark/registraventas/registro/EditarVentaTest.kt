package com.marowark.registraventas.registro

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class EditarVentaTest {
    private val prueba = RegistroDePrueba(hoy = LocalDate.of(2026, 3, 10))
    private val registro = prueba.registro

    @After fun cerrar() = prueba.cerrar()

    private suspend fun canal(nombre: String) = registro.canales().first().single { it.nombre == nombre }

    /** Venta de un cuaderno (2 × 1000) y una agenda (1 × 500) por Mercado Libre. */
    private suspend fun ventaInicial(): Triple<Venta, Producto, Producto> {
        val cuaderno = registro.crearProducto("Cuaderno", 1000).valor()
        val agenda = registro.crearProducto("Agenda", 500).valor()
        val venta = registro.registrarVenta(
            DatosVenta(registro.hoy(), canal("Mercado Libre").id, "Ana", listOf(DatosLinea(cuaderno.id, 2, 1000), DatosLinea(agenda.id, 1, 500))),
        ).valor()
        return Triple(venta, cuaderno, agenda)
    }

    private fun Venta.aDatos() = DatosVenta(
        fechaVenta, canalId, cliente, lineas.map { DatosLinea(it.productoId, it.cantidad, it.precioCobrado, it.id) },
        costoEnvio, comision, fechaDespacho, fechaEntrega,
    )

    @Test
    fun editar_cambia_campos_y_lineas_conservando_ids_y_marcas() = runBlocking {
        val (venta, cuaderno, _) = ventaInicial()
        val lapiz = registro.crearProducto("Lápiz", 100).valor()
        val cuadernoLinea = venta.lineas.single { it.productoId == cuaderno.id }
        prueba.reloj.avanzar(Duration.ofMinutes(5))
        val despues = Instant.parse("2026-03-10T00:05:00Z")

        val editada = registro.editarVenta(
            venta.id,
            DatosVenta(
                fechaVenta = venta.fechaVenta.minusDays(1),
                canalId = canal("En mano").id,
                cliente = " Bea ",
                // Cuaderno modificado, agenda quitada, lápiz agregado.
                lineas = listOf(DatosLinea(cuaderno.id, 3, 900, cuadernoLinea.id), DatosLinea(lapiz.id, 4, 100)),
                costoEnvio = 200,
                comision = 50,
            ),
        ).valor()

        assertEquals(editada, registro.ventasVigentes().first().single())
        assertEquals(LocalDate.of(2026, 3, 9), editada.fechaVenta)
        assertEquals("En mano", editada.canalNombre)
        assertEquals("Bea", editada.cliente)
        assertEquals(200L, editada.costoEnvio)
        assertEquals(50L, editada.comision)
        assertEquals(3 * 900L + 4 * 100, editada.monto)
        assertEquals(despues, editada.ultimaModificacion)
        val porProducto = editada.lineas.associateBy { it.productoId }
        assertEquals(setOf(cuaderno.id, lapiz.id), porProducto.keys)
        assertEquals(cuadernoLinea.id, porProducto.getValue(cuaderno.id).id)
        assertEquals(despues, porProducto.getValue(cuaderno.id).ultimaModificacion)
        assertEquals(despues, porProducto.getValue(lapiz.id).ultimaModificacion)
    }

    @Test
    fun linea_sin_cambios_conserva_su_marca_pero_la_venta_se_actualiza() = runBlocking {
        val (venta, cuaderno, agenda) = ventaInicial()
        prueba.reloj.avanzar(Duration.ofMinutes(5))

        val sinAgenda = venta.aDatos().let { d -> d.copy(lineas = d.lineas.filter { it.productoId == cuaderno.id }) }
        val editada = registro.editarVenta(venta.id, sinAgenda).valor()

        assertEquals(listOf(cuaderno.id), editada.lineas.map { it.productoId })
        assertEquals(venta.lineas.single { it.productoId == cuaderno.id }, editada.lineas.single())
        assertEquals(Instant.parse("2026-03-10T00:05:00Z"), editada.ultimaModificacion)
        // Una Línea quitada no deja rastro (no bloquea borrar el Producto).
        assertEquals(Resultado.Ok(Unit), registro.borrarProducto(agenda.id))
    }

    @Test
    fun producto_y_canal_inactivos_se_conservan_al_editar() = runBlocking {
        val (venta, cuaderno, _) = ventaInicial()
        registro.desactivarProducto(cuaderno.id)
        registro.desactivarCanal(venta.canalId)

        val editada = registro.editarVenta(venta.id, venta.aDatos().copy(cliente = "Carla")).valor()

        assertEquals("Mercado Libre", editada.canalNombre)
        assertEquals(venta.lineas.map { it.productoId }, editada.lineas.map { it.productoId })
        assertEquals(venta.id, registro.venta(venta.id)?.id)
    }

    @Test
    fun editar_valida_como_registrar_sin_cambiar_nada() = runBlocking {
        val (venta, _, _) = ventaInicial()
        val datos = venta.aDatos()

        assertEquals(Resultado.Error(Motivo.SIN_CANAL), registro.editarVenta(venta.id, datos.copy(canalId = null)))
        assertEquals(Resultado.Error(Motivo.SIN_LINEAS), registro.editarVenta(venta.id, datos.copy(lineas = emptyList())))
        assertEquals(Resultado.Error(Motivo.CANTIDAD_INVALIDA), registro.editarVenta(venta.id, datos.copy(lineas = datos.lineas.map { it.copy(cantidad = 0) })))
        assertEquals(Resultado.Error(Motivo.MONTO_NEGATIVO), registro.editarVenta(venta.id, datos.copy(comision = -1)))
        assertEquals(venta, registro.venta(venta.id))
    }

    @Test
    fun editar_fechas_respeta_el_orden_venta_despacho_entrega() = runBlocking {
        val (venta, _, _) = ventaInicial()
        val datos = venta.aDatos()
        val hoy = registro.hoy()

        assertEquals(Resultado.Error(Motivo.ENTREGA_SIN_DESPACHO), registro.editarVenta(venta.id, datos.copy(fechaEntrega = hoy)))
        assertEquals(Resultado.Error(Motivo.FECHAS_FUERA_DE_ORDEN), registro.editarVenta(venta.id, datos.copy(fechaDespacho = hoy.minusDays(1))))
        assertEquals(
            Resultado.Error(Motivo.FECHAS_FUERA_DE_ORDEN),
            registro.editarVenta(venta.id, datos.copy(fechaDespacho = hoy.plusDays(2), fechaEntrega = hoy.plusDays(1))),
        )
        assertEquals(venta, registro.venta(venta.id))

        // Entrega en mano: las tres fechas iguales.
        val enMano = registro.editarVenta(venta.id, datos.copy(fechaDespacho = hoy, fechaEntrega = hoy)).valor()
        assertEquals(hoy, enMano.fechaDespacho)
        assertEquals(hoy, enMano.fechaEntrega)

        // Quitar las fechas vuelve a pendiente.
        val pendiente = registro.editarVenta(venta.id, datos.copy(fechaDespacho = null, fechaEntrega = null)).valor()
        assertNull(pendiente.fechaDespacho)
        assertNull(pendiente.fechaEntrega)
    }
}
