#!/usr/bin/env bash
# Prueba de falla F11: cae el intermediario de mensajes (RabbitMQ).
#   1. Se detiene RabbitMQ y el cliente crea un pedido: el core lo guarda igual (PENDING_PAYMENT).
#   2. El evento queda en el Outbox del core (event_publication sin completar).
#   3. RabbitMQ vuelve: el Outbox reenvía el evento, pagos autoriza y el pedido sigue su camino.
# Demuestra: patrón Outbox (el dato y el mensaje se guardan juntos; nada se pierde si el broker cae).
#
# Requisitos: docker compose -f infra/docker-compose.yml up -d --build --wait
# Uso:        scripts/falla-broker.sh          (OUTAGE=15 segundos de caída por defecto)
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

OUTAGE="${OUTAGE:-15}"
STAMP=$(date +%s)
trap '$COMPOSE start rabbitmq > /dev/null 2>&1 || true' EXIT
pending_events() { psql_q "jornal_$REGION" "SELECT count(*) FROM event_publication WHERE completion_date IS NULL"; }
evidence_start falla-broker "F11: cae RabbitMQ"

step "0. Cliente de prueba"
new_client "$STAMP"
warehouse_category "$CLIENT"
BEFORE=$(pending_events)
evidence "cliente $CLIENT_EMAIL; eventos pendientes en el Outbox antes: $BEFORE"

step "1. Se detiene RabbitMQ y se crea el pedido"
$COMPOSE stop rabbitmq > /dev/null 2>&1
evidence "rabbitmq: $($COMPOSE ps -a --format '{{.State}}' rabbitmq)"
REQUEST=$(create_request "$CLIENT" "falla-broker-$STAMP")
[ -n "$REQUEST" ] && [ "$REQUEST" != null ] || fail "el core no aceptó el pedido con RabbitMQ caído"
evidence "pedido $REQUEST creado con RabbitMQ caído; estado $(status_of "$REQUEST")"
[ "$(status_of "$REQUEST")" = PENDING_PAYMENT ] || fail "se esperaba PENDING_PAYMENT"

step "2. El evento espera en el Outbox del core"
has_pending() { [ "$(pending_events)" -gt "$BEFORE" ]; }
wait_until "el evento del pedido quedó en event_publication sin completar" 20 has_pending
evidence "eventos pendientes en el Outbox: $(pending_events)"
evidence "$(psql_q "jornal_$REGION" "SELECT listener_id || ' ' || to_char(publication_date, 'HH24:MI:SS') FROM event_publication WHERE completion_date IS NULL ORDER BY publication_date DESC LIMIT 3")"
sleep "$OUTAGE"

step "3. RabbitMQ vuelve: el Outbox reenvía"
$COMPOSE start rabbitmq > /dev/null 2>&1
wait_healthy rabbitmq
wait_status "$REQUEST" SEARCHING_WORKER 120
none_pending() { [ "$(pending_events)" -le "$BEFORE" ]; }
wait_until "el Outbox quedó al día" 60 none_pending
evidence "estado final: $(status_of "$REQUEST"); pagos del pedido: $(payments_of "$REQUEST"); pendientes en el Outbox: $(pending_events)"
[ "$(payments_of "$REQUEST")" = 1 ] || fail "se esperaba un solo pago"

step "4. Limpieza: el cliente cancela"
cancel_quietly "$REQUEST"
wait_status "$REQUEST" CANCELLED 30
evidence_end "con RabbitMQ caído el pedido se guardó y su evento esperó en el Outbox; al volver el broker salió, pagos autorizó una vez y el pedido siguió"
