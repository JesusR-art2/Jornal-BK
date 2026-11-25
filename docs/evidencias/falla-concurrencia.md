# F11: dos pedidos simultáneos por el único trabajador

- Fecha: 2026-11-25T04:37:26Z
- Versión: f8e4231
- Script: `scripts/falla-concurrencia.sh`

```text
trabajador worker-1795581446@example.test; clientes client-1795581446-a y client-1795581446-b
pedido A 0714dd32-8fdd-4896-9802-d661876930ff, pedido B 517a977a-0d0f-4abe-ab89-b2f7c7c4aa90
ofertas pendientes del trabajador: 1
la oferta es por el pedido 517a977a-0d0f-4abe-ab89-b2f7c7c4aa90
respuestas:       1 200       7 409 
reservas del trabajador en asignación: 1
pedido ganador: ASSIGNED; el otro: SEARCHING_WORKER
```

**Resultado:** con un solo trabajador y dos pedidos simultáneos hubo una sola oferta, una sola aceptación entre 8 en paralelo y una sola reserva; el otro pedido siguió buscando
