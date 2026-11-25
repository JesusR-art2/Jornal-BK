#!/usr/bin/env bash
# Prueba de falla F11: cae el subsistema de pagos.
#   1. Se detiene payments y el cliente crea un pedido: el core lo acepta y queda en PENDING_PAYMENT.
#   2. El mensaje espera en la cola durable payments.co mientras pagos está caído.
#   3. Pagos vuelve: toma el mensaje, autoriza UNA sola vez y el pedido pasa a SEARCHING_WORKER.
# Demuestra: colas durables, reintento e idempotencia.
#
# Requisitos: docker compose -f infra/docker-compose.yml up -d --build --wait
# Uso:        scripts/falla-pagos.sh            (OUTAGE=30 segundos de caída por defecto)
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

OUTAGE="${OUTAGE:-30}"
STAMP=$(date +%s)
trap '$COMPOSE start payments > /dev/null 2>&1 || true' EXIT   # pase lo que pase, pagos vuelve
evidence_start falla-pagos "F11: cae el subsistema de pagos"

step "0. Cliente de prueba"
new_client "$STAMP"
warehouse_category "$CLIENT"
evidence "cliente $CLIENT_EMAIL"

step "1. Se detiene pagos y se crea el pedido"
$COMPOSE stop payments > /dev/null 2>&1
evidence "payments: $($COMPOSE ps -a --format '{{.State}}' payments)"
REQUEST=$(create_request "$CLIENT" "falla-pagos-$STAMP")
[ -n "$REQUEST" ] && [ "$REQUEST" != null ] || fail "el core no aceptó el pedido con pagos caído"
evidence "pedido $REQUEST creado con pagos caído"
sleep 3
evidence "estado: $(status_of "$REQUEST")"
[ "$(status_of "$REQUEST")" = PENDING_PAYMENT ] || fail "se esperaba PENDING_PAYMENT"
ok "el pedido espera en PENDING_PAYMENT"

step "2. El mensaje espera en la cola payments.$REGION ($OUTAGE s de caída)"
has_queued() { [ "$(queue_messages "payments.$REGION")" -ge 1 ]; }
wait_until "hay mensajes esperando en payments.$REGION" 20 has_queued
evidence "mensajes en payments.$REGION durante la caída: $(queue_messages "payments.$REGION")"
sleep "$OUTAGE"
evidence "tras $OUTAGE s: estado $(status_of "$REQUEST"), pagos registrados $(payments_of "$REQUEST" 2>/dev/null || echo 0)"

step "3. Pagos vuelve"
$COMPOSE start payments > /dev/null 2>&1
wait_healthy payments
wait_status "$REQUEST" SEARCHING_WORKER 90
drained() { [ "$(queue_messages "payments.$REGION")" = 0 ]; }
wait_until "la cola payments.$REGION quedó vacía" 30 drained
N=$(payments_of "$REQUEST")
T=$(transitions_to "$REQUEST" SEARCHING_WORKER)
evidence "estado final: $(status_of "$REQUEST"); pagos del pedido: $N ($(payment_status "$REQUEST")); veces que pasó a SEARCHING_WORKER: $T"
[ "$N" = 1 ] || fail "se esperaba un solo pago y hay $N"
[ "$T" = 1 ] || fail "el pedido pasó $T veces a SEARCHING_WORKER"
ok "se autorizó una sola vez"

step "4. Limpieza: el cliente cancela (pagos anula la autorización)"
cancel_quietly "$REQUEST"
wait_status "$REQUEST" CANCELLED 30
evidence_end "con pagos caído el pedido se creó y esperó en PENDING_PAYMENT; el mensaje esperó en la cola durable y, al volver pagos, se autorizó una sola vez"
