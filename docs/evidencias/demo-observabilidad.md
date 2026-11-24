# F10: correlación, métricas, tablero y consola

- Fecha: 2026-11-24T21:58:34Z
- Versión: 3a5a50e
- Script: `scripts/demo-observabilidad.sh`

```text
pedido 4f7900ca-49ab-4601-b80d-1a2015beeae5
core-co: 2 líneas con cid=demo-cid-1795557514
payments: 4 líneas con cid=demo-cid-1795557514
dispatch: 5 líneas con cid=demo-cid-1795557514
notifications: 9 líneas con cid=demo-cid-1795557514
payments-1  | 2026-11-24T21:58:44.914Z  INFO 1 --- [jornal-payments] [ntContainer#0-1] [cid=demo-cid-1795557514] j.common.subsystem.CloudEventListener    : Processed 3de87140-ca6c-4487-af04-b290de9cdc26 [jornal.request.c
objetivos arriba: ["billing:8080","payments:8080","core-br:8080","notifications:8080","dispatch:8080","core-co:8080","rabbitmq:15692"]
pedidos por estado (co): {"ASSIGNED":"3","CANCELLED":"4","COMPLETED":"4","PENDING_CONFIRMATION":"1","SEARCHING_WORKER":"1"}
mensajes procesados: ["notifications/ok=135","dispatch/ok=93","core/ok=42","billing/ok=16","payments/ok=66","billing/duplicate=1","payments/duplicate=1","notifications/duplicate=1","dispatch/duplicate=1"]
colas con mensajes: []
[{"title":"Jornal","url":"/dashboards/f/cg5axtauj4lc0d/jornal"},{"title":"Jornal: sistema distribuido","url":"/d/jornal-overview/jornal3a-sistema-distribuido"}]
tablero: {"region":"CO","today":{"since":"2026-11-24T05:00:00Z","created":13,"completed":4,"cancelled":4},"workersAvailable":1,"requestsByStatus":{"DRAFT":0,"PENDING_PAYMENT":0,"SEARCHING_WORKER":1,"ASSIGNED":3,"EN_ROUTE":0,"IN_PROGRESS":0,"PENDING_CONFIRMATION":1,"COMPLETED":4,"CANCELLED":4,"FAILED":0,"EXPIRED":0}}
caídos: ["billing","core-fr","core-jp","core-us"]
colas: {"deadLetters":0,"sinConsumidor":["billing.br","billing.co","billing.fr","billing.jp","billing.us","core.fr","core.jp","core.us"]}
```

**Resultado:** el id de correlación aparece en core, pagos, asignación y notificaciones; Prometheus lee todos los procesos; la consola señala el servicio caído
