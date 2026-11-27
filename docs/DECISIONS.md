# Decisiones de producto/ingeniería

## 2026-11-27 — Cobro en un toque

- **Decisión:** Permitir pasar de `ACCEPTED` a `PAID` en una sola acción (`confirmCollected`) con el monto de la oferta precargado.
- **Por qué:** El chofer no puede hacer 6 pasos al volante. El ingreso sigue requiriendo confirmación humana.
- **Alternativa descartada:** Mantener COMPLETED obligatorio; generaba fricción sin valor.
- **Riesgo:** Confirmar el monto de la oferta cuando el cobro real difiere. Mitigación: botón «Otro monto / más tarde» y corrección en Registro.

## 2026-11-27 — No auto-aceptar viajes

- Sin cambios: la app nunca toca la UI de Uber/DiDi/Cabify/inDrive.
