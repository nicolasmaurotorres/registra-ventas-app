package com.marowark.registraventas

import org.junit.Assert.assertEquals
import org.junit.Test

class PesosTest {
    @Test
    fun formatea_ars_con_miles_y_dos_decimales() {
        assertEquals("$1.234,50", formatearPesos(123450))
        assertEquals("$0,05", formatearPesos(5))
        assertEquals("$1.234.567,00", formatearPesos(123456700))
        assertEquals("-$10,00", formatearPesos(-1000))
    }

    @Test
    fun parsea_coma_o_punto_decimal() {
        assertEquals(10050L, parsearPesos("100.50"))
        assertEquals(10050L, parsearPesos("100,5"))
        assertEquals(10000L, parsearPesos(" 100 "))
        assertEquals(123450L, parsearPesos("$1.234,50"))
        assertEquals(123450L, parsearPesos("1,234.50"))
        assertEquals(123400L, parsearPesos("1.234"))
    }

    @Test
    fun rechaza_texto_invalido() {
        assertEquals(null, parsearPesos(""))
        assertEquals(null, parsearPesos("abc"))
        assertEquals(null, parsearPesos("1,234,5"))
        assertEquals(null, parsearPesos("-5"))
    }
}
