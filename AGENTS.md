# Guía para agentes de DriverAI

Antes de trabajar, lee [`docs/AGENT_HANDOFF.md`](docs/AGENT_HANDOFF.md) y comprueba `git status --short --branch` y `git log -1 --oneline`. Ese handoff es el resumen breve del estado actual; sus hashes y resultados son una instantánea, así que confirma la rama y la CI antes de actuar.

Lee solo los archivos que necesites para la tarea. No repitas una auditoría completa ni reproduzcas en el prompt el historial del proyecto. Conserva cualquier cambio local: no uses `reset --hard`, `clean`, rebase destructivo ni force-push. Trabaja en una rama temática y no modifiques ni integres en `main` salvo que el usuario lo pida explícitamente.

## Límites del producto y los datos

- DriverAI recomienda; el conductor decide. Accesibilidad y OCR solo observan. Nunca automatices toques, aceptación o rechazo de viajes.
- No envíes al backend capturas, texto crudo de pasajeros, nombres, teléfonos, direcciones ni ubicación precisa. No actives telemetría o sincronización nueva sin diseño de permisos, autenticación y consentimiento explícito.
- Distingue capacidades implementadas de propuestas. Consulta `docs/KNOWN_LIMITATIONS.md` y actualízalo si cambia una limitación real.
- No llames contratos contra backend productivo, no publiques Releases ni despliegues sin que la tarea lo requiera expresamente.

## Validación y continuidad

El workflow `.github/workflows/android.yml` corre contrato real en eventos `pull_request` y `push` a `main`; `workflow_dispatch` permite evitarlo con `run_live_contract=false`. En una rama de trabajo, usa esa opción si hace falta CI y verifica el estado final del run. No dispares un PR o push a `main` sin considerar esos efectos.

Al terminar cada tarea, actualiza `docs/AGENT_HANDOFF.md`: fecha, rama/commit relevante, cambios, validaciones con enlaces exactos y próximos pasos concretos. Mantén ese archivo corto; enlaza la documentación detallada existente en vez de duplicarla. Actualiza `CHANGELOG.md` solo para cambios reales y no presentes propuestas como funciones ya entregadas.
