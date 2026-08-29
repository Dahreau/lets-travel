#!/bin/sh
set -e

# Cree des voyages de demo varies via les vraies APIs pour peupler recherche Elasticsearch et
# recommandations Neo4j ; cible le vrai schema lets-travel (contrairement a l'ancien seed-demo-data.sh, perime).

BASE_URL="${BASE_URL:-https://localhost:8443}"
MANAGER_USERNAME="${MANAGER_USERNAME:-}"
MANAGER_PASSWORD="${MANAGER_PASSWORD:-}"

if [ -z "$MANAGER_USERNAME" ] || [ -z "$MANAGER_PASSWORD" ]; then
    FIXTURE="$(dirname "$0")/../e2e/.fixtures/run.json"
    if [ -f "$FIXTURE" ]; then
        MANAGER_USERNAME=$(jq -r '.managerUsername' "$FIXTURE")
        MANAGER_PASSWORD=$(jq -r '.managerPassword' "$FIXTURE")
        echo "Manager repris depuis e2e/.fixtures/run.json : $MANAGER_USERNAME"
    fi
fi

if [ -z "$MANAGER_USERNAME" ] || [ -z "$MANAGER_PASSWORD" ]; then
    echo "Aucun manager disponible : lance d'abord 'npx playwright test' (depuis e2e/) une fois," >&2
    echo "ou passe MANAGER_USERNAME/MANAGER_PASSWORD toi-meme en variables d'env." >&2
    exit 1
fi

echo "Connexion en tant que '${MANAGER_USERNAME}'..."
TOKEN=$(curl -sk -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${MANAGER_USERNAME}\",\"password\":\"${MANAGER_PASSWORD}\"}" | jq -r '.token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
    echo "Echec de connexion manager — verifie MANAGER_USERNAME/MANAGER_PASSWORD et que la stack est up." >&2
    exit 1
fi

SUFFIX=$(date +%s)

create_travel() {
    label="$1"
    body="$2"
    id=$(curl -sk -X POST "$BASE_URL/api/travels" \
        -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
        -d "$body" | jq -r '.id // empty')
    if [ -z "$id" ]; then
        echo "  ECHEC : $label" >&2
    else
        echo "  OK : $label ($id)"
    fi
}

echo "Creation de voyages varies (pays/prix/duree differents pour tester les recommandations)..."

# BUDGET (<500) + SHORT (<=3j) - Espagne
create_travel "Weekend a Barcelone" '{
  "title": "[Demo] Weekend a Barcelone '"$SUFFIX"'", "startDate": "2026-11-06", "endDate": "2026-11-08",
  "status": "CONFIRMED", "price": 320, "currency": "EUR",
  "destinations": [{"city": "Barcelone", "country": "Espagne", "arrivalDate": "2026-11-06", "departureDate": "2026-11-08", "orderIndex": 0, "activities": [], "accommodation": null}],
  "transportations": []
}'

# STANDARD (500-1500) + MEDIUM (4-7j) - Italie
create_travel "Semaine a Rome" '{
  "title": "[Demo] Semaine a Rome '"$SUFFIX"'", "startDate": "2026-11-10", "endDate": "2026-11-16",
  "status": "CONFIRMED", "price": 890, "currency": "EUR",
  "destinations": [{"city": "Rome", "country": "Italie", "arrivalDate": "2026-11-10", "departureDate": "2026-11-16", "orderIndex": 0, "activities": [], "accommodation": null}],
  "transportations": []
}'

# PREMIUM (>=1500) + LONG (>7j) - Japon
create_travel "Circuit au Japon" '{
  "title": "[Demo] Circuit au Japon '"$SUFFIX"'", "startDate": "2026-12-01", "endDate": "2026-12-15",
  "status": "CONFIRMED", "price": 2450, "currency": "EUR",
  "destinations": [
    {"city": "Tokyo", "country": "Japon", "arrivalDate": "2026-12-01", "departureDate": "2026-12-08", "orderIndex": 0, "activities": [], "accommodation": null},
    {"city": "Kyoto", "country": "Japon", "arrivalDate": "2026-12-08", "departureDate": "2026-12-15", "orderIndex": 1, "activities": [], "accommodation": null}
  ],
  "transportations": []
}'

# BUDGET (<500) + MEDIUM (4-7j) - meme pays que Barcelone (Espagne) pour voir la reco jouer sur "country"
create_travel "Andalousie pas chere" '{
  "title": "[Demo] Road trip en Andalousie '"$SUFFIX"'", "startDate": "2026-11-20", "endDate": "2026-11-25",
  "status": "CONFIRMED", "price": 410, "currency": "EUR",
  "destinations": [{"city": "Seville", "country": "Espagne", "arrivalDate": "2026-11-20", "departureDate": "2026-11-25", "orderIndex": 0, "activities": [], "accommodation": null}],
  "transportations": []
}'

# PREMIUM (>=1500) + SHORT (<=3j) - Etats-Unis
create_travel "Weekend a New York" '{
  "title": "[Demo] Weekend a New York '"$SUFFIX"'", "startDate": "2026-12-19", "endDate": "2026-12-21",
  "status": "CONFIRMED", "price": 1750, "currency": "EUR",
  "destinations": [{"city": "New York", "country": "Etats-Unis", "arrivalDate": "2026-12-19", "departureDate": "2026-12-21", "orderIndex": 0, "activities": [], "accommodation": null}],
  "transportations": []
}'

# Voyage DEJA TERMINE (pour pouvoir tester le feedback immediatement, sans attendre) - Portugal
create_travel "Lisbonne (deja termine, pour tester le feedback)" '{
  "title": "[Demo] Lisbonne - voyage termine '"$SUFFIX"'", "startDate": "2026-01-05", "endDate": "2026-01-10",
  "status": "CONFIRMED", "price": 380, "currency": "EUR",
  "destinations": [{"city": "Lisbonne", "country": "Portugal", "arrivalDate": "2026-01-05", "departureDate": "2026-01-10", "orderIndex": 0, "activities": [], "accommodation": null}],
  "transportations": []
}'

# Voyage qui part dans moins de 3 jours (pour tester le refus d'annulation / cutoff)
create_travel "Depart imminent (pour tester le cutoff 3 jours)" '{
  "title": "[Demo] Depart imminent - cutoff '"$SUFFIX"'", "startDate": "'"$(date -d '+2 days' +%F 2>/dev/null || date -v+2d +%F)"'", "endDate": "'"$(date -d '+5 days' +%F 2>/dev/null || date -v+5d +%F)"'",
  "status": "CONFIRMED", "price": 199, "currency": "EUR",
  "destinations": [{"city": "Bruxelles", "country": "Belgique", "arrivalDate": "'"$(date -d '+2 days' +%F 2>/dev/null || date -v+2d +%F)"'", "departureDate": "'"$(date -d '+5 days' +%F 2>/dev/null || date -v+5d +%F)"'", "orderIndex": 0, "activities": [], "accommodation": null}],
  "transportations": []
}'

echo "Termine : 7 voyages de demo crees pour ${MANAGER_USERNAME}."
