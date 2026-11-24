#!/usr/bin/env bash
# Demostración F3: un cambio en un core viaja por el Outbox y RabbitMQ hasta el subsistema de pagos,
# y un mensaje reenviado se procesa una sola vez (inbox).
#
# Requisitos: el sistema levantado con   docker compose -f infra/docker-compose.yml up -d --build --wait
# Uso:        scripts/demo-mensajeria.sh [region]      (por defecto co)
set -euo pipefail

REGION="${1:-co}"
GW="${GW:-http://localhost:8000}"
RABBIT_API="${RABBIT_API:-http://localhost:15673/api}"
RABBIT_AUTH="${RABBITMQ_USER:-jornal}:${RABBITMQ_PASSWORD:-jornal-dev-password}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin12345}"

jq() { docker run --rm -i ghcr.io/jqlang/jq:1.7.1 "$@"; }
step() { printf '\n== %s\n' "$*"; }

wait_for() {   # wait_for <descripción> <comando que devuelve 0 cuando se cumple>
  local what="$1"; shift
  for _ in $(seq 1 30); do
    if "$@"; then echo "   OK: $what"; return 0; fi
    sleep 1
  done
  echo "   FALLO: $what (30 s)"; exit 1
}

processed() { curl -sf "$GW/subsystems/payments/status" | jq '.processedMessages'; }
cached_version() {
  curl -sf "$GW/subsystems/payments/status" | jq --arg r "$REGION" '[.regionProfiles[] | select(.region == $r) | .version][0] // -1'
}

step "1. Login del administrador de la región $REGION"
TOKEN=$(printf '{"email":"admin@%s.jornal.test","password":"%s"}' "$REGION" "$ADMIN_PASSWORD" \
  | curl -sf -X POST "$GW/api/$REGION/auth/login" -H 'Content-Type: application/json' --data-binary @- \
  | jq -r '.accessToken')
echo "   token: ${TOKEN:0:25}..."

step "2. Cambio del perfil regional (tarifa de desplazamiento +1)"
BODY=$(curl -sf "$GW/api/$REGION/region-profile" | jq -c 'del(.code, .updatedAt) | .travelFeeMinor += 1')
# Los cuerpos JSON van por stdin (--data-binary @-): en Git Bash para Windows, pasarlos como argumento los altera.
NEW_VERSION=$(printf '%s' "$BODY" | curl -sf -X PUT "$GW/api/$REGION/admin/region-profile" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' --data-binary @- | jq '.version')
echo "   nueva versión del perfil: $NEW_VERSION"

step "3. El evento sale por el Outbox, pasa por RabbitMQ (cola payments.$REGION) y pagos lo guarda"
has_new_version() { [ "$(cached_version)" = "$NEW_VERSION" ]; }
wait_for "payments tiene el perfil $REGION en la versión $NEW_VERSION" has_new_version

step "4. Mismo mensaje enviado dos veces: se procesa una sola vez"
BEFORE=$(processed)
MSG_ID="demo-dup-$(date +%s)"
EVENT=$(printf '{"specversion":"1.0","id":"%s","source":"demo-script","type":"jornal.region.profile-updated.v1","time":"%s","subject":"%s","datacontenttype":"application/json","region":"%s","correlationid":"%s","aggregateversion":0,"data":{}}' \
  "$MSG_ID" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(echo "$REGION" | tr a-z A-Z)" "$REGION" "$MSG_ID")
PUBLISH=$(printf '{"properties":{"content_type":"application/json","delivery_mode":2},"routing_key":"%s.jornal.region.profile-updated.v1","payload":%s,"payload_encoding":"string"}' \
  "$REGION" "$(printf '%s' "$EVENT" | jq -Rs .)")
for i in 1 2; do
  printf '%s' "$PUBLISH" | curl -sf -u "$RABBIT_AUTH" -H 'Content-Type: application/json' \
    -X POST "$RABBIT_API/exchanges/%2F/jornal.events/publish" --data-binary @- > /dev/null
  echo "   enviado #$i (id $MSG_ID)"
done
one_more() { [ "$(processed)" = "$((BEFORE + 1))" ]; }
wait_for "processedMessages pasó de $BEFORE a $((BEFORE + 1))" one_more
sleep 2
AFTER=$(processed)
[ "$AFTER" = "$((BEFORE + 1))" ] && echo "   OK: el duplicado se descartó (processedMessages = $AFTER)" \
  || { echo "   FALLO: processedMessages = $AFTER"; exit 1; }
[ "$(cached_version)" = "$NEW_VERSION" ] && echo "   OK: la versión vieja (0) no reemplazó a la $NEW_VERSION"

step "Listo. Logs: docker compose -f infra/docker-compose.yml logs payments | grep -E 'Processed|Duplicate'"
