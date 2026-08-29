#!/usr/bin/env bash
set -euo pipefail

# Verification "haute charge" minimale via `ab` - supersede par la vraie suite k6
# (voir k6/lets-travel-load-test.js, docs/12-e2e-et-k6.md) - garde en filet de secours.

BASE_URL="${BASE_URL:-http://localhost:8080}"
USERNAME="${LOAD_TEST_USERNAME:?Set LOAD_TEST_USERNAME to an existing local account}"
PASSWORD="${LOAD_TEST_PASSWORD:?Set LOAD_TEST_PASSWORD to that account's password}"
REQUESTS="${REQUESTS:-50}"
CONCURRENCY="${CONCURRENCY:-50}"

if ! command -v ab >/dev/null 2>&1; then
  echo "ab (apache2-utils) introuvable - installer avec: sudo apt install apache2-utils" >&2
  exit 1
fi

echo "Login sur ${BASE_URL}/api/auth/login..."
TOKEN=$(curl -sf -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}" \
  | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")

if [ -z "${TOKEN}" ]; then
  echo "Login echoue - verifie LOAD_TEST_USERNAME/LOAD_TEST_PASSWORD." >&2
  exit 1
fi

echo "Login OK. ${REQUESTS} requetes / ${CONCURRENCY} concurrentes sur GET /api/travels/search?q=paris..."
ab -n "${REQUESTS}" -c "${CONCURRENCY}" \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BASE_URL}/api/travels/search?q=paris"
