# payment-service — Moyens de paiement, paiements Stripe/PayPal

[← Sommaire](00-getting-started.md)

## Lancer en local

Prérequis : la stack applicative tourne (`./scripts/start-app.sh`) — `payment-service` a besoin de `postgres` (base `payment_db`) et `vault`.

**Façon recommandée** (fonctionne toujours, Vault et TLS déjà câblés) :
```bash
docker compose up -d --build payment-service
```

**Hors Docker (hot-reload local)** : Vault n'a volontairement aucun port publié sur l'host (durcissement sécurité), donc `VAULT_ADDR=http://localhost:8200` ne peut pas fonctionner tel quel — il faut d'abord exposer temporairement le port :
```bash
docker compose exec vault vault read -field=role_id auth/approle/role/payment-service/role-id
docker compose exec vault vault write -f -field=secret_id auth/approle/role/payment-service/secret-id
docker compose port vault 8200 || echo "Vault n'a pas de port publié : ajoute temporairement '127.0.0.1:8200:8200' sous vault.ports dans docker-compose.yml puis 'docker compose up -d vault' avant de continuer"
```

Contrairement au secret JWT partagé, les identifiants Stripe/PayPal ne sont **pas** seedés automatiquement par `bootstrap.sh` — ce sont de vraies clés tierces, pas un secret généré aléatoirement. Il faut les écrire une fois toi-même (des clés de test suffisent, sandbox Stripe/PayPal) :

```bash
docker compose exec vault vault kv put secret/payment-service/stripe secret_key=sk_test_xxx
docker compose exec vault vault kv put secret/payment-service/paypal client_id=xxx client_secret=xxx
```

**Ça ne se propage pas automatiquement à l'équipe.** Vault tourne en mode serveur réel (stockage persistant sur volume Docker), mais une instance par machine — ce que tu écris ici n'existe que dans ton Vault local, pas celui d'un collègue qui lance la stack de son côté (contrairement au secret JWT, auto-généré pour tout le monde par `bootstrap.sh`). Deux options, au choix de l'équipe : chacun crée son propre compte sandbox Stripe/PayPal et répète ces deux commandes chez lui (cohérent avec le reste du projet, comportement identique puisqu'on est en sandbox), ou l'équipe partage une seule paire de clés de test par un canal privé (jamais commitée dans le repo) — acceptable seulement parce que ce sont des clés sandbox, sans argent réel en jeu.

Puis lance le service (Postgres est sur le port `5434`, pas le `5432` par défaut) :

```bash
cd backend/payment-service
DB_HOST=localhost DB_PORT=5434 DB_PASSWORD=<ton PAYMENT_DB_PASSWORD> \
VAULT_ADDR=http://localhost:8200 VAULT_ROLE_ID=<role_id> VAULT_SECRET_ID=<secret_id> \
./mvnw spring-boot:run
```

Le service écoute sur `:8084` (voir [`05-api-gateway.md`](05-api-gateway.md) pour y accéder via le gateway plutôt qu'en direct). Toutes les routes exigent un JWT `ADMIN` émis par `auth-service`.

## Endpoints

| Méthode | Route | Body | Réponse |
|---|---|---|---|
| `GET` | `/api/payment-methods` | — | liste de `PaymentMethodResponse` |
| `GET` | `/api/payment-methods/{id}` | — | `PaymentMethodResponse`, `404` si absent |
| `POST` | `/api/payment-methods` | `PaymentMethodRequest` | `201` + `PaymentMethodResponse` |
| `PUT` | `/api/payment-methods/{id}` | `PaymentMethodRequest` | `200` + `PaymentMethodResponse`, `404` si absent |
| `DELETE` | `/api/payment-methods/{id}` | — | `204`, `404` si absent |
| `GET` | `/api/payments` | — | liste de `PaymentResponse` |
| `GET` | `/api/payments/{id}` | — | `PaymentResponse`, `404` si absent |
| `POST` | `/api/payments` | `PaymentRequest` | `201` + `PaymentResponse` (déclenche le vrai appel Stripe/PayPal) |
| `POST` | `/api/payments/{id}/refund` | — | `200` + `PaymentResponse`, `409` si le paiement n'est pas `SUCCEEDED` |

Pas de `PUT`/`DELETE` sur `/api/payments` — voir plus bas pourquoi.

`PaymentMethodRequest` (jamais de numéro de carte en clair, seulement un token opaque retourné par Stripe/PayPal côté frontend) :

```json
{
  "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "provider": "STRIPE",
  "type": "CARD",
  "providerToken": "pm_1AbCdEfGhIjKlMnO",
  "brand": "visa",
  "last4": "4242",
  "isDefault": true
}
```

`PaymentRequest` :

```json
{
  "travelId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "paymentMethodId": "9c858901-8a57-4791-81fe-4c455b099bc9",
  "amount": 349.90,
  "currency": "EUR"
}
```

Le `provider` n'est jamais demandé au client : il est déduit du `paymentMethod` déjà choisi (un moyen de paiement Stripe est forcément débité via Stripe).

## Erreurs gérées (`ApiExceptionHandler`)

| Cas | Code |
|---|---|
| `providerToken`/`currency` vide, `amount` non positif | `400` |
| Moyen de paiement ou paiement introuvable | `404` |
| Remboursement demandé sur un paiement qui n'est pas `SUCCEEDED` | `409` |
| Violation d'intégrité | `409` |

## Ce qui est construit

| Fichier / package | Rôle |
|---|---|
| `domain/PaymentMethod.java`, `Payment.java` | entités JPA — `Payment.paymentMethod` en `@ManyToOne` nullable, **pas de cascade** |
| `db/migration/V1__create_payment_tables.sql` | schéma Flyway — `payment_method_id` en `ON DELETE SET NULL`, pas `CASCADE` |
| `provider/StripePaymentProvider.java`, `PayPalPaymentProvider.java` | appels HTTP directs (`RestClient`) vers l'API Stripe / PayPal |
| `provider/PaymentProviderResolver.java` | choisit le bon provider (`STRIPE`/`PAYPAL`) selon le moyen de paiement du client |
| `vault/VaultClient.java`, `security/*` | identique à `travel-service` (Vault AppRole, JWT validation-only, `hasRole("ADMIN")`) |
| `service/PaymentMethodService.java` | CRUD complet |
| `service/PaymentService.java` | création (charge le provider), lecture, remboursement — pas de mise à jour ni de suppression |
| `web/PaymentMethodController.java`, `PaymentController.java` + DTOs | endpoints ci-dessus |
| `exception/*NotFoundException.java`, `InvalidRefundException.java`, `ApiExceptionHandler.java` | 404 / 409 / 400 |

## Nouveau par rapport à buy-02

Voir [`nouveautes-vs-buy02.md`](nouveautes-vs-buy02.md#payment-service-featpayment-service-crud) (intégration Stripe/PayPal réelle, non-cascade délibérée, paiement immuable).

## Pourquoi ces choix

### Pourquoi `RestClient` plutôt que les SDK officiels Stripe/PayPal
Les deux SDK sont des dépendances lourdes avec des historiques de compatibilité Spring Boot différents (risque accru sur une stack Spring Boot 4.1 encore récente). Stripe et PayPal exposent tous les deux une API REST simple (form-urlencoded pour Stripe, JSON + OAuth2 client_credentials pour PayPal) — un appel HTTP direct via `RestClient` couvre le besoin sans dépendance supplémentaire, même logique que le choix déjà fait pour Vault (`VaultClient` sans `spring-cloud-vault-config`).

### Pourquoi `Payment.paymentMethod` n'est pas en cascade (`ON DELETE SET NULL`, pas `CASCADE`)
Tout n'est pas fait pour cascader. `User`/`Address` et `Travel`/`Destination` cascadent parce que l'enfant n'a aucun sens sans le parent. Un `Payment` est l'inverse : c'est un enregistrement financier qui doit survivre à la suppression du moyen de paiement qui l'a produit (carte expirée retirée du compte, par exemple) — le supprimer effacerait une trace comptable. `payment_method_id` passe donc à `NULL` plutôt que d'entraîner la suppression de la ligne `payments`, aux deux niveaux (Flyway *et* l'annotation Hibernate `@OnDelete(action = OnDeleteAction.SET_NULL)`, pour que le schéma généré par les tests `@DataJpaTest`/H2 corresponde au schéma réel). Vérifié par un test dédié (`PaymentRepositoryTest.deletingPaymentMethodSetsPaymentMethodIdToNullInsteadOfDeletingPayment`).

### Pourquoi il n'y a pas de `PUT`/`DELETE` sur `/api/payments`
Un paiement n'est jamais "modifié" dans un vrai système de paiement — Stripe et PayPal eux-mêmes ne permettent pas d'éditer une charge, seulement de la rembourser. `PaymentService` reflète ça : `create` (débite), `findAll`/`findById` (lecture), `refund` (transition d'état contrôlée, refusée si le paiement n'est pas déjà `SUCCEEDED`). Aucune route ne permet de réécrire l'historique.

### Pourquoi le remboursement rappelle réellement Stripe/PayPal
`refund()` notifie d'abord le vrai provider (`POST /v1/refunds` chez Stripe avec `payment_intent`, `POST /v2/payments/captures/{id}/refund` chez PayPal) avant de changer le statut local en `REFUNDED` — si le provider refuse ou est injoignable, aucun changement d'état local n'a lieu (`RestClientException` intercepté par `ApiExceptionHandler` → 502, transaction annulée par `@Transactional`). Bonus non exigé par l'énoncé/l'audit (CRUD payment-methods + support Stripe/PayPal suffisent) ; nécessite une vraie clé de test `sk_test_...` dans `.env`/Vault pour être démontré en live, sinon l'appel échoue en 502 (comportement attendu, pas un bug).
