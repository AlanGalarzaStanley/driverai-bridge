# DriverAI Bridge — copiloto Android para conductores

App Android nativa (Kotlin + Jetpack Compose) que evalúa ofertas de viaje con reglas económicas configurables por el conductor, muestra una tarjeta flotante con la recomendación (CONVIENE / NO CONVIENE / REVISAR) y suma el contexto estratégico del backend existente de DriverAI (Supabase).

**DriverAI recomienda; el conductor decide.** La app no toca la pantalla, no acepta ni rechaza viajes.

## Estado
| | |
|---|---|
| Compila en la nube y genera APK | Sí — GitHub Actions → artefacto `driverai-debug-apk-N` |
| Pruebas unitarias / lint | En verde en CI (run 17, commit `713f1fe`) |
| Contrato con backend real | En verde (job `backend-contract`) |
| Emulador Android 11 | 6 pruebas instrumentadas en verde, incluida accesibilidad → tarjeta con un simulador |
| Uber real en teléfono real | **OCR leyó una oferta real** (run 17 / APK `713f1fe`); revalidar accesibilidad automática y más formatos |

## Documentación
- [Auditoría inicial](docs/INITIAL_AUDIT.md) — repos, web, backend y hallazgos.
- [Arquitectura](docs/ARCHITECTURE.md)
- [Visión y hoja de ruta del copiloto](docs/PRODUCT_VISION.md)
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
