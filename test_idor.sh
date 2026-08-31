#!/bin/bash
# acceptedPrivacyPolicy:true est obligatoire sur /api/users/register (400 sinon).
set +H
BASE=https://localhost:8443
SUFFIX=$(date +%s)

echo ">>> register traveler A"
TRAVELER_A=$(curl -sk -X POST $BASE/api/users/register -H "Content-Type: application/json" -d "{\"firstName\":\"Alice\",\"lastName\":\"TravelerA\",\"email\":\"alice.$SUFFIX@example.com\",\"acceptedPrivacyPolicy\":true}")
echo "$TRAVELER_A"
UID_A=$(echo "$TRAVELER_A" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')

echo ">>> register auth account for traveler A"
REG_TOKEN_A=$(echo "$TRAVELER_A" | sed -n 's/.*"registrationToken":"\([^"]*\)".*/\1/p')
TOKEN_A_RAW=$(curl -sk -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d "{\"username\":\"alice_$SUFFIX\",\"password\":\"Password1234\",\"registrationToken\":\"$REG_TOKEN_A\"}")
echo "$TOKEN_A_RAW"
TOKEN_A=$(echo "$TOKEN_A_RAW" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

echo ">>> register traveler B"
TRAVELER_B=$(curl -sk -X POST $BASE/api/users/register -H "Content-Type: application/json" -d "{\"firstName\":\"Bob\",\"lastName\":\"TravelerB\",\"email\":\"bob.$SUFFIX@example.com\",\"acceptedPrivacyPolicy\":true}")
echo "$TRAVELER_B"
UID_B=$(echo "$TRAVELER_B" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')

echo ">>> register auth account for traveler B"
REG_TOKEN_B=$(echo "$TRAVELER_B" | sed -n 's/.*"registrationToken":"\([^"]*\)".*/\1/p')
TOKEN_B_RAW=$(curl -sk -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d "{\"username\":\"bob_$SUFFIX\",\"password\":\"Password1234\",\"registrationToken\":\"$REG_TOKEN_B\"}")
echo "$TOKEN_B_RAW"
TOKEN_B=$(echo "$TOKEN_B_RAW" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

echo ">>> register manager profile"
MANAGER_PROFILE=$(curl -sk -X POST $BASE/api/users/register -H "Content-Type: application/json" -d "{\"firstName\":\"Mia\",\"lastName\":\"Manager\",\"email\":\"mia.$SUFFIX@example.com\",\"acceptedPrivacyPolicy\":true}")
echo "$MANAGER_PROFILE"
UID_MGR=$(echo "$MANAGER_PROFILE" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')

echo ">>> admin login"
ADMIN_LOGIN_RAW=$(curl -sk -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"changeme_dev_only"}')
echo "$ADMIN_LOGIN_RAW"
ADMIN_TOKEN=$(echo "$ADMIN_LOGIN_RAW" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
echo "ADMIN_TOKEN=[$ADMIN_TOKEN]"

echo ">>> create manager account"
CREATE_ACCOUNT_RAW=$(curl -sk -w "\nHTTP_CODE:%{http_code}\n" -X POST $BASE/api/auth/accounts -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"username\":\"mia_manager_$SUFFIX\",\"password\":\"Password1234\",\"role\":\"TRAVEL_MANAGER\",\"userId\":\"$UID_MGR\"}")
echo "$CREATE_ACCOUNT_RAW"

echo ">>> manager login"
MANAGER_LOGIN_RAW=$(curl -sk -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"mia_manager_$SUFFIX\",\"password\":\"Password1234\"}")
echo "$MANAGER_LOGIN_RAW"
MANAGER_TOKEN=$(echo "$MANAGER_LOGIN_RAW" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
echo "MANAGER_TOKEN=[$MANAGER_TOKEN]"

echo ">>> create travel"
TRAVEL=$(curl -sk -w "\nHTTP_CODE:%{http_code}\n" -X POST $BASE/api/travels -H "Content-Type: application/json" -H "Authorization: Bearer $MANAGER_TOKEN" -d '{"title":"Test Trip","startDate":"2026-09-01","endDate":"2026-09-10","status":"PLANNED","price":100,"currency":"EUR","destinations":[{"city":"Paris","country":"France","arrivalDate":"2026-09-01","departureDate":"2026-09-10","orderIndex":0}]}')
echo "$TRAVEL"
TRAVEL_ID=$(echo "$TRAVEL" | grep -o '"id":"[^"]*"' | head -n1 | cut -d'"' -f4)

echo ">>> subscribe traveler A"
curl -sk -w "\nHTTP_CODE:%{http_code}\n" -X POST $BASE/api/travels/$TRAVEL_ID/subscriptions -H "Authorization: Bearer $TOKEN_A"

echo "=== TEST 1 : traveler ABONNE, attendu 200 ==="
curl -sk -o /dev/null -w "%{http_code}\n" $BASE/api/users/$UID_A -H "Authorization: Bearer $MANAGER_TOKEN"

echo "=== TEST 2 : traveler NON ABONNE, attendu 403 ==="
curl -sk -o /dev/null -w "%{http_code}\n" $BASE/api/users/$UID_B -H "Authorization: Bearer $MANAGER_TOKEN"
