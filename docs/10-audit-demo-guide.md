# Guide de démo pour l'oral d'audit

[← Sommaire](00-getting-started.md)

Une entrée par question **littérale** de `travel-plan_audit.md` (même formulation, même découpage) : réponse courte, exemple ou manip à faire, puis un lien "plus de détails" seulement si nécessaire. Toutes les commandes ont été testées en direct sur cette stack, pas des exemples théoriques.

Prérequis pour toute la section Functional : la stack tourne (`docker compose ps` → tout `Healthy`) et un token admin est en variable d'env. La commande ci-dessous va lire le vrai mot de passe directement dans `.env`, aucune édition manuelle nécessaire :

```bash
TOKEN=$(curl -k -s -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"$(grep -m1 '^DEFAULT_ADMIN_PASSWORD=' .env | cut -d= -f2-)\"}" \
  | jq -r .token)
```

Pour ne pas avoir à taper les exemples CRUD ci-dessous en direct pendant l'oral, `./scripts/seed-demo-data.sh` peuple la stack en une commande (3 users, 2 travels avec destinations/activités/hébergement/transport, 2 payment methods) via les vraies APIs — donc Neo4j est aussi peuplé au passage. À lancer une fois avant l'audit, pas pendant (pas idempotent : un deuxième lancement échoue sur des emails déjà pris).

---

## Comprehension

### "Ask the student to describe the microservices architecture implemented."

| Question | Réponse courte | Exemple / où le montrer |
|---|---|---|
| Have you clearly defined the boundaries for each microservice based on business domains? | Oui : 4 domaines (auth, user, travel, payment), chacun avec sa propre DB Postgres et son propre repo Maven. Aucun appel direct DB-à-DB entre services. | `docker-compose.yml` — 4 `build:` distincts, 4 bases (`auth_db`, `user_db`, `travel_db`, `payment_db`) |
| Do your microservices align directly with specific business functions? | Oui, un service = une responsabilité métier (identité, profils, voyages, paiements) | idem |
| Are your microservices designed to operate independently of one another? | Oui — communication uniquement via HTTP à travers l'API Gateway, jamais de partage de DB | `RouteConfig.java` (api-gateway) |
| Can each microservice be deployed, updated, and scaled without affecting others? | Oui | `docker compose build travel-service && docker compose up -d travel-service` ne touche à rien d'autre |
| Is your architecture designed to support the independent scalability of each microservice? | Oui, `deploy.replicas` réglable par service | `docker-compose.yml` : `deploy.replicas: 2` sur auth/user/travel/payment/api-gateway |
| Does your system maintain functionality even when one or more services fail? | Oui pour la panne d'un replica (l'autre absorbe le trafic) — pas de circuit breaker inter-services au-delà | Démo failover, voir section Functional "Simulate load" |
| Is there an API Gateway in your architecture to manage incoming requests? | Oui, point d'entrée unique, JWT vérifié une seule fois | `backend/api-gateway/.../gateway/RouteConfig.java` |
| Can you track and trace a request across multiple services easily? | Oui, Zipkin — chaque span porte le même `traceId` à travers gateway → service | voir commande formatée ci-dessous |

Le JSON brut de l'API Zipkin est illisible tel quel à l'oral. Fais une requête précise juste avant d'interroger Zipkin (`limit=1` = la trace la plus récente — si tu n'as rien demandé juste avant, tu retombes sur une trace au hasard, ou rien du tout) :

```bash
curl -k -s https://localhost/api/travels -H "Authorization: Bearer $TOKEN" > /dev/null
sleep 2   # laisse le temps au span de remonter à Zipkin avant de l'interroger
docker compose exec zipkin wget -qO- "http://localhost:9411/api/v2/traces?serviceName=travel-service&limit=1" \
  | jq -r '.[0] | sort_by(.timestamp)[] | "\(.traceId[0:8])  \(.localEndpoint.serviceName)  \(.name)"'
```

Sortie réelle (testée) — même préfixe de `traceId` sur toutes les lignes, mais le `service` change :

```
6a841533  api-gateway     http get /api/travels/**
6a841533  api-gateway     http get
6a841533  travel-service  http get /api/travels
6a841533  travel-service  security filterchain before
6a841533  travel-service  authorize request
6a841533  travel-service  secured request
6a841533  travel-service  security filterchain after
```

À dire à l'oral : "je fais un `GET /api/travels` — il passe par `api-gateway` qui vérifie le JWT, puis est routé vers `travel-service`. Dans Zipkin, les deux services apparaissent sur le **même** `traceId` (`6a841533` ici) : c'est la preuve que la requête est tracée de bout en bout, pas juste que Zipkin tourne dans un coin."

### "Ask the student to explain one of the Ansible playbook"

**Did he/she clearly explain all the Ansible playbook?** — Pas une question à réponse écrite : il faut commenter à l'oral, ligne par ligne, un des playbooks. Ouvre-le en gardant les numéros de ligne sous les yeux :

```bash
cat -n ansible/playbooks/deploy.yml
```

`deploy.yml` est le plus complet à présenter — 3 mécanismes d'idempotence à pointer explicitement :
- `creates: "{{ project_dir }}/infra/vault/certs/vault.crt"` (génération de certs TLS) : la tâche saute si le fichier existe déjà, donc relancer le playbook ne régénère pas un certificat déjà en place.
- `changed_when: "'Removing' in vault_init_rm.stdout"` : Ansible ne rapporte "changed" que si quelque chose a vraiment été supprimé, pas juste parce que la commande a tourné.
- `docker compose up -d --build` : Compose lui-même ne recrée que les conteneurs dont la config a changé — relancer la commande sur une stack déjà à jour ne casse rien.

Chaque décision non-évidente du fichier est déjà commentée en français dans le YAML lui-même — les relire avant l'oral vaut mieux que les réciter de mémoire.

### "Discuss the CI/CD pipeline setup."

| Question | Réponse courte | Exemple |
|---|---|---|
| Are there unit tests for each functionality and are the tests running for each new PR? | Oui, tests présents pour les 5 services (controller/service/repository/security/provider) ; `Jenkinsfile` lance `mvn clean verify` + `ng test` par service dans le stage `Build & Test` | `Jenkinsfile`, stage `Build & Test`, ou http://localhost:8090 (Jenkins, port `8090` — voir `infra/ci/docker-compose.yml`) |
| Is the SonarQube report free from any error or warning that can break the CI/CD Process? | Le pipeline bloque si le Quality Gate est rouge (`-Dsonar.qualitygate.wait=true`) ; version du plugin épinglée pour éviter le warning "unspecified plugin version" | http://localhost:9000 (SonarQube), montrer un Quality Gate vert récent |

### "Detail the security measures implemented."

**Were comprehensive security measures like SSL/TLS, secret management, and the principle of least privilege correctly implemented?**

Court : oui, sur les 3 axes.

**TLS = chaque connexion entre deux points est chiffrée**, donc illisible pour qui intercepte le trafic réseau (même WiFi, même réseau Docker interne). Sans TLS, quelqu'un qui intercepte le trafic verrait le JSON en clair — mot de passe de login compris. Ici, ce n'est pas juste le navigateur → serveur qui est protégé, chaque hop interne l'est aussi : client→nginx, nginx→api-gateway (certificat vérifié), api-gateway→les 4 services, services→Postgres (`sslmode=require`), travel-service→Neo4j (`bolt+ssc://`), tout→Vault. Aucun hop en clair nulle part dans la stack.

```bash
grep -rn "TLS\|SSL\|sslmode\|bolt+ssc" docker-compose.yml
```

**Secrets = un coffre-fort séparé (HashiCorp Vault) pour les mots de passe/clés API**, rien en dur dans le code. Vault tourne en mode serveur réel (stockage persistant, TLS), pas en mode dev/mémoire. Chaque service doit s'authentifier avec son propre couple `role_id`/`secret_id` (AppRole) pour aller lire — uniquement lire, jamais écrire — ses propres secrets, jamais ceux d'un autre service :

```bash
cat infra/vault/policies/user-service-policy.hcl   # user-service ne peut lire QUE secret/data/user-service/*
```

Le `secret_id` de chaque service est révoqué et régénéré à chaque déploiement (`ansible/playbooks/fetch-vault-secrets.yml`), pas fixé une fois pour toutes.

**Moindre privilège = toutes les routes API exigent d'être Admin**, sauf le login lui-même (forcément public, sinon personne ne peut se connecter) :

```bash
grep -r "hasRole" backend/*/src/main/java --include=SecurityConfig.java
```

Plus de détails → `08-ansible-deploy-tls.md` (TLS + pourquoi Vault ne tourne plus en mode dev).

### "Ask the student to explain the database schema for PostgreSQL and Neo4j."

**Did the data structure in PostgreSQL and Neo4j effectively support the application's requirements?**

**Postgres stocke les données métier "classiques"** : une table par entité (users/addresses, travels/destinations/activities/accommodations/transportations, payments/payment_methods), avec des règles de suppression en cascade pensées au cas par cas — pas un `CASCADE` partout par réflexe :

```bash
cat backend/travel-service/src/main/resources/db/migration/V1__create_travel_tables.sql
cat backend/payment-service/src/main/resources/db/migration/V1__create_payment_tables.sql
```

Deux comportements différents à pointer dans ces fichiers : `destinations`/`activities`/`accommodations`/`transportations` sont en `ON DELETE CASCADE` (supprimer un `Travel` supprime tout ce qui lui appartient) ; `payment_method_id` sur `payments` est en `ON DELETE SET NULL` (supprimer un moyen de paiement garde l'historique des paiements passés, juste sans le rattacher à ce moyen de paiement supprimé — on ne perd jamais une trace financière).

**Neo4j ne stocke pas les voyages** : c'est un graphe séparé de "quelles villes sont reliées entre elles par au moins un voyage" (`PlaceNode`/`ROUTE_TO`), utile pour suggérer des destinations, indépendant de la durée de vie d'un `Travel` précis — une ville reste dans le graphe même si le voyage qui l'a créée est supprimé (décision assumée, voir `audit-findings.md`) :

```bash
docker compose exec neo4j cypher-shell -a bolt+ssc://localhost:7687 -u neo4j -p "$(grep ^NEO4J_PASSWORD= .env | cut -d= -f2-)" \
  "MATCH (a:Place)-[r:ROUTE_TO]->(b:Place) RETURN a.city, b.city, r.tripCount;"
```

Plus de détails → `02-app-infra.md`.

---

## Functional

### "Verify the execution of Ansible playbooks."

```bash
cd ansible
ansible-playbook -i inventory.ini playbooks/site.yml --ask-become-pass \
  -e project_dir="$(pwd)/../infra/ci/deploy-workspace"
```

Deux précisions nécessaires pour que cette commande marche, pas juste "en théorie" :
- `--ask-become-pass` (ou `-K`) demande ton mot de passe sudo au lancement — nécessaire seulement quand tu lances le playbook toi-même. Sur Jenkins ce n'est jamais demandé : le conteneur Jenkins tourne déjà en root (`infra/ci/jenkins/Dockerfile` finit sur `USER root`).
- `-e project_dir=...` cible explicitement `infra/ci/deploy-workspace`, le dossier que Jenkins gère réellement (son propre `.env` et sa propre clé de déscellement Vault, préservés entre builds). Sans ce flag, `project_dir` retombe par défaut sur la racine du repo, qui a son **propre** `.env`/clé Vault — différents de ceux du Vault réellement en train de tourner — et l'étape "Unseal Vault" échoue avec la mauvaise clé.

**Did the Ansible playbooks execute without errors and configure the environment as intended?** → `PLAY RECAP` final : `failed=0` partout.

**Were the playbooks able to handle re-running scenarios without causing disruptions or inconsistencies?** → relancer la même commande immédiatement après : doit aussi finir `failed=0`, sans casser les conteneurs déjà up (les tâches `creates:`/`changed_when` sautent ce qui existe déjà).

### "Verify Docker and Ansible setup."

```bash
docker compose ps
```

**Were Docker containers and Ansible playbooks set up correctly and functionally?** → tout `Up`/`Healthy`, 2 replicas visibles pour auth/user/travel/payment-service et api-gateway.

### "Test each microservice API."

```bash
curl -k -s -o /dev/null -w "%{http_code}\n" https://localhost/api/travels                              # sans token
curl -k -s -o /dev/null -w "%{http_code}\n" https://localhost/api/travels -H "Authorization: Bearer $TOKEN"  # avec token admin
```

**Are all the microservices' APIs only accessible when logged in with an Admin profile?** → le premier `401`/`403`, le second `200`.

### "Admin should be able to perform CRUD operations for users, travelers and payment methods."

<details>
<summary>CRUD users (create/read/update/delete + erreurs)</summary>

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
curl -k -s -w "\nHTTP: %{http_code}\n" https://localhost/api/users/$USER_ID -H "Authorization: Bearer $TOKEN"   # doit être 404

# Erreur de validation (champ manquant, doit lister le champ)
curl -k -s -w "\nHTTP: %{http_code}\n" -X POST https://localhost/api/users -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"firstName":"Test","lastName":"NoEmail","role":"TRAVELER"}'
```
</details>

<details>
<summary>CRUD travels (create/read/update avec 2 destinations, sync Neo4j)</summary>

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

# Preuve que la route est aussi écrite dans Neo4j (pas juste Postgres), et que ce hop est bien en bolt+ssc (TLS)
docker compose exec neo4j cypher-shell -a bolt+ssc://localhost:7687 -u neo4j -p "$(grep ^NEO4J_PASSWORD= .env | cut -d= -f2-)" \
  "MATCH (a:Place)-[r:ROUTE_TO]->(b:Place) RETURN a.city, b.city, r.tripCount;"
```
</details>

<details>
<summary>CRUD payment-methods + payments (Stripe test mode réel, cascade ON DELETE SET NULL)</summary>

`pm_card_visa` : ID de test permanent fourni par Stripe (mode test uniquement, aucune vraie carte, documenté sur [docs.stripe.com/testing](https://docs.stripe.com/testing)). Même valeur à taper en curl ci-dessous ou directement dans le champ **Token Provider** du formulaire "new payment method" du dashboard.

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

# Supprimer le moyen de paiement : le paiement doit survivre (cascade SET NULL, pas de perte de trace financière)
curl -k -s -w "\nHTTP: %{http_code}\n" -X DELETE https://localhost/api/payment-methods/$PM_ID -H "Authorization: Bearer $TOKEN"
curl -k -s https://localhost/api/payments/$PAYMENT_ID -H "Authorization: Bearer $TOKEN"   # paymentMethodId doit être null, le reste intact
```

Le refund (`POST /payments/{id}/refund`) existe et notifie réellement Stripe/PayPal (voir `PaymentService.refund()`), mais nécessite une vraie clé `sk_test_...` dans `.env` pour être démontré en live — non requis par l'audit, à ne mentionner que si demandé. PayPal fonctionne différemment de Stripe côté token de test : pas d'équivalent statique à `pm_card_visa`, il faut créer un ordre puis le faire approuver par un compte sandbox buyer via OAuth PayPal, plus long à dérouler en direct. Pour l'oral : mentionner que l'intégration existe et est testée (`PayPalPaymentProvider.java`, `PayPalPaymentProviderTest.java`, même interface `PaymentProvider` que Stripe) sans forcément l'exécuter en live.
</details>

**Is everything working as expected? / Are errors handled correctly?**

| Cas | Commande | Attendu |
|---|---|---|
| Champ requis manquant | voir CRUD users ci-dessus | `400` + nom du champ dans le message |
| Ressource inexistante | `GET /api/payment-methods/00000000-0000-0000-0000-000000000000` | `404` |
| Double refund | refaire `POST /api/payments/$PAYMENT_ID/refund` après un premier refund | `409` |
| JSON malformé / enum invalide | `POST /api/users` avec `"role":"SUPERADMIN"` | `400` (pas 500) |
| Provider de paiement refuse le refund | credentials Stripe/PayPal invalides ou déjà remboursé côté provider | `500` avec message explicite, statut local inchangé |

### "Test Authentication and Authorization."

```bash
# Mauvais mot de passe -> 401
curl -k -s -o /dev/null -w "%{http_code}\n" -X POST https://localhost/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"wrong"}'

# Token absent sur une route protégée -> 401/403
curl -k -s -o /dev/null -w "%{http_code}\n" https://localhost/api/travels
```

**Was the authentication service robust and did the role-based access control function correctly?** → les deux commandes ci-dessus + le test "Admin profile only" plus haut couvrent RBAC et robustesse (échec explicite, pas de fuite d'info).

### "Ask the student to Simulate load on microservices."

<details>
<summary>Load balancing (preuve via traces Zipkin, pas juste "ça a l'air de marcher")</summary>

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

Doit montrer 2 IPs différentes avec un compte comparable (ex : 175/175) — preuve que les 2 replicas de `travel-service` reçoivent bien du trafic.
</details>

<details>
<summary>Failover (couper un replica en pleine charge)</summary>

```bash
docker stop travel-plan-app-travel-service-2
for i in $(seq 1 10); do
  curl -k -s -o /dev/null -w "%{http_code} " https://localhost/api/travels -H "Authorization: Bearer $TOKEN"
done
echo
docker start travel-plan-app-travel-service-2
```

**Did the microservices demonstrate effective load balancing and failover under heavy traffic?** → toutes les requêtes restent `200` malgré la coupure ; la répartition ci-dessus prouve que ce n'est pas un hasard de DNS.
</details>

### "Validate CI/CD pipeline and code quality."

**Did the CI/CD pipeline function correctly for build, test, and deployment processes, and were code quality standards maintained?** → http://localhost:8090 (Jenkins), montrer un build récent vert sur `main`/une PR (stages `Checkout → Build & Test → SonarQube → Deploy`), puis http://localhost:9000 (SonarQube) avec le Quality Gate passé.

### "Assess code review and best practices."

```bash
git log --oneline -20
```

**Is the code consistent and well-structured?** → structure identique par service (controller/service/repository/security/exception/provider), tests en miroir de cette structure.

**Are all pull requests following naming conventions such as (Camel Case, Pascal Case, ...), Consistency, clarity and descriptiveness?** → commits/branches en Conventional Commits (`feat/...`, `fix/...`, `refactor/...`), montrer `git log` ci-dessus. Le code Java suit PascalCase pour les classes et camelCase pour méthodes/variables (convention Java standard, pas de dérogation).

### "Check SonarQube logs in recent pull requests."

**Is the log free of warnings about unsupported or deprecated libraries? / Are the security vulnerabilities found by SonarQube resolved in the pull requests?** → http://localhost:9000, onglet **Projects** → choisir un service → **Activity**, vérifier l'absence de warning "deprecated/unsupported library" et que les vulnérabilités signalées sont à 0 sur l'analyse la plus récente.

À part : le pipeline Ansible/Jenkins lui-même affiche un warning de dépréciation (`ansible.builtin.apt_repository` deprecated, sans lien avec SonarQube) — si demandé, ce n'est pas ce que cette question du jury vise, mais autant le savoir expliquer si on te le fait remarquer.

---

## Bonus

| Question | État |
|---|---|
| Did the students provide clear documentation about the application and the database? | Oui — `docs/` (15 fichiers), un par thème infra/service, plus ce guide |
| Did the students Incorporate Kubernetes alongside Ansible to enhance service management, orchestration, and load-balancing capabilities? | Non fait — priorisé après le cœur du sujet, à assumer tel quel à l'oral |
| Did the student add any valuable bonuses and it works fine without any error? | Oui : architecture multi-provider de paiement (Stripe + PayPal derrière une interface commune, extensible), tracing Zipkin fonctionnel, gestion d'erreurs uniforme sur les 3 services métier. Pas fait : tests E2E/intégration. |

---

## Pourquoi ce document existe

Chaque commande ci-dessus a été testée en direct sur cette stack le 30/07/2026 — plusieurs après avoir corrigé de vrais bugs découverts en testant, pas en relisant le code (transaction manager Neo4j manquant, `updatedAt` jamais rafraîchi, erreurs 500 qui auraient dû être 400, tracing Zipkin cassé par un renommage de propriété Spring Boot 4.x, load balancing qui ne répartissait rien en pratique, TLS interne incomplet, refund qui ne notifiait jamais Stripe/PayPal). Voir `audit-findings.md` pour le détail de ces corrections.
