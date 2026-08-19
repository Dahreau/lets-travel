# Audit findings — état du backend/infra au 2026-07-30

[← Sommaire](00-getting-started.md)

Revue complète du code contre la grille d'audit officielle (`travel-plan_audit.md`), refaite avec un regard neuf le 30/07 sur `fix/audit-hardening` — objectif : que cette branche soit la **dernière PR avant le merge final**, tous les points validés.

Légende : ✅ vérifié aujourd'hui, prêt pour l'audit — ⚠️ vrai écart restant, à corriger ou à savoir justifier à l'oral.

## Ce qui était ouvert hier (2026-07-29) et est maintenant vérifié fermé

| Point | Vérifié comment |
|---|---|
| Postgres/Neo4j/Vault/Zipkin exposés sur l'host | `docker-compose.yml` : plus aucun `ports:` sur ces 4 services, seul `nginx` publie encore |
| `auth-service` en `.authenticated()` au lieu de `.hasRole("ADMIN")` | Les 4 `SecurityConfig.java` (auth/user/travel/payment) disent tous `.anyRequest().hasRole("ADMIN")` — grep fait sur les 4 fichiers |
| `secret_id` Vault jamais révoqué | `fetch-vault-secrets.yml` détruit l'ancien (`/secret-id/destroy`, tolérant sur 400/404) avant d'en émettre un nouveau |
| 3 trous de tests (`JwtService`, `PaymentMethodRepository`, `PlaceRepository`) | Les 3 fichiers de test existent et sont substantiels (ex. `PlaceRepositoryTest` tourne contre un vrai Neo4j via Testcontainers, pas un mock) |
| `nginx`/`zipkin` sans healthcheck | Les deux ont un healthcheck dans `docker-compose.yml` |

## Corrigé ce soir (30/07, deuxième passe)

| Point | Fix |
|---|---|
| Vault en mode dev | Mode serveur réel : storage `file` persistant, listener TLS (cert auto-signé), init/unseal réel via `ansible/playbooks/vault-unseal.yml` (une seule part de clé — justifié, un seul opérateur/pipeline gère tout le cycle de vie, pas une vraie organisation multi-personnes). Root token régénéré à froid et écrit dans `.env`, plus jamais un token fixe en dur. |
| `sonar-maven-plugin` sans version explicite | Épinglé (`:5.7.0.6970:sonar`) dans le `Jenkinsfile` — le warning "unspecified plugin version" ne devrait plus apparaître. |
| `docs/01-ci-cd.md` obsolète | Réécrit : Jenkins a bien accès à Docker (`docker.sock`), le stage Deploy est décrit tel qu'il tourne réellement, mention de `cleanWs()`. |
| Trafic Vault en clair | `vault:8200` en HTTPS (cert auto-signé) ; les 5 `VaultClient.java` font confiance à ce certificat précis (même compromis que Nginx) ; `fetch-vault-secrets.yml`/`vault-unseal.yml` passent par `docker compose exec` (pas d'appel HTTP externe, cohérent avec le port non publié). |
| Clé privée Nginx commitée dans Git | `.gitignore` corrigé (les lignes qui l'ignoraient étaient en commentaire) — **action manuelle requise**, voir plus bas. |

## Corrigé ce soir (30/07, quatrième passe — TLS interne complet + refund réel)

| Point | Fix |
|---|---|
| Trafic interne en clair (`Postgres`, `Neo4j`, `api-gateway→services`, `nginx→api-gateway`) | Les 4 derniers hops sont maintenant chiffrés : Postgres (`sslmode=require`, cert genere au build de `infra/postgres/Dockerfile`), Neo4j (`bolt+ssc://`, meme principe dans `infra/neo4j/Dockerfile`), `api-gateway↔auth/user/travel/payment-service` (certificat interne partage `infra/internal-tls/`, importe dans le keystore JVM d'api-gateway au build — Spring Cloud Gateway Server MVC ignore `spring.http.client.ssl.bundle` pour son client HTTP interne, verifie empiriquement), `nginx→api-gateway` (`proxy_ssl_verify on` avec le meme certificat, SAN couvrant `api-gateway` et chaque conteneur de replica). Verifie en direct : login + creation d'un travel 2 destinations + lecture payments/payment-methods, logs nginx/api-gateway/travel-service propres (aucune erreur SSL). |
| Refund ne notifiait jamais Stripe/PayPal (seul le statut local changeait) | `PaymentProvider` n'avait qu'une methode `charge()`, pas de `refund()`. Ajoute des deux cotes (Stripe `POST /v1/refunds`, PayPal `POST /v2/payments/captures/{id}/refund` — necessite de stocker l'id de capture, pas l'id de commande, cote PayPal). `PaymentService.refund()` appelle desormais le provider avant de changer le statut local ; si le provider refuse, l'exception remonte avant tout changement d'etat. |
| Healthcheck Nginx `unhealthy` en permanence | `wget` resolvait `localhost` en IPv6 en premier alors que nginx n'ecoute qu'en IPv4 — healthcheck pointe maintenant sur `127.0.0.1` explicitement. |
| `docker compose up` echouait a demarrer travel-service/api-gateway/nginx apres l'activation TLS sur Neo4j | Le policy SSL bolt allonge le temps de boot de Neo4j au-dela du budget du healthcheck (5×10s) ; retries porte a 12. |

## Décisions assumées à justifier à l'oral (pas des tâches restantes)

Les deux points ci-dessous ont déjà été tranchés le 30/07 — rien à corriger avant l'audit, juste à savoir expliquer si le jury les relève. Le seul vrai point non fait reste Kubernetes/E2E, déjà cadré comme tel dans la section Bonus plus bas.

### 1. Nœuds Neo4j jamais supprimés (seulement les relations) — décision assumée, pas un oubli

Supprimer un `Travel` supprime la relation `ROUTE_TO` mais garde le `PlaceNode` (ville). Comportement probablement correct (une ville est une référence partagée entre voyages) mais l'énoncé insiste sur "cascade entre PostgreSQL et Neo4j" — prépare la justification orale plutôt que de laisser croire à un oubli. **Décision (30/07) : pas un bug, ne pas changer.**

### 2. Clé privée Nginx déjà commitée dans l'historique Git

`infra/nginx/certs/travel-plan.crt`/`.key` ont été trackés dans un commit passé avant d'être retirés du répertoire de travail (`.gitignore` corrigé). Le fichier n'existe plus aujourd'hui, seul l'historique Git le garde encore. **Décision (30/07) : pas d'action — certificat de dev auto-signé, aucune donnée réelle protégée, le coût d'une réécriture d'historique ne se justifie pas ici.**

## Vérifié aujourd'hui avec un regard neuf (au-delà du scope backend d'hier)

- **CRUD Admin Dashboard (frontend)** : `create`/`update`/`delete` bien présents et câblés au bon verbe HTTP pour les 3 entités — users (`users.ts`), travels (`travels.ts`), payments + payment-methods (`payments.ts`/`payment-methods.ts`).
- **Responsive** : `shell.scss` a un vrai breakpoint (`@media (max-width: 860px)`) qui bascule la sidebar en panneau coulissant avec bouton burger — pas un habillage superficiel.
- **Stripe/PayPal** : `PaymentProvider` (interface) + `StripePaymentProvider`/`PayPalPaymentProvider` + `ProviderType` + credentials dédiées par provider, résolues via `PaymentProviderResolver` — architecture propre, extensible à un 3ᵉ provider sans toucher à l'existant.
- **Cascade Postgres, vérifié au niveau du code (pas juste la doc)** : `User.address` en `@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)` (supprimer un User supprime son Address) ; `Payment.paymentMethod` en `@OnDelete(action = OnDeleteAction.SET_NULL)` (supprimer une PaymentMethod met le paiement à `NULL`, garde l'historique). Les deux choix sont cohérents et défendables à l'oral.
- **Tracing** : ~~les 5 services ont une config Zipkin~~ — **faux avant ce soir**, voir section suivante.
- **HA/replicas** : `replicas: 2` confirmé sur les 5 microservices dans `docker-compose.yml`, mais avoir des replicas ne suffit pas à garantir la répartition de charge — voir section suivante.

## Corrigé ce soir (30/07, troisième passe — bugs trouvés en testant en direct, pas en relisant le code)

| Point | Root cause | Fix |
|---|---|---|
| `POST /api/travels` avec 2+ destinations → 500 | `Neo4jTransactionManager` jamais auto-configuré par Spring Boot quand JPA+Neo4j coexistent ; une fois ajouté manuellement, ça désactivait à son tour l'auto-config du `transactionManager` JPA (`@ConditionalOnMissingBean(TransactionManager.class)`) | `Neo4jTransactionConfig.java` déclare explicitement les deux `PlatformTransactionManager` (JPA en `@Primary`, Neo4j à part) |
| `updatedAt` jamais rafraîchi sur `PUT /travels`, `/users`, `/payments/refund` | `@PreUpdate` ne s'exécute qu'au flush Hibernate (différé au commit), pas au moment de `save()` — la réponse HTTP était donc construite avec l'ancienne valeur | `save()` → `saveAndFlush()` sur les 3 chemins concernés |
| JSON malformé / enum invalide → 500 au lieu de 400 | `HttpMessageNotReadableException` (échoue avant Bean Validation) tombait dans le catch-all `Exception.class` générique | Handler dédié `HttpMessageNotReadableException` → 400, sur les 3 services |
| Formulaires frontend muets si champ invalide/manquant | Les 5 formulaires Angular faisaient `markAllAsTouched()` sans jamais afficher de message (ni toast, ni erreur inline) | Toast d'erreur ajouté sur les 4 formulaires CRUD (travel/user/payment/payment-method) |
| Zipkin ne recevait **aucune** trace (`/api/v2/services` → `[]`) malgré une config qui semblait correcte | Deux bugs cumulés propres à Spring Boot 4.x : (1) propriété renommée `management.zipkin.tracing.endpoint` → `management.tracing.export.zipkin.endpoint` ; (2) le couple `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` assemblé à la main ne suffit plus, il faut le starter unique `spring-boot-starter-zipkin` | Propriété renommée + dépendance remplacée dans les 5 `pom.xml`/`application.properties`. Vérifié : les 3 services traçables apparaissent bien dans `/api/v2/services` |
| Load balancing entre replicas : tout le trafic restait collé à un seul conteneur (100/0 sur un burst de 20 requêtes) | `api-gateway` utilise déjà `spring-cloud-starter-loadbalancer` (`RouteConfig.java`, filtre `lb(...)`), mais le `SimpleDiscoveryClient` ne déclarait qu'**une seule instance** par service (le nom DNS partagé) — rien à répartir. Le DNS round-robin de Docker seul ne suffit pas : le client HTTP garde sa connexion keep-alive vers la première IP résolue | 2 instances explicites déclarées par service (noms de conteneurs Compose), dans `application.properties` + `docker-compose.yml`. Vérifié via traces Zipkin : répartition 175/175 sur un burst de 30 requêtes |

Détail des commandes de vérification (réutilisables à l'oral) : [`10-audit-demo-guide.md`](10-audit-demo-guide.md).

## Ce qui reste solide (inchangé depuis hier, pas re-détaillé ici)

Indépendance des microservices, API Gateway, policies Vault scopées, aucun secret en dur, idempotence Ansible, Quality Gate SonarQube bloquant, Docker multi-stage non-root, conventions Git/PR. Voir la version du 29/07 dans l'historique Git si le détail est utile à l'oral.

## Bonus — état

- **Doc** : complète et à jour pour tout sauf `01-ci-cd.md` (point 4 ci-dessus).
- **Kubernetes / E2E** : non commencés — à mentionner comme "non fait, priorisé après le cœur du sujet" si demandé.
