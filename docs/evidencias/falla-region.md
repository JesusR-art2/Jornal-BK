# F11: cae la región br

- Fecha: 2026-11-25T04:36:40Z
- Versión: f8e4231
- Script: `scripts/falla-region.sh`

```text
GET /api/br/region-profile -> 200
GET /api/co/region-profile -> 200
core-br: exited
GET /api/br/region-profile -> 503 {"type":"about:blank","title":"Service Unavailable","status":503,"detail":"The region or service is not available","code":"SERVICE_UNAVAILABLE"}
GET /api/co/region-profile -> 200
login del administrador de co: OK
consola de co, servicios caídos: ["core-br","core-fr","core-jp","core-us"]
GET /api/br/region-profile -> 200
```

**Resultado:** con core-br caído el gateway respondió 503 solo para esa región; co siguió atendiendo y su consola señaló la caída; al volver, br respondió de nuevo
