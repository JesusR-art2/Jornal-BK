#!/usr/bin/env bash
# Prueba obligatoria de localización (sección 8, F11): el pedido D4 (2 jornadas de bodega + desplazamiento +
# propina) en los 5 países con el reloj fijo a las 20:30 locales del 15/11/2026.
#
# La hace una prueba automática del core (LocalizationReportTest) con los perfiles reales de la base, el cálculo
# de precios, los validadores y los formatos CLDR. Fijar la hora en una prueba no rompe la sesión de nadie (el
# token también depende del reloj). Deja docs/evidencias/prueba-localizacion.md y .csv.
#
# Requisitos: Docker (Testcontainers levanta PostgreSQL, Redis y RabbitMQ) y Java 21. No necesita el sistema arriba.
# Uso:        scripts/prueba-localizacion.sh
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(git rev-parse --short HEAD 2>/dev/null || echo desconocida)
echo "== Prueba de localización (versión $VERSION)"
./mvnw -q -pl jornal-core -am install -DskipTests > /dev/null
./mvnw -q -pl jornal-core test -Dtest=LocalizationReportTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Djornal.version="$VERSION" -Djornal.evidence.dir="$(pwd)/docs/evidencias" > /dev/null \
  || { echo "   FALLO: revisa jornal-core/target/surefire-reports/com.jornal.LocalizationReportTest.txt"; exit 1; }

echo "   OK: los 5 países pasan"
sed -n '/^## Resumen/,/^Colombia/p' docs/evidencias/prueba-localizacion.md
echo "   Evidencia: docs/evidencias/prueba-localizacion.md y docs/evidencias/prueba-localizacion.csv"
