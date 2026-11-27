# Auditoría V2 — DriverAI Bridge

Fecha: 2026-11-27. Base: `manus/offer-reading-card-cleanup` @ `19243e89`.

## Crítico

1. **Fricción de cobro (6–7 toques)** — Tras «La acepté» el chofer debía abrir app → Registro → Completar → Cobrar → monto → guardar.
   - Impacto: en la calle no se registra; la billetera queda vacía.
   - Arreglo: `confirmCollected` + tarjeta overlay «¿Cobraste $X?» + botón en Registro con tarifa precargada.

2. **Decode silencioso de JSON** — `getOrDefault(emptyList())` ante corrupción.
   - Impacto: un write posterior puede pisar el historial.
   - Arreglo parcial: `isCorrupt()` expuesto; backup activo pendiente.

## Alto

3. Firma debug rotativa en CI — ver `docs/SIGNING.md`.
4. Lectura automática en teléfonos viejos — híbrido OCR en base; prueba real pendiente.

## Medio

5. Aprendizaje por franja/zona incompleto en UI.
6. Multi-app: parsers solo validados contra Uber real.
7. Exportar/importar datos pendiente.
