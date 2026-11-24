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
import kotlinx.coroutines.flow.Flow

// shortcut: sin migraciones ni schema exportado mientras la app no tenga datos reales; agregarlas antes de la primera instalación en uso.
@Database(entities = [ProductoEntity::class], version = 1, exportSchema = false)
abstract class RegistroDatabase : RoomDatabase() {
    abstract fun productos(): ProductoDao

    companion object {
        fun crear(context: Context): RegistroDatabase =
            Room.databaseBuilder(context, RegistroDatabase::class.java, "registro.db").build()
    }
}

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
