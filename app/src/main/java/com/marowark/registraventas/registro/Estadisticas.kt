package com.marowark.registraventas.registro

/** Estadísticas de un Período (sólo Ventas no canceladas). Montos en centavos. */
data class Estadisticas(
    val cantidadVentas: Int,
    val unidades: Int,
    val monto: Long,
    val costosEnvio: Long,
    val comisiones: Long,
    /** De mayor a menor Monto; incluye Productos inactivos. Sin Neto (no se prorratean costos). */
    val porProducto: List<FilaProducto>,
    /** De mayor a menor Monto. */
    val porCanal: List<FilaCanal>,
) {
    val neto: Long get() = monto - costosEnvio - comisiones
}

data class FilaProducto(val productoId: String, val nombre: String, val unidades: Int, val monto: Long)

data class FilaCanal(val canalId: String, val nombre: String, val monto: Long, val costosEnvio: Long, val comisiones: Long) {
    val neto: Long get() = monto - costosEnvio - comisiones
}

internal fun calcularEstadisticas(ventas: List<Venta>, nombresProducto: Map<String, String>): Estadisticas {
    val lineas = ventas.flatMap { it.lineas }
    return Estadisticas(
        cantidadVentas = ventas.size,
        unidades = lineas.sumOf { it.cantidad },
        monto = ventas.sumOf { it.monto },
        costosEnvio = ventas.sumOf { it.costoEnvio },
        comisiones = ventas.sumOf { it.comision },
        porProducto = lineas.groupBy { it.productoId }.map { (id, ls) ->
            FilaProducto(id, nombresProducto[id].orEmpty(), ls.sumOf { it.cantidad }, ls.sumOf { it.cantidad * it.precioCobrado })
        }.sortedWith(compareByDescending<FilaProducto> { it.monto }.thenBy { it.nombre }),
        porCanal = ventas.groupBy { it.canalId }.map { (id, vs) ->
            FilaCanal(id, vs.first().canalNombre, vs.sumOf { it.monto }, vs.sumOf { it.costoEnvio }, vs.sumOf { it.comision })
        }.sortedWith(compareByDescending<FilaCanal> { it.monto }.thenBy { it.nombre }),
    )
}
