package com.marowark.registraventas.registro

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class RespaldoTest {
    private val prueba = RegistroDePrueba(hoy = LocalDate.of(2026, 3, 10))
    private val registro = prueba.registro

    @After fun cerrar() = prueba.cerrar()

    @Test
    fun exportar_incluye_todas_las_entidades_con_sus_campos() = runBlocking {
        val inactivo = registro.crearProducto("Agenda", 2500).valor()
        registro.desactivarProducto(inactivo.id)
        val activo = registro.crearProducto("Cuaderno", 1000).valor()
        val canalInactivo = registro.crearCanal("Feria").valor()
        registro.desactivarCanal(canalInactivo.id)
        val venta = registro.registrarVenta(
            DatosVenta(LocalDate.of(2026, 3, 1), canalInactivo.id, "Ana", listOf(DatosLinea(activo.id, 2, 900)), costoEnvio = 300, comision = 50),
        ).valor()
        registro.despachar(venta.id, LocalDate.of(2026, 3, 2))
        registro.entregar(venta.id, LocalDate.of(2026, 3, 4))
        registro.cancelarVenta(venta.id)

        val json = Json.parseToJsonElement(registro.exportar()).jsonObject

        assertEquals(1, json.getValue("version").jsonPrimitive.int)
        val productos = json.getValue("productos").jsonArray.map { it.jsonObject }
        val agenda = productos.single { it.texto("id") == inactivo.id }
        assertEquals("Agenda", agenda.texto("nombre"))
        assertEquals("2500", agenda.texto("precioReferencia"))
        assertEquals("false", agenda.texto("activo"))
        assertEquals(registro.productos().first().single { it.id == inactivo.id }.ultimaModificacion.toEpochMilli().toString(), agenda.texto("ultimaModificacion"))
        assertEquals(2, productos.size)

        val canales = json.getValue("canales").jsonArray.map { it.jsonObject }
        assertEquals(registro.canales().first().map { it.id }.toSet(), canales.map { it.texto("id") }.toSet())
        assertEquals("false", canales.single { it.texto("id") == canalInactivo.id }.texto("activo"))

        val v = json.getValue("ventas").jsonArray.single().jsonObject
        val cancelada = registro.ventasCanceladas().first().single()
        assertEquals(venta.id, v.texto("id"))
        assertEquals(LocalDate.of(2026, 3, 1).toEpochDay().toString(), v.texto("fechaVenta"))
        assertEquals(LocalDate.of(2026, 3, 2).toEpochDay().toString(), v.texto("fechaDespacho"))
        assertEquals(LocalDate.of(2026, 3, 4).toEpochDay().toString(), v.texto("fechaEntrega"))
        assertEquals(canalInactivo.id, v.texto("canalId"))
        assertEquals("Ana", v.texto("cliente"))
        assertEquals("300", v.texto("costoEnvio"))
        assertEquals("50", v.texto("comision"))
        assertEquals("true", v.texto("cancelada"))
        assertEquals(cancelada.ultimaModificacion.toEpochMilli().toString(), v.texto("ultimaModificacion"))

        val l = json.getValue("lineas").jsonArray.single().jsonObject
        val linea = venta.lineas.single()
        assertEquals(linea.id, l.texto("id"))
        assertEquals(venta.id, l.texto("ventaId"))
        assertEquals(activo.id, l.texto("productoId"))
        assertEquals("2", l.texto("cantidad"))
        assertEquals("900", l.texto("precioCobrado"))
        assertEquals(linea.ultimaModificacion.toEpochMilli().toString(), l.texto("ultimaModificacion"))
    }

    private fun kotlinx.serialization.json.JsonObject.texto(campo: String) = getValue(campo).jsonPrimitive.content
}
