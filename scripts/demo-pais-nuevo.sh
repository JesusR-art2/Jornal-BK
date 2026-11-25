#!/usr/bin/env bash
# Demostración P2: un sexto país solo con datos (Alemania, de-DE, EUR).
#   1. La migración V18 agrega el perfil de Alemania (formatos, USt 19 %, Rechnung, medios de pago, propina,
#      feriados) y su catálogo traducido con precios en euros. No hay código por país.
#   2. Se levanta core-de: el mismo jar con APP_REGION=DE y su propia base (jornal_de).
#   3. Un pedido real en Berlín recorre pagos, asignación, notificaciones y facturación hasta COMPLETED, con la
#      factura RE-... en euros. Los textos sin traducción al alemán salen del respaldo en inglés.
# Lo único que cambia fuera de los datos es el despliegue: el servicio core-de, su base y la ruta del gateway.
#
# Requisitos: imágenes construidas con este cambio:  docker compose -f infra/docker-compose.yml up -d --build --wait
# Uso:        scripts/demo-pais-nuevo.sh
set -euo pipefail
cd "$(dirname "$0")/.."

REGION=de
LAT=52.5170; LON=13.3889          # Berlín (datos ficticios)
ZONE=Europe/Berlin
ADDRESS_JSON='{"street_address":"Unter den Linden 77","postal_code":"10117","city":"Berlin"}'
PAYMENT_METHOD=SEPA
TIP_MINOR=1400                    # 5 % de 2 × 140,00 €
source scripts/lib.sh

STAMP=$(date +%s)
evidence_start demo-pais-nuevo "P2: sexto país solo con datos (Alemania)"

step "1. Base de datos de la región"
if [ "$(psql_q jornal "SELECT count(*) FROM pg_database WHERE datname = 'jornal_de'")" = 0 ]; then
  $COMPOSE exec -T postgres psql -U "${POSTGRES_USER:-jornalAdmin}" -d jornal -c "CREATE DATABASE jornal_de" > /dev/null
  evidence "base jornal_de creada (el volumen es anterior a este cambio)"
else
  evidence "base jornal_de ya existe"
fi

step "2. core-de arriba (mismo jar, APP_REGION=DE)"
$COMPOSE --profile de up -d core-de gateway > /dev/null 2>&1
$COMPOSE exec -T gateway nginx -s reload > /dev/null 2>&1   # toma la ruta /api/de/ si el gateway ya corría
wait_healthy core-de 240
[ "$(http_code "$API/region-profile")" = 200 ] || fail "core-de no responde; reconstruye las imágenes con --build"
evidence "perfil: $(curl -sf "$API/region-profile" | jq -c '{code, defaultLocale, currency, datePattern, receiptType,
  tax: .taxRules[0].name, rate: .taxRules[0].rateBps, tipMode, pagos: [.paymentMethods[].code]}')"
evidence "migración aplicada: $(psql_q jornal_de "SELECT version || ' ' || description FROM flyway_schema_history WHERE version = '18'")"

step "3. Usuarios en Alemania"
retire_demo_workers
ADMIN=$(admin_token)
WORKER_EMAIL="worker-$STAMP@example.test"
WORKER_ID=$(send POST "$API/auth/register" "" "{\"email\":\"$WORKER_EMAIL\",\"password\":\"Clave12345\",
  \"givenName\":\"Lukas\",\"familyName\":\"Muster\",\"rol\":\"WORKER\",\"phone\":\"0151 23456789\",\"acceptedTerms\":true}" | jq -r '.id')
send PATCH "$API/admin/users/$WORKER_ID/verification" "$ADMIN" '{"state":"VERIFIED"}' > /dev/null
WORKER=$(login "$WORKER_EMAIL" Clave12345)
warehouse_category "$WORKER"
send PUT "$API/workers/me" "$WORKER" "{\"bio\":\"Lager und Inventur\",\"workRadiusKm\":15,
  \"categoryIds\":[\"$CATEGORY\"],\"baseLatitude\":52.5250,\"baseLongitude\":$LON}" > /dev/null
send PATCH "$API/workers/me/availability" "$WORKER" '{"available":true}' > /dev/null
CLIENT_EMAIL="client-$STAMP@example.test"
evidence "cliente: $(send POST "$API/auth/register" "" "{\"email\":\"$CLIENT_EMAIL\",\"password\":\"Clave12345\",
  \"givenName\":\"Erika\",\"familyName\":\"Muster\",\"rol\":\"APPLICANT\",\"phone\":\"030 12345678\",
  \"address\":{\"fields\":$ADDRESS_JSON},\"acceptedTerms\":true}" | jq -c '{name, countryCode, locale, phone}')"
CLIENT=$(login "$CLIENT_EMAIL" Clave12345)
evidence "catálogo en alemán: $(curl -sf "$API/categories?q=lager" -H "Authorization: Bearer $CLIENT" \
  -H 'Accept-Language: de-DE' | jq -c '.[0] | {name, price}')"

step "4. Pedido D4 en Berlín hasta COMPLETED"
REQUEST=$(create_request "$CLIENT" "de-$STAMP")
evidence "pedido $REQUEST"
wait_status "$REQUEST" SEARCHING_WORKER 60
accept_offer
wait_status "$REQUEST" ASSIGNED
finish_and_confirm "$REQUEST"
wait_status "$REQUEST" COMPLETED 60
evidence "$(curl -sf "$API/requests/$REQUEST" -H "Authorization: Bearer $CLIENT" | jq -c '{status, subtotalMinor,
  travelFeeMinor, taxMinor, tipMinor, totalMinor, currency, receiptNumber}')"
evidence "pago: $(curl -sf "$PAYMENTS/payments?requestId=$REQUEST" | jq -c '.payments[0] | {method, flow, status, amountMinor, currency}')"

step "5. Factura y avisos"
RECEIPT=$(curl -sf "$BILLING/receipts/$REQUEST" -H "Authorization: Bearer $CLIENT")
evidence "factura: $(printf '%s' "$RECEIPT" | jq -c '{title, number: .receipt.number, locale, date: .issuedAtText, totals}')"
printf '%s' "$RECEIPT" | jq -e '.receipt.number | startswith("RE-")' > /dev/null || fail "la factura no usa el prefijo RE"
evidence "último aviso al cliente: $(curl -sf "$NOTIFY/notifications/me" -H "Authorization: Bearer $CLIENT" | jq -r '.[0] | "\(.title): \(.body)"')"
evidence "colas de la región: $(curl -sf -u "$RABBIT_AUTH" "$RABBIT_API/queues" | jq -c '[.[] | select(.name | endswith(".de")) | .name]')"

evidence_end "Alemania funcionó de punta a punta sin código por país: perfil, catálogo y precios desde la base; pago SEPA, asignación, avisos y factura RE en euros; los textos sin traducción salen en inglés"
