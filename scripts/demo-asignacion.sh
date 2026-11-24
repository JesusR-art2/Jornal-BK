#!/usr/bin/env bash
# Demostración F6: pedido real con asignación y seguimiento.
#   1. Un trabajador verificado, con categoría y ubicación base, queda disponible.
#   2. Un cliente crea un pedido cerca: pagos autoriza y asignación le ofrece el pedido al trabajador.
#   3. El trabajador acepta (reserva atómica): el pedido queda ASSIGNED.
#   4. El trabajador va en camino enviando su posición; el cliente la ve acercarse.
#   5. Termina el trabajo, el cliente confirma y pagos captura (el comprobante llega en F8).
#
# Requisitos: docker compose -f infra/docker-compose.yml up -d --build --wait
set -euo pipefail

GW="${GW:-http://localhost:8000}"
API="$GW/api/co"
DISPATCH="$GW/subsystems/dispatch"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin12345}"
LAT=4.6584; LON=-74.0937          # lugar del trabajo (Bogotá, datos ficticios)

jq() { docker run --rm -i ghcr.io/jqlang/jq:1.7.1 "$@"; }
step() { printf '\n== %s\n' "$*"; }
send() {   # send <método> <url> <token> [json]   (JSON por stdin: Git Bash en Windows altera los argumentos)
  local method="$1" url="$2" token="$3" body="${4:-}"
  printf '%s' "$body" | curl -sf -X "$method" "$url" -H 'Content-Type: application/json' \
    ${token:+-H "Authorization: Bearer $token"} --data-binary @-
}
login() { send POST "$API/auth/login" "" "{\"email\":\"$1\",\"password\":\"$2\"}" | jq -r '.accessToken'; }
register() {
  send POST "$API/auth/register" "" "{\"email\":\"$1\",\"password\":\"Clave12345\",\"givenName\":\"$2\",
    \"familyName\":\"Prueba\",\"rol\":\"$3\",\"acceptedTerms\":true}" | jq -r '.id'
}
status_of() { curl -sf "$API/requests/$1" -H "Authorization: Bearer $CLIENT" | jq -r '.status'; }
wait_status() {
  for _ in $(seq 1 40); do
    [ "$(status_of "$1")" = "$2" ] && { echo "   OK: pedido en $2"; return 0; }
    sleep 1
  done
  echo "   FALLO: el pedido sigue en $(status_of "$1")"; exit 1
}

STAMP=$(date +%s)
source "$(dirname "$0")/lib.sh"
retire_demo_workers   # un solo trabajador disponible: el de esta demo
ADMIN=$(login "admin@co.jornal.test" "$ADMIN_PASSWORD")

step "1. Trabajador verificado y disponible"
WORKER_ID=$(register "worker-$STAMP@example.test" "Luis" WORKER)
send PATCH "$API/admin/users/$WORKER_ID/verification" "$ADMIN" '{"state":"VERIFIED"}' > /dev/null
WORKER=$(login "worker-$STAMP@example.test" Clave12345)
CATEGORY=$(curl -sf "$API/categories?q=warehouse" -H "Authorization: Bearer $WORKER" | jq -r '.[0].id')
send PUT "$API/workers/me" "$WORKER" "{\"bio\":\"Bodega y cargue\",\"workRadiusKm\":15,
  \"categoryIds\":[\"$CATEGORY\"],\"baseLatitude\":4.6700,\"baseLongitude\":-74.0937}" > /dev/null
send PATCH "$API/workers/me/availability" "$WORKER" '{"available":true}' | jq -c '{available, baseLatitude, baseLongitude}'

step "2. Pedido del cliente: pagos autoriza y asignación ofrece"
register "client-$STAMP@example.test" "Ana" APPLICANT > /dev/null
CLIENT=$(login "client-$STAMP@example.test" Clave12345)
DAY=$(date -d '+2 days' +%Y-%m-%d 2>/dev/null || date -v+2d +%Y-%m-%d)
REQUEST=$(printf '%s' "{\"categoryId\":\"$CATEGORY\",\"units\":2,\"scheduledLocal\":\"${DAY}T08:00\",
  \"timezone\":\"America/Bogota\",\"address\":{\"street_address\":\"Calle 26 # 68-35\",\"city\":\"Bogotá\",
  \"department\":\"Cundinamarca\"},\"latitude\":$LAT,\"longitude\":$LON,\"paymentMethod\":\"CARD\",\"tipMinor\":5000}" \
  | curl -sf -X POST "$API/requests" -H 'Content-Type: application/json' -H "Authorization: Bearer $CLIENT" \
    -H "Idempotency-Key: demo-$STAMP" --data-binary @- | jq -r '.id')
echo "   pedido $REQUEST"
wait_status "$REQUEST" SEARCHING_WORKER
OFFER=""
for _ in $(seq 1 20); do
  OFFER=$(curl -sf "$DISPATCH/offers/me" -H "Authorization: Bearer $WORKER" | jq -r '.[0].offerId // empty')
  [ -n "$OFFER" ] && break; sleep 1
done
[ -n "$OFFER" ] || { echo "   FALLO: no llegó la oferta"; exit 1; }
curl -sf "$DISPATCH/offers/me" -H "Authorization: Bearer $WORKER" | jq -c '.[0] | {offerId, distanceKm, expiresAt}'

step "3. El trabajador acepta"
send POST "$DISPATCH/offers/$OFFER/accept" "$WORKER" | jq -c .
wait_status "$REQUEST" ASSIGNED

step "4. En camino: el cliente ve acercarse al trabajador"
send POST "$API/requests/$REQUEST/en-route" "$WORKER" > /dev/null
for lat in 4.6680 4.6640 4.6600; do
  send PUT "$DISPATCH/locations/me" "$WORKER" "{\"latitude\":$lat,\"longitude\":$LON}" > /dev/null
  curl -sf "$DISPATCH/tracking/$REQUEST" -H "Authorization: Bearer $CLIENT" \
    | jq -c '{status, lat: .position.latitude, distanceKm}'
done

step "5. Trabajo terminado, confirmación y captura del pago"
send POST "$API/requests/$REQUEST/start" "$WORKER" > /dev/null
send POST "$API/requests/$REQUEST/finish" "$WORKER" > /dev/null
send POST "$API/requests/$REQUEST/confirm" "$CLIENT" > /dev/null
for _ in $(seq 1 20); do
  CAPTURED=$(curl -sf "$API/requests/$REQUEST" -H "Authorization: Bearer $CLIENT" | jq -r '.paymentCaptured')
  [ "$CAPTURED" = "true" ] && break; sleep 1
done
curl -sf "$API/requests/$REQUEST" -H "Authorization: Bearer $CLIENT" | jq -c '{status, paymentCaptured, totalMinor, currency}'
curl -sf "$GW/subsystems/payments/payments?requestId=$REQUEST" | jq -c '[.ledger[] | {kind, amount_minor}]'
[ "$CAPTURED" = "true" ] && echo "   OK: pago capturado y liquidado (con el comprobante de facturación el pedido pasa a COMPLETED)"
