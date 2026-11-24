package com.marowark.registraventas.registro

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Registro sobre Room en memoria con reloj controlable. Usar desde tests con @RunWith(RobolectricTestRunner::class). */
class RegistroDePrueba(hoy: LocalDate = LocalDate.of(2026, 1, 1)) {
    val reloj = RelojDePrueba(hoy.atStartOfDay().toInstant(ZoneOffset.UTC))
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        RegistroDatabase::class.java,
    ).allowMainThreadQueries().conPrecarga(reloj).build()
    val registro = Registro(db, reloj)

    fun cerrar() = db.close()
}

/** Reloj fijo en UTC que los tests pueden adelantar (para "hoy" y la última modificación). */
class RelojDePrueba(var instante: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = instante

    fun avanzar(d: Duration) { instante += d }
    fun avanzarDias(n: Long) = avanzar(Duration.ofDays(n))
}
