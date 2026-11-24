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

/** Línea de una Venta. [precioCobrado] por unidad, en centavos. */
data class Linea(
    val id: String,
    val productoId: String,
    val cantidad: Int,
    val precioCobrado: Long,
    val ultimaModificacion: Instant,
)

/** Venta registrada. Montos en centavos; Estado de envío se deriva de las fechas. */
data class Venta(
    val id: String,
    val fechaVenta: LocalDate,
    val fechaDespacho: LocalDate?,
    val fechaEntrega: LocalDate?,
    val canalId: String,
    val canalNombre: String,
    val cliente: String?,
    val lineas: List<Linea>,
    val costoEnvio: Long,
    val comision: Long,
    val cancelada: Boolean,
    val ultimaModificacion: Instant,
) {
    /** Σ cantidad × Precio cobrado. */
    val monto: Long get() = lineas.sumOf { it.cantidad * it.precioCobrado }

    val estadoEnvio: EstadoEnvio get() = when {
        fechaEntrega != null -> EstadoEnvio.ENTREGADA
        fechaDespacho != null -> EstadoEnvio.EN_CAMINO
        else -> EstadoEnvio.PENDIENTE_DE_DESPACHO
    }
}

/** Derivado de las fechas de la Venta; no se persiste. */
enum class EstadoEnvio(val nombre: String) {
    PENDIENTE_DE_DESPACHO("Pendiente de despacho"),
    EN_CAMINO("En camino"),
    ENTREGADA("Entregada"),
}

/** Lo que se carga en el formulario de Venta (registrar o editar). [canalId] null = sin elegir. */
data class DatosVenta(
    val fechaVenta: LocalDate,
    val canalId: String?,
    val cliente: String?,
    val lineas: List<DatosLinea>,
    val costoEnvio: Long = 0,
    val comision: Long = 0,
)

data class DatosLinea(val productoId: String, val cantidad: Int, val precioCobrado: Long)

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
    private val ventas = db.ventas()

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

    suspend fun borrarProducto(id: String): Resultado<Unit> {
        if (ventas.hayConProducto(id)) return Resultado.Error(Motivo.TIENE_VENTAS)
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

    suspend fun borrarCanal(id: String): Resultado<Unit> {
        if (ventas.hayConCanal(id)) return Resultado.Error(Motivo.TIENE_VENTAS)
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

    // --- Ventas ---

    /** Ventas no canceladas, más recientes primero; [estado] null = todas. */
    fun ventasVigentes(estado: EstadoEnvio? = null): Flow<List<Venta>> =
        ventas.vigentes().map { lista -> lista.map(VentaConLineas::aVenta).filter { estado == null || it.estadoEnvio == estado } }

    suspend fun registrarVenta(datos: DatosVenta): Resultado<Venta> {
        validar(datos)?.let { return Resultado.Error(it) }
        val id = UUID.randomUUID().toString()
        val ahora = ahora().toEpochMilli()
        val venta = VentaEntity(
            id, datos.fechaVenta.toEpochDay(), fechaDespacho = null, fechaEntrega = null, datos.canalId!!,
            datos.cliente?.trim()?.ifEmpty { null }, datos.costoEnvio, datos.comision, cancelada = false, ahora,
        )
        val lineas = datos.lineas.map { LineaEntity(UUID.randomUUID().toString(), id, it.productoId, it.cantidad, it.precioCobrado, ahora) }
        ventas.guardarConLineas(venta, lineas)
        return Resultado.Ok(checkNotNull(ventas.porId(id)).aVenta())
    }

    /** Reglas del formulario de Venta, compartidas por registrar y editar. */
    internal fun validar(datos: DatosVenta): Motivo? = when {
        datos.canalId == null -> Motivo.SIN_CANAL
        datos.lineas.isEmpty() -> Motivo.SIN_LINEAS
        datos.lineas.any { it.cantidad < 1 } -> Motivo.CANTIDAD_INVALIDA
        datos.costoEnvio < 0 || datos.comision < 0 || datos.lineas.any { it.precioCobrado < 0 } -> Motivo.MONTO_NEGATIVO
        else -> null
    }

    // --- Despacho y entrega ---

    suspend fun despachar(id: String, fecha: LocalDate) = cambiarFechas(id) { it.copy(fechaDespacho = fecha.toEpochDay()) }
    suspend fun entregar(id: String, fecha: LocalDate) = cambiarFechas(id) { it.copy(fechaEntrega = fecha.toEpochDay()) }
    suspend fun quitarDespacho(id: String) = cambiarFechas(id) { it.copy(fechaDespacho = null) }
    suspend fun quitarEntrega(id: String) = cambiarFechas(id) { it.copy(fechaEntrega = null) }

    private suspend fun cambiarFechas(id: String, cambio: (VentaEntity) -> VentaEntity): Resultado<Venta> {
        val nueva = cambio(checkNotNull(ventas.porId(id)) { "Venta inexistente: $id" }.venta)
        ordenDeFechas(
            LocalDate.ofEpochDay(nueva.fechaVenta), nueva.fechaDespacho?.let(LocalDate::ofEpochDay), nueva.fechaEntrega?.let(LocalDate::ofEpochDay),
        )?.let { return Resultado.Error(it) }
        ventas.guardar(nueva.copy(ultimaModificacion = ahora().toEpochMilli()))
        return Resultado.Ok(checkNotNull(ventas.porId(id)).aVenta())
    }

    /** venta ≤ despacho ≤ entrega, y no hay entrega sin despacho. También para editar Venta (#7). */
    internal fun ordenDeFechas(fechaVenta: LocalDate, fechaDespacho: LocalDate?, fechaEntrega: LocalDate?): Motivo? = when {
        fechaEntrega != null && fechaDespacho == null -> Motivo.ENTREGA_SIN_DESPACHO
        fechaDespacho != null && fechaDespacho < fechaVenta -> Motivo.FECHAS_FUERA_DE_ORDEN
        fechaEntrega != null && fechaDespacho != null && fechaEntrega < fechaDespacho -> Motivo.FECHAS_FUERA_DE_ORDEN
        else -> null
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

private fun LineaEntity.aLinea() = Linea(id, productoId, cantidad, precioCobrado, Instant.ofEpochMilli(ultimaModificacion))

private fun VentaConLineas.aVenta() = with(venta) {
    Venta(
        id, LocalDate.ofEpochDay(fechaVenta), fechaDespacho?.let(LocalDate::ofEpochDay), fechaEntrega?.let(LocalDate::ofEpochDay),
        canalId, canalNombre, cliente, lineas.map { it.aLinea() }, costoEnvio, comision, cancelada,
        Instant.ofEpochMilli(ultimaModificacion),
    )
}
