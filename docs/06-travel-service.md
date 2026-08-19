# travel-service — Voyages, destinations, Postgres + Neo4j

[← Sommaire](00-getting-started.md)

## Lancer en local

Prérequis : la stack applicative tourne (`./scripts/start-app.sh`) — `travel-service` a besoin de `postgres` (base `travel_db`), `neo4j` et `vault`.

**Façon recommandée** (fonctionne toujours, Vault et TLS déjà câblés) :
```bash
docker compose up -d --build travel-service
```

**Hors Docker (hot-reload local)** : Vault n'a volontairement aucun port publié sur l'host (durcissement sécurité), donc `VAULT_ADDR=http://localhost:8200` ne peut pas fonctionner tel quel — il faut d'abord exposer temporairement le port :
```bash
docker compose exec vault vault read -field=role_id auth/approle/role/travel-service/role-id
docker compose exec vault vault write -f -field=secret_id auth/approle/role/travel-service/secret-id
docker compose port vault 8200 || echo "Vault n'a pas de port publié : ajoute temporairement '127.0.0.1:8200:8200' sous vault.ports dans docker-compose.yml puis 'docker compose up -d vault' avant de continuer"
```

Puis lance le service (Postgres est sur le port `5434`, pas le `5432` par défaut ; Neo4j exige une connexion chiffrée, `bolt://` seul échoue) :

```bash
cd backend/travel-service
DB_HOST=localhost DB_PORT=5434 DB_PASSWORD=<ton TRAVEL_DB_PASSWORD> \
NEO4J_URI=bolt+ssc://localhost:7687 NEO4J_PASSWORD=<ton NEO4J_PASSWORD> \
VAULT_ADDR=http://localhost:8200 VAULT_ROLE_ID=<role_id> VAULT_SECRET_ID=<secret_id> \
./mvnw spring-boot:run
```

Le service écoute sur `:8083` (voir [`05-api-gateway.md`](05-api-gateway.md) pour y accéder via le gateway plutôt qu'en direct). Toutes les routes exigent un JWT `ADMIN` émis par `auth-service`.

## Endpoints

| Méthode | Route | Body | Réponse |
|---|---|---|---|
| `GET` | `/api/travels` | — | liste de `TravelResponse` |
| `GET` | `/api/travels/{id}` | — | `TravelResponse`, `404` si absent |
| `POST` | `/api/travels` | `TravelRequest` | `201` + `TravelResponse` |
| `PUT` | `/api/travels/{id}` | `TravelRequest` | `200` + `TravelResponse` (remplace entièrement destinations/transports), `404` si absent |
| `DELETE` | `/api/travels/{id}` | — | `204`, `404` si absent |

`TravelRequest` (un voyage = un ou plusieurs destinations, chacune avec ses activités et son hébergement, plus les transports) :

```json
{
  "title": "Tour ibérique",
  "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "startDate": "2026-09-01",
  "endDate": "2026-09-10",
  "status": "PLANNED",
  "destinations": [
    {
      "city": "Lisbonne",
      "country": "Portugal",
      "arrivalDate": "2026-09-01",
      "departureDate": "2026-09-05",
      "orderIndex": 0,
      "activities": [
        { "name": "Tram 28", "date": "2026-09-02", "cost": 3.5 }
      ],
      "accommodation": {
        "name": "Alfama Hostel",
        "type": "HOSTEL",
        "address": "Rua de Sao Miguel 10",
        "checkIn": "2026-09-01",
        "checkOut": "2026-09-05"
      }
    }
  ],
  "transportations": [
    {
      "type": "FLIGHT",
      "fromLocation": "Paris CDG",
      "toLocation": "Lisbonne LIS",
      "departureTime": "2026-09-01T08:00:00Z",
      "arrivalTime": "2026-09-01T10:00:00Z",
      "provider": "TAP Air Portugal"
    }
  ]
}
```

`durationDays` dans la réponse est calculé (`endDate - startDate + 1`), jamais stocké.

## Erreurs gérées (`ApiExceptionHandler`)

| Cas | Code |
|---|---|
| `title` vide, `destinations` vide, dates absentes | `400` |
| Voyage introuvable | `404` |
| Violation d'intégrité (ex. double hébergement sur une même destination) | `409` |

## Ce qui est construit

| Fichier / package | Rôle |
|---|---|
| `domain/Travel.java`, `Destination.java`, `Activity.java`, `Accommodation.java`, `Transportation.java` | entités JPA — `Travel` racine, cascade `ALL` + `orphanRemoval` sur toute la hiérarchie |
| `db/migration/V1__create_travel_tables.sql` | schéma Flyway — chaque table enfant en `ON DELETE CASCADE` vers son parent |
| `graph/PlaceNode.java`, `RouteRelationship.java`, `PlaceRepository.java` | graphe Neo4j des destinations (`Place`) reliées par `ROUTE_TO`, avec un compteur `tripCount` |
| `graph/TravelGraphSyncService.java` | synchronise le graphe avec Postgres à la création/mise à jour/suppression d'un voyage |
| `service/TravelService.java` | orchestre l'écriture JPA + l'appel au sync Neo4j, remplace complètement destinations/transports sur `PUT` |
| `vault/VaultClient.java`, `security/*` | identique à `user-service` (Vault AppRole, JWT validation-only, `hasRole("ADMIN")`) |
| `web/TravelController.java` + DTOs | CRUD complet, DTOs imbriqués pour destinations/activités/hébergement/transport |
| `exception/TravelNotFoundException.java`, `exception/ApiExceptionHandler.java` | 404 / 409 / 400 |

## Nouveau par rapport à buy-02

Voir [`nouveautes-vs-buy02.md`](nouveautes-vs-buy02.md#travel-service-feattravel-service-crud) (persistance polyglotte Postgres + Neo4j, cascade entre les deux systèmes).

## Pourquoi ces choix

### Pourquoi Postgres pour la réservation et Neo4j pour les destinations, plutôt qu'un seul système
Un voyage (dates, activités, hébergement, transport) est fondamentalement relationnel — cascade delete/update, contraintes d'intégrité, transactions ACID : Postgres fait ça nativement. Mais "quelles destinations sont souvent enchaînées ensemble" est une question de traversée de graphe (chemins à profondeur variable) qu'un JOIN SQL récursif exprime mal et exécute lentement. Neo4j stocke un graphe cumulatif de `Place` (ville+pays) relié par `ROUTE_TO`, alimenté par tous les voyages, pour ce seul usage : la recommandation. Chaque système fait ce pour quoi il est fait.

### Pourquoi le graphe Neo4j n'est pas un miroir 1:1 de chaque `Destination`
Si dix voyages différents visitent Lisbonne, il ne doit y avoir qu'un seul nœud `Place("Lisbonne", "Portugal")` — sinon le graphe ne peut jamais répondre à "qu'est-ce qui suit Lisbonne en général". `TravelGraphSyncService` cherche ou crée le nœud par ville+pays, et incrémente `tripCount` sur la relation `ROUTE_TO` à chaque voyage qui enchaîne ces deux villes.

### Comment la cascade fonctionne *entre* Postgres et Neo4j (le point demandé par l'audit)
Il n'existe pas de transaction unique couvrant deux bases de nature différente sans 2PC/saga — hors scope ici. La cohérence est donc assurée par le code applicatif, pas par une contrainte native : `TravelService.delete()` lit d'abord les destinations du voyage, décrémente `tripCount` sur chaque relation `ROUTE_TO` correspondante (`TravelGraphSyncService.removeRoute`), supprime la relation si elle tombe à zéro, *puis* supprime le voyage en Postgres (qui cascade nativement vers destinations/activités/hébergement/transports). `update()` fait la même chose : retire l'ancien trajet du graphe avant d'enregistrer le nouveau. C'est une cohérence orchestrée, pas une contrainte de base — le point à expliciter à l'audit.

### Pourquoi `PUT` remplace entièrement les destinations/transports plutôt qu'un diff champ par champ
Fusionner intelligemment une liste de sous-objets imbriqués (quelle activité a changé, laquelle est nouvelle, laquelle a disparu) ajoute une complexité que rien ne justifie ici : un `PUT` est sémantiquement un remplacement complet. `attachDestinations`/`attachTransportations` vident la collection JPA (`orphanRemoval` supprime les anciennes lignes) et reconstruisent à partir de la requête — même idiome que `attachAddress` dans `user-service`, appliqué à des collections.

### Pourquoi `durationDays` est calculé plutôt que stocké
Une colonne dupliquerait une information dérivable de `startDate`/`endDate`, avec un risque de désynchronisation si l'une des deux dates change sans recalcul. `Travel.getDurationDays()` la recalcule à chaque lecture — jamais fausse, jamais à migrer si la formule change.
