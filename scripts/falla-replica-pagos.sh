#!/usr/bin/env bash
# Prueba de falla F11: réplica de pagos.
#   1. Pagos corre con dos réplicas que consumen las mismas colas.
#   2. Con el proveedor simulado lento (10 s por llamada), llegan varios pedidos y una réplica se cae de golpe
#      (docker kill) con mensajes a medio procesar.
#   3. RabbitMQ devuelve a la cola lo que la réplica no confirmó y la otra lo procesa: todos los pedidos se
#      autorizan, cada uno una sola vez.
# Demuestra: conmutación a réplica sin perder mensajes (confirmación manual + inbox).
#
# Requisitos: docker compose -f infra/docker-compose.yml up -d --build --wait
# Uso:        scripts/falla-replica-pagos.sh        (ORDERS=4 pedidos por defecto)
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

ORDERS="${ORDERS:-4}"
STAMP=$(date +%s)
replicas() { docker ps -q --filter "label=com.docker.compose.project=jornal" --filter "label=com.docker.compose.service=payments"; }
simulator() {   # simulator <contenedor> <latencia ISO-8601>: cambia la latencia del proveedor simulado de esa réplica
  local current
  current=$(docker exec "$1" curl -sf localhost:8080/simulator)
  printf '%s' "$current" | jq -c --arg l "$2" '.latency = $l' \
    | docker exec -i "$1" curl -sf -X PUT localhost:8080/simulator -H 'Content-Type: application/json' --data-binary @- > /dev/null
}
restore() {
  $COMPOSE up -d --scale payments=1 payments > /dev/null 2>&1 || true
  for id in $(replicas); do simulator "$id" PT0S 2>/dev/null || true; done
}
trap restore EXIT
evidence_start falla-replica-pagos "F11: réplica de pagos"

step "1. Dos réplicas de pagos"
$COMPOSE up -d --no-recreate --scale payments=2 payments > /dev/null 2>&1
wait_healthy payments
mapfile -t IDS < <(replicas)
[ "${#IDS[@]}" = 2 ] || fail "se esperaban 2 réplicas y hay ${#IDS[@]}"
for id in "${IDS[@]}"; do simulator "$id" PT10S; done
evidence "réplicas: $(for id in "${IDS[@]}"; do docker inspect --format '{{.Name}}' "$id"; done | tr '\n' ' ')(proveedor simulado con 10 s de latencia)"
consumers() { curl -sf -u "$RABBIT_AUTH" "$RABBIT_API/queues/%2F/payments.$REGION" | jq '.consumers // 0'; }
two_consumers() { [ "$(consumers)" -ge 2 ]; }
wait_until "las 2 réplicas consumen payments.$REGION" 60 two_consumers
evidence "consumidores de payments.$REGION: $(consumers)"

step "2. $ORDERS pedidos y una réplica se cae a mitad del procesamiento"
new_client "$STAMP"
warehouse_category "$CLIENT"
since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
redelivered() { curl -sf -u "$RABBIT_AUTH" "$RABBIT_API/queues/%2F/payments.$REGION" | jq '.message_stats.redeliver // 0'; }
REDELIVERED_BEFORE=$(redelivered)
REQUESTS=()
for i in $(seq 1 "$ORDERS"); do REQUESTS+=("$(create_request "$CLIENT" "replica-$STAMP-$i")"); done
evidence "pedidos: ${REQUESTS[*]}"
sleep 1
VICTIM=${IDS[0]}; SURVIVOR=${IDS[1]}
evidence "mensajes sin confirmar en payments.$REGION justo antes de la caída: $(curl -sf -u "$RABBIT_AUTH" "$RABBIT_API/queues/%2F/payments.$REGION" | jq '.messages_unacknowledged')"
docker kill "$VICTIM" > /dev/null
evidence "réplica caída (kill): $(docker inspect --format '{{.Name}}' "$VICTIM"); sigue: $(docker inspect --format '{{.Name}}' "$SURVIVOR")"

step "3. La otra réplica termina el trabajo"
for r in "${REQUESTS[@]}"; do wait_status "$r" SEARCHING_WORKER 90; done
for r in "${REQUESTS[@]}"; do
  n=$(payments_of "$r")
  evidence "pedido $r: $(status_of "$r"), pagos $n ($(payment_status "$r"))"
  [ "$n" = 1 ] || fail "el pedido $r tiene $n pagos"
done
evidence "mensajes procesados por la réplica que siguió: $(docker logs --since "$since" "$SURVIVOR" 2>&1 | grep -c 'Processed .*jornal.request.created' || true)"
evidence "mensajes procesados por la réplica caída antes del kill: $(docker logs --since "$since" "$VICTIM" 2>&1 | grep -c 'Processed .*jornal.request.created' || true)"
REDELIVERED=$(( $(redelivered) - REDELIVERED_BEFORE ))
evidence "mensajes que RabbitMQ reentregó a la otra réplica: $REDELIVERED"
[ "$REDELIVERED" -ge 1 ] || fail "la réplica caída no tenía mensajes a medio procesar (nada se reentregó)"
ok "todos los pedidos se autorizaron una sola vez, incluidos los que la réplica caída no alcanzó a confirmar"

step "4. Limpieza: pedidos cancelados y pagos vuelve a una réplica"
for r in "${REQUESTS[@]}"; do cancel_quietly "$r"; done
restore
wait_healthy payments
evidence "réplicas de pagos: $(replicas | wc -l | tr -d ' ')"
evidence_end "con dos réplicas y una caída de golpe a mitad del procesamiento, la otra tomó los mensajes sin confirmar: los $ORDERS pedidos se autorizaron, cada uno una sola vez"
