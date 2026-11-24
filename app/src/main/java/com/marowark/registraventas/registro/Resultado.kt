package com.marowark.registraventas.registro

/** Resultado de una operación de Registro: Ok o una regla violada con su Motivo (spec #1). */
sealed interface Resultado<out T> {
    data class Ok<T>(val valor: T) : Resultado<T>
    data class Error(val motivo: Motivo) : Resultado<Nothing>
}

/** Reglas de dominio violadas. Cada ticket agrega las suyas; la UI muestra [mensaje]. */
enum class Motivo(val mensaje: String) {
    NOMBRE_VACIO("El nombre no puede estar vacío"),
    NOMBRE_DUPLICADO("Ya existe uno con ese nombre"),
    MONTO_NEGATIVO("Los montos no pueden ser negativos"),
    SIN_CANAL("Elegí un Canal"),
    SIN_LINEAS("Agregá al menos un Producto"),
    CANTIDAD_INVALIDA("La cantidad debe ser al menos 1"),
    TIENE_VENTAS("No se puede borrar: tiene Ventas. Desactivalo en su lugar"),
    ENTREGA_SIN_DESPACHO("No puede haber entrega sin despacho"),
    FECHAS_FUERA_DE_ORDEN("Las fechas deben ir en orden: venta, despacho, entrega"),
    RESPALDO_INVALIDO("El archivo no es un respaldo válido"),
}
