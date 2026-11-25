#!/usr/bin/env bash
# Datos de demostración para el frontend y la sustentación: en cada región que esté arriba deja
#   - un cliente     cliente@<país>.jornal.test
#   - un comercio    comercio@<país>.jornal.test   (con identificación fiscal válida para el país)
#   - un trabajador  trabajador@<país>.jornal.test (verificado, con bodega, ubicación base y disponible)
# todos con la clave Demo12345 (variable DEMO_PASSWORD). Todos los datos son ficticios.
#
# Se puede correr varias veces: si el usuario ya existe, solo lo deja listo (verificado y disponible).
# Requisitos: docker compose -f infra/docker-compose.yml up -d --build --wait   (con --profile full, las 5 regiones)
# Uso:        scripts/seed-demo.sh [región...]      (por defecto co br us fr jp; salta las que no respondan)
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

DEMO_PASSWORD="${DEMO_PASSWORD:-Demo12345}"
REGIONS=("$@"); [ ${#REGIONS[@]} -gt 0 ] || REGIONS=(co br us fr jp)

# Datos ficticios por país: teléfono, dirección (según el esquema del país), identificación fiscal y ubicación.
data_of() {
  case "$1" in
    co) PHONE="300 765 4321"; LAT=4.6584; LON=-74.0937
        ADDRESS='{"street_address":"Calle 26 # 68-35","city":"Bogotá","department":"Cundinamarca"}'
        TAX='"taxIdType":"NIT","taxIdNumber":"900123456-8"'; LEGAL="Comercio Demo SAS" ;;
    br) PHONE="(11) 98765-4321"; LAT=-23.5558; LON=-46.6623
        ADDRESS='{"street":"Rua Augusta","number":"1500","neighborhood":"Consolação","city":"São Paulo","state":"SP","postal_code":"01304-001"}'
        TAX='"taxIdType":"CNPJ","taxIdNumber":"11.222.333/0001-81"'; LEGAL="Comércio Demo Ltda" ;;
    us) PHONE="(212) 736-3100"; LAT=40.7484; LON=-73.9857
        ADDRESS='{"line1":"350 5th Ave","city":"New York","state":"NY","postal_code":"10118"}'
        TAX='"taxIdType":"EIN","taxIdNumber":"12-3456789"'; LEGAL="Demo Business LLC" ;;
    fr) PHONE="06 98 76 54 32"; LAT=48.8559; LON=2.3601
        ADDRESS='{"street_address":"8 rue de Rivoli","postal_code":"75004","city":"Paris"}'
        TAX='"taxIdType":"SIRET","taxIdNumber":"12345678900015"'; LEGAL="Commerce Démo SARL" ;;
    jp) PHONE="090-8765-4321"; LAT=35.6812; LON=139.7671
        ADDRESS='{"postal_code":"100-0001","prefecture":"東京都","city":"千代田区","town":"千代田","block":"1-1"}'
        TAX='"taxIdType":"CORPORATE_NUMBER","taxIdNumber":"8700110005901"'; LEGAL="デモ商事株式会社" ;;
    *) fail "región desconocida: $1" ;;
  esac
}

# register_once <email> <nombre> <rol> [json extra]: registra si no existe (409 = ya existe)
register_once() {
  local body code
  body="{\"email\":\"$1\",\"password\":\"$DEMO_PASSWORD\",\"givenName\":\"$2\",\"familyName\":\"Demo\",
    \"rol\":\"$3\",\"phone\":\"$PHONE\",\"acceptedTerms\":true${4:+,$4}}"
  code=$(printf '%s' "$body" | curl -s -o /tmp/seed-register.json -w '%{http_code}' -X POST "$API/auth/register" \
    -H 'Content-Type: application/json' --data-binary @-)
  case "$code" in
    201) ok "$1 registrado" ;;
    409) ok "$1 ya existía" ;;
    *)   fail "registro de $1: HTTP $code $(cat /tmp/seed-register.json)" ;;
  esac
}

SEEDED=()
for REGION in "${REGIONS[@]}"; do
  API="$GW/api/$REGION"
  step "Región $REGION"
  if [ "$(http_code "$API/region-profile")" != 200 ]; then
    echo "   (no responde: se salta; levanta las 5 regiones con --profile full)"; continue
  fi
  data_of "$REGION"

  register_once "cliente@$REGION.jornal.test" "Cliente" APPLICANT "\"address\":{\"label\":\"Casa\",\"fields\":$ADDRESS}"
  register_once "comercio@$REGION.jornal.test" "Comercio" BUSINESS \
    "\"business\":{\"legalName\":\"$LEGAL\",$TAX,\"billingAddress\":{\"fields\":$ADDRESS}}"
  register_once "trabajador@$REGION.jornal.test" "Trabajador" WORKER

  ADMIN=$(admin_token)
  WORKER=$(login "trabajador@$REGION.jornal.test" "$DEMO_PASSWORD")
  WORKER_ID=$(curl -sf "$API/users/me" -H "Authorization: Bearer $WORKER" | jq -r '.id')
  send PATCH "$API/admin/users/$WORKER_ID/verification" "$ADMIN" '{"state":"VERIFIED"}' > /dev/null
  warehouse_category "$WORKER"
  send PUT "$API/workers/me" "$WORKER" "{\"bio\":\"Trabajador de demostración\",\"workRadiusKm\":20,
    \"categoryIds\":[\"$CATEGORY\"],\"baseLatitude\":$LAT,\"baseLongitude\":$LON}" > /dev/null
  send PATCH "$API/workers/me/availability" "$WORKER" '{"available":true}' > /dev/null
  ok "trabajador verificado, con bodega, ubicación base ($LAT, $LON) y disponible"
  SEEDED+=("$REGION")
done

step "Listo"
[ ${#SEEDED[@]} -gt 0 ] || fail "ninguna región respondió"
echo "   Regiones: ${SEEDED[*]}"
echo "   Usuarios: cliente@<país>.jornal.test, comercio@<país>.jornal.test, trabajador@<país>.jornal.test"
echo "   Clave:    $DEMO_PASSWORD      Administrador: admin@<país>.jornal.test / $ADMIN_PASSWORD"
echo "   Nota: las demos y pruebas de falla de scripts/ retiran a este trabajador para tener un único candidato;"
echo "         después de correrlas, vuelve a ejecutar scripts/seed-demo.sh."
