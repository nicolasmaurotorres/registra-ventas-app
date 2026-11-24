package com.marowark.registraventas.registro

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
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
    val fechaDespacho: LocalDate? = null,
    val fechaEntrega: LocalDate? = null,
)

/** [id]: Línea existente al editar (conserva su id); null = Línea nueva. */
data class DatosLinea(val productoId: String, val cantidad: Int, val precioCobrado: Long, val id: String? = null)

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
            id, datos.fechaVenta.toEpochDay(), datos.fechaDespacho?.toEpochDay(), datos.fechaEntrega?.toEpochDay(), datos.canalId!!,
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
        else -> ordenDeFechas(datos.fechaVenta, datos.fechaDespacho, datos.fechaEntrega)
    }

    /** Una Venta por id (vigente o cancelada), p. ej. para abrir su edición. */
    suspend fun venta(id: String): Venta? = ventas.porId(id)?.aVenta()

    /**
     * Reemplaza todos los campos y Líneas de la Venta con las mismas reglas que registrar.
     * Las Líneas con id conservan su id y sólo se re-marcan si cambiaron; las que faltan se borran.
     */
    suspend fun editarVenta(id: String, datos: DatosVenta): Resultado<Venta> {
        validar(datos)?.let { return Resultado.Error(it) }
        val actual = checkNotNull(ventas.porId(id)) { "Venta inexistente: $id" }
        val previas = actual.lineas.associateBy { it.id }
        val ahora = ahora().toEpochMilli()
        val venta = actual.venta.copy(
            fechaVenta = datos.fechaVenta.toEpochDay(), fechaDespacho = datos.fechaDespacho?.toEpochDay(),
            fechaEntrega = datos.fechaEntrega?.toEpochDay(), canalId = datos.canalId!!, cliente = datos.cliente?.trim()?.ifEmpty { null },
            costoEnvio = datos.costoEnvio, comision = datos.comision, ultimaModificacion = ahora,
        )
        val lineas = datos.lineas.map { d ->
            val previa = d.id?.let(previas::get)
            val nueva = LineaEntity(previa?.id ?: UUID.randomUUID().toString(), id, d.productoId, d.cantidad, d.precioCobrado, ahora)
            if (previa != null && previa == nueva.copy(ultimaModificacion = previa.ultimaModificacion)) previa else nueva
        }
        ventas.reemplazarConLineas(venta, lineas)
        return Resultado.Ok(checkNotNull(ventas.porId(id)).aVenta())
    }

    /** Ventas canceladas (⋮ → Ventas canceladas), más recientes primero. */
    fun ventasCanceladas(): Flow<List<Venta>> = ventas.canceladas().map { it.map(VentaConLineas::aVenta) }

    suspend fun cancelarVenta(id: String) = ventas.marcarCancelada(id, true, ahora().toEpochMilli())
    suspend fun reactivarVenta(id: String) = ventas.marcarCancelada(id, false, ahora().toEpochMilli())

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

    // --- Respaldo ---

    /** JSON con todo (incluye inactivos y canceladas), leído en una sola transacción. */
    suspend fun exportar(): String = db.withTransaction {
        Json.encodeToString(
            Respaldo.serializer(),
            Respaldo(VERSION_RESPALDO, productos.lista(), canales.lista(), ventas.lista(), ventas.lineas()),
        )
    }

    /**
     * Fusiona un respaldo sin borrar nada (ADR-0001), todo o nada. Producto/Canal: el gemelo local es el del
     * mismo id o, si no hay, el del mismo nombre normalizado; si lo importado es más nuevo pisa sus campos pero
     * conserva el id local, y las Ventas/Líneas importadas se reescriben para apuntar a él.
     * Una Venta viaja con su set de Líneas: si gana la importada, sus Líneas reemplazan las locales; si no, se ignoran.
     */
    suspend fun importar(json: String): Resultado<ResumenImportacion> {
        val r = leerRespaldo(json) ?: return Resultado.Error(Motivo.RESPALDO_INVALIDO)
        return Resultado.Ok(
            db.withTransaction {
                val idProducto = mutableMapOf<String, String>()
                var productosAgregados = 0
                var productosActualizados = 0
                for (p in r.productos) {
                    val nuevo = p.copy(nombre = p.nombre.trim(), nombreNormalizado = normalizar(p.nombre))
                    val local = productos.porId(p.id) ?: productos.idConNombre(nuevo.nombreNormalizado)?.let { productos.porId(it) }
                    if (local == null) productos.guardar(nuevo).also { productosAgregados++ }
                    else if (nuevo.ultimaModificacion > local.ultimaModificacion && libre(productos.idConNombre(nuevo.nombreNormalizado), local.id)) {
                        productos.guardar(nuevo.copy(id = local.id)).also { productosActualizados++ }
                    }
                    idProducto[p.id] = local?.id ?: p.id
                }
                val idCanal = mutableMapOf<String, String>()
                var canalesAgregados = 0
                var canalesActualizados = 0
                for (c in r.canales) {
                    val nuevo = c.copy(nombre = c.nombre.trim(), nombreNormalizado = normalizar(c.nombre))
                    val local = canales.porId(c.id) ?: canales.idConNombre(nuevo.nombreNormalizado)?.let { canales.porId(it) }
                    if (local == null) canales.guardar(nuevo).also { canalesAgregados++ }
                    else if (nuevo.ultimaModificacion > local.ultimaModificacion && libre(canales.idConNombre(nuevo.nombreNormalizado), local.id)) {
                        canales.guardar(nuevo.copy(id = local.id)).also { canalesActualizados++ }
                    }
                    idCanal[c.id] = local?.id ?: c.id
                }
                val lineasPorVenta = r.lineas.groupBy { it.ventaId }
                var ventasAgregadas = 0
                var ventasActualizadas = 0
                for (v in r.ventas) {
                    val venta = v.copy(canalId = idCanal.getValue(v.canalId))
                    val lineas = lineasPorVenta[v.id].orEmpty().map { it.copy(productoId = idProducto.getValue(it.productoId)) }
                    val local = ventas.porId(v.id)?.venta
                    if (local == null) ventas.guardarConLineas(venta, lineas).also { ventasAgregadas++ }
                    else if (venta.ultimaModificacion > local.ultimaModificacion) ventas.reemplazarConLineas(venta, lineas).also { ventasActualizadas++ }
                }
                ResumenImportacion(productosAgregados, productosActualizados, canalesAgregados, canalesActualizados, ventasAgregadas, ventasActualizadas)
            },
        )
    }

    /** Un rename importado que choca con otro nombre local se descarta (gana el local) en vez de romper la unicidad. */
    private fun libre(duenio: String?, id: String) = duenio == null || duenio == id

    /** Respaldo completo validado (formato, version, nombres e ids únicos, referencias) o null. */
    private fun leerRespaldo(json: String): Respaldo? {
        val r = try {
            Json.decodeFromString(Respaldo.serializer(), json)
        } catch (e: IllegalArgumentException) { // SerializationException incluida
            return null
        }
        fun unicos(valores: List<String>) = valores.toSet().size == valores.size
        val productoIds = r.productos.map { it.id }.toSet()
        val canalIds = r.canales.map { it.id }.toSet()
        val ventaIds = r.ventas.map { it.id }.toSet()
        val valido = r.version == VERSION_RESPALDO &&
            r.productos.none { it.nombre.isBlank() } && r.canales.none { it.nombre.isBlank() } &&
            unicos(r.productos.map { it.id }) && unicos(r.canales.map { it.id }) && unicos(r.ventas.map { it.id }) && unicos(r.lineas.map { it.id }) &&
            unicos(r.productos.map { normalizar(it.nombre) }) && unicos(r.canales.map { normalizar(it.nombre) }) &&
            r.ventas.all { it.canalId in canalIds } &&
            r.lineas.all { it.ventaId in ventaIds && it.productoId in productoIds }
        return r.takeIf { valido }
    }

    /** [duenio]: id que ya usa ese nombre normalizado, si hay. */
    private fun nombreInvalido(id: String, limpio: String, duenio: String?): Motivo? = when {
        limpio.isEmpty() -> Motivo.NOMBRE_VACIO
        duenio != null && duenio != id -> Motivo.NOMBRE_DUPLICADO
        else -> null
    }
}

/** Lo que cambió al importar un respaldo, por tipo. */
data class ResumenImportacion(
    val productosAgregados: Int,
    val productosActualizados: Int,
    val canalesAgregados: Int,
    val canalesActualizados: Int,
    val ventasAgregadas: Int,
    val ventasActualizadas: Int,
)

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
