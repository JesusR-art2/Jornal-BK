# F11: réplica de pagos

- Fecha: 2026-11-25T04:45:11Z
- Versión: f8e4231
- Script: `scripts/falla-replica-pagos.sh`

```text
réplicas: /jornal-payments-2 /jornal-payments-1 (proveedor simulado con 10 s de latencia)
consumidores de payments.co: 2
pedidos: 711420b4-bd8f-425a-bfc5-ecb57a97984c a16b12fa-9063-4f15-8a02-b9d4b63a0f15 beb76202-0154-4387-a1bb-8007ef382ea8 c760df61-1bbd-482e-95a2-58f9ec489981
mensajes sin confirmar en payments.co justo antes de la caída: 3
réplica caída (kill): /jornal-payments-2; sigue: /jornal-payments-1
pedido 711420b4-bd8f-425a-bfc5-ecb57a97984c: SEARCHING_WORKER, pagos 1 (AUTHORIZED)
pedido a16b12fa-9063-4f15-8a02-b9d4b63a0f15: SEARCHING_WORKER, pagos 1 (AUTHORIZED)
pedido beb76202-0154-4387-a1bb-8007ef382ea8: SEARCHING_WORKER, pagos 1 (AUTHORIZED)
pedido c760df61-1bbd-482e-95a2-58f9ec489981: SEARCHING_WORKER, pagos 1 (AUTHORIZED)
mensajes procesados por la réplica que siguió: 3
mensajes procesados por la réplica caída antes del kill: 1
mensajes que RabbitMQ reentregó a la otra réplica: 3
réplicas de pagos: 1
```

**Resultado:** con dos réplicas y una caída de golpe a mitad del procesamiento, la otra tomó los mensajes sin confirmar: los 4 pedidos se autorizaron, cada uno una sola vez
