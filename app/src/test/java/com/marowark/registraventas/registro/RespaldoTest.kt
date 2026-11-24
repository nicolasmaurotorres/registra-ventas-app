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

    // --- Importar ---

    private val otra = RegistroDePrueba(hoy = LocalDate.of(2026, 3, 10))
    private val destino = otra.registro

    @After fun cerrarOtra() = otra.cerrar()

    @Test
    fun exportar_e_importar_en_base_vacia_reproduce_todo() = runBlocking {
        val agenda = registro.crearProducto("Agenda", 2500).valor()
        registro.desactivarProducto(agenda.id)
        val cuaderno = registro.crearProducto("Cuaderno", 1000).valor()
        val feria = registro.crearCanal("Feria").valor()
        val enMano = registro.canales().first().single { it.nombre == "En mano" }
        val v1 = registro.registrarVenta(
            DatosVenta(LocalDate.of(2026, 3, 1), feria.id, "Ana", listOf(DatosLinea(cuaderno.id, 2, 900), DatosLinea(agenda.id, 1, 2500)), 300, 50),
        ).valor()
        registro.despachar(v1.id, LocalDate.of(2026, 3, 2))
        val v2 = registro.registrarVenta(DatosVenta(LocalDate.of(2026, 3, 5), enMano.id, null, listOf(DatosLinea(cuaderno.id, 1, 1000)))).valor()
        registro.cancelarVenta(v2.id)

        val resumen = destino.importar(registro.exportar()).valor()

        assertEquals(ResumenImportacion(productosAgregados = 2, productosActualizados = 0, canalesAgregados = 1, canalesActualizados = 0, ventasAgregadas = 2, ventasActualizadas = 0), resumen)
        assertEquals(registro.productos().first(), destino.productos().first())
        // Los Canales precargados se emparejan por nombre: quedan los ids locales.
        val canalesDestino = destino.canales().first()
        assertEquals(registro.canales().first().map { it.nombre }, canalesDestino.map { it.nombre })
        val enManoDestino = canalesDestino.single { it.nombre == "En mano" }
        assertEquals(registro.ventasVigentes().first(), destino.ventasVigentes().first())
        assertEquals(registro.ventasCanceladas().first().map { it.copy(canalId = enManoDestino.id) }, destino.ventasCanceladas().first())
    }

    @Test
    fun importar_un_respaldo_viejo_no_pierde_ventas_nuevas() = runBlocking {
        val cuaderno = registro.crearProducto("Cuaderno", 1000).valor()
        val canal = registro.canales().first().first().id
        val vieja = registro.registrarVenta(DatosVenta(LocalDate.of(2026, 3, 1), canal, null, listOf(DatosLinea(cuaderno.id, 1, 1000)))).valor()
        val respaldo = registro.exportar()
        prueba.reloj.avanzarDias(1)
        val nueva = registro.registrarVenta(DatosVenta(LocalDate.of(2026, 3, 11), canal, null, listOf(DatosLinea(cuaderno.id, 3, 1000)))).valor()

        val resumen = registro.importar(respaldo).valor()

        assertEquals(ResumenImportacion(0, 0, 0, 0, 0, 0), resumen)
        assertEquals(listOf(nueva.id, vieja.id), registro.ventasVigentes().first().map { it.id })
    }

    @Test
    fun conflicto_por_id_gana_la_ultima_modificacion_mas_reciente() = runBlocking {
        val cuaderno = registro.crearProducto("Cuaderno", 1000).valor()
        val canal = registro.canales().first().first().id
        val venta = registro.registrarVenta(DatosVenta(LocalDate.of(2026, 3, 1), canal, "Ana", listOf(DatosLinea(cuaderno.id, 1, 1000)))).valor()
        destino.importar(registro.exportar()).valor()
        // Origen cambia el Producto después; destino cambia la Venta todavía más tarde.
        prueba.reloj.avanzarDias(1)
        registro.editarProducto(cuaderno.id, "Cuaderno A5", 1200)
        registro.despachar(venta.id, LocalDate.of(2026, 3, 2))
        otra.reloj.avanzarDias(2)
        destino.despachar(venta.id, LocalDate.of(2026, 3, 5))

        val resumen = destino.importar(registro.exportar()).valor()

        assertEquals(ResumenImportacion(0, 1, 0, 0, 0, 0), resumen)
        assertEquals(listOf("Cuaderno A5" to 1200L), destino.productos().first().map { it.nombre to it.precioReferencia })
        assertEquals(LocalDate.of(2026, 3, 5), destino.venta(venta.id)!!.fechaDespacho)
    }

    @Test
    fun producto_y_canal_con_otro_id_y_mismo_nombre_se_emparejan_con_el_local() = runBlocking {
        val origenCuaderno = registro.crearProducto("Cuaderno", 1000).valor()
        val origenFeria = registro.crearCanal("Feria").valor()
        registro.registrarVenta(DatosVenta(LocalDate.of(2026, 3, 1), origenFeria.id, null, listOf(DatosLinea(origenCuaderno.id, 2, 900)))).valor()
        val respaldo = registro.exportar()
        otra.reloj.avanzarDias(1) // el local es más nuevo: conserva sus datos
        val cuaderno = destino.crearProducto("  CUADERNO ", 1500).valor()
        val feria = destino.crearCanal("feria").valor()

        val resumen = destino.importar(respaldo).valor()

        assertEquals(ResumenImportacion(0, 0, 0, 0, 1, 0), resumen)
        assertEquals(listOf(cuaderno), destino.productos().first())
        assertEquals(feria, destino.canales().first().single { it.id == feria.id })
        assertEquals(5, destino.canales().first().size)
        val venta = destino.ventasVigentes().first().single()
        assertEquals(feria.id, venta.canalId)
        assertEquals("feria", venta.canalNombre)
        assertEquals(listOf(cuaderno.id), venta.lineas.map { it.productoId })
    }

    @Test
    fun emparejado_por_nombre_y_mas_nuevo_actualiza_el_local_sin_cambiar_su_id() = runBlocking {
        val local = destino.crearProducto("cuaderno", 1500).valor()
        prueba.reloj.avanzarDias(1)
        registro.crearProducto("Cuaderno", 1000).valor()

        val resumen = destino.importar(registro.exportar()).valor()

        assertEquals(1, resumen.productosActualizados)
        assertEquals(listOf(Triple(local.id, "Cuaderno", 1000L)), destino.productos().first().map { Triple(it.id, it.nombre, it.precioReferencia) })
    }

    @Test
    fun la_cancelacion_viaja_en_ambos_sentidos() = runBlocking {
        val cuaderno = registro.crearProducto("Cuaderno", 1000).valor()
        val canal = registro.canales().first().first().id
        val venta = registro.registrarVenta(DatosVenta(LocalDate.of(2026, 3, 1), canal, null, listOf(DatosLinea(cuaderno.id, 1, 1000)))).valor()
        destino.importar(registro.exportar()).valor()

        prueba.reloj.avanzarDias(1)
        registro.cancelarVenta(venta.id)
        assertEquals(1, destino.importar(registro.exportar()).valor().ventasActualizadas)
        assertEquals(listOf(venta.id), destino.ventasCanceladas().first().map { it.id })

        prueba.reloj.avanzarDias(1)
        registro.reactivarVenta(venta.id)
        destino.importar(registro.exportar()).valor()
        assertEquals(listOf(venta.id), destino.ventasVigentes().first().map { it.id })
        assertEquals(emptyList<Venta>(), destino.ventasCanceladas().first())
    }

    @Test
    fun las_lineas_viajan_con_su_venta() = runBlocking {
        val cuaderno = registro.crearProducto("Cuaderno", 1000).valor()
        val agenda = registro.crearProducto("Agenda", 2500).valor()
        val canal = registro.canales().first().first().id
        val venta = registro.registrarVenta(
            DatosVenta(LocalDate.of(2026, 3, 1), canal, null, listOf(DatosLinea(cuaderno.id, 1, 1000), DatosLinea(agenda.id, 1, 2500))),
        ).valor()
        val conDos = registro.exportar()
        // Se quita la Agenda: un respaldo viejo no debe traerla de vuelta.
        prueba.reloj.avanzarDias(1)
        val cuadernoLinea = venta.lineas.first { it.productoId == cuaderno.id }
        registro.editarVenta(venta.id, DatosVenta(LocalDate.of(2026, 3, 1), canal, null, listOf(DatosLinea(cuaderno.id, 1, 1000, cuadernoLinea.id))))

        registro.importar(conDos).valor()
        assertEquals(listOf(cuaderno.id), registro.venta(venta.id)!!.lineas.map { it.productoId })

        // Y si gana la importada, su set reemplaza al local.
        destino.importar(conDos).valor()
        destino.importar(registro.exportar()).valor()
        assertEquals(listOf(cuadernoLinea.id), destino.venta(venta.id)!!.lineas.map { it.id })
    }

    @Test
    fun un_archivo_invalido_no_modifica_nada() = runBlocking {
        val cuaderno = registro.crearProducto("Cuaderno", 1000).valor()
        val canal = registro.canales().first().first().id
        registro.registrarVenta(DatosVenta(LocalDate.of(2026, 3, 1), canal, null, listOf(DatosLinea(cuaderno.id, 1, 1000)))).valor()
        val bueno = registro.exportar()
        val antes = destino.exportar()

        val invalidos = listOf(
            "no es json",
            bueno.substring(0, bueno.length / 2),
            "{}",
            bueno.replace("\"version\":1", "\"version\":99"),
            bueno.replace("\"canalId\":\"$canal\"", "\"canalId\":\"inexistente\""),
            bueno.replace("\"productoId\":\"${cuaderno.id}\"", "\"productoId\":\"inexistente\""),
        )
        for (json in invalidos) {
            assertEquals(json, Resultado.Error(Motivo.RESPALDO_INVALIDO), destino.importar(json))
            assertEquals(antes, destino.exportar())
        }
    }

    @Test
    fun el_emparejado_no_depende_del_orden_del_archivo_nuevo_primero() = emparejadoIndependienteDelOrden(nuevoPrimero = true)

    @Test
    fun el_emparejado_no_depende_del_orden_del_archivo_renombrado_primero() = emparejadoIndependienteDelOrden(nuevoPrimero = false)

    /** Local tiene "a"; el archivo trae ese mismo id renombrado a "b" (más nuevo) y otro id nuevo llamado "a". */
    private fun emparejadoIndependienteDelOrden(nuevoPrimero: Boolean) = runBlocking {
        val p1 = destino.crearProducto("a", 100).valor()
        val c1 = destino.crearCanal("a").valor()
        registro.importar(destino.exportar()).valor()
        prueba.reloj.avanzarDias(1)
        registro.editarProducto(p1.id, "b", 100)
        registro.editarCanal(c1.id, "b")
        val p2 = registro.crearProducto("a", 200).valor()
        val c2 = registro.crearCanal("a").valor()
        val venta = registro.registrarVenta(DatosVenta(LocalDate.of(2026, 3, 1), c2.id, null, listOf(DatosLinea(p2.id, 1, 200)))).valor()
        val r = Json.decodeFromString(Respaldo.serializer(), registro.exportar())
        fun <T> ordenar(lista: List<T>, nuevo: (T) -> Boolean) = lista.sortedBy { if (nuevo(it) == nuevoPrimero) 0 else 1 }
        val archivo = r.copy(productos = ordenar(r.productos) { it.id == p2.id }, canales = ordenar(r.canales) { it.id == c2.id })

        destino.importar(Json.encodeToString(Respaldo.serializer(), archivo)).valor()

        assertEquals(setOf(p1.id to "b", p2.id to "a"), destino.productos().first().map { it.id to it.nombre }.toSet())
        assertEquals("b", destino.canales().first().single { it.id == c1.id }.nombre)
        assertEquals("a", destino.canales().first().single { it.id == c2.id }.nombre)
        val importada = destino.venta(venta.id)!!
        assertEquals(c2.id, importada.canalId)
        assertEquals(listOf(p2.id), importada.lineas.map { it.productoId })
    }

    @Test
    fun ventas_que_rompen_las_reglas_no_se_importan() = runBlocking {
        val cuaderno = registro.crearProducto("Cuaderno", 1000).valor()
        val canal = registro.canales().first().first().id
        registro.registrarVenta(DatosVenta(LocalDate.of(2026, 3, 5), canal, null, listOf(DatosLinea(cuaderno.id, 1, 1000)))).valor()
        val r = Json.decodeFromString(Respaldo.serializer(), registro.exportar())
        val v = r.ventas.single()
        val dia = v.fechaVenta
        val antes = destino.exportar()

        val invalidos = listOf(
            r.copy(lineas = emptyList()),
            r.copy(lineas = r.lineas.map { it.copy(cantidad = 0) }),
            r.copy(lineas = r.lineas.map { it.copy(precioCobrado = -1) }),
            r.copy(ventas = listOf(v.copy(costoEnvio = -1))),
            r.copy(ventas = listOf(v.copy(comision = -1))),
            r.copy(ventas = listOf(v.copy(fechaEntrega = dia))),
            r.copy(ventas = listOf(v.copy(fechaDespacho = dia - 1))),
            r.copy(ventas = listOf(v.copy(fechaDespacho = dia + 2, fechaEntrega = dia + 1))),
            r.copy(productos = r.productos.map { it.copy(precioReferencia = -1) }),
        )
        for (archivo in invalidos) {
            assertEquals(archivo.toString(), Resultado.Error(Motivo.RESPALDO_INVALIDO), destino.importar(Json.encodeToString(Respaldo.serializer(), archivo)))
            assertEquals(antes, destino.exportar())
        }
    }

    private fun kotlinx.serialization.json.JsonObject.texto(campo: String) = getValue(campo).jsonPrimitive.content
}
