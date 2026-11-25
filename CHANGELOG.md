# Changelog

## En desarrollo
### MVP para piloto (2026-11-25)
- **Inicio simplificado**: cuatro tarjetas (Ahora, Turno, Última oferta, Hoy), primeros pasos guiados que se pueden ocultar y cuatro pestañas (Inicio, Radar, Billetera, Más). Evaluar a mano y el copiloto se abren desde Inicio.
- **Actualizar con IA**: pedido listo para copiar o compartir a una IA externa; la respuesta se pega o se comparte a DriverAI, se valida localmente y se carga al radar con la confianza acotada.
- **Alertas de pico**: notificación (y voz con el turno activo) 40 minutos antes de un pico con un motivo concreto.
- **Mínimos rápidos**: sugiere el mínimo $/km a partir del combustible, el consumo y la meta por hora.
- **Resumen para tu flota** en Informes: texto y fila CSV para compartir por WhatsApp, sin servidor.
- Ajustes con "Opciones avanzadas"; el Radar mueve fuentes, contexto del servidor y carga manual a "Ver detalles".
- Documentos: `docs/PILOTO_FLOTA.md`, brief del MVP en `PRODUCT_VISION.md` y la sección Informe IA en `RADAR_DEMANDA.md`.

### Agregado
- **Radar de demanda** (nueva pestaña). Calcula un índice 0–100 por zona del AMBA y franja de 30 minutos para las próximas 6 horas, con la confianza por separado y los motivos explicados.
  - Combina el patrón por perfil de zona y tipo de día, los feriados de ArgentinaDatos, los partidos de TheSportsDB, la lluvia (en la build privada) y tu propio historial.
  - Admite señales cargadas por el conductor: paros de colectivos, subte o trenes; partidos, recitales, vuelos EZE/AEP, cruceros y lluvia.
  - Modela la ventana real de impacto de cada señal: la salida del público, el arribo más el tiempo de equipaje y el desembarco escalonado.
- **Demanda en el destino**: la tarjeta flotante muestra el nivel previsto en la zona de destino a la hora estimada de llegada. Solo se guarda el id de zona, nunca la dirección.
- **Lectura multi-app**: Uber (verificado) y, en beta, DiDi, Cabify, inDrive y Bolt. Cada app se activa o desactiva en Ajustes, y el historial guarda la app de origen.
- **Límite de recogida**: máximo de km y de minutos hasta el pasajero. Si se excede, el resultado es NO CONVIENE y no se sugiere una tarifa alternativa. Si el dato no se lee, el resultado es REVISAR.
- **Informes**: métricas por app (ofertas, aceptación, $/km, $/h, % de ofertas convenientes), por hora del día, tus mejores horas y la billetera por semana. Exportación CSV del historial y la billetera, con protección contra fórmulas.
- Copiloto: "¿dónde espero?" y "¿qué eventos hay?" responden primero con el radar local; el servidor queda como dato secundario y no verificado. Nuevas preguntas: "¿hay paro?" y "¿cuál es mi mejor hora?".
- Tarjeta de radar en Inicio y `docs/RADAR_DEMANDA.md`, que documenta el modelo, las fuentes y la propuesta del servidor `driverai-senales` (sin desplegar).

### Cambiado
- Navegación: Inicio · Radar · Oferta · Billetera · Más. Vehículo pasa a Más, junto con Informes.
- El parser reconoce el contexto de solicitud de otras apps ("Ofrecé tu tarifa", "Nuevo pedido") y sus productos (DiDi Express, Cabify Lite, etc.).
- **Diario de viaje de punta a punta** en Historial: oferta observada → aceptada/rechazada → completada/cancelada → cobro confirmado por el conductor.
- Los viajes completados admiten kilómetros y minutos reales opcionales; el cobro se vincula a Billetera en una transacción local y se puede corregir o deshacer sin duplicarlo.
- Los cobros vinculados no se pueden borrar por separado en Billetera; se corrigen o revierten desde Actividad de ofertas para mantener ambos registros alineados.
- Si una oferta pagada sale del historial acotado, su ingreso se conserva y se desvincula para que siga siendo editable en Billetera; una aceptación errónea también se puede devolver a oferta observada.
- Copiloto de turno diferencia tarifas de ofertas aceptadas de ingresos cobrados y vinculados, sin llamar ganancia neta a la diferencia.
- Cliente **Open-Meteo** en el teléfono (sin API key) para clima real de BA.
- Sección **Clima (verificado)** en Inicio, separada del reporte del servidor.
- `docs/TRUTH_LAYER.md`: contrato de hechos vs estimaciones.
- **Design system Cockpit** centralizado en `Theme.kt`: `DriverColors`, `ScreenScaffold`, `Section`, `MetricTile`, `PrimaryCta`, `GhostCta`, `StatusPill`, `VerdictBanner`, `CockpitField`.

### Mejorado
- Migración local transparente: registros antiguos marcados “La tomé” pasan a “Aceptada por vos”, sin asumir que el viaje terminó o se cobró.
- Borrar el historial de ofertas conserva los movimientos reales de Billetera y los desvincula antes de eliminar sus ofertas.
- **Rediseño visual integral (Cockpit)** en todas las pantallas:
  - Inicio, Evaluar, Historial, Ajustes y Chequeo usan el mismo lenguaje visual.
  - Fondo void/hull, acentos cyan/go, tipografía mono-sans, métricas en tiles, CTAs consistentes.
  - Ajustes y Chequeo dejan de usar Material genérico suelto (botones, campos y grises planos).
- Contexto del servidor etiquetado como **no verificado**; no se presenta la demanda inventada como dato.
- Copiloto: sin eventos verificados dice la verdad en lugar de rellenar.
- Chequeo conserva y copia únicamente métricas de oferta y etiquetas permitidas; nombres, direcciones y texto libre se descartan en el dispositivo.
- El registro de diagnóstico incluye veredicto y campos detectados, se borra al desactivar el diagnóstico y no se transmite al backend.
- Inicio de turno consulta el reporte estratégico solo por acción explícita y guarda un snapshot local mínimo; el panel aclara que la hora es de recepción y que el endpoint no informa fuente, vigencia ni confianza por señal.
- La respuesta distingue una lista de eventos ausente de una lista explícitamente vacía; se documentaron fuentes posibles y brechas de licencia/cobertura para AMBA.

## 0.1.0 — 2026-10-17
### Agregado
- App Android nativa (Kotlin + Compose, minSdk 26, target 35).
- Motor económico determinístico: tarifa mínima, $/km, $/h, costo por km, combustible, margen, recogida por defecto, retorno vacío, tolerancia a incertidumbre; veredictos CONVIENE / NO CONVIENE / REVISAR con motivos.
- Parser de ofertas independiente de la fuente con confianza por dato (es/en, km/mi/m, correcciones OCR).
- Servicio de accesibilidad limitado a Uber Driver, con divulgación destacada y sin acciones automáticas.
- Lectura por captura (MediaProjection) bajo demanda + OCR local ML Kit.
- Tarjeta flotante movible, cerrable, compacta y con auto-ocultamiento; alternativa por notificación.
- Integración con `driverai-reporte` y `driverai-posicion` con timeouts y modo desactualizado.
- Copiloto por reglas con voz es-AR y dictado bajo demanda.
- Ajustes, historial numérico, turno, diagnóstico y simulador de oferta (debug).
- CI: escaneo de secretos, unit tests, lint, APK verificado como artefacto, contrato en vivo, emulador Android 11 (API 30, AOSP).
- Publicación: GitHub Release `v0.1.0-cc64438` y tag móvil `latest-debug`.
- Documentación en `docs/`.
### Corregido
- Reemplazo de regex con `$` literal en la corrección OCR (detectado por test).
- CI y emulador estabilizados en API 30 AOSP.
### Mejorado
- Accesibilidad: escaneos espaciados a 900 ms cuando no hay oferta en pantalla.
- OCR: la tarjeta flotante se oculta antes de capturar.
