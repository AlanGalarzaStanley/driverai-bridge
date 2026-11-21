# Permisos, divulgaciones y políticas

## Permisos declarados (verificados con `aapt2 dump badging` en CI)

| Permiso | Para qué | ¿Obligatorio? |
|---|---|---|
| `INTERNET` | Llamar a `driverai-reporte` y `driverai-posicion` | Sí (el motor funciona sin red) |
| `ACCESS_NETWORK_STATE` | Agregado por dependencias (ML Kit/OkHttp). Permiso normal, sin diálogo | Automático |
| `SYSTEM_ALERT_WINDOW` | Tarjeta flotante y botón "Leer oferta" cuando la accesibilidad NO está activa | Opcional |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Sesión de lectura por captura (OCR) con notificación visible | Solo si se usa OCR |
| `POST_NOTIFICATIONS` | Notificación de la sesión de captura y alternativa a la tarjeta | Opcional |
| `BIND_ACCESSIBILITY_SERVICE` (en el servicio) | Leer el texto visible de Uber Driver | Opcional (lectura automática) |

No se piden: ubicación, micrófono (el dictado lo hace la app de voz del sistema vía `RecognizerIntent`), contactos, almacenamiento, ni `canPerformGestures`.

## AccessibilityService

- Se limita a `com.ubercab.driver` (y, solo en builds debug, al simulador interno).
- **No** declara `isAccessibilityTool`: DriverAI no es una herramienta para personas con discapacidad. Según la [política de Google Play sobre la AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491?hl=es-419), las apps que no son herramientas de accesibilidad deben mostrar una divulgación destacada dentro de la app, durante el uso normal, separada de otras divulgaciones y con una acción afirmativa del usuario; además, las apps con target API 31+ deben completar la declaración en Play Console y obtener aprobación.
- Implementado: al tocar "Activar lectura automática" en Inicio se muestra la divulgación completa (`DISCLOSURE_ACCESSIBILITY` en `HomeScreen.kt`) con "Acepto" / "No, gracias". Solo después se abre Ajustes > Accesibilidad. Si el usuario rechaza, puede volver a verla con el mismo botón.
- El servicio nunca llama a `performAction` ni a gestos: **no acepta ni rechaza viajes**.
- Chequeo es opcional y reduce localmente los textos a métricas numéricas y etiquetas permitidas; no mantiene texto libre, nombres ni direcciones. El registro queda en memoria y solo se copia al portapapeles por acción explícita del usuario. No se transmite al backend.

### Borrador de declaración para Play Console (para la fase de publicación)
1. ¿Por qué usa la API? → Funciones de la app: leer tarifa, distancia y tiempo de la oferta visible en Uber Driver para mostrar una evaluación económica.
2. ¿Recopila o comparte datos personales o sensibles? → Procesa en el dispositivo "actividad en apps" (contenido visible de Uber Driver). No lo transmite. Guarda opcionalmente solo valores numéricos de la evaluación. Responder con precisión cuando se complete el formulario.
3. Video: abrir app → Inicio → "Activar lectura automática" → divulgación completa → aceptar → activar servicio → oferta → tarjeta; y flujo de rechazo.

**No hay garantía de aprobación en Google Play.** La distribución actual es privada (APK de depuración).

## MediaProjection (OCR)
- Consentimiento por sesión con el diálogo del sistema, precedido por la divulgación `DISCLOSURE_CAPTURE`.
- Servicio en primer plano tipo `mediaProjection` con notificación y botón "Detener"; `MediaProjection.Callback` registrado antes de crear la `VirtualDisplay`; una sola `VirtualDisplay` por sesión.
- Captura **un cuadro por toque**; el bitmap se recicla después del OCR. Nunca se guarda ni se envía.
- Algunas apps marcan su ventana como segura (`FLAG_SECURE`) y la captura sale negra. Si Uber lo hace, el OCR no funcionará: **pendiente de verificar en el teléfono**.

## Superposición
- Con accesibilidad activa se usa `TYPE_ACCESSIBILITY_OVERLAY` (no requiere permiso extra). Si no, `TYPE_APPLICATION_OVERLAY` con permiso del usuario. Si no hay ninguno, notificación.
- La tarjeta no es enfocable, se mueve arrastrando, se cierra con "×", tiene modo compacto y se oculta sola.
