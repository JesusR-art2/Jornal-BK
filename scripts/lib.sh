#!/usr/bin/env bash
# Funciones comunes de las demostraciones y pruebas de falla (se cargan con: source scripts/lib.sh).
# Requisitos: el sistema en Docker (infra/docker-compose.yml), curl y Docker (jq corre en un contenedor).

GW="${GW:-http://localhost:8000}"
MAILPIT="${MAILPIT:-http://localhost:8025}"
RABBIT_API="${RABBIT_API:-http://localhost:15673/api}"
RABBIT_AUTH="${RABBIT_AUTH:-jornal:jornal-dev-password}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin12345}"
COMPOSE="docker compose -f infra/docker-compose.yml"
REGION="${REGION:-co}"
API="$GW/api/$REGION"
DISPATCH="$GW/subsystems/dispatch"
NOTIFY="$GW/subsystems/notifications"
BILLING="$GW/subsystems/billing"
PAYMENTS="$GW/subsystems/payments"
LAT="${LAT:-4.6584}"; LON="${LON:--74.0937}"   # Bogotá (datos ficticios)

jq() { docker run --rm -i ghcr.io/jqlang/jq:1.7.1 "$@"; }
step() { printf '\n== %s\n' "$*"; }
ok() { printf '   OK: %s\n' "$*"; }
fail() { printf '   FALLO: %s\n' "$*"; exit 1; }

send() {   # send <método> <url> <token> [json]   (JSON por stdin: Git Bash en Windows altera los argumentos)
  local method="$1" url="$2" token="$3" body="${4:-}"
  printf '%s' "$body" | curl -sf -X "$method" "$url" -H 'Content-Type: application/json' \
    ${token:+-H "Authorization: Bearer $token"} --data-binary @-
}
login() { send POST "$API/auth/login" "" "{\"email\":\"$1\",\"password\":\"$2\"}" | jq -r '.accessToken'; }
register() {   # register <email> <nombre> <rol> [idioma] [teléfono]
  local locale=null phone=null
  [ -n "${4:-}" ] && locale="\"$4\""
  [ -n "${5:-}" ] && phone="\"$5\""
  send POST "$API/auth/register" "" "{\"email\":\"$1\",\"password\":\"Clave12345\",\"givenName\":\"$2\",
    \"familyName\":\"Prueba\",\"rol\":\"$3\",\"locale\":$locale,\"phone\":$phone,\"acceptedTerms\":true}" | jq -r '.id'
}
admin_token() { login "admin@$REGION.jornal.test" "$ADMIN_PASSWORD"; }

# Trabajador verificado, con categoría de bodega, ubicación base y disponible. Deja WORKER, WORKER_ID, CATEGORY.
new_worker() {
  local stamp="$1" base_lat="${2:-4.6700}"
  local admin; admin=$(admin_token)
  WORKER_EMAIL="worker-$stamp@example.test"
  WORKER_ID=$(register "$WORKER_EMAIL" "Luis" WORKER)
  send PATCH "$API/admin/users/$WORKER_ID/verification" "$admin" '{"state":"VERIFIED"}' > /dev/null
  WORKER=$(login "$WORKER_EMAIL" Clave12345)
  CATEGORY=$(curl -sf "$API/categories?q=warehouse" -H "Authorization: Bearer $WORKER" | jq -r '.[0].id')
  send PUT "$API/workers/me" "$WORKER" "{\"bio\":\"Bodega y cargue\",\"workRadiusKm\":15,
    \"categoryIds\":[\"$CATEGORY\"],\"baseLatitude\":$base_lat,\"baseLongitude\":$LON}" > /dev/null
  send PATCH "$API/workers/me/availability" "$WORKER" '{"available":true}' > /dev/null
}

# Cliente registrado. Deja CLIENT y CLIENT_EMAIL.
new_client() {
  CLIENT_EMAIL="client-$1@example.test"
  register "$CLIENT_EMAIL" "Ana" APPLICANT > /dev/null
  CLIENT=$(login "$CLIENT_EMAIL" Clave12345)
}

# create_request <token> <idempotency-key> [categoría]  -> imprime el id del pedido
create_request() {
  local token="$1" key="$2" category="${3:-$CATEGORY}"
  local day; day=$(date -d '+2 days' +%Y-%m-%d 2>/dev/null || date -v+2d +%Y-%m-%d)
  printf '%s' "{\"categoryId\":\"$category\",\"units\":2,\"scheduledLocal\":\"${day}T08:00\",
    \"timezone\":\"America/Bogota\",\"address\":{\"street_address\":\"Calle 26 # 68-35\",\"city\":\"Bogotá\",
    \"department\":\"Cundinamarca\"},\"latitude\":$LAT,\"longitude\":$LON,\"paymentMethod\":\"CARD\",\"tipMinor\":5000}" \
    | curl -sf -X POST "$API/requests" -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
      -H "Idempotency-Key: $key" --data-binary @- | jq -r '.id'
}

status_of() { curl -sf "$API/requests/$1" -H "Authorization: Bearer ${2:-$CLIENT}" | jq -r '.status'; }

# wait_status <pedido> <estado> [segundos]
wait_status() {
  local seconds="${3:-40}"
  for _ in $(seq 1 "$seconds"); do
    [ "$(status_of "$1")" = "$2" ] && { ok "pedido en $2"; return 0; }
    sleep 1
  done
  fail "el pedido sigue en $(status_of "$1") (se esperaba $2)"
}

# Espera la oferta del trabajador y la acepta.
accept_offer() {
  local offer=""
  for _ in $(seq 1 30); do
    offer=$(curl -sf "$DISPATCH/offers/me" -H "Authorization: Bearer $WORKER" | jq -r '.[0].offerId // empty')
    [ -n "$offer" ] && break; sleep 1
  done
  [ -n "$offer" ] || fail "no llegó la oferta"
  send POST "$DISPATCH/offers/$offer/accept" "$WORKER" > /dev/null
}

# Recorre el pedido asignado hasta la confirmación del cliente.
finish_and_confirm() {
  for s in en-route start finish; do send POST "$API/requests/$1/$s" "$WORKER" > /dev/null; done
  send POST "$API/requests/$1/confirm" "$CLIENT" > /dev/null
}

# psql <base> <sql>  (consulta en el PostgreSQL de Docker)
psql_q() { $COMPOSE exec -T postgres psql -U "${POSTGRES_USER:-jornalAdmin}" -d "$1" -tAc "$2" | tr -d '\r'; }

# Evidencia: evidence_start <nombre> <descripción>; luego evidence "<texto>"; evidence_end <resultado>.
EVIDENCE_DIR="${EVIDENCE_DIR:-docs/evidencias}"
evidence_start() {
  mkdir -p "$EVIDENCE_DIR"
  EVIDENCE_FILE="$EVIDENCE_DIR/$1.md"
  {
    echo "# $2"
    echo
    echo "- Fecha: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "- Versión: $(git rev-parse --short HEAD 2>/dev/null || echo desconocida)"
    echo "- Script: \`scripts/$1.sh\`"
    echo
    echo '```text'
  } > "$EVIDENCE_FILE"
}
evidence() { printf '%s\n' "$*" | tee -a "$EVIDENCE_FILE"; }
evidence_end() {
  { echo '```'; echo; echo "**Resultado:** $*"; } >> "$EVIDENCE_FILE"
  printf '\n   Evidencia: %s\n' "$EVIDENCE_FILE"
}
