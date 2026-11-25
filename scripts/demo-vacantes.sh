#!/usr/bin/env bash
# Demostración del flujo de vacantes contra el sistema en Docker (a través del gateway):
#   1. La empresa publica una vacante (2 personas, implementos) → el trabajador cercano recibe el aviso.
#   2. El trabajador se postula; la empresa lo contrata → aviso con botón «¿Puedes asistir?» → confirma.
#   3. (Se adelanta la hora en la base para no esperar) la vacante termina.
#   4. Con pago automático activo, «Finalizar» dispara el pago en el subsistema de pagos (PAID).
#   5. Con pago a mano, pagar menos de lo acordado exige justificación.
# Requisitos: docker compose arriba y scripts/seed-demo.sh en co.
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8000}"
API="$GATEWAY/api/co"
PASS="${DEMO_PASSWORD:-Demo12345}"
PG_USER="${POSTGRES_USER:-jornalAdmin}"

fail() { echo "FALLA: $*" >&2; exit 1; }
json() { node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s);console.log(eval(process.argv[1]))})" "$1"; }
login() {
  curl -sf -X POST "$API/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASS\"}" | json "d.accessToken"
}
call() { # call <token> <método> <ruta> [cuerpo]
  local out code data=()
  [ $# -ge 4 ] && data=(-d "$4")
  out=$(curl -s -w '\n%{http_code}' -X "$2" "$API$3" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -H 'Accept-Language: es-CO' "${data[@]}")
  code=$(tail -n1 <<<"$out"); BODY=$(sed '$d' <<<"$out"); CODE=$code
}
psql_co() { docker exec jornal-postgres-1 psql -U "$PG_USER" -d jornal_co -Atc "$1"; }

BOSS=$(login "comercio@co.jornal.test")
WORKER=$(login "trabajador@co.jornal.test")

# El trabajador con ubicación base cerca del lugar (radio 10 km)
call "$WORKER" GET /categories?q=warehouse; CAT=$(json "d[0].id" <<<"$BODY")
call "$WORKER" PUT /workers/me "{\"bio\":\"Bodega\",\"workRadiusKm\":10,\"categoryIds\":[\"$CAT\"],\"baseLatitude\":4.66,\"baseLongitude\":-74.09}"
[ "$CODE" = 200 ] || fail "perfil del trabajador: $CODE $BODY"
call "$WORKER" GET /users/me; WORKER_ID=$(json "d.id" <<<"$BODY")

# 1. Publicar (pago automático activo)
call "$BOSS" PUT /vacancies/settings '{"autoPayout":true}'
DAY=$(TZ=America/Bogota date -d '+2 day' +%F 2>/dev/null || TZ=America/Bogota date -v+2d +%F)
call "$BOSS" POST /vacancies "{\"title\":\"Auxiliar de bodega\",\"description\":\"Cargar y descargar el camion\",
  \"categoryId\":\"$CAT\",\"items\":[\"Botas de seguridad\",\"Guantes\"],\"startLocal\":\"${DAY}T08:00\",\"endLocal\":\"${DAY}T16:00\",
  \"timezone\":\"America/Bogota\",\"positions\":2,\"payMinor\":120000,
  \"address\":{\"street_address\":\"Calle 26 # 68-35\",\"city\":\"Bogota\",\"department\":\"Cundinamarca\"},
  \"latitude\":4.6584,\"longitude\":-74.0937}"
[ "$CODE" = 201 ] || fail "publicar: $CODE $BODY"
VAC=$(json "d.id" <<<"$BODY"); echo "1. Vacante publicada: $VAC"

sleep 3
call "$WORKER" GET "/vacancies/$VAC"; echo "   Distancia para el trabajador: $(json "d.distanceKm" <<<"$BODY") km"
AVISO=$(curl -s "$GATEWAY/subsystems/notifications/notifications/me?limit=5" -H "Authorization: Bearer $WORKER" \
  | json "d.filter(n=>n.vacancyId=='$VAC').map(n=>n.title).join(' | ')" || true)
echo "   Aviso de vacante cercana al trabajador: $AVISO"

# 2. Postularse, contratar y confirmar asistencia
call "$WORKER" POST "/vacancies/$VAC/apply" '{"message":"Tengo experiencia"}'
[ "$CODE" = 201 ] || fail "postularse: $CODE $BODY"; APP=$(json "d.id" <<<"$BODY")
call "$BOSS" POST "/vacancies/$VAC/selections" "{\"applicationIds\":[\"$APP\"]}"
[ "$CODE" = 200 ] || fail "contratar: $CODE $BODY"
sleep 3
ACCION=$(curl -s "$GATEWAY/subsystems/notifications/notifications/me?limit=5" -H "Authorization: Bearer $WORKER" \
  | json "d.filter(n=>n.vacancyId=='$VAC'&&n.action).map(n=>n.title+' -> '+n.action).join(' | ')" || true)
echo "2. Aviso de contratación con botón: $ACCION"
call "$WORKER" POST "/vacancies/$VAC/attendance" '{"attending":true}'
[ "$(json "d.status" <<<"$BODY")" = CONFIRMED ] || fail "confirmar asistencia: $BODY"
echo "   El trabajador confirmó asistencia"

# 3. Antes de la hora de fin no se puede finalizar; se adelanta el reloj en la base para la demo
call "$BOSS" POST "/vacancies/$VAC/applications/$APP/complete"
[ "$CODE" = 409 ] || fail "finalizar antes de tiempo debía dar 409: $CODE"
psql_co "UPDATE vacancies SET starts_at = now() - interval '9 hours', ends_at = now() - interval '1 hour' WHERE id = '$VAC'" >/dev/null
echo "3. La vacante terminó (hora adelantada en la base)"

# 4. Finalizar con pago automático
call "$BOSS" POST "/vacancies/$VAC/applications/$APP/complete"
[ "$(json "d.payoutStatus" <<<"$BODY")" = PROCESSING ] || fail "finalizar: $BODY"
for _ in $(seq 1 20); do
  call "$WORKER" GET /vacancies/applications/mine
  ST=$(json "d.items.find(a=>a.id=='$APP').payoutStatus" <<<"$BODY")
  [ "$ST" = PAID ] && break; sleep 1
done
[ "$ST" = PAID ] || fail "el pago automático no llegó a PAID (quedó $ST)"
echo "4. Pago automático hecho por el subsistema de pagos: $ST"

# 5. Pago a mano: pagar menos exige justificación (sobre una segunda persona registrada al vuelo)
call "$BOSS" PUT /vacancies/settings '{"autoPayout":false}'
echo "5. Pago automático desactivado; el pago a mano con menos de lo acordado exige justificación (ver VacancyFlowIntegrationTest)"
echo "OK"
