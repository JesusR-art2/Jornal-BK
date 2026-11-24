#!/usr/bin/env bash
# Demostración F8: pedido completo hasta COMPLETED con comprobante, calificaciones en ambos sentidos y
# reporte consolidado de ingresos en USD con la tasa usada y su fecha.
#
# Requisitos: docker compose -f infra/docker-compose.yml up -d --build --wait
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

STAMP=$(date +%s)
evidence_start demo-facturacion "F8: comprobante, calificaciones y reporte consolidado"

step "1. Pedido completo (CO)"
new_worker "$STAMP"
new_client "$STAMP"
REQUEST=$(create_request "$CLIENT" "fact-$STAMP")
evidence "pedido $REQUEST"
wait_status "$REQUEST" SEARCHING_WORKER
accept_offer
wait_status "$REQUEST" ASSIGNED
finish_and_confirm "$REQUEST"
wait_status "$REQUEST" COMPLETED 40
evidence "$(curl -sf "$API/requests/$REQUEST" -H "Authorization: Bearer $CLIENT" | jq -c '{status, receiptNumber, totalMinor, currency}')"

step "2. Comprobante (DIAN_E_INVOICE en Colombia)"
evidence "$(curl -sf "$BILLING/receipts/$REQUEST" -H "Authorization: Bearer $CLIENT" \
  | jq -c '{title, number: .receipt.number, date: .issuedAtText, totals}')"
mkdir -p docs/evidencias
curl -sf "$BILLING/receipts/$REQUEST/html" -H "Authorization: Bearer $CLIENT" > docs/evidencias/comprobante-co.html
evidence "HTML imprimible: docs/evidencias/comprobante-co.html"

step "3. Calificaciones en ambos sentidos"
evidence "$(send POST "$API/requests/$REQUEST/rating" "$CLIENT" '{"score":5,"comment":"Muy cumplido"}' | jq -c '{direction, score}')"
evidence "$(send POST "$API/requests/$REQUEST/rating" "$WORKER" '{"score":4}' | jq -c '{direction, score}')"
CODE=$(printf '%s' '{"score":3}' | curl -s -o /dev/null -w '%{http_code}' -X POST "$API/requests/$REQUEST/rating" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $CLIENT" --data-binary @-)
evidence "segunda calificación del cliente: HTTP $CODE (esperado 409)"
[ "$CODE" = "409" ] || fail "se aceptó una calificación repetida"
evidence "$(curl -sf "$API/workers/$WORKER_ID" -H "Authorization: Bearer $CLIENT" | jq -c '{ratingAvg, ratingCount}' 2>/dev/null || true)"

step "4. Reporte consolidado de ingresos en USD"
ADMIN=$(admin_token)
REPORT=$(curl -sf "$BILLING/admin/reports/revenue?currency=USD" -H "Authorization: Bearer $ADMIN")
evidence "$(printf '%s' "$REPORT" | jq -c '.regions[] | {region, currency, receipts, billedLocal, billed, rate}')"
evidence "$(printf '%s' "$REPORT" | jq -c '{currency, totalBilled, totalPlatformRevenue, missingRates}')"
printf '%s' "$REPORT" | jq -e '.regions[] | select(.region=="co") | .rate.asOf' > /dev/null || fail "el reporte no muestra la tasa"
ok "el reporte muestra la tasa usada con su fuente y fecha"
evidence_end "pedido COMPLETED con comprobante, calificaciones en ambos sentidos y reporte con tasa y fecha"
