package com.marowark.registraventas.registro

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Fachada del dominio sobre Room: el único seam de test (ver spec #1).
 * Los ViewModels y los tests sólo hablan con esta clase.
 */
class Registro(
    private val db: RegistroDatabase,
    private val reloj: Clock,
) {
    /** Fecha de hoy según el reloj inyectado (p. ej. Fecha de venta por defecto). */
    fun hoy(): LocalDate = LocalDate.now(reloj)

    /** Marca de última modificación para cada cambio (ADR-0001). */
    internal fun ahora(): Instant = reloj.instant()
}
