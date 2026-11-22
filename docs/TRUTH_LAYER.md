# Capa de hechos (Truth Layer)

## Principio
Si no hay fuente verificable, no hay número de demanda ni evento inventado.

## Fuentes actuales en la app
| Señal | Fuente | Estado |
|---|---|---|
| Clima BA | Open-Meteo (cliente, sin key) | Verificado en dispositivo |
| Evaluación de oferta | Motor local + umbrales del conductor | Verificado |
| Zonas / scores del servidor | `driverai-reporte` | **Estimación no verificada** — no tratar como demanda real |
| Eventos / partidos del servidor | `driverai-reporte` | Solo si `eventos` no vacío y hay fuente; hoy suele venir vacío o incorrecto |

## Fuentes a integrar en Supabase (secrets)
1. Open-Meteo — sin key (también en servidor).
2. API-Football — `https://dashboard.api-football.com/` key en secret `API_FOOTBALL_KEY`.
3. Ticketmaster Discovery (opcional) — cobertura AR limitada.
4. Curaduría manual del día (tabla `manual_events`) para Movistar / Luna Park cuando no hay API.

## Contrato objetivo `estado-real`
```json
{
  "ok": true,
  "as_of": "2026-11-22T20:00:00-03:00",
  "clima": { "temp_c": 25.1, "fuente": "open-meteo" },
  "eventos": [
    { "tipo": "futbol", "titulo": "River vs Lanús", "lugar": "Monumental", "hora": "19:00", "fuente": "api-football" }
  ],
  "demanda": null,
  "notas": ["Sin heatmap de apps. demanda=null a propósito."]
}
```

## UI
- Mostrar clima con etiqueta "Open-Meteo".
- Mostrar zonas del servidor solo como "estimación del servidor (no verificada)".
- Si eventos vacíos: "Sin eventos verificados" — nunca inventar.
