package com.marowark.registraventas.registro

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Producto del catálogo. [precioReferencia] en centavos. */
data class Producto(
    val id: String,
    val nombre: String,
    val precioReferencia: Long,
    val activo: Boolean,
    val ultimaModificacion: Instant,
)

/**
 * Fachada del dominio sobre Room: el único seam de test (ver spec #1).
 * Los ViewModels y los tests sólo hablan con esta clase.
 */
class Registro(
    private val db: RegistroDatabase,
    private val reloj: Clock,
) {
    private val productos = db.productos()

    /** Fecha de hoy según el reloj inyectado (p. ej. Fecha de venta por defecto). */
    fun hoy(): LocalDate = LocalDate.now(reloj)

    /** Marca de última modificación para cada cambio (ADR-0001). */
    internal fun ahora(): Instant = reloj.instant()

    // --- Productos ---

    fun productosActivos(): Flow<List<Producto>> = productos.activos().map { it.map(ProductoEntity::aProducto) }
    fun productos(): Flow<List<Producto>> = productos.todos().map { it.map(ProductoEntity::aProducto) }

    suspend fun crearProducto(nombre: String, precioReferencia: Long): Resultado<Producto> =
        guardarProducto(UUID.randomUUID().toString(), nombre, precioReferencia, activo = true)

    suspend fun editarProducto(id: String, nombre: String, precioReferencia: Long): Resultado<Producto> =
        guardarProducto(id, nombre, precioReferencia, producto(id).activo)

    suspend fun desactivarProducto(id: String) = activarProducto(id, false)
    suspend fun reactivarProducto(id: String) = activarProducto(id, true)

    private suspend fun activarProducto(id: String, activo: Boolean) =
        productos.guardar(producto(id).copy(activo = activo, ultimaModificacion = ahora().toEpochMilli()))

    /** Siempre Ok mientras no existan Ventas; "Registrar Venta" agrega el rechazo si las tiene. */
    suspend fun borrarProducto(id: String): Resultado<Unit> {
        productos.borrar(id)
        return Resultado.Ok(Unit)
    }

    private suspend fun producto(id: String) = checkNotNull(productos.porId(id)) { "Producto inexistente: $id" }

    private suspend fun guardarProducto(id: String, nombre: String, precioReferencia: Long, activo: Boolean): Resultado<Producto> {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) return Resultado.Error(Motivo.NOMBRE_VACIO)
        if (precioReferencia < 0) return Resultado.Error(Motivo.MONTO_NEGATIVO)
        val duenio = productos.idConNombre(normalizar(limpio))
        if (duenio != null && duenio != id) return Resultado.Error(Motivo.NOMBRE_DUPLICADO)
        val entidad = ProductoEntity(id, limpio, normalizar(limpio), precioReferencia, activo, ahora().toEpochMilli())
        productos.guardar(entidad)
        return Resultado.Ok(entidad.aProducto())
    }
}

/** Clave de unicidad de nombres de Producto y Canal: sin espacios extremos ni mayúsculas. */
internal fun normalizar(nombre: String) = nombre.trim().lowercase()

private fun ProductoEntity.aProducto() =
    Producto(id, nombre, precioReferencia, activo, Instant.ofEpochMilli(ultimaModificacion))
