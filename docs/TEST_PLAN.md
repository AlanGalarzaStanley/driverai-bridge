# Plan y resultados de pruebas

Estado: **E** = ejecutada y pasó · **CI** = ejecutada y pasó en GitHub Actions · **P** = pendiente (requiere teléfono real o Uber).

## Resultado de la última ejecución verificada
- Android CI run 17, commit `713f1fe`: unitarias, lint, generación/verificación de APK, contrato backend y pruebas instrumentadas en emulador **pasaron**. [Ejecución](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/44862295430).
  - APK debug verificado: 43,8 MB. El paso de publicar GitHub Release se omitió por ser una ejecución manual desde rama de trabajo; no se publicó en Play.
  - Contrato con Supabase real: OK. Web publicada: HTTP 200.
  - Emulador Android 11 (API 30, AOSP x86_64): **OK (6 tests)**. La accesibilidad quedó enlazada en el simulador; esto no demuestra compatibilidad con Uber real.
  - Prueba real informada por el conductor: el APK `713f1fe` detectó una oferta con fuente OCR; mostró REVISAR porque no había umbrales configurados. Es una muestra exitosa, no una validación general. La lectura automática por accesibilidad sigue pendiente de revalidación.
- Fallas encontradas y corregidas en el camino: regex con `$` (unitaria), `setup-android` (CI), `setUp()` no-void (instrumentada), UiAutomator suspendía la accesibilidad (instrumentada), etiquetas de navegación cortadas (captura de pantalla).

## A. Unitarias (`app/src/test`, JVM)
| Área | Archivo | Casos | Estado |
|---|---|---|---|
| Motor económico | `OfferEvaluatorTest` | ejemplo $8.000/10 km/25 min → $800/km y $19.200/h; desfavorable; límite exacto (≥); justo debajo; sin umbrales; sin recogida (pasa → REVISAR, falla → NO CONVIENE); recogida por defecto; sin tarifa; inválidos (negativo, NaN, ∞, 0); baja confianza vs tolerancia; costos + combustible + margen; margen sin costos; combustible desactualizado; retorno vacío; sin minutos; formatos | E |
| Parser / OCR | `OfferTextParserTest` | importes AR/US/ambiguos y ARS al final; oferta clara es/en (millas); horas+min; metros; sin etiquetas; un solo par ambiguo; texto parcial; no-oferta; bonos y $/h ignorados; varios importes; correcciones OCR (S→$, rn→m); distancia-antes-de-tiempo; espacios no separables | CI (run 17) |
| Chequeo / privacidad | `DiagnosticTextSanitizerTest` | conserva solo importes (también ARS al final), tiempos, distancias y etiquetas permitidas; descarta nombres/direcciones/texto libre; no trata “Km 25” como distancia; limita y normaliza fragmentos; copia campos, confianza porcentual y veredicto reducidos | CI (run 17) |
| Backend | `BackendTest` (MockWebServer) | contrato real de ambos endpoints (JSON capturado), path/método/body, sin `Authorization`; campos nuevos/faltantes; HTTP 500; `ok:false`; JSON inválido; timeout; desconexión; contexto desactualizado y conservación del último dato | E |
| Datos y copiloto | `DataAndCopilotTest` | persistencia DataStore; JSON corrupto; historial (límite 100, sin texto); turno $/h; intenciones de las 6 consultas; respuestas breves y con fuente | E |

La suite pasó en GitHub Actions run 17. No se repitió localmente en esta sandbox porque no tiene Android SDK.

## B. Integración
| Prueba | Estado |
|---|---|
| `LiveBackendContractTest` contra Supabase real (job `backend-contract`) | CI |
| Regresión web: `https://driverai-ba.lovable.app` responde 200 (job `backend-contract`) | CI |
| Sin backend: evaluación local sigue funcionando (por diseño: el motor no depende de la red; `StrategicRepository` marca "desactualizado") | E (unitaria) |
| Persistencia de configuración | E (unitaria) + CI (instrumentada `ajustes_guardan`) |

## C. Android (emulador API 30 AOSP en CI; prueba física parcial: una lectura OCR real; paridad completa pendiente) — `AppInstrumentedTest`
| Prueba | Estado |
|---|---|
| Inicio de la app | CI |
| Evaluación manual de punta a punta en la UI | CI |
| Guardado de ajustes | CI |
| Tarjeta flotante aparece y se cierra con "×" | CI |
| Accesibilidad → parser → motor → tarjeta con el **simulador** (no Uber); rotación con tarjeta visible; ocultamiento al desaparecer la oferta; la app de origen sigue en primer plano (no se tocó nada) | CI |
| Deduplicación (misma oferta 3 veces = 1 evaluación) | CI |
| Capturas de pantalla, logcat, meminfo y batterystats como evidencia | CI (artefacto `emulator-N`) |
| Captura por MediaProjection (requiere diálogo de consentimiento humano) | P |
| Revocación de permisos y recuperación tras forzar cierre | P |

## D. Lectura de ofertas reales de Uber
| Prueba | Estado |
|---|---|
| ¿Uber Driver expone el texto de la oferta por accesibilidad? | **P** — la prueba automática previa no detectó; falta repetir en la nueva versión |
| ¿OCR puede leer una oferta real? | **E (una muestra)** — el APK `713f1fe` leyó una oferta; no cubre todos los diseños |
| Precisión del parser con textos reales (usar Diagnóstico → "Registrar lectura reducida" → copiar registro reducido; no compartir captura ni texto crudo) | **E parcial** — 1 lectura OCR; faltan más ofertas y validar Accesibilidad |

## E. Regresión y seguridad
| Prueba | Estado |
|---|---|
| Escaneo de secretos en CI (service_role, gsk_, sk-, claves privadas, JWT largos, keystores) | CI |
| Permisos del APK listados en `build-info.txt` | CI |
| Web y funciones Supabase no modificadas | E (no se tocaron) |

## Protocolo para la siguiente prueba física
1. Usar el APK `713f1fe` o posterior (ver `BUILD_AND_INSTALL.md`). Anotar modelo, Android y versión de Uber Driver; no anotar datos personales.
2. En Ajustes, cargar al menos un umbral personal —no usar valores inventados— para que el veredicto pueda evaluarse.
3. En Inicio, activar la lectura automática y habilitar el servicio de Accesibilidad de DriverAI.
4. En Diagnóstico, activar **Registrar lectura reducida** antes de abrir Uber Driver.
5. Dejar que llegue una oferta y comprobar primero si Accesibilidad la detecta sin tocar **Leer oferta**. Comparar tarifa, recogida, tramo del viaje y veredicto; no copiar ni transcribir texto crudo.
6. En Diagnóstico, usar **Copiar registro reducido** y revisar fuente, campos y confianza. Compartir solo ese registro si se necesita calibración; nunca compartir texto crudo ni capturas.
7. Si Accesibilidad falla, probar OCR por separado con el consentimiento del sistema. Ya hubo una lectura OCR real exitosa; esta repetición sirve para aislar la ruta automática. Desactivar el registro reducido al terminar.
