package com.marowark.registraventas.registro

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class CancelarVentasTest {
    private val prueba = RegistroDePrueba(hoy = LocalDate.of(2026, 3, 10))
    private val registro = prueba.registro

    @After fun cerrar() = prueba.cerrar()

    private suspend fun registrar(): Venta {
        val canal = registro.canales().first().first()
        val p = registro.crearProducto("Cuaderno ${System.nanoTime()}", 100).valor()
        return registro.registrarVenta(DatosVenta(registro.hoy(), canal.id, null, listOf(DatosLinea(p.id, 1, 100)))).valor()
    }

    @Test
    fun cancelar_la_saca_de_vigentes_y_la_pasa_a_canceladas() = runBlocking {
        val queda = registrar()
        val cancelada = registrar()
        prueba.reloj.avanzar(Duration.ofMinutes(5))

        registro.cancelarVenta(cancelada.id)

        assertEquals(listOf(queda.id), registro.ventasVigentes().first().map { it.id })
        val enCanceladas = registro.ventasCanceladas().first().single()
        assertEquals(cancelada.id, enCanceladas.id)
        assertEquals(true, enCanceladas.cancelada)
        assertEquals(cancelada.ultimaModificacion.plus(Duration.ofMinutes(5)), enCanceladas.ultimaModificacion)
    }

    @Test
    fun reactivar_la_devuelve_a_vigentes() = runBlocking {
        val venta = registrar()
        registro.cancelarVenta(venta.id)
        prueba.reloj.avanzar(Duration.ofMinutes(5))

        registro.reactivarVenta(venta.id)

        assertEquals(emptyList<Venta>(), registro.ventasCanceladas().first())
        val vigente = registro.ventasVigentes().first().single()
        assertEquals(false, vigente.cancelada)
        assertEquals(venta.lineas, vigente.lineas)
        assertEquals(venta.ultimaModificacion.plus(Duration.ofMinutes(5)), vigente.ultimaModificacion)
    }
}
