# Auditoría inicial — DriverAI

Fecha: 2026-10-17 (hora de Buenos Aires). Todo lo que figura acá fue verificado con llamadas reales; lo que no se pudo verificar está marcado como tal.

## 1. Inventario de repositorios (cuenta GitHub `AlanGalarzaStanley`)

| Repositorio | Visibilidad | Estado | ¿Es DriverAI? |
|---|---|---|---|
| `driverai-bridge` | Privado | **Vacío** (creado 2026-10-14, sin commits, sin issues ni workflows) | Sí → se usó para la app Android |
| `ba-en-ruta` | Público | Sitio de traslados turísticos; tiene `DriverSignup.tsx` | No (otro negocio) |
| `Arther-system`, `aether-system` (vacío), `ESA_*`, `ESA.01-by-Wachim`, `Lpi-deploy` | — | Sin archivos relacionados con DriverAI, Android, Gradle ni Supabase | No |

Conclusión: **no existía código Android ni código del backend en GitHub**. No se creó ningún repositorio duplicado.

## 2. Frontend web (Lovable "Driverai Ba")

- `https://driverai-ba.lovable.app` → HTTP 200. App TanStack Start (React SSR), título "DriverAI — Qué onda hoy".
- Funciones presentes en el bundle: reporte de zonas, selector de 14 zonas, decisión "¿me quedo o me muevo?", lectura en voz (Web `speechSynthesis`) y dictado (`webkitSpeechRecognition`).
- Llamadas al backend: **no** van directo del navegador a Supabase. Pasan por una *server function* (`createServerFn`, método POST) que recibe `{fn, body}` y reenvía a la función Edge. Por eso el bundle **no contiene claves** de Supabase ni de Groq (verificado buscando `eyJ…`, `sb_publishable_`, `gsk_`).
- Llamar a la server function desde fuera del navegador devuelve `Forbidden` (protección del framework). No se pudo confirmar si el proxy restringe `fn` a una lista blanca: **pendiente** revisar en el código de Lovable.
- Preview `id-preview--…lovable.app` → HTTP 401 (requiere sesión de Lovable; esperado).
- **Sin acceso al código fuente de Lovable** desde este entorno (no hay conector de Lovable ni el proyecto está sincronizado a GitHub). No se modificó la web.

## 3. Backend Supabase (`bzjvfexqqxeccmbxwipe`)

Sin acceso a la consola ni al código de las funciones (no hay conector de Supabase). Se auditó el **contrato HTTP real**:

| Endpoint | Request | Respuesta | Latencia medida |
|---|---|---|---|
| `POST /driverai-reporte` | `{}` | 200, `{ok, reporte{resumen, precio_minimo_sugerido, mejor_zona_ahora, mejor_zona_en_2h, zonas_rankeadas[], estrategia_viajes, alertas[], factor_lluvia, nivel_demanda_general}, groq_status, demanda_por_zona[], clima{…}, eventos[], hora, dia, score_turno, multiplicador, hay_partido, post_explosion, quincena}` | 2,5–4,3 s |
| `POST /driverai-posicion` | `{"zona_actual":"Microcentro"}` | 200, `{ok, zona_actual, score_actual, decision ("moverte"/"quedarte"), zona_recomendada, score_recomendado, alternativas[], espera_estimada_min, tip, clima, hay_partido}` | 3,1–3,9 s |

- Autenticación: **ninguna**. Responden 200 sin `Authorization` ni `apikey` (las funciones están desplegadas sin verificación de JWT).
- CORS: `Access-Control-Allow-Origin: *`, `Allow-Methods: POST,OPTIONS`, `Allow-Headers: Content-Type`. Si un cliente web agregara `Authorization`/`apikey`, el preflight fallaría. (La app nativa no está sujeta a CORS.)
- Groq: `groq_status: 200` en la respuesta confirma la integración.
- RLS: **no verificable** sin acceso a la base. La app no accede a tablas; solo llama a estas dos funciones.

### Hallazgos del backend (no se modificó nada)

1. **Hora en UTC**: a las 02:58 de Buenos Aires (05:58 UTC) `hora` devolvió `5`. Los factores horarios probablemente estén corridos 3 horas. Recomendación: calcular con `America/Argentina/Buenos_Aires`.
2. **Texto del LLM inconsistente con la hora**: a las 3 a. m. el `tip` dice "Aprovechá la tarde…". Es texto generado, no dato.
3. **`precio_minimo_sugerido: 150`**: no es plausible como tarifa en ARS. La app lo muestra como "referencia no verificada" y **nunca** lo usa como umbral.
4. **Sin validación de zona**: `zona_actual: "Inexistente"` devuelve `ok:true`, recomienda quedarse en "Inexistente" con puntaje 4 aunque las alternativas tienen 5. Devuelve zonas fuera de la lista (p. ej. "Puerto Madero").
5. **Endpoints públicos sin límite de uso**: cualquiera puede invocarlos y consumir la cuota de Groq. Recomendación: límite por IP/minuto o exigir la clave anónima + verificación de JWT (y actualizar CORS para `apikey, authorization`).
6. Body inválido en `driverai-posicion` → `{"error":"zona_actual requerida"}`. `driverai-reporte` acepta también GET.

### Secretos
No se encontraron secretos expuestos en la web publicada ni en los repositorios revisados.

## 4. Qué se reutiliza y qué faltaba

- Reutilizado: contratos de las dos funciones, lista de zonas, enfoque de voz en rioplatense, formato de mensajes "movete / quedate".
- Faltaba por completo (construido en este repo): app Android, lectura de ofertas, motor económico, tarjeta flotante, configuración de umbrales, historial, diagnóstico, CI con APK.
