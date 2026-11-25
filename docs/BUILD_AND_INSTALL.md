# Compilar, descargar e instalar

## Descarga recomendada (piloto actual)

El repositorio es **privado**. Iniciá sesión en GitHub en Chrome del teléfono.

**Última build estable de la rama piloto** (`feature/copiloto-radar-latam`):

1. Abrí [Android CI run 69](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45694670753).
2. En **Artifacts**, descargá `driverai-debug-apk-69` (GitHub baja un `.zip`).
3. Extraé el `.apk` con Archivos.
4. Instalá. Si Android lo pide, permití "Instalar apps desconocidas" solo para Archivos o Chrome.
5. Play Protect puede avisar (APK de depuración fuera de Play): "Instalar de todos modos".

Incluye: cockpit premium, radar de demanda local, lectura multi-app, **Actualizar con IA** (copiar pedido / enviar a IA / pegar respuesta / cargar al radar) y el flujo de señales con confianza acotada.

Tras el commit `Abrir Google con búsqueda lista`, generá una nueva corrida de Android CI sobre la misma rama para obtener el APK con ese botón.

## Otras copias

Copia fija del commit `95c4590` (run 22):

<https://github.com/AlanGalarzaStanley/driverai-bridge/releases/download/apk-22/driverai-debug-95c4590.apk>

SHA-256: `919649f7f645d5b2aa9ac0e16774ce797ebe3ddee21e569e297eed2656911e4f`

El tag `latest-debug` / releases `apk-*` en `main` pueden estar detrás de la rama piloto.

## Compilación en la nube

Cada push a `main` (y workflow_dispatch) ejecuta `.github/workflows/android.yml`:
1. JDK 17 + SDK + Gradle wrapper.
2. Unit tests → Lint → `assembleDebug`.
3. Artefacto `driverai-debug-apk-<N>` + reportes.
4. Jobs de contrato backend y emulador Android 11.

Para correr a mano: GitHub → Actions → Android CI → Run workflow (elegí la rama `feature/copiloto-radar-latam` si querés el piloto completo).

## Instalar
1. Tocá el `.apk` y permití instalar desde esa app.
2. Si hay conflicto de firma, desinstalá la versión anterior y volvé a instalar (se pierden ajustes locales).

## Problemas habituales
| Síntoma | Solución |
|---|---|
| "No se puede abrir el archivo" | Descomprimí el `.zip` primero. |
| Conflicto de paquete | Desinstalá DriverAI anterior. |
| Play Protect | "Instalar de todos modos". |
| Accesibilidad gris (Android 13+) | Apps → DriverAI → ⋮ → Permitir configuración restringida. |
| No aparece la tarjeta | Diagnóstico: accesibilidad + superposición; desactivar optimización de batería. |

## Firma
Solo firma de depuración del runner. No hay keystore de producción en el repo.
