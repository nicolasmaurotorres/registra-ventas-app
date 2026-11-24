package com.marowark.registraventas.registro

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class RegistroTest {
    private val prueba = RegistroDePrueba(hoy = LocalDate.of(2026, 3, 15))
    private val registro = prueba.registro

    @After fun cerrar() = prueba.cerrar()

    @Test
    fun hoy_sale_del_reloj_inyectado() {
        assertEquals(LocalDate.of(2026, 3, 15), registro.hoy())

        prueba.reloj.avanzarDias(1)

        assertEquals(LocalDate.of(2026, 3, 16), registro.hoy())
    }
}
