# Plan del Día

## Contrato JSON (schemaVersion: 1)

Campos raíz: `schemaVersion`, `generadoAt`, `vigenciaHasta`, `zonaBase`, `bloques[]`, `huecos[]`, `zonasPrecaucion[]`, `supuestos[]`.

Cada bloque: `id`, `tipo`, `nombre`, `lugar|{lugarNoConfirmado}`, `inicio`, `finEstimado`, `finFuente`, `ventanaPico`, `puntosEspera[]`, `destinosLargos[]`, `confianza`, `fuentes[]`.

## Edge Function `plan-dia`

Sin fuentes vigentes → error explícito. Nunca plan relleno. Demo en app solo con toque del conductor.
