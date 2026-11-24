package com.marowark.registraventas.registro

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.UUID

// shortcut: sin migraciones ni schema exportado mientras la app no tenga datos reales; agregarlas antes de la primera instalación en uso.
@Database(entities = [ProductoEntity::class, CanalEntity::class], version = 1, exportSchema = false)
abstract class RegistroDatabase : RoomDatabase() {
    abstract fun productos(): ProductoDao
    abstract fun canales(): CanalDao

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
}

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
}
