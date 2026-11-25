# DriverAI Bridge — copiloto Android para conductores

App Android nativa (Kotlin + Jetpack Compose) que evalúa ofertas de viaje con reglas económicas configurables por el conductor, muestra una tarjeta flotante con la recomendación (CONVIENE / NO CONVIENE / REVISAR) y suma el contexto estratégico del backend existente de DriverAI (Supabase).

**DriverAI recomienda; el conductor decide.** La app no toca la pantalla, no acepta ni rechaza viajes.

## Estado
| | |
|---|---|
| Compila en la nube y genera APK | Sí — CI verde en run `45628698011`, commit `d1619ce` |
| Pruebas unitarias / lint | En verde en CI (run `45628698011`, commit `d1619ce`) |
| Contrato con backend real | Validado históricamente (run 17); omitido deliberadamente en el último CI manual |
| Emulador Android 11 | En verde en run `45628698011`, incluida accesibilidad → tarjeta con un simulador |
| Uber real en teléfono real | **OCR leyó una oferta real** (run 17 / APK `713f1fe`); revalidar accesibilidad automática y más formatos |
| Briefing de turno | En desarrollo en `manus/briefing-end-to-end`: consulta explícita al reporte heredado y snapshot local; aún sin feeds verificados ni índice calibrado |

## Documentación
- [Auditoría inicial](docs/INITIAL_AUDIT.md) — repos, web, backend y hallazgos.
- [Arquitectura](docs/ARCHITECTURE.md)
- [Visión y hoja de ruta del copiloto](docs/PRODUCT_VISION.md)
- [Investigación de fuentes para el briefing](docs/BRIEFING_SOURCE_RESEARCH.md) — cobertura, vigencia, permisos y brechas de producción.
- [Permisos y políticas](docs/ANDROID_PERMISSIONS.md)
- [Plan y resultados de pruebas](docs/TEST_PLAN.md)
- [Compilar, descargar e instalar](docs/BUILD_AND_INSTALL.md)
- [Limitaciones conocidas](docs/KNOWN_LIMITATIONS.md)
- [Changelog](CHANGELOG.md)

## Uso rápido
1. Descargá el APK desde Actions (ver guía de instalación) e instalalo.
2. **Ajustes**: cargá al menos un umbral (por ejemplo, mínimo $ por km).
3. **Evaluar**: probá una oferta a mano (modo demo).
4. **Inicio → Activar lectura automática**: leé la divulgación, aceptá y activá "DriverAI – lectura de ofertas" en Accesibilidad.
5. Abrí Uber Driver. Cuando llegue una oferta, debería aparecer la tarjeta. Si no aparece, probá **Activar lectura por captura (OCR)** y el botón "Leer oferta".
6. **Chequeo**: estado de cada permiso y módulo, prueba del backend y registro reducido (métricas y etiquetas permitidas, sin nombres ni direcciones) para calibrar.
