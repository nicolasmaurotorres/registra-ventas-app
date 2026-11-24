package com.marowark.registraventas.registro

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class EstadisticasTest {
    private val prueba = RegistroDePrueba(hoy = LocalDate.of(2026, 3, 10))
    private val registro = prueba.registro

    @After fun cerrar() = prueba.cerrar()

    private fun dia(m: Int, d: Int, a: Int = 2026) = LocalDate.of(a, m, d)

    // --- Período ---

    @Test
    fun semana_va_de_lunes_a_domingo() {
        // 2026-03-10 es martes
        val semana = Periodo(TipoPeriodo.SEMANA, dia(3, 10))
        assertEquals(dia(3, 9), semana.inicio)
        assertEquals(dia(3, 15), semana.fin)
        assertEquals(Periodo(TipoPeriodo.SEMANA, dia(3, 15)).inicio, semana.inicio) // domingo cae en la misma
        assertEquals(dia(3, 16), Periodo(TipoPeriodo.SEMANA, dia(3, 16)).inicio) // lunes empieza otra
        assertEquals(dia(3, 2), semana.anterior().inicio)
        assertEquals(dia(3, 16), semana.siguiente().inicio)
    }

    @Test
    fun mes_y_anio_con_sus_bordes() {
        val febrero = Periodo(TipoPeriodo.MES, dia(2, 14))
        assertEquals(dia(2, 1), febrero.inicio)
        assertEquals(dia(2, 28), febrero.fin)
        assertEquals(dia(1, 1), febrero.anterior().inicio)
        assertEquals(dia(3, 31), febrero.siguiente().fin)

        val anio = Periodo(TipoPeriodo.ANIO, dia(6, 1))
        assertEquals(dia(1, 1), anio.inicio)
        assertEquals(dia(12, 31), anio.fin)
        assertEquals(dia(1, 1, 2025), anio.anterior().inicio)
        assertEquals(dia(12, 31, 2027), anio.siguiente().fin)
    }

    // --- Estadísticas ---

    private suspend fun canal(nombre: String) = registro.canales().first().single { it.nombre == nombre }

    private suspend fun vender(
        fecha: LocalDate,
        canal: Canal,
        vararg lineas: DatosLinea,
        envio: Long = 0,
        comision: Long = 0,
    ) = registro.registrarVenta(DatosVenta(fecha, canal.id, null, lineas.toList(), envio, comision)).valor()

    private suspend fun estadisticas(p: Periodo) = registro.estadisticas(p).first()

    @Test
    fun sin_ventas_esta_vacio() = runBlocking {
        val e = estadisticas(Periodo(TipoPeriodo.MES, registro.hoy()))
        assertEquals(0, e.cantidadVentas)
        assertEquals(0L, e.neto)
        assertEquals(emptyList<FilaProducto>(), e.porProducto)
        assertEquals(emptyList<FilaCanal>(), e.porCanal)
    }

    @Test
    fun totales_y_neto_por_canal_y_total() = runBlocking {
        val cuaderno = registro.crearProducto("Cuaderno", 0).valor()
        val agenda = registro.crearProducto("Agenda", 0).valor()
        val ml = canal("Mercado Libre")
        val mano = canal("En mano")
        vender(dia(3, 10), ml, DatosLinea(cuaderno.id, 2, 1000), DatosLinea(agenda.id, 1, 500), envio = 300, comision = 200)
        vender(dia(3, 11), ml, DatosLinea(cuaderno.id, 1, 1000), envio = 100, comision = 50)
        vender(dia(3, 12), mano, DatosLinea(agenda.id, 3, 400))

        val e = estadisticas(Periodo(TipoPeriodo.SEMANA, dia(3, 10)))

        assertEquals(3, e.cantidadVentas)
        assertEquals(7, e.unidades)
        assertEquals(4700L, e.monto)
        assertEquals(400L, e.costosEnvio)
        assertEquals(250L, e.comisiones)
        assertEquals(4050L, e.neto)
        assertEquals(
            listOf(FilaCanal(ml.id, "Mercado Libre", 3500, 400, 250), FilaCanal(mano.id, "En mano", 1200, 0, 0)),
            e.porCanal,
        )
        assertEquals(listOf(2850L, 1200L), e.porCanal.map { it.neto })
        // de mayor a menor Monto
        assertEquals(
            listOf(FilaProducto(cuaderno.id, "Cuaderno", 3, 3000), FilaProducto(agenda.id, "Agenda", 4, 1700)),
            e.porProducto,
        )
    }

    @Test
    fun cuenta_por_fecha_de_venta_dentro_del_periodo() = runBlocking {
        val p = registro.crearProducto("Cuaderno", 0).valor()
        val ml = canal("Mercado Libre")
        vender(dia(3, 8), ml, DatosLinea(p.id, 1, 1)) // domingo: semana anterior
        vender(dia(3, 9), ml, DatosLinea(p.id, 1, 10)) // lunes
        vender(dia(3, 15), ml, DatosLinea(p.id, 1, 100)) // domingo
        vender(dia(3, 16), ml, DatosLinea(p.id, 1, 1000)) // lunes siguiente
        vender(dia(3, 31), ml, DatosLinea(p.id, 1, 10000)) // fin de mes
        vender(dia(12, 31, 2025), ml, DatosLinea(p.id, 1, 100000)) // fin de año anterior

        assertEquals(110L, estadisticas(Periodo(TipoPeriodo.SEMANA, dia(3, 11))).monto)
        assertEquals(11111L, estadisticas(Periodo(TipoPeriodo.MES, dia(3, 1))).monto)
        assertEquals(0L, estadisticas(Periodo(TipoPeriodo.MES, dia(4, 1))).monto)
        assertEquals(11111L, estadisticas(Periodo(TipoPeriodo.ANIO, dia(1, 1))).monto)
        assertEquals(100000L, estadisticas(Periodo(TipoPeriodo.ANIO, dia(1, 1)).anterior()).monto)
    }

    @Test
    fun excluye_canceladas_incluye_inactivos_y_sin_despachar() = runBlocking {
        val activo = registro.crearProducto("Cuaderno", 0).valor()
        val viejo = registro.crearProducto("Agenda vieja", 0).valor()
        val ml = canal("Mercado Libre")
        vender(dia(3, 10), ml, DatosLinea(viejo.id, 1, 500)) // sin despachar
        val cancelada = vender(dia(3, 10), ml, DatosLinea(activo.id, 5, 999))
        registro.cancelarVenta(cancelada.id)
        registro.desactivarProducto(viejo.id)
        registro.desactivarCanal(ml.id)

        val e = estadisticas(Periodo(TipoPeriodo.MES, dia(3, 10)))

        assertEquals(1, e.cantidadVentas)
        assertEquals(listOf(FilaProducto(viejo.id, "Agenda vieja", 1, 500)), e.porProducto)
        assertEquals(listOf("Mercado Libre"), e.porCanal.map { it.nombre })
    }
}
