# DriverAI Bridge — copiloto Android para conductores

App Android nativa (Kotlin + Jetpack Compose) que funciona como copiloto en tiempo real para conductores de apps en LATAM. Evalúa ofertas de Uber, DiDi, Cabify, inDrive y Bolt con reglas económicas configurables por el conductor, predice dónde y cuándo conviene estar con un radar de demanda local (partidos, feriados, paros, vuelos, cruceros, lluvia y el propio historial), muestra la demanda prevista en el destino de cada oferta y lleva los informes de ingresos. También muestra una tarjeta flotante con la recomendación (CONVIENE / NO CONVIENE / REVISAR) y suma el contexto estratégico del backend existente de DriverAI (Supabase).

**DriverAI recomienda; el conductor decide.** La app no toca la pantalla, no acepta ni rechaza viajes.

## Estado
| | |
|---|---|
| Compila en la nube y genera APK | Sí — [CI verde](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45652876845), commit `c12f718` en rama `feature/realtime-fleet-and-brand` |
| Pruebas unitarias / lint | En verde en CI (run `45652876845`, commit `c12f718`) |
| Contrato con backend real | Validado históricamente (run 17); omitido deliberadamente en la CI manual más reciente |
| Emulador Android 11 | En verde en run `45652876845` (API 30) |
| Uber real en teléfono real | **OCR leyó una oferta real** (run 17 / APK `713f1fe`); revalidar accesibilidad automática y más formatos |
| Briefing de turno | Parcial: consulta explícita al reporte heredado y guarda un snapshot local; aún sin feeds verificados ni índice calibrado |
| Flotas y demanda colaborativa | Propuesta documentada; sin backend multiusuario ni ingestión de observaciones |

## Documentación
- [Auditoría inicial](docs/INITIAL_AUDIT.md) — repos, web, backend y hallazgos.
- [Arquitectura](docs/ARCHITECTURE.md)
- [Radar de demanda y multi-app](docs/RADAR_DEMANDA.md): modelo del índice, fuentes, privacidad y propuesta de servidor.
- [Visión y hoja de ruta del copiloto](docs/PRODUCT_VISION.md)
- [Investigación de fuentes para el briefing](docs/BRIEFING_SOURCE_RESEARCH.md) — cobertura, vigencia, permisos y brechas de producción.
- [Permisos y políticas](docs/ANDROID_PERMISSIONS.md)
- [Plan y resultados de pruebas](docs/TEST_PLAN.md)
- [Compilar, descargar e instalar](docs/BUILD_AND_INSTALL.md)
- [Limitaciones conocidas](docs/KNOWN_LIMITATIONS.md)
- [Changelog](CHANGELOG.md)
- [Instrucciones para agentes](AGENTS.md) y [handoff actualizado](docs/AGENT_HANDOFF.md) — estado, límites y cómo continuar sin repetir auditorías.

## Uso rápido
1. Descargá el APK desde Actions (ver guía de instalación) e instalalo.
2. **Ajustes**: cargá al menos un umbral (por ejemplo, mínimo $ por km).
3. **Evaluar**: probá una oferta a mano (modo demo).
4. **Inicio → Activar lectura automática**: leé la divulgación, aceptá y activá "DriverAI – lectura de ofertas" en Accesibilidad.
5. Abrí Uber Driver. Cuando llegue una oferta, debería aparecer la tarjeta. Si no aparece, probá **Activar lectura por captura (OCR)** y el botón "Leer oferta".
6. **Chequeo**: estado de cada permiso y módulo, prueba del backend y registro reducido (métricas y etiquetas permitidas, sin nombres ni direcciones) para calibrar.
