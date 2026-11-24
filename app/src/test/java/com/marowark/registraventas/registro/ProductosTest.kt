package com.marowark.registraventas.registro

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProductosTest {
    private val prueba = RegistroDePrueba()
    private val registro = prueba.registro

    @After fun cerrar() = prueba.cerrar()

    @Test
    fun crear_producto_lo_lista_activo() = runBlocking {
        val creado = registro.crearProducto("Cuaderno A5", 150050).valor()

        assertEquals(listOf(creado), registro.productosActivos().first())
        assertEquals("Cuaderno A5", creado.nombre)
        assertEquals(150050L, creado.precioReferencia)
    }

    @Test
    fun rechaza_nombre_duplicado_sin_distinguir_mayusculas_ni_espacios() = runBlocking {
        registro.crearProducto("Cuaderno A5", 100)

        assertEquals(Resultado.Error(Motivo.NOMBRE_DUPLICADO), registro.crearProducto("  cuaderno a5 ", 200))
        assertEquals(1, registro.productos().first().size)
    }

    @Test
    fun editar_cambia_precio_y_ultima_modificacion() = runBlocking {
        val creado = registro.crearProducto("Cuaderno A5", 100).valor()
        prueba.reloj.avanzarDias(1)

        val editado = registro.editarProducto(creado.id, "cuaderno a5", 250).valor()

        assertEquals(250L, editado.precioReferencia)
        assertEquals("cuaderno a5", editado.nombre)
        assertEquals(creado.ultimaModificacion.plusSeconds(86400), editado.ultimaModificacion)
        assertEquals(listOf(editado), registro.productos().first())
    }

    @Test
    fun editar_rechaza_nombre_de_otro_producto() = runBlocking {
        registro.crearProducto("Cuaderno A5", 100)
        val agenda = registro.crearProducto("Agenda", 100).valor()

        assertEquals(Resultado.Error(Motivo.NOMBRE_DUPLICADO), registro.editarProducto(agenda.id, "CUADERNO A5", 100))
    }

    @Test
    fun desactivado_sale_de_activos_pero_sigue_en_todos_y_se_reactiva() = runBlocking {
        val creado = registro.crearProducto("Cuaderno A5", 100).valor()
        prueba.reloj.avanzarDias(1)

        registro.desactivarProducto(creado.id)

        assertEquals(emptyList<Producto>(), registro.productosActivos().first())
        val inactivo = registro.productos().first().single()
        assertEquals(false, inactivo.activo)
        assertEquals(creado.ultimaModificacion.plusSeconds(86400), inactivo.ultimaModificacion)

        registro.reactivarProducto(creado.id)

        assertEquals(listOf("Cuaderno A5"), registro.productosActivos().first().map { it.nombre })
    }

    @Test
    fun borrar_sin_ventas_lo_quita() = runBlocking {
        val creado = registro.crearProducto("Cuaderno A5", 100).valor()

        assertEquals(Resultado.Ok(Unit), registro.borrarProducto(creado.id))
        assertEquals(emptyList<Producto>(), registro.productos().first())
    }

    @Test
    fun rechaza_precio_negativo_y_nombre_vacio() = runBlocking {
        assertEquals(Resultado.Error(Motivo.MONTO_NEGATIVO), registro.crearProducto("Agenda", -1))
        assertEquals(Resultado.Error(Motivo.NOMBRE_VACIO), registro.crearProducto("   ", 100))
    }
}

fun <T> Resultado<T>.valor(): T = (this as Resultado.Ok).valor
