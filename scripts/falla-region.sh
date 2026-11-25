#!/usr/bin/env bash
# Prueba de falla F11: cae una región entera (core-br).
#   1. Se detiene core-br: el gateway responde 503 SERVICE_UNAVAILABLE solo para Brasil.
#   2. Colombia sigue atendiendo (perfil, login y consola), y su consola señala a core-br caído.
#   3. core-br vuelve y Brasil responde otra vez.
# Demuestra: aislamiento regional (una región caída no tumba a las demás).
#
# Requisitos: docker compose -f infra/docker-compose.yml up -d --build --wait
# Uso:        scripts/falla-region.sh [región caída]     (por defecto br; la que sigue viva es co)
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

DOWN="${1:-br}"
[ "$DOWN" != "$REGION" ] || fail "la región caída debe ser distinta de $REGION"
trap '$COMPOSE start core-$DOWN > /dev/null 2>&1 || true' EXIT
evidence_start falla-region "F11: cae la región $DOWN"

step "0. Antes: las dos regiones responden"
evidence "GET /api/$DOWN/region-profile -> $(http_code "$GW/api/$DOWN/region-profile")"
evidence "GET /api/$REGION/region-profile -> $(http_code "$API/region-profile")"
[ "$(http_code "$GW/api/$DOWN/region-profile")" = 200 ] || fail "core-$DOWN no está arriba"

step "1. Se detiene core-$DOWN"
$COMPOSE stop "core-$DOWN" > /dev/null 2>&1
evidence "core-$DOWN: $($COMPOSE ps -a --format '{{.State}}' "core-$DOWN")"
BODY=$(curl -s "$GW/api/$DOWN/region-profile")
CODE=$(http_code "$GW/api/$DOWN/region-profile")
evidence "GET /api/$DOWN/region-profile -> $CODE $BODY"
[ "$CODE" = 503 ] || fail "se esperaba 503 para $DOWN y llegó $CODE"
printf '%s' "$BODY" | jq -e '.code == "SERVICE_UNAVAILABLE"' > /dev/null || fail "falta el código SERVICE_UNAVAILABLE"
ok "el gateway responde 503 solo para $DOWN"

step "2. $REGION sigue funcionando"
CODE=$(http_code "$API/region-profile")
evidence "GET /api/$REGION/region-profile -> $CODE"
[ "$CODE" = 200 ] || fail "$REGION dejó de responder"
ADMIN=$(admin_token)
[ -n "$ADMIN" ] && [ "$ADMIN" != null ] || fail "el login en $REGION falló"
evidence "login del administrador de $REGION: OK"
DOWN_LIST=$(curl -sf "$API/admin/console" -H "Authorization: Bearer $ADMIN" | jq -c '.down')
evidence "consola de $REGION, servicios caídos: $DOWN_LIST"
printf '%s' "$DOWN_LIST" | jq -e --arg s "core-$DOWN" 'index($s) != null' > /dev/null \
  || fail "la consola no señala a core-$DOWN"
ok "la consola de $REGION señala a core-$DOWN caído"

step "3. core-$DOWN vuelve"
$COMPOSE start "core-$DOWN" > /dev/null 2>&1
wait_healthy "core-$DOWN"
back() { [ "$(http_code "$GW/api/$DOWN/region-profile")" = 200 ]; }
wait_until "$DOWN responde otra vez por el gateway" 30 back
evidence "GET /api/$DOWN/region-profile -> $(http_code "$GW/api/$DOWN/region-profile")"
evidence_end "con core-$DOWN caído el gateway respondió 503 solo para esa región; $REGION siguió atendiendo y su consola señaló la caída; al volver, $DOWN respondió de nuevo"
