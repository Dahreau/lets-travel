# api-gateway — routing, auth au périmètre, load balancing

[← Sommaire](00-getting-started.md)

## Lancer en local

Prérequis : `auth-service` (port 8081), `user-service` (port 8082), `travel-service` (port 8083) et `payment-service` (port 8084) tournent déjà (voir leurs pages respectives), plus Vault et Zipkin (`./scripts/start-app.sh`).

**Façon recommandée** (fonctionne toujours, Vault et TLS déjà câblés) :
```bash
docker compose up -d --build api-gateway
```

**Hors Docker (hot-reload local)** : Vault n'a volontairement aucun port publié sur l'host (durcissement sécurité), donc `VAULT_ADDR=http://localhost:8200` ne peut pas fonctionner tel quel — il faut d'abord exposer temporairement le port :

```bash
docker compose exec vault vault read -field=role_id auth/approle/role/api-gateway/role-id
docker compose exec vault vault write -f -field=secret_id auth/approle/role/api-gateway/secret-id
docker compose port vault 8200 || echo "Vault n'a pas de port publié : ajoute temporairement '127.0.0.1:8200:8200' sous vault.ports dans docker-compose.yml puis 'docker compose up -d vault' avant de continuer"
```

Puis lance le gateway :

```bash
cd backend/api-gateway
VAULT_ADDR=http://localhost:8200 VAULT_ROLE_ID=<role_id> VAULT_SECRET_ID=<secret_id> \
./mvnw spring-boot:run
```

Le gateway écoute sur `:8080`. Toutes les requêtes passent par lui : `curl http://localhost:8080/api/auth/login`, `curl http://localhost:8080/api/users -H "Authorization: Bearer <token>"`.

## Routage

| Route | Cible | JWT requis au gateway |
|---|---|---|
| `POST /api/auth/login` | `auth-service` | non (c'est là qu'on obtient le token) |
| `/api/auth/**` (reste) | `auth-service` | oui |
| `/api/users/**` | `user-service` | oui |
| `/api/travels/**` | `travel-service` | oui |
| `/api/payments/**` | `payment-service` | oui |
| `/api/payment-methods/**` | `payment-service` | oui |

Le chemin n'est pas réécrit : `/api/auth/login` arrive tel quel sur `auth-service`, `/api/users/{id}` tel quel sur `user-service`, `/api/travels/{id}` tel quel sur `travel-service`, `/api/payments/{id}` tel quel sur `payment-service`.

## Ce qui est construit

| Fichier / package | Rôle |
|---|---|
| `gateway/RouteConfig.java` | déclare les routes (`RouterFunction` beans), une par service ciblé |
| `gateway/JwtGatewayFilterFunction.java` | filtre appliqué aux routes protégées : `401` si `Authorization` absent/invalide, sinon laisse passer |
| `vault/VaultClient.java`, `security/JwtSigningKeyConfig.java`, `security/JwtService.java` | identiques à `user-service` (validation seule, même secret partagé Vault) |
| `application.properties` | déclare les instances de chaque service (`spring.cloud.discovery.client.simple.instances.*`) utilisées pour le load balancing |

## Load balancing et replicas

Chaque route cible un service par son nom logique (`lb("auth-service")`), pas une URL en dur — `spring-cloud-starter-loadbalancer` résout ce nom vers une liste d'instances déclarée dans `application.properties` :

```properties
spring.cloud.discovery.client.simple.instances.auth-service[0].uri=http://localhost:8081
```

Aujourd'hui il n'y a qu'une instance par service (une seule tourne en local). Ajouter une deuxième replica = ajouter une ligne `[1].uri=...` et lancer une deuxième instance de ce service sur un autre port — le gateway répartit alors automatiquement le trafic entre les deux (round-robin) et bascule sur l'instance restante si l'une tombe (failover). Aucune ligne de code à changer.

## Nouveau par rapport à buy-02

Voir [`nouveautes-vs-buy02.md`](nouveautes-vs-buy02.md#api-gateway-featapi-gateway-routing) (API Gateway, load balancing par nom de service, tracing distribué).

## Pourquoi ces choix

### Pourquoi `spring-cloud-starter-gateway-server-webmvc` plutôt que le Gateway réactif classique
Les 4 autres services sont tous Spring MVC (Servlet, pas WebFlux) — utiliser la variante MVC du Gateway garde toute la stack sur le même modèle de programmation (pas de mélange reactive/servlet à expliquer à l'audit), et Spring Initializr avait déjà posé cette dépendance dès la génération du projet.

### Pourquoi un `SimpleDiscoveryClient` (config statique) plutôt qu'Eureka/Consul
Un vrai service registry (Eureka, Consul) ajoute un composant d'infrastructure de plus à faire tourner et fiabiliser, pour un bénéfice qui ne se voit qu'avec des instances qui apparaissent/disparaissent dynamiquement. Ici les instances sont connues à l'avance (déclarées en config) — le `SimpleDiscoveryClient` de Spring Cloud Commons donne le même mécanisme de load balancing par nom de service, sans ce composant supplémentaire. Migrer vers Eureka plus tard ne changerait aucune ligne de `RouteConfig.java`, seulement la configuration.

### Pourquoi le JWT est vérifié au gateway ET re-vérifié dans chaque service
Le gateway rejette tôt un token absent ou clairement invalide (`401` avant même le saut réseau vers `auth-service`/`user-service`), mais ne connaît pas le rôle exact requis par chaque route (`auth-service` accepte `authenticated()`, `user-service` exige `ADMIN`). Chaque service garde donc sa propre vérification fine (rôle, ressource) — le gateway fait l'authentification (token valide ou pas), chaque service fait l'autorisation (a-t-il le droit de faire CETTE action précise). Retirer la vérification du service supposerait de faire confiance au gateway pour connaître toutes les règles métier de tous les services, ce qui recrée un couplage fort entre eux.

### Pourquoi `/api/auth/login` est une route à part, déclarée avant la route générale
`GatewayRouterFunctions` teste les routes dans l'ordre où elles sont composées (`RouterFunction.and()`) : la route `login` (sans filtre JWT) est composée en premier, donc elle capte `/api/auth/login` avant que la route générale `/api/auth/**` (avec filtre JWT) ne soit even testée. Sans ce découpage, se connecter deviendrait impossible : il faudrait déjà un token pour en obtenir un.
