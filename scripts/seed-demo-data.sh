#!/bin/sh
set -e

# Peuple la stack avec des utilisateurs, voyages et moyens de paiement de demo,
# via les vraies APIs (pas d'insertion SQL/Cypher directe) pour que la
# synchronisation Neo4j (TravelGraphSyncService) et les validations metier
# s'appliquent comme en conditions reelles. A lancer une fois la stack up
# (./scripts/start-app.sh ou ansible-playbook site.yml termine).
#
# Necessite curl et jq. A relancer seulement sur une base vide : les emails
# sont fixes, un deuxieme lancement echouera sur des doublons (attendu, ce
# n'est pas un script idempotent comme les playbooks Ansible).

cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-https://localhost}"
ADMIN_USERNAME="${DEFAULT_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${DEFAULT_ADMIN_PASSWORD:-}"

if [ -z "$ADMIN_PASSWORD" ] && [ -f .env ]; then
    ADMIN_PASSWORD=$(grep -m1 '^DEFAULT_ADMIN_PASSWORD=' .env | cut -d= -f2-)
fi
ADMIN_PASSWORD="${ADMIN_PASSWORD:-changeme_dev_only}"

echo "Connexion en tant que '${ADMIN_USERNAME}'..."
TOKEN=$(curl -sk -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${ADMIN_USERNAME}\",\"password\":\"${ADMIN_PASSWORD}\"}" | jq -r '.token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
    echo "Echec de connexion — verifie DEFAULT_ADMIN_PASSWORD dans .env et que la stack est up." >&2
    exit 1
fi

api() {
    method="$1"
    path="$2"
    body="$3"
    curl -sk -X "$method" "$BASE_URL$path" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        ${body:+-d "$body"}
}

create_user() {
    curl_body="$1"
    api POST /api/users "$curl_body" | jq -r '.id'
}

echo "Creation des utilisateurs de demo..."
USER1=$(create_user '{"firstName":"Alice","lastName":"Martin","email":"alice.martin@example.com","phone":"+33600000001","role":"TRAVELER","address":{"street":"12 rue de la Paix","city":"Paris","postalCode":"75002","country":"France"}}')
USER2=$(create_user '{"firstName":"Bruno","lastName":"Lefevre","email":"bruno.lefevre@example.com","phone":"+33600000002","role":"TRAVELER","address":{"street":"5 avenue Jean Jaures","city":"Lyon","postalCode":"69007","country":"France"}}')
USER3=$(create_user '{"firstName":"Chloe","lastName":"Dupont","email":"chloe.dupont@example.com","phone":"+33600000003","role":"ADMIN","address":{"street":"3 quai des Chartrons","city":"Bordeaux","postalCode":"33000","country":"France"}}')
echo "  -> $USER1 / $USER2 / $USER3"

echo "Creation des voyages de demo..."
api POST /api/travels '{
  "title":"Road trip au Japon","ownerId":"'"$USER1"'","startDate":"2026-09-10","endDate":"2026-09-20","status":"PLANNED",
  "destinations":[
    {"city":"Tokyo","country":"Japon","arrivalDate":"2026-09-10","departureDate":"2026-09-15","orderIndex":1,
     "activities":[{"name":"Shibuya Crossing","description":"Quartier emblematique","date":"2026-09-11","cost":0}],
     "accommodation":{"name":"Shinjuku Granbell Hotel","type":"HOTEL","address":"Shinjuku, Tokyo","checkIn":"2026-09-10","checkOut":"2026-09-15"}},
    {"city":"Kyoto","country":"Japon","arrivalDate":"2026-09-15","departureDate":"2026-09-20","orderIndex":2,
     "activities":[{"name":"Temple Fushimi Inari","description":"Randonnee des torii","date":"2026-09-16","cost":0}],
     "accommodation":{"name":"Kyoto Machiya Inn","type":"APARTMENT","address":"Higashiyama, Kyoto","checkIn":"2026-09-15","checkOut":"2026-09-20"}}
  ],
  "transportations":[
    {"type":"FLIGHT","fromLocation":"Paris CDG","toLocation":"Tokyo Haneda","departureTime":"2026-09-10T08:00:00Z","arrivalTime":"2026-09-11T04:00:00Z","provider":"Air France"},
    {"type":"TRAIN","fromLocation":"Tokyo","toLocation":"Kyoto","departureTime":"2026-09-15T09:00:00Z","arrivalTime":"2026-09-15T11:20:00Z","provider":"Shinkansen"}
  ]}' > /dev/null

api POST /api/travels '{
  "title":"Week-end a Rome","ownerId":"'"$USER2"'","startDate":"2026-10-03","endDate":"2026-10-06","status":"CONFIRMED",
  "destinations":[
    {"city":"Rome","country":"Italie","arrivalDate":"2026-10-03","departureDate":"2026-10-06","orderIndex":1,
     "activities":[{"name":"Colisee","description":"Visite guidee","date":"2026-10-04","cost":25.00}],
     "accommodation":{"name":"Hotel Artemide","type":"HOTEL","address":"Via Nazionale, Rome","checkIn":"2026-10-03","checkOut":"2026-10-06"}}
  ],
  "transportations":[
    {"type":"FLIGHT","fromLocation":"Paris Orly","toLocation":"Rome Fiumicino","departureTime":"2026-10-03T07:30:00Z","arrivalTime":"2026-10-03T09:15:00Z","provider":"Vueling"}
  ]}' > /dev/null

echo "Creation des moyens de paiement de demo..."
api POST /api/payment-methods '{"ownerId":"'"$USER1"'","provider":"STRIPE","type":"CARD","providerToken":"tok_visa_demo","brand":"Visa","last4":"4242","isDefault":true}' > /dev/null
api POST /api/payment-methods '{"ownerId":"'"$USER2"'","provider":"PAYPAL","type":"PAYPAL_ACCOUNT","providerToken":"paypal_demo_token","brand":"PayPal","last4":"","isDefault":true}' > /dev/null

echo "Termine : 3 utilisateurs, 2 voyages, 2 moyens de paiement de demo crees."
