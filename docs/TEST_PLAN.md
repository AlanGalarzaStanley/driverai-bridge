# Plan y resultados de pruebas

Estado: **E** = ejecutada y pasó · **CI** = ejecutada y pasó en GitHub Actions · **P** = pendiente (requiere teléfono real o Uber).

## Cambios de `manus/premium-driver-cockpit`
- Lector de accesibilidad: recoge texto efímero de eventos de Uber cuando Android devuelve otra ventana, limpia datos al cambiar de app/ventana y no actúa sobre controles. **CI con simulador: pasó (run 32); prueba física de solicitud entrante: P.**
- Rating del pasajero: umbral explícito 1–5; rating ausente/ambiguo/de baja confianza → REVISAR; rating bajo no produce tarifa piso; no se confunde una etiqueta genérica de usuario. **Unitarias, lint y emulador: CI (run 32).**
- Open-Meteo: solo build debug privada, punto fijo CABA Centro, sin GPS, con atribución/licencia; release apagado hasta fuente/plan autorizado. **CI (tests con respuesta simulada): pasó (run 32); petición real/uso en teléfono: P.**
- Home/cockpit muestra billetera, km y estado de fuentes; vuelos, cruceros, eventos y demanda por franja siguen sin feed verificado.

## Resultado de la última ejecución verificada
- Android CI manual run `45235500410` (run 32), commit `c39ba09`: **unitarias, lint, compilación/verificación del APK y emulador API 30 pasaron**. [Ejecución](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45235500410).
  - APK `0.1.0-c39ba09`: 43.958.669 bytes; SHA-256 `c2b61e61b3fdf89e433eceaded07d06bf1c4d698d35891dff4075c65030d1f82`. Está como artefacto privado `driverai-debug-apk-32` (retención 30 días); no se publicó en GitHub Releases ni reemplazó `latest-debug`.
  - Se usó `workflow_dispatch` con `run_live_contract=false`: contrato Supabase, chequeo web y job de Release omitidos. La instrumentación valida el simulador, no la lectura de Uber en un teléfono.
- Android CI manual run `45077279565`, commit `b2e5d59`: **unitarias, lint, compilación/verificación del APK y emulador pasaron**. [Ejecución](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45077279565).
  - Añadió el piso de tarifa que alcanza el máximo de los umbrales configurados de tarifa, bruto/km, bruto/h y margen; se omite ante datos incompletos o inciertos, recogida desconocida para métricas de recorrido, costo de combustible vencido para margen, margen sin piso finito o resultados fuera de rango. Se muestran los supuestos y no se presenta como oferta futura.
  - APK debug `0.1.0-b2e5d59` disponible como artefacto `driverai-debug-apk-24` (43.909.521 bytes; SHA-256 `cd34d4969cb27affe09de1404bb886523155cb827498fb586211fc37cccf6732`); no se publicó en GitHub Releases ni reemplazó `latest-debug`.
  - El evento fue `workflow_dispatch` con `run_live_contract=false`: se omitieron Supabase, la comprobación de la web y el job de release. El emulador no valida lectura de Uber en un teléfono real.
- Android CI manual run `44984165683`, commit `be5f8c6`: **unitarias, lint, compilación/verificación del APK y emulador pasaron**. [Ejecución](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/44984165683).
  - Añadió cobertura para ofertas Uber Priority con resumen compacto `min · km`, moneda `ARS` sufija y exclusión de `ARS/km` como tarifa. Un resumen único queda a confianza 45 % y la evaluación debe permanecer en REVISAR.
  - APK debug disponible como artefacto `driverai-debug-apk-23` (retención de 30 días); no se publicó en GitHub Releases y no reemplazó `latest-debug`.
  - El contrato live de Supabase y el job de release quedaron omitidos: `run_live_contract=false` y `workflow_dispatch`. No se validó Uber en un teléfono real.
- Android CI manual run `44926383458`, commit `95c4590`: **unitarias, lint, compilación/verificación del APK y emulador pasaron**. [Ejecución](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/44926383458).
  - Validó la billetera, el parser explícito de ARS, los registros diarios de odómetro, los recordatorios de mantenimiento y la navegación de cinco áreas. El chequeo de secretos también pasó.
  - APK debug disponible en el artefacto `driverai-debug-apk-22` de esa ejecución (retención de 30 días). Tras validar el run, se publicó como GitHub Release fijo `apk-22` y se actualizó el asset de `latest-debug`.
  - El contrato live y el job de release del workflow quedaron omitidos por diseño: `run_live_contract=false` y el evento fue `workflow_dispatch`. La publicación manual fue solo a GitHub Releases; no se publicó en Play.
  - La prueba de emulador no valida Uber Driver real. La lectura de Accesibilidad/OCR en teléfono y la frescura de fuentes de demanda siguen requiriendo validación adicional.
- Android CI run 19, commit `e9e140b`: **58 tests JVM (57 pasaron, 1 omitido), lint, compilación/verificación del APK y emulador pasaron**. [Ejecución](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/44875590847).
  - APK debug verificado: 43.811.213 bytes. Está como artefacto `driverai-debug-apk-19` en la ejecución; no se creó un GitHub Release ni se publicó en Play.
  - Emulador Android 11 (API 30, AOSP x86_64): **OK (6 tests)**, incluida la tarjeta de briefing en estado vacío. Esto no demuestra compatibilidad con Uber real.
  - El contrato live de Supabase y la regresión web se omitieron intencionalmente en este despacho manual; no hubo llamadas al backend ni al sitio productivo. El job de release también se omitió.
- Android CI run 17, commit `713f1fe` (referencia histórica): unitarias, lint, APK, contrato backend y emulador **pasaron**. [Ejecución](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/44862295430). El contrato live y la web dieron OK en esa ejecución anterior.
  - Prueba real informada por el conductor: ese APK detectó una oferta con fuente OCR y mostró REVISAR porque no había umbrales configurados. Es una muestra exitosa, no una validación general. La lectura automática por accesibilidad sigue pendiente de revalidación.
- Fallas encontradas y corregidas en el camino: regex con `$` (unitaria), `setup-android` (CI), `setUp()` no-void (instrumentada), UiAutomator suspendía la accesibilidad (instrumentada), etiquetas de navegación cortadas (captura de pantalla).

## A. Unitarias (`app/src/test`, JVM)
| Área | Archivo | Casos | Estado |
|---|---|---|---|
| Motor económico | `OfferEvaluatorTest` | ejemplos y umbrales existentes; tarifa piso combinada; límites, incertidumbre y desbordes; rating (igualdad, ausente/baja confianza → REVISAR, bajo sin tarifa piso, rating solo no sustituye economía); copiloto prioriza falla económica frente a rating aprobado | CI (run 32) |
| Parser / OCR | `OfferTextParserTest` | importes AR/US/ambiguos; tiempo/distancia; no-oferta; correcciones OCR; rating solo con etiqueta inequívoca del pasajero; tarifa y par tiempo/distancia fragmentados entre nodos/eventos; exige señal de solicitud más tarifa, km y minutos, y rechaza historiales completos | CI (run 32) |
| Chequeo / privacidad | `DiagnosticTextSanitizerTest` | conserva métricas/etiquetas permitidas; descarta nombres/direcciones/texto libre; límites/normalización; acepta rating con etiqueta inequívoca de pasajero, no etiquetas genéricas de usuario | CI (run 32) |
| Backend | `BackendTest` (MockWebServer) | contrato real de ambos endpoints (JSON capturado), path/método/body, sin `Authorization`; campos nuevos/faltantes; HTTP 500; `ok:false`; JSON inválido; timeout; desconexión; contexto desactualizado y conservación del último dato | CI (run 24) |
| Briefing | `ShiftBriefingTest` + `BackendTest` | snapshot local minimizado, hora de recepción, campos de contexto, eventos ausentes vs lista vacía | CI (run 24) |
| Datos y copiloto | `DataAndCopilotTest` | persistencia DataStore; JSON corrupto; historial (límite 100, sin texto); turno $/h; billetera ARS con fechas locales y parseo de miles/decimales; odómetro monotónico/upsert diario; vencimientos por fecha/km; resumen horario de ofertas claramente separado de cobros; intenciones y respuestas del copiloto, incluida tarifa piso | CI (run 24) |
| Clima de prueba | `OpenMeteoClientTest` | precipitación ausente no equivale a cero; cero explícito; intervalo reportado; probabilidad horaria; timestamp y punto fijo; repositorio release deshabilitado | CI (run 32; respuesta simulada) |

El run `45235500410` validó el commit `c39ba09` en runner Linux x86_64 y emulador API 30. En la sandbox local no está configurado Android SDK/`ANDROID_HOME`, por lo que Gradle local no pudo ejecutarse.

## B. Integración
| Prueba | Estado |
|---|---|
| `LiveBackendContractTest` contra Supabase real (job `backend-contract`) | CI histórico (run 17); omitido en runs 19, 23, 24 y 32 para evitar llamada live |
| Regresión web: `https://driverai-ba.lovable.app` responde 200 (job `backend-contract`) | CI histórico (run 17); omitido en runs 19, 23, 24 y 32 |
| Sin backend: evaluación local sigue funcionando (por diseño: el motor no depende de la red; `StrategicRepository` marca "desactualizado") | E (unitaria) |
| Inicio de turno → consulta explícita del reporte heredado → snapshot local | Código compilado; mapeo/persistencia unitaria CI (run `44926383458`). Llamada real al endpoint omitida |
| Persistencia de configuración y rechazo de NaN para rating | CI (run 32; instrumentada `ajustes_guardan` / `ajustes_rechazanUmbralRatingNoFinito`) |

## C. Android (emulador API 30 AOSP en CI; prueba física parcial: una lectura OCR real; paridad completa pendiente) — `AppInstrumentedTest`
| Prueba | Estado |
|---|---|
| Inicio de la app | CI (run 32) |
| Tarjeta de briefing: carga, fuente/vigencia desconocida y estado vacío | CI (run 32, API 30) |
| Evaluación manual de punta a punta en la UI | CI (run 32) |
| Guardado de ajustes y rechazo de NaN en rating mínimo | CI (run 32) |
| Registrar ingreso/gasto real y mantenerlo separado de ofertas | CI (run 32) |
| Guardar odómetro diario y alta de recordatorio de service | CI (run 32) |
| Tarjeta flotante aparece y se cierra con "×" | CI (run 32) |
| Accesibilidad → parser → motor → tarjeta con el **simulador** (no Uber); rotación con tarjeta visible; ocultamiento al desaparecer la oferta; la app de origen sigue en primer plano (no se tocó nada) | CI (run 32) |
| Deduplicación (misma oferta 3 veces = 1 evaluación) | CI (run 32) |
| Capturas de pantalla, logcat, meminfo y batterystats como evidencia | CI (artefacto `emulator-32`) |
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
1. Usar el APK validado `0.1.0-c39ba09` o posterior (ver `BUILD_AND_INSTALL.md`). Anotar modelo, Android y versión de Uber Driver; no anotar datos personales.
2. En Ajustes, cargar al menos un umbral personal —no usar valores inventados— para que el veredicto pueda evaluarse.
3. En Inicio, activar la lectura automática y habilitar el servicio de Accesibilidad de DriverAI.
4. En Diagnóstico, activar **Registrar lectura reducida** antes de abrir Uber Driver.
5. Dejar que llegue una oferta y comprobar primero si Accesibilidad la detecta sin tocar **Leer oferta**. Comparar tarifa, recogida, tramo del viaje y veredicto; no copiar ni transcribir texto crudo.
6. En Diagnóstico, usar **Copiar registro reducido** y revisar fuente, campos y confianza. Compartir solo ese registro si se necesita calibración; nunca compartir texto crudo ni capturas.
7. Si Accesibilidad falla, probar OCR por separado con el consentimiento del sistema. Ya hubo una lectura OCR real exitosa; esta repetición sirve para aislar la ruta automática. Desactivar el registro reducido al terminar.
