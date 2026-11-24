package com.marowark.registraventas.registro

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase

// shortcut: sin migraciones ni schema exportado mientras la app no tenga datos reales; agregarlas antes de la primera instalación en uso.
@Database(entities = [ProductoEntity::class], version = 1, exportSchema = false)
abstract class RegistroDatabase : RoomDatabase() {
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
