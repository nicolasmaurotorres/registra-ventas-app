package com.marowark.registraventas.registro

import android.content.Context
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.Clock
import java.util.UUID

// shortcut: sin migraciones ni schema exportado mientras la app no tenga datos reales; agregarlas antes de la primera instalación en uso.
@Database(entities = [ProductoEntity::class, CanalEntity::class, VentaEntity::class, LineaEntity::class], version = 1, exportSchema = false)
abstract class RegistroDatabase : RoomDatabase() {
    abstract fun productos(): ProductoDao
    abstract fun canales(): CanalDao
    abstract fun ventas(): VentaDao

    companion object {
        fun crear(context: Context, reloj: Clock): RegistroDatabase =
            Room.databaseBuilder(context, RegistroDatabase::class.java, "registro.db").conPrecarga(reloj).build()
    }
}

val CANALES_PRECARGADOS = listOf("Mercado Libre", "Facebook Marketplace", "Tienda web", "En mano")

/** Precarga los Canales sólo al crear el archivo de la base (onCreate), así reabrir no los duplica. */
fun RoomDatabase.Builder<RegistroDatabase>.conPrecarga(reloj: Clock) = addCallback(object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        for (nombre in CANALES_PRECARGADOS) {
            db.execSQL(
                "INSERT INTO canal (id, nombre, nombreNormalizado, activo, ultimaModificacion) VALUES (?, ?, ?, 1, ?)",
                arrayOf<Any>(UUID.randomUUID().toString(), nombre, normalizar(nombre), reloj.millis()),
            )
        }
    }
})

/** Ids UUID y última modificación en epoch millis (ADR-0001). Montos en centavos. */
@Serializable
@Entity(tableName = "producto", indices = [Index(value = ["nombreNormalizado"], unique = true)])
data class ProductoEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val nombreNormalizado: String,
    val precioReferencia: Long,
    val activo: Boolean,
    val ultimaModificacion: Long,
)

@Dao
interface ProductoDao {
    @Query("SELECT * FROM producto WHERE activo ORDER BY nombreNormalizado")
    fun activos(): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM producto ORDER BY nombreNormalizado")
    fun todos(): Flow<List<ProductoEntity>>

    @Query("SELECT id FROM producto WHERE nombreNormalizado = :nombreNormalizado")
    suspend fun idConNombre(nombreNormalizado: String): String?

    @Query("SELECT * FROM producto WHERE id = :id")
    suspend fun porId(id: String): ProductoEntity?

    @Query("DELETE FROM producto WHERE id = :id")
    suspend fun borrar(id: String)

    @Upsert
    suspend fun guardar(producto: ProductoEntity)

    @Query("SELECT * FROM producto") suspend fun lista(): List<ProductoEntity>
}

@Serializable
@Entity(tableName = "canal", indices = [Index(value = ["nombreNormalizado"], unique = true)])
data class CanalEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val nombreNormalizado: String,
    val activo: Boolean,
    val ultimaModificacion: Long,
)

@Dao
interface CanalDao {
    @Query("SELECT * FROM canal WHERE activo ORDER BY nombreNormalizado")
    fun activos(): Flow<List<CanalEntity>>

    @Query("SELECT * FROM canal ORDER BY nombreNormalizado")
    fun todos(): Flow<List<CanalEntity>>

    @Query("SELECT id FROM canal WHERE nombreNormalizado = :nombreNormalizado")
    suspend fun idConNombre(nombreNormalizado: String): String?

    @Query("SELECT * FROM canal WHERE id = :id")
    suspend fun porId(id: String): CanalEntity?

    @Query("DELETE FROM canal WHERE id = :id")
    suspend fun borrar(id: String)

    @Upsert
    suspend fun guardar(canal: CanalEntity)

    @Query("SELECT * FROM canal") suspend fun lista(): List<CanalEntity>
}

/** Fechas en epoch days (LocalDate.toEpochDay). Despacho/entrega null mientras no ocurren. */
@Serializable
@Entity(tableName = "venta", indices = [Index("canalId")])
data class VentaEntity(
    @PrimaryKey val id: String,
    val fechaVenta: Long,
    val fechaDespacho: Long?,
    val fechaEntrega: Long?,
    val canalId: String,
    val cliente: String?,
    val costoEnvio: Long,
    val comision: Long,
    val cancelada: Boolean,
    val ultimaModificacion: Long,
)

@Serializable
@Entity(tableName = "linea", indices = [Index("ventaId"), Index("productoId")])
data class LineaEntity(
    @PrimaryKey val id: String,
    val ventaId: String,
    val productoId: String,
    val cantidad: Int,
    val precioCobrado: Long,
    val ultimaModificacion: Long,
)

data class VentaConLineas(
    @Embedded val venta: VentaEntity,
    val canalNombre: String,
    @Relation(parentColumn = "id", entityColumn = "ventaId") val lineas: List<LineaEntity>,
)

@Dao
interface VentaDao {
    @Transaction
    @Query(
        "SELECT venta.*, canal.nombre AS canalNombre FROM venta JOIN canal ON canal.id = venta.canalId " +
            "WHERE NOT cancelada ORDER BY fechaVenta DESC, venta.ultimaModificacion DESC",
    )
    fun vigentes(): Flow<List<VentaConLineas>>

    @Transaction
    @Query("SELECT venta.*, canal.nombre AS canalNombre FROM venta JOIN canal ON canal.id = venta.canalId WHERE venta.id = :id")
    suspend fun porId(id: String): VentaConLineas?

    @Query("SELECT EXISTS(SELECT 1 FROM linea WHERE productoId = :productoId)")
    suspend fun hayConProducto(productoId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM venta WHERE canalId = :canalId)")
    suspend fun hayConCanal(canalId: String): Boolean

    @Upsert suspend fun guardar(venta: VentaEntity)
    @Upsert suspend fun guardarLineas(lineas: List<LineaEntity>)

    /** Venta y Líneas juntas o nada. */
    @Transaction
    suspend fun guardarConLineas(venta: VentaEntity, lineas: List<LineaEntity>) {
        guardar(venta)
        guardarLineas(lineas)
    }

    @Transaction
    @Query(
        "SELECT venta.*, canal.nombre AS canalNombre FROM venta JOIN canal ON canal.id = venta.canalId " +
            "WHERE cancelada ORDER BY fechaVenta DESC, venta.ultimaModificacion DESC",
    )
    fun canceladas(): Flow<List<VentaConLineas>>

    @Query("UPDATE venta SET cancelada = :cancelada, ultimaModificacion = :ultimaModificacion WHERE id = :id")
    suspend fun marcarCancelada(id: String, cancelada: Boolean, ultimaModificacion: Long)

    @Query("SELECT * FROM venta") suspend fun lista(): List<VentaEntity>
    @Query("SELECT * FROM linea") suspend fun lineas(): List<LineaEntity>
}
