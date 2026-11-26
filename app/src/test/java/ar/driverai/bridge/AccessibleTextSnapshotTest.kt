package ar.driverai.bridge

import ar.driverai.bridge.capture.AccessibleTextSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibleTextSnapshotTest {
    @Test fun samePackage_mergesVisibleNodesAndEventTextWithoutDuplicates() {
        val result = AccessibleTextSnapshot.select(
            eventPackage = "com.ubercab.driver",
            rootPackage = "com.ubercab.driver",
            eventTexts = listOf("Aceptar", "$ 8.540"),
            treeTexts = listOf("UberX", "$ 8.540", "Viaje de 18 min (9,3 km)"),
        )

        assertEquals(
            listOf("UberX", "$ 8.540", "Viaje de 18 min (9,3 km)", "Aceptar"),
            result,
        )
    }

    @Test fun differentRootPackage_usesOnlyTextsBelongingToTheObservedEvent() {
        val result = AccessibleTextSnapshot.select(
            eventPackage = "com.ubercab.driver",
            rootPackage = "com.android.systemui",
            eventTexts = listOf("$ 8.540", "Viaje de 18 min (9,3 km)"),
            treeTexts = listOf("Notificación privada", "Texto de otra app"),
        )

        assertEquals(listOf("$ 8.540", "Viaje de 18 min (9,3 km)"), result)
    }

    @Test fun nullRoot_keepsEventTextButMissingEventPackageIsIgnored() {
        assertEquals(
            listOf("Solicitud nueva"),
            AccessibleTextSnapshot.select("com.ubercab.driver", null, listOf("Solicitud nueva"), emptyList()),
        )
        assertEquals(
            emptyList<String>(),
            AccessibleTextSnapshot.select(null, null, listOf("texto"), listOf("árbol")),
        )
    }
}
