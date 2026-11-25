#!/usr/bin/env bash
# Prueba de falla F11: concurrencia por el único trabajador disponible.
#   1. Hay un solo trabajador disponible y dos clientes piden al mismo tiempo.
#   2. Asignación le ofrece solo uno de los pedidos (un trabajador no tiene dos ofertas a la vez).
#   3. El trabajador acepta 8 veces en paralelo la misma oferta: una sola aceptación gana (reserva atómica).
#   4. Queda una sola reserva; el otro pedido sigue buscando otro candidato.
# Demuestra: exclusión mutua sin bloqueos distribuidos (actualización condicional en la base).
#
# Requisitos: docker compose -f infra/docker-compose.yml up -d --build --wait
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

STAMP=$(date +%s)
evidence_start falla-concurrencia "F11: dos pedidos simultáneos por el único trabajador"
reservations() { psql_q jornal_dispatch "SELECT count(*) FROM reservations WHERE worker_id = '$WORKER_ID'"; }

step "0. Un solo trabajador disponible y dos clientes"
new_worker "$STAMP"
new_client "$STAMP-a"; CLIENT_A=$CLIENT
new_client "$STAMP-b"; CLIENT_B=$CLIENT
evidence "trabajador $WORKER_EMAIL; clientes client-$STAMP-a y client-$STAMP-b"

step "1. Dos pedidos al mismo tiempo"
TMP=$(mktemp -d)
create_request "$CLIENT_A" "conc-a-$STAMP" > "$TMP/a" &
create_request "$CLIENT_B" "conc-b-$STAMP" > "$TMP/b" &
wait
REQ_A=$(cat "$TMP/a"); REQ_B=$(cat "$TMP/b")
evidence "pedido A $REQ_A, pedido B $REQ_B"
CLIENT=$CLIENT_A wait_status "$REQ_A" SEARCHING_WORKER
CLIENT=$CLIENT_B wait_status "$REQ_B" SEARCHING_WORKER

step "2. El trabajador recibe una sola oferta"
one_offer() { [ "$(curl -sf "$DISPATCH/offers/me" -H "Authorization: Bearer $WORKER" | jq 'length')" -ge 1 ]; }
wait_until "llegó una oferta" 30 one_offer
sleep 6   # una vuelta más de la búsqueda (cada 5 s): no debe aparecer una segunda oferta
OFFERS=$(curl -sf "$DISPATCH/offers/me" -H "Authorization: Bearer $WORKER")
evidence "ofertas pendientes del trabajador: $(printf '%s' "$OFFERS" | jq 'length')"
[ "$(printf '%s' "$OFFERS" | jq 'length')" = 1 ] || fail "el trabajador tiene más de una oferta"
OFFER=$(printf '%s' "$OFFERS" | jq -r '.[0].offerId')
WON=$(printf '%s' "$OFFERS" | jq -r '.[0].requestId')
[ "$WON" = "$REQ_A" ] && { LOST=$REQ_B; LOST_CLIENT=$CLIENT_B; WON_CLIENT=$CLIENT_A; } \
                      || { LOST=$REQ_A; LOST_CLIENT=$CLIENT_A; WON_CLIENT=$CLIENT_B; }
evidence "la oferta es por el pedido $WON"

step "3. Ocho aceptaciones en paralelo de la misma oferta"
for i in $(seq 1 8); do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST "$DISPATCH/offers/$OFFER/accept" \
    -H "Authorization: Bearer $WORKER" > "$TMP/accept-$i" &
done
wait
CODES=$(cat "$TMP"/accept-* | sort | uniq -c | tr '\n' ' ')
evidence "respuestas: $CODES"
[ "$(cat "$TMP"/accept-* | grep -c '^200$')" = 1 ] || fail "se esperaba exactamente una aceptación con 200"
ok "una sola aceptación ganó"

step "4. Una sola reserva"
CLIENT=$WON_CLIENT wait_status "$WON" ASSIGNED
evidence "reservas del trabajador en asignación: $(reservations)"
[ "$(reservations)" = 1 ] || fail "hay $(reservations) reservas"
evidence "pedido ganador: $(CLIENT=$WON_CLIENT status_of "$WON"); el otro: $(CLIENT=$LOST_CLIENT status_of "$LOST")"
[ "$(CLIENT=$LOST_CLIENT status_of "$LOST")" = SEARCHING_WORKER ] || fail "el otro pedido debía seguir buscando"
ok "el otro pedido sigue buscando otro candidato"

step "5. Limpieza: se cancelan los dos pedidos"
cancel_quietly "$WON" "$WON_CLIENT"
cancel_quietly "$LOST" "$LOST_CLIENT"
rm -rf "$TMP"
evidence_end "con un solo trabajador y dos pedidos simultáneos hubo una sola oferta, una sola aceptación entre 8 en paralelo y una sola reserva; el otro pedido siguió buscando"
