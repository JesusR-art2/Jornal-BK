# F8: comprobante, calificaciones y reporte consolidado

- Fecha: 2026-11-24T21:12:10Z
- Versión: 470dc7b
- Script: `scripts/demo-facturacion.sh`

```text
pedido 462ffe92-eb33-417f-85ca-9dc9475a2c3d
{"status":"COMPLETED","receiptNumber":"FE-00000001","totalMinor":183000,"currency":"COP"}
{"title":"Factura electrónica de venta","number":"FE-00000001","date":"24 de nov de 2026, 4:12 p. m.","totals":{"Subtotal":"$ 170.000","Desplazamiento":"$ 8.000","IVA (19%, incluido)":"$ 28.420","Propina":"$ 5.000","Total":"$ 183.000"}}
HTML imprimible: docs/evidencias/comprobante-co.html
{"direction":"CLIENT_TO_WORKER","score":5}
{"direction":"WORKER_TO_CLIENT","score":4}
segunda calificación del cliente: HTTP 409 (esperado 409)
{"ratingAvg":5.00,"ratingCount":1}
{"region":"co","currency":"COP","receipts":1,"billedLocal":183000,"billed":45.75,"rate":{"asOf":"2026-11-24","source":"Proveedor simulado (valores de referencia, VERIFICAR)","rate":"0.00025","pair":"COP/USD","method":"DIRECT"}}
{"currency":"USD","totalBilled":45.75,"totalPlatformRevenue":6.25,"missingRates":[]}
```

**Resultado:** pedido COMPLETED con comprobante, calificaciones en ambos sentidos y reporte con tasa y fecha
