#!/usr/bin/env bash
# Demostración F5: un pedido real recorre core → pagos → core.
#   1. El cliente crea el pedido (con Idempotency-Key) y pagos lo autoriza: SEARCHING_WORKER.
#   2. El cliente lo cancela y pagos anula la autorización (compensación).
#   3. Con el simulador rechazando tarjetas, el pedido termina CANCELLED por pago fallido.
#
# Requisitos: docker compose -f infra/docker-compose.yml up -d --build --wait
# Uso:        scripts/demo-pagos.sh            (región co)
set -euo pipefail

GW="${GW:-http://localhost:8000}"
REGION="co"
API="$GW/api/$REGION"
PAY="$GW/subsystems/payments"

jq() { docker run --rm -i ghcr.io/jqlang/jq:1.7.1 "$@"; }
step() { printf '\n== %s\n' "$*"; }
post() {   # post <url> <json> [headers...]   (cuerpo por stdin: Git Bash en Windows altera los argumentos JSON)
  local url="$1" body="$2"; shift 2
  printf '%s' "$body" | curl -sf -X POST "$url" -H 'Content-Type: application/json' "$@" --data-binary @-
}
put() {
  local url="$1" body="$2"; shift 2
  printf '%s' "$body" | curl -sf -X PUT "$url" -H 'Content-Type: application/json' "$@" --data-binary @-
}
status_of() { curl -sf "$API/requests/$1" -H "Authorization: Bearer $TOKEN" | jq -r '.status'; }
wait_status() {   # wait_status <id> <estado>
  for _ in $(seq 1 30); do
    [ "$(status_of "$1")" = "$2" ] && { echo "   OK: pedido en $2"; return 0; }
    sleep 1
  done
  echo "   FALLO: el pedido sigue en $(status_of "$1")"; exit 1
}
create_request() {
  local day; day=$(date -d '+2 days' +%Y-%m-%d 2>/dev/null || date -v+2d +%Y-%m-%d)
  post "$API/requests" "{\"categoryId\":\"$CATEGORY\",\"units\":2,\"scheduledLocal\":\"${day}T08:00\",
    \"timezone\":\"America/Bogota\",\"address\":{\"street_address\":\"Calle 26 # 68-35\",\"city\":\"Bogotá\",
    \"department\":\"Cundinamarca\"},\"latitude\":4.6584,\"longitude\":-74.0937,\"paymentMethod\":\"CARD\",
    \"tipMinor\":5000}" -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: demo-$(date +%s%N)" | jq -r '.id'
}

step "Cliente de prueba (datos ficticios)"
EMAIL="demo-$(date +%s)@example.test"
post "$API/auth/register" "{\"email\":\"$EMAIL\",\"password\":\"Clave12345\",\"givenName\":\"Ana\",
  \"familyName\":\"Prueba\",\"rol\":\"APPLICANT\",\"acceptedTerms\":true}" > /dev/null
TOKEN=$(post "$API/auth/login" "{\"email\":\"$EMAIL\",\"password\":\"Clave12345\"}" | jq -r '.accessToken')
CATEGORY=$(curl -sf "$API/categories?q=warehouse" -H "Authorization: Bearer $TOKEN" | jq -r '.[0].id')
echo "   $EMAIL, categoría bodega $CATEGORY"

step "1. Pedido con tarjeta: pagos autoriza"
ID=$(create_request)
echo "   pedido $ID"
wait_status "$ID" SEARCHING_WORKER
curl -sf "$PAY/payments?requestId=$ID" | jq -c '.payments[] | {attempt, method, flow, status, amountMinor, currency}'

step "2. El cliente cancela: pagos anula la autorización"
post "$API/requests/$ID/cancel" '{"reason":"Demo"}' -H "Authorization: Bearer $TOKEN" > /dev/null
wait_status "$ID" CANCELLED
for _ in $(seq 1 15); do
  S=$(curl -sf "$PAY/payments?requestId=$ID" | jq -r '.payments[0].status')
  [ "$S" = "VOIDED" ] && break; sleep 1
done
echo "   pago: $S"; [ "$S" = "VOIDED" ]

step "3. Simulador rechazando tarjetas: el pedido se cancela por pago fallido"
ORIGINAL=$(curl -sf "$PAY/simulator")
put "$PAY/simulator" '{"failureRate":0,"declineMethods":["CARD"],"latency":"PT0S","voucherAutoPayAfter":"PT10S","voucherExpireAfter":"PT90S"}' > /dev/null
ID2=$(create_request)
wait_status "$ID2" CANCELLED
curl -sf "$API/requests/$ID2" -H "Authorization: Bearer $TOKEN" | jq -r '"   motivo: " + .cancelReason'
put "$PAY/simulator" "$ORIGINAL" > /dev/null
echo "   simulador restaurado"

step "Libro de movimientos por región"
curl -sf "$PAY/ledger/summary" | jq -c '.[]'
