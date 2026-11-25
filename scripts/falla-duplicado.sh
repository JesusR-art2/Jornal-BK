#!/usr/bin/env bash
# Prueba de falla F11: mensaje duplicado.
#   1. Un pedido real: pagos autoriza y publica payment.authorized (queda guardado en su outbox).
#   2. Se reenvía DOS veces ese mismo mensaje (mismo id) por la API de RabbitMQ, como si el broker o el
#      emisor lo repitieran.
#   3. El core, notificaciones y facturación lo descartan en su inbox: el pedido no cambia y no hay avisos de más.
# Demuestra: idempotencia de los consumidores (inbox por id de mensaje).
#
# Requisitos: docker compose -f infra/docker-compose.yml up -d --build --wait
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

STAMP=$(date +%s)
evidence_start falla-duplicado "F11: mensaje duplicado"
since=$(date -u +%Y-%m-%dT%H:%M:%SZ)

step "1. Pedido real con pago autorizado"
new_client "$STAMP"
warehouse_category "$CLIENT"
REQUEST=$(create_request "$CLIENT" "falla-dup-$STAMP")
wait_status "$REQUEST" SEARCHING_WORKER
ROW=$(psql_q jornal_payments "SELECT routing_key || '|' || payload FROM outbox
      WHERE routing_key LIKE '%jornal.payment.authorized%' AND payload LIKE '%$REQUEST%' ORDER BY created_at LIMIT 1")
[ -n "$ROW" ] || fail "no se encontró el mensaje payment.authorized del pedido"
ROUTING=${ROW%%|*}; PAYLOAD=${ROW#*|}
MSG_ID=$(printf '%s' "$PAYLOAD" | jq -r '.id')
evidence "pedido $REQUEST; mensaje original $MSG_ID ($ROUTING)"
in_inbox() { psql_q "jornal_$1" "SELECT count(*) FROM inbox WHERE message_id = '$MSG_ID'"; }
evidence "inbox antes: core $(in_inbox "$REGION"), notificaciones $(in_inbox notifications), facturación $(in_inbox billing)"
NOTES_BEFORE=$(curl -sf "$NOTIFY/notifications/me" -H "Authorization: Bearer $CLIENT" | jq 'length')

step "2. Se reenvía dos veces el mismo mensaje por la API de RabbitMQ"
PUBLISH=$(printf '{"properties":{"content_type":"application/json","delivery_mode":2},"routing_key":"%s","payload":%s,"payload_encoding":"string"}' \
  "$ROUTING" "$(printf '%s' "$PAYLOAD" | jq -Rs .)")
for i in 1 2; do
  R=$(printf '%s' "$PUBLISH" | curl -sf -u "$RABBIT_AUTH" -H 'Content-Type: application/json' \
      -X POST "$RABBIT_API/exchanges/%2F/jornal.events/publish" --data-binary @- | jq -c .)
  evidence "reenvío #$i: $R"
done
sleep 8

step "3. Cada consumidor lo descartó"
for svc in "core-$REGION" notifications billing; do
  n=$($COMPOSE logs --since "$since" "$svc" 2>/dev/null | grep -c "Duplicate message $MSG_ID" || true)
  evidence "$svc: $n líneas «Duplicate message $MSG_ID ... discarded»"
  [ "$n" -ge 2 ] || fail "$svc no descartó los 2 duplicados"
done
evidence "inbox después: core $(in_inbox "$REGION"), notificaciones $(in_inbox notifications), facturación $(in_inbox billing)"
[ "$(in_inbox "$REGION")" = 1 ] || fail "el inbox del core tiene más de un registro del mensaje"
T=$(transitions_to "$REQUEST" SEARCHING_WORKER)
NOTES_AFTER=$(curl -sf "$NOTIFY/notifications/me" -H "Authorization: Bearer $CLIENT" | jq 'length')
evidence "estado: $(status_of "$REQUEST"); veces que pasó a SEARCHING_WORKER: $T; avisos al cliente: $NOTES_BEFORE antes, $NOTES_AFTER después; pagos: $(payments_of "$REQUEST")"
[ "$T" = 1 ] || fail "el pedido cambió de estado por el duplicado"
[ "$NOTES_AFTER" = "$NOTES_BEFORE" ] || fail "el duplicado generó avisos"
ok "los duplicados no cambiaron nada"

step "4. Limpieza: el cliente cancela"
cancel_quietly "$REQUEST"
wait_status "$REQUEST" CANCELLED 30
evidence_end "el mismo mensaje reenviado dos veces se descartó en el inbox del core, de notificaciones y de facturación: el pedido no cambió y no hubo avisos repetidos"
