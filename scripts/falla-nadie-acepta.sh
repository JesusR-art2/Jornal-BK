#!/usr/bin/env bash
# Prueba de falla F11: nadie acepta el pedido.
#   1. Asignación corre con el plazo de búsqueda bajado (SEARCH_TIMEOUT, 20 s) para la demostración.
#   2. El único trabajador disponible rechaza la oferta y no hay más candidatos.
#   3. Vence la búsqueda: assignment-failed, pagos anula la autorización, el pedido queda CANCELLED
#      y el cliente recibe el aviso.
# Demuestra: compensación de la saga.
#
# Requisitos: docker compose -f infra/docker-compose.yml up -d --build --wait
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

SEARCH_TIMEOUT="${SEARCH_TIMEOUT:-20s}"
STAMP=$(date +%s)
restore_dispatch() { $COMPOSE up -d dispatch > /dev/null 2>&1 || true; }
trap restore_dispatch EXIT
evidence_start falla-nadie-acepta "F11: nadie acepta el pedido"

step "0. Asignación con plazo de búsqueda de $SEARCH_TIMEOUT"
DISPATCH_SEARCH_TIMEOUT="$SEARCH_TIMEOUT" $COMPOSE up -d dispatch > /dev/null 2>&1
wait_healthy dispatch
evidence "dispatch: DISPATCH_SEARCH_TIMEOUT=$($COMPOSE exec -T dispatch printenv DISPATCH_SEARCH_TIMEOUT | tr -d '\r')"

step "1. Un solo trabajador disponible y un pedido"
new_worker "$STAMP"
new_client "$STAMP"
REQUEST=$(create_request "$CLIENT" "nadie-$STAMP")
evidence "trabajador $WORKER_EMAIL; pedido $REQUEST"
wait_status "$REQUEST" SEARCHING_WORKER

step "2. El trabajador rechaza la oferta"
offer_id() { curl -sf "$DISPATCH/offers/me" -H "Authorization: Bearer $WORKER" | jq -r '.[0].offerId // empty'; }
has_offer() { [ -n "$(offer_id)" ]; }
wait_until "llegó la oferta" 30 has_offer
OFFER=$(offer_id)
evidence "rechazo: $(send POST "$DISPATCH/offers/$OFFER/reject" "$WORKER" | jq -c .)"

step "3. Vence la búsqueda: compensación"
wait_status "$REQUEST" CANCELLED 90
REASON=$(curl -sf "$API/requests/$REQUEST" -H "Authorization: Bearer $CLIENT" | jq -r '.cancelReason')
evidence "estado: CANCELLED; motivo: $REASON"
voided() { [ "$(payment_status "$REQUEST")" = VOIDED ]; }
wait_until "pagos anuló la autorización" 30 voided
evidence "pago: $(payment_status "$REQUEST")"
evidence "historial: $(curl -sf "$API/requests/$REQUEST/history" -H "Authorization: Bearer $CLIENT" | jq -c '[.[] | "\(.fromStatus // "-")→\(.toStatus) \(.reason // "")"]')"
notified() { curl -sf "$NOTIFY/notifications/me" -H "Authorization: Bearer $CLIENT" | jq -e 'length > 0' > /dev/null; }
wait_until "el cliente recibió el aviso" 30 notified
evidence "último aviso al cliente: $(curl -sf "$NOTIFY/notifications/me" -H "Authorization: Bearer $CLIENT" | jq -r '.[0] | "\(.title): \(.body)"')"

step "4. Asignación vuelve a su plazo normal"
restore_dispatch
wait_healthy dispatch
evidence_end "el único trabajador rechazó y venció la búsqueda: assignment-failed, pago anulado, pedido CANCELLED y aviso al cliente"
