package com.marowark.registraventas.registro

import kotlinx.serialization.Serializable

/**
 * Archivo de respaldo (ADR-0001). Usa las mismas entidades de Room: fechas en epoch days,
 * ultimaModificacion en epoch millis, montos en centavos. Las Líneas van planas con su ventaId.
 * Cambiar el formato o las entidades exige subir [VERSION_RESPALDO].
 */
@Serializable
data class Respaldo(
    val version: Int,
    val productos: List<ProductoEntity>,
    val canales: List<CanalEntity>,
    val ventas: List<VentaEntity>,
    val lineas: List<LineaEntity>,
)

const val VERSION_RESPALDO = 1
