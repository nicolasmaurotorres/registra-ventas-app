package com.marowark.registraventas

import kotlin.math.abs

/** Centavos → "$1.234,50" (pesos argentinos, 2 decimales). */
fun formatearPesos(centavos: Long): String {
    val positivo = abs(centavos)
    val pesos = (positivo / 100).toString().reversed().chunked(3).joinToString(".").reversed()
    val signo = if (centavos < 0) "-" else ""
    return "$signo$$pesos,${(positivo % 100).toString().padStart(2, '0')}"
}

/**
 * Texto ingresado → centavos, o null si no es un monto válido (≥ 0, hasta 2 decimales).
 * Acepta coma o punto decimal y separador de miles: "100.50", "100,5", "$1.234,50", "1,234.50".
 * Un único separador seguido de 3 dígitos se toma como miles ("1.234" = 1234).
 */
fun parsearPesos(texto: String): Long? {
    val s = texto.trim().removePrefix("$").trim()
    val ultimo = s.indexOfLast { it == '.' || it == ',' }
    val ambos = '.' in s && ',' in s
    val esDecimal = ultimo >= 0 && (ambos || s.count { it == s[ultimo] } == 1 && s.length - ultimo - 1 in 1..2)
    val entero = if (esDecimal) s.substring(0, ultimo) else s
    val decimales = if (esDecimal) s.substring(ultimo + 1) else ""
    if (esDecimal && decimales.length !in 1..2 || !decimales.all(Char::isDigit)) return null
    val miles = when {
        esDecimal -> if (s[ultimo] == '.') ',' else '.'
        ultimo >= 0 -> s[ultimo]
        else -> null
    }
    val enteroValido = Regex("\\d+").matches(entero) ||
        miles != null && Regex("\\d{1,3}([$miles]\\d{3})+").matches(entero)
    if (!enteroValido) return null
    return entero.filter(Char::isDigit).toLongOrNull()?.times(100)?.plus(decimales.padEnd(2, '0').toLong())
}
