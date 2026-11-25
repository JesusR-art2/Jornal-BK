# F11: cae el subsistema de pagos

- Fecha: 2026-11-25T04:35:12Z
- Versión: f8e4231
- Script: `scripts/falla-pagos.sh`

```text
cliente client-1795581312@example.test
payments: exited
pedido 0ffcc894-3fd8-48b4-83c4-99ddd3bf3133 creado con pagos caído
estado: PENDING_PAYMENT
mensajes en payments.co durante la caída: 2
tras 30 s: estado PENDING_PAYMENT, pagos registrados 0
estado final: SEARCHING_WORKER; pagos del pedido: 1 (AUTHORIZED); veces que pasó a SEARCHING_WORKER: 1
```

**Resultado:** con pagos caído el pedido se creó y esperó en PENDING_PAYMENT; el mensaje esperó en la cola durable y, al volver pagos, se autorizó una sola vez
