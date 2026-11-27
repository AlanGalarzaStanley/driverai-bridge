# Wiring HomeScreen

En `HomeScreen.kt`, después del row Billetera/Vehículo, agregar:

```kotlin
HomePlanDiaSection(now = now)
```

La sección completa vive en `PlanDiaScreen.kt` (`HomePlanDiaSection`).
App.kt ya expone `planDia` en el grafo.
