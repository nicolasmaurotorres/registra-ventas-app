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
class VentasTest {
    private val prueba = RegistroDePrueba(hoy = LocalDate.of(2026, 3, 10))
    private val registro = prueba.registro

    @After fun cerrar() = prueba.cerrar()

    private suspend fun canal() = registro.canales().first().single { it.nombre == "Mercado Libre" }
    private val mercadoLibre by lazy { runBlocking { canal() } }

    private suspend fun datos(
        productoId: String,
        canalId: String? = mercadoLibre.id,
        fecha: LocalDate = registro.hoy(),
        cantidad: Int = 1,
        precio: Long = 1000,
    ) = DatosVenta(fecha, canalId, cliente = null, lineas = listOf(DatosLinea(productoId, cantidad, precio)))

    @Test
    fun registrar_lista_la_venta_con_sus_lineas_y_monto() = runBlocking {
        val cuaderno = registro.crearProducto("Cuaderno", 150050).valor()
        val agenda = registro.crearProducto("Agenda", 2000).valor()
        val ml = canal()

        val venta = registro.registrarVenta(
            DatosVenta(
                fechaVenta = registro.hoy(),
                canalId = ml.id,
                cliente = "  Ana ",
                lineas = listOf(DatosLinea(cuaderno.id, 2, 150050), DatosLinea(agenda.id, 1, 1500)),
                costoEnvio = 300,
                comision = 450,
            ),
        ).valor()

        val listada = registro.ventasVigentes().first().single()
        assertEquals(venta, listada)
        assertEquals(LocalDate.of(2026, 3, 10), listada.fechaVenta)
        assertEquals("Mercado Libre", listada.canalNombre)
        assertEquals("Ana", listada.cliente)
        assertEquals(2 * 150050L + 1500, listada.monto)
        assertEquals(listOf(cuaderno.id to 2, agenda.id to 1), listada.lineas.map { it.productoId to it.cantidad })
        assertEquals(300L, listada.costoEnvio)
        assertEquals(450L, listada.comision)
        assertNull(listada.fechaDespacho)
        assertNull(listada.fechaEntrega)
        assertEquals(false, listada.cancelada)
    }

    @Test
    fun cliente_en_blanco_queda_vacio_y_costos_por_defecto_cero() = runBlocking {
        val p = registro.crearProducto("Cuaderno", 100).valor()

        val venta = registro.registrarVenta(DatosVenta(registro.hoy(), canal().id, " ", listOf(DatosLinea(p.id, 1, 100)))).valor()

        assertNull(venta.cliente)
        assertEquals(0L, venta.costoEnvio)
        assertEquals(0L, venta.comision)
    }

    @Test
    fun lista_mas_recientes_primero() = runBlocking {
        val p = registro.crearProducto("Cuaderno", 100).valor()
        val hoy = registro.hoy()

        registro.registrarVenta(datos(p.id, fecha = hoy.minusDays(2), cantidad = 1))
        registro.registrarVenta(datos(p.id, fecha = hoy, cantidad = 2))
        registro.registrarVenta(datos(p.id, fecha = hoy.minusDays(1), cantidad = 3))

        assertEquals(listOf(hoy, hoy.minusDays(1), hoy.minusDays(2)), registro.ventasVigentes().first().map { it.fechaVenta })
    }

    @Test
    fun rechaza_venta_invalida_sin_guardar_nada() = runBlocking {
        val p = registro.crearProducto("Cuaderno", 100).valor()
        val valida = datos(p.id)

        assertEquals(Resultado.Error(Motivo.SIN_CANAL), registro.registrarVenta(valida.copy(canalId = null)))
        assertEquals(Resultado.Error(Motivo.SIN_LINEAS), registro.registrarVenta(valida.copy(lineas = emptyList())))
        assertEquals(Resultado.Error(Motivo.CANTIDAD_INVALIDA), registro.registrarVenta(datos(p.id, cantidad = 0)))
        assertEquals(Resultado.Error(Motivo.MONTO_NEGATIVO), registro.registrarVenta(datos(p.id, precio = -1)))
        assertEquals(Resultado.Error(Motivo.MONTO_NEGATIVO), registro.registrarVenta(valida.copy(costoEnvio = -1)))
        assertEquals(Resultado.Error(Motivo.MONTO_NEGATIVO), registro.registrarVenta(valida.copy(comision = -1)))
        assertEquals(emptyList<Venta>(), registro.ventasVigentes().first())
    }

    @Test
    fun cambiar_precio_de_referencia_no_altera_el_precio_cobrado() = runBlocking {
        val p = registro.crearProducto("Cuaderno", 1000).valor()
        registro.registrarVenta(datos(p.id, precio = 900))

        registro.editarProducto(p.id, "Cuaderno", 5000)

        assertEquals(900L, registro.ventasVigentes().first().single().lineas.single().precioCobrado)
    }

    @Test
    fun no_se_borra_producto_ni_canal_con_ventas() = runBlocking {
        val p = registro.crearProducto("Cuaderno", 1000).valor()
        val ml = canal()
        registro.registrarVenta(datos(p.id, canalId = ml.id))

        assertEquals(Resultado.Error(Motivo.TIENE_VENTAS), registro.borrarProducto(p.id))
        assertEquals(Resultado.Error(Motivo.TIENE_VENTAS), registro.borrarCanal(ml.id))
        assertEquals(listOf(p.id), registro.productos().first().map { it.id })
        assertEquals(4, registro.canales().first().size)
    }
}
