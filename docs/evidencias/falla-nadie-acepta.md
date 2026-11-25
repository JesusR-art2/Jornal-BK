# F11: nadie acepta el pedido

- Fecha: 2026-11-25T04:39:48Z
- Versión: f8e4231
- Script: `scripts/falla-nadie-acepta.sh`

```text
dispatch: DISPATCH_SEARCH_TIMEOUT=20s
trabajador worker-1795581588@example.test; pedido 8b071045-74e3-45a9-998a-a5a5879a7822
rechazo: {"status":"REJECTED","offerId":"bbc249eb-ccf8-491b-846d-35a75dabc6fa"}
estado: CANCELLED; motivo: NO_WORKER_AVAILABLE
pago: VOIDED
historial: ["-→null ","-→null PAYMENT_AUTHORIZED","-→null NO_WORKER_AVAILABLE"]
último aviso al cliente: Pedido cancelado: Tu pedido de Bodega e inventario para el jue, 26 nov 2026, 8:00 a. m. COT fue cancelado. Si se hizo un cobro, se te devolverá el dinero.
```

**Resultado:** el único trabajador rechazó y venció la búsqueda: assignment-failed, pago anulado, pedido CANCELLED y aviso al cliente
