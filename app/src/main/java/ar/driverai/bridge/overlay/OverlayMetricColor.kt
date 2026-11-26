package ar.driverai.bridge.overlay

/** Colores de semáforo para una métrica frente al umbral personal del conductor. */
internal object OverlayMetricColor {
    val GREEN = 0xFF62E6A4.toInt()
    val RED = 0xFFFF777D.toInt()
    val AMBER = 0xFFFFC66D.toInt()
    val NEUTRAL = 0xFFB6C2BB.toInt()

    fun forThreshold(value: Double?, limit: Double?, higherIsBetter: Boolean): Int = when {
        limit == null -> NEUTRAL
        value == null -> AMBER
        !value.isFinite() || !limit.isFinite() || limit < 0.0 -> AMBER
        (if (higherIsBetter) value >= limit else value <= limit) -> GREEN
        else -> RED
    }
}
