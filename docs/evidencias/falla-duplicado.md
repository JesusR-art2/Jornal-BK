# F11: mensaje duplicado

- Fecha: 2026-11-25T04:38:09Z
- Versión: f8e4231
- Script: `scripts/falla-duplicado.sh`

```text
pedido da67dc8d-8206-4ee9-8fd7-29beb14c1c66; mensaje original 50b660a2-5539-432c-b647-73f96ef43dfd (co.jornal.payment.authorized.v1)
inbox antes: core 1, notificaciones 1, facturación 1
reenvío #1: {"routed":true}
reenvío #2: {"routed":true}
core-co: 2 líneas «Duplicate message 50b660a2-5539-432c-b647-73f96ef43dfd ... discarded»
notifications: 2 líneas «Duplicate message 50b660a2-5539-432c-b647-73f96ef43dfd ... discarded»
billing: 2 líneas «Duplicate message 50b660a2-5539-432c-b647-73f96ef43dfd ... discarded»
inbox después: core 1, notificaciones 1, facturación 1
estado: SEARCHING_WORKER; veces que pasó a SEARCHING_WORKER: 1; avisos al cliente: 2 antes, 2 después; pagos: 1
```

**Resultado:** el mismo mensaje reenviado dos veces se descartó en el inbox del core, de notificaciones y de facturación: el pedido no cambió y no hubo avisos repetidos
