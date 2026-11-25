package ar.driverai.bridge.engine

import kotlinx.serialization.Serializable

/**
 * Apps de conductor que DriverAI puede observar (solo lectura). La compatibilidad real de cada
 * diseño se valida con muestras; [verified] indica si hay al menos una lectura real confirmada.
 */
@Serializable
enum class RidePlatform(
    val label: String,
    val packageName: String?,
    val verified: Boolean,
    private val textHints: List<String>,
) {
    UBER("Uber", "com.ubercab.driver", verified = true, textHints = listOf("uber", "uberx", "uber priority", "uber comfort")),
    DIDI("DiDi", "com.didiglobal.driver", verified = false, textHints = listOf("didi", "didi express", "didi conductor")),
    CABIFY("Cabify", "com.cabify.driver", verified = false, textHints = listOf("cabify")),
    INDRIVE("inDrive", "sinet.startup.inDriver", verified = false, textHints = listOf("indrive", "indriver", "ofrecer tu tarifa", "propón tu tarifa", "propone tu tarifa")),
    BOLT("Bolt", "ee.mtakso.driver", verified = false, textHints = listOf("bolt")),
    OTRA("Otra / manual", null, verified = false, textHints = emptyList());

    companion object {
        val READABLE: List<RidePlatform> = entries.filter { it.packageName != null }

        fun fromPackage(pkg: String?): RidePlatform? = entries.firstOrNull { it.packageName != null && it.packageName == pkg }

        fun fromName(name: String?): RidePlatform = entries.firstOrNull { it.name == name } ?: OTRA

        /** Para OCR: adivina la app por palabras visibles. Devuelve null si no hay una única coincidencia. */
        fun guessFromText(fragments: List<String>): RidePlatform? {
            val text = fragments.joinToString(" ").lowercase()
            val hits = READABLE.filter { p -> p.textHints.any { hint -> Regex("(?<![\\p{L}])" + Regex.escape(hint) + "(?![\\p{L}])").containsMatchIn(text) } }
            return hits.singleOrNull()
        }
    }
}
