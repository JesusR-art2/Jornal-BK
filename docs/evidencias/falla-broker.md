# F11: cae RabbitMQ

- Fecha: 2026-11-25T04:38:38Z
- Versión: f8e4231
- Script: `scripts/falla-broker.sh`

```text
cliente client-1795581518@example.test; eventos pendientes en el Outbox antes: 0
rabbitmq: exited
pedido 5706902b-faf7-4cc6-a950-8225cfc2d1dd creado con RabbitMQ caído; estado PENDING_PAYMENT
eventos pendientes en el Outbox: 2
org.springframework.modulith.events.support.EventExternalizerModuleListener.externalize(java.lang.Object) 04:38:44
org.springframework.modulith.events.support.EventExternalizerModuleListener.externalize(java.lang.Object) 04:38:44
estado final: SEARCHING_WORKER; pagos del pedido: 1; pendientes en el Outbox: 0
```

**Resultado:** con RabbitMQ caído el pedido se guardó y su evento esperó en el Outbox; al volver el broker salió, pagos autorizó una vez y el pedido siguió
