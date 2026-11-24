#!/usr/bin/env bash
# Demostración F7: cada cambio de estado llega a cliente y trabajador en su idioma y su zona horaria.
#   - El cliente usa la app en español (es-CO) y el trabajador en inglés (en-US), los dos en Colombia.
#   - Se recorre un pedido completo (oferta, asignación, en camino, inicio, fin, confirmación).
#   - Se muestran la bandeja de la app de cada uno, el registro de entregas y los correos en Mailpit.
#
# Requisitos: docker compose -f infra/docker-compose.yml up -d --build --wait
set -euo pipefail

GW="${GW:-http://localhost:8000}"
MAILPIT="${MAILPIT:-http://localhost:8025}"
API="$GW/api/co"
DISPATCH="$GW/subsystems/dispatch"
NOTIFY="$GW/subsystems/notifications"
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
register() {   # register <email> <nombre> <rol> <idioma> [teléfono]
  local phone=null
  [ -n "${5:-}" ] && phone="\"$5\""
  send POST "$API/auth/register" "" "{\"email\":\"$1\",\"password\":\"Clave12345\",\"givenName\":\"$2\",
    \"familyName\":\"Prueba\",\"rol\":\"$3\",\"locale\":\"$4\",\"phone\":$phone,\"acceptedTerms\":true}" \
    | jq -r '.id'
}
status_of() { curl -sf "$API/requests/$1" -H "Authorization: Bearer $CLIENT" | jq -r '.status'; }
wait_status() {
  for _ in $(seq 1 40); do
    [ "$(status_of "$1")" = "$2" ] && { echo "   pedido en $2"; return 0; }
    sleep 1
  done
  echo "   FALLO: el pedido sigue en $(status_of "$1")"; exit 1
}

STAMP=$(date +%s)
source "$(dirname "$0")/lib.sh"
retire_demo_workers   # un solo trabajador disponible: el de esta demo
ADMIN=$(login "admin@co.jornal.test" "$ADMIN_PASSWORD")

step "1. Trabajador (app en inglés) y cliente (app en español)"
WORKER_EMAIL="worker-$STAMP@example.test"
CLIENT_EMAIL="client-$STAMP@example.test"
WORKER_ID=$(register "$WORKER_EMAIL" "Luis" WORKER en-US "3001234567")
send PATCH "$API/admin/users/$WORKER_ID/verification" "$ADMIN" '{"state":"VERIFIED"}' > /dev/null
WORKER=$(login "$WORKER_EMAIL" Clave12345)
CATEGORY=$(curl -sf "$API/categories?q=warehouse" -H "Authorization: Bearer $WORKER" | jq -r '.[0].id')
send PUT "$API/workers/me" "$WORKER" "{\"bio\":\"Bodega y cargue\",\"workRadiusKm\":15,
  \"categoryIds\":[\"$CATEGORY\"],\"baseLatitude\":4.6700,\"baseLongitude\":-74.0937}" > /dev/null
send PATCH "$API/workers/me/availability" "$WORKER" '{"available":true}' > /dev/null
register "$CLIENT_EMAIL" "Ana" APPLICANT es-CO > /dev/null
CLIENT=$(login "$CLIENT_EMAIL" Clave12345)
echo "   trabajador $WORKER_EMAIL (en-US), cliente $CLIENT_EMAIL (es-CO)"

step "2. Pedido completo"
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
send POST "$DISPATCH/offers/$OFFER/accept" "$WORKER" > /dev/null
wait_status "$REQUEST" ASSIGNED
send POST "$API/requests/$REQUEST/en-route" "$WORKER" > /dev/null
send POST "$API/requests/$REQUEST/start" "$WORKER" > /dev/null
send POST "$API/requests/$REQUEST/finish" "$WORKER" > /dev/null
send POST "$API/requests/$REQUEST/confirm" "$CLIENT" > /dev/null
wait_status "$REQUEST" PENDING_CONFIRMATION
sleep 3   # los últimos avisos viajan por RabbitMQ

step "3. Bandeja de la app del cliente (es-CO)"
curl -sf "$NOTIFY/notifications/me" -H "Authorization: Bearer $CLIENT" | jq -r 'reverse | .[] | "   \(.title): \(.body)"'

step "4. Bandeja de la app del trabajador (en-US)"
curl -sf "$NOTIFY/notifications/me" -H "Authorization: Bearer $WORKER" | jq -r 'reverse | .[] | "   \(.title): \(.body)"'

step "5. Registro de entregas del pedido (canal y estado)"
curl -sf "$NOTIFY/deliveries?requestId=$REQUEST" \
  | jq -r 'group_by(.channel + ":" + .status) | .[] | "   \(.[0].channel) \(.[0].status): \(length)"'

step "6. Correos en Mailpit ($MAILPIT)"
for who in "$CLIENT_EMAIL" "$WORKER_EMAIL"; do
  echo "   $who"
  curl -sf "$MAILPIT/api/v1/search?query=to:$who" | jq -r '.messages | reverse | .[] | "     - \(.Subject)"'
done
COUNT=$(curl -sf "$MAILPIT/api/v1/search?query=to:$CLIENT_EMAIL" | jq -r '.messages_count')
[ "$COUNT" -ge 5 ] && echo "   OK: el cliente recibió $COUNT correos en español"
