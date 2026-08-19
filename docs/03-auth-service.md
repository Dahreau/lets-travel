# auth-service — JWT + RBAC

[← Sommaire](00-getting-started.md)

## Lancer en local

Prérequis : la stack applicative tourne (`./scripts/start-app.sh`) — `auth-service` a besoin de `postgres` (base `auth_db`) et `vault`.

**Façon recommandée** (fonctionne toujours, Vault et TLS déjà câblés) :
```bash
docker compose up -d --build auth-service
```

**Hors Docker (hot-reload local)** : Vault n'a volontairement aucun port publié sur l'host (durcissement sécurité), donc `VAULT_ADDR=http://localhost:8200` ne peut pas fonctionner tel quel — il faut d'abord exposer temporairement le port :
```bash
docker compose exec vault vault read -field=role_id auth/approle/role/auth-service/role-id
docker compose exec vault vault write -f -field=secret_id auth/approle/role/auth-service/secret-id
docker compose port vault 8200 || echo "Vault n'a pas de port publié : ajoute temporairement '127.0.0.1:8200:8200' sous vault.ports dans docker-compose.yml puis 'docker compose up -d vault' avant de continuer"
```

Puis lance le service (Postgres est sur le port `5434`, pas le `5432` par défaut) :

```bash
cd backend/auth-service
DB_HOST=localhost DB_PORT=5434 DB_PASSWORD=<ton AUTH_DB_PASSWORD> \
VAULT_ADDR=http://localhost:8200 VAULT_ROLE_ID=<role_id> VAULT_SECRET_ID=<secret_id> \
./mvnw spring-boot:run
```

Au premier démarrage, un admin par défaut est créé (`admin` / `changeme_dev_only` sauf si tu surcharges `DEFAULT_ADMIN_USERNAME`/`DEFAULT_ADMIN_PASSWORD`) — change ce mot de passe avant tout usage réel.

Le service écoute sur `:8081` (voir [`05-api-gateway.md`](05-api-gateway.md) pour y accéder via le gateway plutôt qu'en direct).

## Endpoints

| Méthode | Route | Accès | Rôle |
|---|---|---|---|
| `POST` | `/api/auth/login` | public | authentifie `{username, password}`, renvoie `{token}` (JWT) |
| `GET` | `/api/auth/me` | `Authorization: Bearer <token>` requis | renvoie `{username, role}` du token présenté |

Le token est un JWT HS256 signé avec un secret partagé stocké dans Vault (`secret/shared/jwt`), valable `JWT_EXPIRATION_MINUTES` (60 par défaut).

## Ce qui est construit

| Fichier / package | Rôle |
|---|---|
| `domain/Admin.java`, `domain/Role.java` | entité JPA (id UUID généré côté Java, username, hash de mot de passe, rôle) |
| `db/migration/V1__create_admins_table.sql` | schéma Flyway de la table `admins` |
| `repository/AdminRepository.java` | `findByUsername` |
| `bootstrap/AdminSeeder.java` | crée l'admin par défaut si la table est vide (idempotent) |
| `vault/VaultClient.java` | login AppRole + lecture d'un secret KV v2, via `RestClient` (pas de dépendance Spring Cloud Vault) |
| `security/JwtSigningKeyConfig.java` | va chercher le secret de signature dans Vault au démarrage |
| `security/JwtService.java` | génère/valide les JWT (HS256) |
| `security/JwtAuthenticationFilter.java` | extrait le `Bearer <token>`, peuple le contexte de sécurité Spring |
| `security/SecurityConfig.java` | stateless, `/api/auth/login` public, tout le reste authentifié |
| `web/AuthController.java` | `/login`, `/me` |

## Nouveau par rapport à buy-02

Voir [`nouveautes-vs-buy02.md`](nouveautes-vs-buy02.md#auth-service-featauth-service-jwt) (Vault, AppRole, Flyway, JWT stateless).

## Pourquoi ces choix

### Pourquoi HS256 + un secret Vault partagé plutôt que RSA
Une paire de clés RSA (privée chez `auth-service`, publique partagée) serait plus stricte en moindre privilège — un service compromis ne pourrait jamais forger de token, seulement les vérifier. On a choisi HS256 + secret symétrique partagé (`secret/shared/jwt`, lisible par les 5 services via une policy Vault dédiée) pour aller plus vite maintenant : un seul secret à gérer, pas de génération/rotation de paire de clés. Passage à RS256 documenté comme amélioration naturelle une fois que d'autres services valident vraiment des tokens.

### Pourquoi appeler Vault en HTTP direct plutôt que `spring-cloud-vault-config`
Spring Boot 4.1 est très récent ; la compatibilité de Spring Cloud Vault avec cette version n'est pas garantie et aurait pu bloquer le build sur un problème de résolution de dépendances, sans rapport avec la logique métier. Deux appels HTTP (`RestClient`, déjà disponible) vers l'API Vault (login AppRole, lecture KV v2) font la même chose sans dépendance supplémentaire ni risque de version.

### Pourquoi un admin seedé plutôt qu'un endpoint d'inscription
Un `POST /api/auth/register` public créerait un moyen non protégé de fabriquer des comptes admin. `AdminSeeder` crée un admin unique au premier démarrage (si la table est vide) à partir de variables d'environnement — pas de surface d'attaque, et le mot de passe par défaut est explicitement signalé dans les logs pour être changé.

### Pourquoi `contextLoads()` mocke `AdminRepository` et `jwtSigningKey`
Ce test exclut toujours `DataSourceAutoConfiguration`/`HibernateJpaAutoConfiguration` (pas de vraie base sur l'agent Jenkins). Mais `AdminSeeder` a maintenant besoin d'un vrai `AdminRepository`, et `JwtService` d'une vraie `SecretKey` (récupérée depuis Vault) : sans mock, le contexte Spring ne démarrerait pas. `@MockitoBean` remplace ces deux beans par des doubles Mockito, donc le test vérifie toujours "le câblage Spring est correct" sans avoir besoin d'une vraie base ni d'un vrai Vault.
