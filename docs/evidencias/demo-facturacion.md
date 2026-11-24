# F8: comprobante, calificaciones y reporte consolidado

- Fecha: 2026-11-24T21:58:04Z
- Versión: 3a5a50e
- Script: `scripts/demo-facturacion.sh`

```text
pedido 9256283c-1b5e-4ede-abe2-0df6c012e352
{"status":"COMPLETED","receiptNumber":"FE-00000004","totalMinor":183001,"currency":"COP"}
{"title":"Factura electrónica de venta","number":"FE-00000004","date":"24 de nov de 2026, 4:58 p. m.","totals":{"Subtotal":"$ 170.000","Desplazamiento":"$ 8.001","IVA (19%, incluido)":"$ 28.420","Propina":"$ 5.000","Total":"$ 183.001"}}
HTML imprimible: docs/evidencias/comprobante-co.html
{"direction":"CLIENT_TO_WORKER","score":5}
{"direction":"WORKER_TO_CLIENT","score":4}
segunda calificación del cliente: HTTP 409 (esperado 409)
{"ratingAvg":5.00,"ratingCount":1}
{"region":"co","currency":"COP","receipts":4,"billedLocal":732003,"billed":183.00,"rate":{"asOf":"2026-11-24","method":"DIRECT","pair":"COP/USD","rate":"0.00025","source":"Proveedor simulado (valores de referencia, VERIFICAR)"}}
{"currency":"USD","totalBilled":183.00,"totalPlatformRevenue":25.00,"missingRates":[]}
```

**Resultado:** pedido COMPLETED con comprobante, calificaciones en ambos sentidos y reporte con tasa y fecha
