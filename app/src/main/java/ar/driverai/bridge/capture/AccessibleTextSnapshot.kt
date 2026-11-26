package ar.driverai.bridge.capture

/** Selecciona un lote efímero de texto sin mezclar ventanas de aplicaciones distintas. */
internal object AccessibleTextSnapshot {
    fun select(
        eventPackage: String?,
        rootPackage: String?,
        eventTexts: List<String>,
        treeTexts: List<String>,
    ): List<String> {
        if (eventPackage.isNullOrBlank()) return emptyList()
        val sameWindow = rootPackage == eventPackage
        val visibleTree = if (sameWindow) treeTexts else emptyList()
        return (visibleTree + eventTexts)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
}
