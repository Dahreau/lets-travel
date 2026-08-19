# user-service — CRUD utilisateurs + cascade

[← Sommaire](00-getting-started.md)

## Lancer en local

Prérequis : la stack applicative tourne (`./scripts/start-app.sh`) — `user-service` a besoin de `postgres` (base `user_db`) et `vault`.

**Façon recommandée** (fonctionne toujours, Vault et TLS déjà câblés) :
```bash
docker compose up -d --build user-service
```

**Hors Docker (hot-reload local)** : Vault n'a volontairement aucun port publié sur l'host (durcissement sécurité), donc `VAULT_ADDR=http://localhost:8200` ne peut pas fonctionner tel quel — il faut d'abord exposer temporairement le port :
```bash
docker compose exec vault vault read -field=role_id auth/approle/role/user-service/role-id
docker compose exec vault vault write -f -field=secret_id auth/approle/role/user-service/secret-id
docker compose port vault 8200 || echo "Vault n'a pas de port publié : ajoute temporairement '127.0.0.1:8200:8200' sous vault.ports dans docker-compose.yml puis 'docker compose up -d vault' avant de continuer"
```

Puis lance le service (Postgres est sur le port `5434`, pas le `5432` par défaut) :

```bash
cd backend/user-service
DB_HOST=localhost DB_PORT=5434 DB_PASSWORD=<ton USER_DB_PASSWORD> \
VAULT_ADDR=http://localhost:8200 VAULT_ROLE_ID=<role_id> VAULT_SECRET_ID=<secret_id> \
./mvnw spring-boot:run
```

Le service écoute sur `:8082` (voir [`05-api-gateway.md`](05-api-gateway.md) pour y accéder via le gateway plutôt qu'en direct). Toutes les routes exigent un JWT `ADMIN` émis par `auth-service` (`Authorization: Bearer <token>`) — récupère-le via `POST /api/auth/login` d'abord.

## Endpoints

| Méthode | Route | Body | Réponse |
|---|---|---|---|
| `GET` | `/api/users` | — | liste de `UserResponse` |
| `GET` | `/api/users/{id}` | — | `UserResponse`, `404` si absent |
| `POST` | `/api/users` | `UserRequest` | `201` + `UserResponse` |
| `PUT` | `/api/users/{id}` | `UserRequest` | `200` + `UserResponse`, `404` si absent |
| `DELETE` | `/api/users/{id}` | — | `204`, `404` si absent |

Toutes les routes exigent le rôle `ADMIN` (`SecurityConfig` : `anyRequest().hasRole("ADMIN")`) — plus strict qu'`auth-service`, qui se contente d'`authenticated()`.

`UserRequest` :

```json
{
  "firstName": "Ada",
  "lastName": "Lovelace",
  "email": "ada@travel-plan.com",
  "phone": "0102030405",
  "role": "TRAVELER",
  "address": { "street": "...", "city": "...", "postalCode": "...", "country": "..." }
}
```

`address` est optionnel : `null` (ou absent) supprime l'adresse existante côté base à la mise à jour.

## Erreurs gérées (`ApiExceptionHandler`)

| Cas | Code |
|---|---|
| `firstName`/`lastName`/`email` vide, `role` absent, email mal formé | `400` |
| Utilisateur introuvable | `404` |
| Email déjà utilisé par un autre utilisateur | `409` |

## Ce qui est construit

| Fichier / package | Rôle |
|---|---|
| `domain/User.java`, `domain/Address.java`, `domain/Role.java` | entités JPA — relation one-to-one bidirectionnelle `User` ↔ `Address` |
| `db/migration/V1__create_users_and_addresses_tables.sql` | schéma Flyway — `addresses.user_id` en `UNIQUE REFERENCES users(id) ON DELETE CASCADE` |
| `repository/UserRepository.java` | `findByEmail` |
| `vault/VaultClient.java` | identique à `auth-service` (login AppRole + lecture KV v2 via `RestClient`) |
| `security/JwtSigningKeyConfig.java`, `security/JwtService.java` | **validation uniquement** — pas de `generateToken`, ce service ne délivre jamais de JWT |
| `security/JwtAuthenticationFilter.java`, `security/SecurityConfig.java` | mêmes mécaniques qu'`auth-service`, mais `hasRole("ADMIN")` partout |
| `web/UserController.java` | CRUD complet, gère la liaison/déliaison de l'adresse |
| `exception/UserNotFoundException.java`, `exception/ApiExceptionHandler.java` | 404 / 409 / 400 |

## Nouveau par rapport à buy-02

Voir [`nouveautes-vs-buy02.md`](nouveautes-vs-buy02.md#user-service-featuser-service-crud) (cascade delete Postgres + JPA, `JwtService` validation-only).

## Pourquoi ces choix

### Pourquoi une vraie relation `User` ↔ `Address` avec cascade, plutôt qu'une simple colonne
L'audit demande explicitement de gérer les suppressions/mises à jour en cascade. Une relation one-to-one réelle (`Address` a sa propre table, sa propre clé, référence `User`) permet de démontrer la cascade aux deux niveaux : côté base (`ON DELETE CASCADE` dans la migration Flyway) et côté JPA (`cascade = CascadeType.ALL, orphanRemoval = true` sur `User.address`). Supprimer un `User` supprime son `Address` sans requête séparée ; mettre l'adresse à `null` dans une requête `PUT` la supprime aussi (`orphanRemoval`).

### Pourquoi `JwtService` n'a pas de `generateToken` ici
Seul `auth-service` délivre des tokens — `user-service` ne fait que les vérifier. Garder une méthode `generateToken` inutilisée dans ce service serait du code mort, contraire à la règle "pas de code inutile".

### Pourquoi `hasRole("ADMIN")` sur toutes les routes plutôt que `authenticated()`
`user-service` n'a aucune route destinée à un utilisateur non-admin (pas de "mon profil" self-service pour l'instant) — toutes ses routes sont des opérations de back-office. `authenticated()` autoriserait n'importe quel JWT valide, y compris un futur token `TRAVELER` ; `hasRole("ADMIN")` correspond exactement à ce que fait réellement le service aujourd'hui.
