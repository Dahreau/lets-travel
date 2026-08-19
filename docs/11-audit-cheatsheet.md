# Cheat-sheet oral — commandes seules

[← Sommaire](00-getting-started.md)

Juste les commandes à copier-coller pendant l'oral, dans l'ordre du guide complet (`10-audit-demo-guide.md`, qui garde toutes les explications). Rien à lire en direct, juste à exécuter.

## Prérequis

```bash
TOKEN=$(curl -k -s -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"$(grep -m1 '^DEFAULT_ADMIN_PASSWORD=' .env | cut -d= -f2-)\"}" \
  | jq -r .token)
```

`./scripts/seed-demo-data.sh` — à lancer AVANT l'oral, pas pendant.

## Comprehension

**Tracing cross-service :**
```bash
curl -k -s https://localhost/api/travels -H "Authorization: Bearer $TOKEN" > /dev/null
sleep 2
docker compose exec zipkin wget -qO- "http://localhost:9411/api/v2/traces?serviceName=travel-service&limit=1" \
  | jq -r '.[0] | sort_by(.timestamp)[] | "\(.traceId[0:8])  \(.localEndpoint.serviceName)  \(.name)"'
```
→ même `traceId`, `service` qui passe de `api-gateway` à `travel-service`.

**Playbook Ansible :**
```bash
cat -n ansible/playbooks/deploy.yml
```

**Sécurité :**
```bash
grep -rn "TLS\|SSL\|sslmode\|bolt+ssc" docker-compose.yml
cat infra/vault/policies/user-service-policy.hcl
grep -r "hasRole" backend/*/src/main/java --include=SecurityConfig.java
```

**Schéma DB :**
```bash
cat backend/travel-service/src/main/resources/db/migration/V1__create_travel_tables.sql
cat backend/payment-service/src/main/resources/db/migration/V1__create_payment_tables.sql
docker compose exec neo4j cypher-shell -a bolt+ssc://localhost:7687 -u neo4j -p "$(grep ^NEO4J_PASSWORD= .env | cut -d= -f2-)" \
  "MATCH (a:Place)-[r:ROUTE_TO]->(b:Place) RETURN a.city, b.city, r.tripCount;"
```

**CI/CD :** http://localhost:8090 (Jenkins) · http://localhost:9000 (SonarQube)

## Functional

**Ansible :**
```bash
cd ansible
ansible-playbook -i inventory.ini playbooks/site.yml --ask-become-pass \
  -e project_dir="$(pwd)/../infra/ci/deploy-workspace"
```

**Docker/Ansible OK :**
```bash
docker compose ps
```

**API admin-only :**
```bash
curl -k -s -o /dev/null -w "%{http_code}\n" https://localhost/api/travels
curl -k -s -o /dev/null -w "%{http_code}\n" https://localhost/api/travels -H "Authorization: Bearer $TOKEN"
```
→ `401` puis `200`.

<details>
<summary>CRUD users</summary>

```bash
cat > /tmp/user.json <<'EOF'
{
  "firstName": "Jean", "lastName": "Dupont", "email": "jean.dupont@example.com",
  "phone": "+33612345678", "role": "TRAVELER",
  "address": {"street": "12 rue de Paris", "city": "Lyon", "postalCode": "69000", "country": "France"}
}
EOF

USER_ID=$(curl -k -s -X POST https://localhost/api/users -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" --data @/tmp/user.json | jq -r .id)

curl -k -s -w "\nHTTP: %{http_code}\n" https://localhost/api/users/$USER_ID -H "Authorization: Bearer $TOKEN"
curl -k -s -w "\nHTTP: %{http_code}\n" -X PUT https://localhost/api/users/$USER_ID -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"firstName":"Jean","lastName":"Dupont-Martin","email":"jean.dupont@example.com","role":"TRAVELER"}'
curl -k -s -w "\nHTTP: %{http_code}\n" -X DELETE https://localhost/api/users/$USER_ID -H "Authorization: Bearer $TOKEN"
curl -k -s -w "\nHTTP: %{http_code}\n" https://localhost/api/users/$USER_ID -H "Authorization: Bearer $TOKEN"

curl -k -s -w "\nHTTP: %{http_code}\n" -X POST https://localhost/api/users -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"firstName":"Test","lastName":"NoEmail","role":"TRAVELER"}'
```
</details>

<details>
<summary>CRUD travels (2 destinations)</summary>

```bash
cat > /tmp/travel.json <<'EOF'
{
  "title": "Test 2 destinations", "ownerId": "11111111-1111-1111-1111-111111111111",
  "startDate": "2026-09-01", "endDate": "2026-09-10", "status": "PLANNED",
  "destinations": [
    {"city": "Lisbon", "country": "Portugal", "arrivalDate": "2026-09-01", "departureDate": "2026-09-05", "orderIndex": 0, "activities": [], "accommodation": null},
    {"city": "Porto", "country": "Portugal", "arrivalDate": "2026-09-05", "departureDate": "2026-09-10", "orderIndex": 1, "activities": [], "accommodation": null}
  ],
  "transportations": []
}
EOF

TRAVEL_ID=$(curl -k -s -X POST https://localhost/api/travels -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" --data @/tmp/travel.json | jq -r .id)

curl -k -s -w "\nHTTP: %{http_code}\n" https://localhost/api/travels/$TRAVEL_ID -H "Authorization: Bearer $TOKEN"

docker compose exec neo4j cypher-shell -a bolt+ssc://localhost:7687 -u neo4j -p "$(grep ^NEO4J_PASSWORD= .env | cut -d= -f2-)" \
  "MATCH (a:Place)-[r:ROUTE_TO]->(b:Place) RETURN a.city, b.city, r.tripCount;"
```
</details>

<details>
<summary>CRUD payment-methods + payments</summary>

```bash
cat > /tmp/pm.json <<'EOF'
{"ownerId": "11111111-1111-1111-1111-111111111111", "provider": "STRIPE", "type": "CARD",
 "providerToken": "pm_card_visa", "brand": "Visa", "last4": "4242", "isDefault": true}
EOF

PM_ID=$(curl -k -s -X POST https://localhost/api/payment-methods -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" --data @/tmp/pm.json | jq -r .id)

cat > /tmp/payment.json <<EOF
{"travelId": "$TRAVEL_ID", "ownerId": "11111111-1111-1111-1111-111111111111",
 "paymentMethodId": "$PM_ID", "amount": 150.00, "currency": "eur"}
EOF

PAYMENT_ID=$(curl -k -s -X POST https://localhost/api/payments -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" --data @/tmp/payment.json | jq -r .id)

curl -k -s -w "\nHTTP: %{http_code}\n" -X DELETE https://localhost/api/payment-methods/$PM_ID -H "Authorization: Bearer $TOKEN"
curl -k -s https://localhost/api/payments/$PAYMENT_ID -H "Authorization: Bearer $TOKEN"
```
</details>

**Erreurs :**
```bash
curl -k -s -w "\nHTTP: %{http_code}\n" https://localhost/api/payment-methods/00000000-0000-0000-0000-000000000000 -H "Authorization: Bearer $TOKEN"
curl -k -s -w "\nHTTP: %{http_code}\n" -X POST https://localhost/api/users -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"role":"SUPERADMIN"}'
```

**Auth/RBAC :**
```bash
curl -k -s -o /dev/null -w "%{http_code}\n" -X POST https://localhost/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"wrong"}'
curl -k -s -o /dev/null -w "%{http_code}\n" https://localhost/api/travels
```

**Load balancing :**
```bash
for i in $(seq 1 30); do
  curl -k -s -o /dev/null -w "%{http_code} " https://localhost/api/travels -H "Authorization: Bearer $TOKEN"
done
echo
sleep 3
docker compose exec zipkin wget -qO- "http://localhost:9411/api/v2/traces?serviceName=travel-service&limit=100" \
  | grep -o '"localEndpoint":{"serviceName":"travel-service","ipv4":"[^"]*"' \
  | grep -o '"ipv4":"[^"]*"' | sort | uniq -c
```

**Failover :**
```bash
docker stop travel-plan-app-travel-service-2
for i in $(seq 1 10); do
  curl -k -s -o /dev/null -w "%{http_code} " https://localhost/api/travels -H "Authorization: Bearer $TOKEN"
done
echo
docker start travel-plan-app-travel-service-2
```

**Code review :**
```bash
git log --oneline -20
```
