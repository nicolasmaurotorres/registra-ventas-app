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

/** Canal de venta (p. ej. Mercado Libre). */
data class Canal(
    val id: String,
    val nombre: String,
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
    private val canales = db.canales()

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
        nombreInvalido(id, limpio, productos.idConNombre(normalizar(limpio)))?.let { return Resultado.Error(it) }
        if (precioReferencia < 0) return Resultado.Error(Motivo.MONTO_NEGATIVO)
        val entidad = ProductoEntity(id, limpio, normalizar(limpio), precioReferencia, activo, ahora().toEpochMilli())
        productos.guardar(entidad)
        return Resultado.Ok(entidad.aProducto())
    }

    // --- Canales (mismas reglas que Productos; los precargados los crea RegistroDatabase) ---

    fun canalesActivos(): Flow<List<Canal>> = canales.activos().map { it.map(CanalEntity::aCanal) }
    fun canales(): Flow<List<Canal>> = canales.todos().map { it.map(CanalEntity::aCanal) }

    suspend fun crearCanal(nombre: String): Resultado<Canal> = guardarCanal(UUID.randomUUID().toString(), nombre, activo = true)
    suspend fun editarCanal(id: String, nombre: String): Resultado<Canal> = guardarCanal(id, nombre, canal(id).activo)

    suspend fun desactivarCanal(id: String) = activarCanal(id, false)
    suspend fun reactivarCanal(id: String) = activarCanal(id, true)

    private suspend fun activarCanal(id: String, activo: Boolean) =
        canales.guardar(canal(id).copy(activo = activo, ultimaModificacion = ahora().toEpochMilli()))

    /** Siempre Ok mientras no existan Ventas; "Registrar Venta" agrega el rechazo si las tiene. */
    suspend fun borrarCanal(id: String): Resultado<Unit> {
        canales.borrar(id)
        return Resultado.Ok(Unit)
    }

    private suspend fun canal(id: String) = checkNotNull(canales.porId(id)) { "Canal inexistente: $id" }

    private suspend fun guardarCanal(id: String, nombre: String, activo: Boolean): Resultado<Canal> {
        val limpio = nombre.trim()
        nombreInvalido(id, limpio, canales.idConNombre(normalizar(limpio)))?.let { return Resultado.Error(it) }
        val entidad = CanalEntity(id, limpio, normalizar(limpio), activo, ahora().toEpochMilli())
        canales.guardar(entidad)
        return Resultado.Ok(entidad.aCanal())
    }

    /** [duenio]: id que ya usa ese nombre normalizado, si hay. */
    private fun nombreInvalido(id: String, limpio: String, duenio: String?): Motivo? = when {
        limpio.isEmpty() -> Motivo.NOMBRE_VACIO
        duenio != null && duenio != id -> Motivo.NOMBRE_DUPLICADO
        else -> null
    }
}

/** Clave de unicidad de nombres de Producto y Canal: sin espacios extremos ni mayúsculas. */
internal fun normalizar(nombre: String) = nombre.trim().lowercase()

private fun ProductoEntity.aProducto() =
    Producto(id, nombre, precioReferencia, activo, Instant.ofEpochMilli(ultimaModificacion))

private fun CanalEntity.aCanal() = Canal(id, nombre, activo, Instant.ofEpochMilli(ultimaModificacion))
