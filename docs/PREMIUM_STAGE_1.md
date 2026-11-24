# DriverAI — etapa premium 1

**Rama:** `manus/premium-driver-cockpit`
**Alcance:** sistema visual compartido, Home, meta diaria opcional y experiencia inicial del copiloto.

## Cambios implementados

- Sistema visual común con colores semánticos, escala tipográfica, espaciado y radios. Se conservaron aliases temporales para que las pantallas que todavía no fueron rediseñadas mantengan compatibilidad.
- Home prioriza el estado del turno, la última oferta evaluada, caja diaria registrada y acceso al copiloto. La oferta enseña veredicto, tarifa, bruto/h, bruto/km, métricas totales, fuente y confianza mínima cuando están completos los cinco campos requeridos.
- El resumen diario separa ingresos anotados, gastos, diferencia de caja y kilómetros del odómetro. El valor de “diferencia” no se denomina ganancia neta; los kilómetros manuales no se completan con distancias de ofertas.
- Se agregó una meta diaria opcional, sin valor precargado. Su progreso compara solamente ingresos anotados en Billetera; no calcula cobros a partir de ofertas.
- Se agregó una pantalla de conversación con historial en memoria durante la sesión, sugerencias contextuales, entrada de texto/voz y respuestas del motor determinista existente. El chat informa que no invoca un LLM desde Android. Los intents de ubicación/eventos pueden actualizar el mismo backend estratégico existente; el mensaje del usuario no se presenta como una llamada a Groq.
- “Informe web” compone un prompt con fecha/hora local, zona seleccionada manualmente, meta, comienzo de turno y resumen de la última oferta cuando existan. Permite copiarlo al portapapeles. Explicita que DriverAI no hizo la búsqueda, pide fuentes verificables y exige separar hechos, estimaciones e hipótesis.
- Si un turno iniciado manualmente pertenece a un día anterior, Inicio informa que el cálculo actual mide tiempo de reloj desde el inicio y no descuenta pausas. No se elimina ni modifica el registro automáticamente.
- Se conservaron la barra de cinco secciones, los tags existentes de pruebas, la lectura OCR/accesibilidad, OfferPipeline y el motor de evaluación.

## Límites conocidos que continúan

- El chat determinista y su historial no se guardan al cerrar el proceso. Las respuestas existentes siguen siendo por reglas; no se incorporó una integración LLM nueva ni se atribuye a Groq el chat Android.
- Copiar un prompt no inicia navegación ni investigación: la búsqueda y la validación de fuentes ocurren fuera de DriverAI.
- No hay feeds verificados integrados de eventos, vuelos, cruceros, transporte o tráfico, ni predicción propia de demanda.
- La duración de turno aún no descuenta pausas y no existe un flujo completo de pausar/continuar con resumen de cierre. El aviso de turno anterior es informativo; el usuario debe finalizar el turno manualmente.
- La lectura y compatibilidad de ofertas Uber reales siguen pendientes de una prueba diagnóstica en teléfono del propietario; el emulador no demuestra compatibilidad real.
- Las pantallas secundarias recibirán la actualización visual en etapas posteriores.

## Validación

Se añadieron pruebas unitarias para persistencia de la meta y contenido/etiquetado del prompt, y una prueba instrumentada que navega por la meta diaria y el flujo de copiar el informe. El intento local de `./gradlew :app:testDebugUnitTest` se bloqueó antes de compilar porque este sandbox no tiene Android SDK configurado (`ANDROID_HOME`/`sdk.dir`). La validación definitiva de compilación, lint y emulador debe ejecutarse por Android CI en esta rama.
