package com.marowark.registraventas.registro

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class TipoPeriodo { SEMANA, MES, ANIO }

/** Semana (lunes a domingo), mes o año que contiene [ancla]. [inicio] y [fin] inclusivos. */
data class Periodo(val tipo: TipoPeriodo, val ancla: LocalDate) {
    val inicio: LocalDate get() = when (tipo) {
        TipoPeriodo.SEMANA -> ancla.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        TipoPeriodo.MES -> ancla.withDayOfMonth(1)
        TipoPeriodo.ANIO -> ancla.withDayOfYear(1)
    }

    val fin: LocalDate get() = siguiente().inicio.minusDays(1)

    fun anterior() = mover(-1)
    fun siguiente() = mover(1)

    private fun mover(n: Long) = Periodo(
        tipo,
        when (tipo) {
            TipoPeriodo.SEMANA -> inicio.plusWeeks(n)
            TipoPeriodo.MES -> inicio.plusMonths(n)
            TipoPeriodo.ANIO -> inicio.plusYears(n)
        },
    )
}
