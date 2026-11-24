#!/usr/bin/env bash
# Demostración F10: id de correlación de punta a punta, métricas en Prometheus, tablero de Grafana y consola
# del administrador que señala qué servicio está caído.
#
# Requisitos: docker compose -f infra/docker-compose.yml up -d --build --wait
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

STAMP=$(date +%s)
CID="demo-cid-$STAMP"
evidence_start demo-observabilidad "F10: correlación, métricas, tablero y consola"

step "1. Un pedido con X-Correlation-Id=$CID"
new_worker "$STAMP"
new_client "$STAMP"
DAY=$(date -d '+2 days' +%Y-%m-%d 2>/dev/null || date -v+2d +%Y-%m-%d)
REQUEST=$(printf '%s' "{\"categoryId\":\"$CATEGORY\",\"units\":1,\"scheduledLocal\":\"${DAY}T08:00\",
  \"timezone\":\"America/Bogota\",\"address\":{\"street_address\":\"Calle 26 # 68-35\",\"city\":\"Bogotá\",
  \"department\":\"Cundinamarca\"},\"latitude\":$LAT,\"longitude\":$LON,\"paymentMethod\":\"CARD\"}" \
  | curl -sf -X POST "$API/requests" -H 'Content-Type: application/json' -H "Authorization: Bearer $CLIENT" \
    -H "Idempotency-Key: obs-$STAMP" -H "X-Correlation-Id: $CID" --data-binary @- | jq -r '.id')
evidence "pedido $REQUEST"
wait_status "$REQUEST" SEARCHING_WORKER
sleep 3

step "2. El mismo id en los logs de cada proceso"
for svc in core-co payments dispatch notifications; do
  n=$($COMPOSE logs --since 5m "$svc" 2>/dev/null | grep -c "cid=$CID" || true)
  evidence "$svc: $n líneas con cid=$CID"
  [ "$n" -gt 0 ] || fail "$svc no registró el id de correlación"
done
evidence "$($COMPOSE logs --since 5m payments 2>/dev/null | grep "cid=$CID" | head -1 | cut -c1-220)"

step "3. Métricas en Prometheus"
sleep 20   # un ciclo de recolección
q() { curl -sf "http://localhost:9090/api/v1/query" --data-urlencode "query=$1" | jq -c "$2"; }
evidence "objetivos arriba: $(q 'up' '[.data.result[] | select(.value[1]=="1") | .metric.instance]')"
evidence "pedidos por estado (co): $(q 'jornal_requests{region="co"} > 0' '[.data.result[] | {(.metric.status): .value[1]}] | add')"
evidence "mensajes procesados: $(q 'sum by (consumer, outcome) (jornal_messages_total)' '[.data.result[] | "\(.metric.consumer)/\(.metric.outcome)=\(.value[1])"]')"
evidence "colas con mensajes: $(q 'sum by (queue) (rabbitmq_detailed_queue_messages) > 0' '[.data.result[] | "\(.metric.queue)=\(.value[1])"]')"
[ "$(q 'up{instance="core-co:8080"}' '.data.result[0].value[1]')" = '"1"' ] || fail "Prometheus no lee al core"

step "4. Tablero de Grafana provisionado"
evidence "$(curl -sf -u admin:${GRAFANA_PASSWORD:-admin} 'http://localhost:3000/api/search?query=Jornal' | jq -c '[.[] | {title, url}]')"

step "5. Consola del administrador: billing caído"
$COMPOSE stop billing > /dev/null 2>&1
ADMIN=$(admin_token)
CONSOLE=$(curl -sf "$API/admin/console" -H "Authorization: Bearer $ADMIN")
evidence "tablero: $(printf '%s' "$CONSOLE" | jq -c '.dashboard | {region, today, workersAvailable, requestsByStatus}')"
evidence "caídos: $(printf '%s' "$CONSOLE" | jq -c '.down')"
evidence "colas: $(printf '%s' "$CONSOLE" | jq -c '{deadLetters: .queues.deadLetters, sinConsumidor: [.queues.queues[] | select(.noConsumer) | .name]}')"
printf '%s' "$CONSOLE" | jq -e '.down | index("billing")' > /dev/null || fail "la consola no señala billing caído"
ok "la consola señala billing como caído"
$COMPOSE start billing > /dev/null 2>&1
evidence_end "el id de correlación aparece en core, pagos, asignación y notificaciones; Prometheus lee todos los procesos; la consola señala el servicio caído"
