# Plan y resultados de pruebas

Estado: **E** = ejecutada y pasó · **CI** = ejecutada y pasó en GitHub Actions · **P** = pendiente (requiere teléfono real o Uber).

## Resultado de la última ejecución verificada
- Runs 5, 6 y 7 de Android CI (último verificado: run 7, commit `fc9eed9`): los tres jobs en verde.
  - Compilación: 47 unitarias (0 fallas, 1 omitida a propósito), lint OK, APK debug 43,8 MB verificado.
  - Contrato con Supabase real: OK. Web publicada: HTTP 200.
  - Emulador Android 11 (API 30, AOSP x86_64): **OK (6 tests)**. El servicio de accesibilidad quedó enlazado (`dumpsys accessibility`). Sin crashes en logcat. Memoria PSS total ≈ 70 MB. Capturas `simulador-tarjeta.png`, `tarjeta-demo.png`, `evaluacion-manual.png` en el artefacto `emulator-N` → `screens/`.
  - Evidencia clave: el servicio de accesibilidad leyó del simulador "$ 8.540 / A 4 min (1,2 km) / Viaje de 18 min (9,3 km)" y la tarjeta mostró CONVIENE · $8.540 · 10,5 km · 22 min · $813/km · $23.291/h (cálculo correcto). La evaluación manual reprodujo el ejemplo del pedido: $8.000, 10 km, 25 min → $800/km y $19.200/h.
- Fallas encontradas y corregidas en el camino: regex con `$` (unitaria), `setup-android` (CI), `setUp()` no-void (instrumentada), UiAutomator suspendía la accesibilidad (instrumentada), etiquetas de navegación cortadas (captura de pantalla).

## A. Unitarias (`app/src/test`, JVM)
| Área | Archivo | Casos | Estado |
|---|---|---|---|
| Motor económico | `OfferEvaluatorTest` | ejemplo $8.000/10 km/25 min → $800/km y $19.200/h; desfavorable; límite exacto (≥); justo debajo; sin umbrales; sin recogida (pasa → REVISAR, falla → NO CONVIENE); recogida por defecto; sin tarifa; inválidos (negativo, NaN, ∞, 0); baja confianza vs tolerancia; costos + combustible + margen; margen sin costos; combustible desactualizado; retorno vacío; sin minutos; formatos | E |
| Parser / OCR | `OfferTextParserTest` | importes AR/US/ambiguos; oferta clara es/en (millas); horas+min; metros; sin etiquetas; un solo par ambiguo; texto parcial; no-oferta; bonos y $/h ignorados; varios importes; correcciones OCR (S→$, rn→m); distancia-antes-de-tiempo; espacios no separables | E |
| Backend | `BackendTest` (MockWebServer) | contrato real de ambos endpoints (JSON capturado), path/método/body, sin `Authorization`; campos nuevos/faltantes; HTTP 500; `ok:false`; JSON inválido; timeout; desconexión; contexto desactualizado y conservación del último dato | E |
| Datos y copiloto | `DataAndCopilotTest` | persistencia DataStore; JSON corrupto; historial (límite 100, sin texto); turno $/h; intenciones de las 6 consultas; respuestas breves y con fuente | E |

Ejecutadas localmente (JDK 17, AGP 8.7.3): **47 pruebas, 0 fallas, 1 omitida** (la de contrato en vivo, que corre en CI).

## B. Integración
| Prueba | Estado |
|---|---|
| `LiveBackendContractTest` contra Supabase real (job `backend-contract`) | CI |
| Regresión web: `https://driverai-ba.lovable.app` responde 200 (job `backend-contract`) | CI |
| Sin backend: evaluación local sigue funcionando (por diseño: el motor no depende de la red; `StrategicRepository` marca "desactualizado") | E (unitaria) |
| Persistencia de configuración | E (unitaria) + CI (instrumentada `ajustes_guardan`) |

## C. Android (emulador API 30 AOSP en CI; el S10 usa Android 12 / API 31 → paridad pendiente de prueba física) — `AppInstrumentedTest`
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
| ¿Uber Driver expone el texto de la oferta por accesibilidad? | **P** — requiere teléfono con Uber Driver y una oferta real |
| ¿La captura de Uber sale legible o negra (FLAG_SECURE)? | **P** |
| Precisión del parser con textos reales (usar Diagnóstico → "Registrar textos" → copiar) | **P** |

## E. Regresión y seguridad
| Prueba | Estado |
|---|---|
| Escaneo de secretos en CI (service_role, gsk_, sk-, claves privadas, JWT largos, keystores) | CI |
| Permisos del APK listados en `build-info.txt` | CI |
| Web y funciones Supabase no modificadas | E (no se tocaron) |

## Protocolo de prueba física (cuando esté el teléfono)
1. Instalar el APK (ver `BUILD_AND_INSTALL.md`). Diagnóstico → anotar modelo, Android y versión de Uber Driver.
2. Ajustes → cargar al menos un umbral.
3. Inicio → Activar lectura automática → aceptar → activar el servicio.
4. Diagnóstico → activar "Registrar textos".
5. Conectarse en Uber. Al llegar una oferta: ¿aparece la tarjeta? Anotar lo que muestra vs. lo que muestra Uber.
6. Diagnóstico → copiar textos leídos y pegarlos en un issue (borrar direcciones/nombres antes).
7. Repetir con lectura por captura (botón "Leer oferta").
