# Nouveautés par rapport à buy-02

[← Sommaire](00-getting-started.md)

Ce fichier réunit, branche après branche, tout ce qui est vraiment nouveau par rapport à buy-02 — pour réviser vite avant l'audit sans rouvrir chaque page de doc.

## CI/CD (`chore/setup-jenkins`)

buy-02 avait déjà Jenkins + SonarQube (l'énoncé buy-02 l'imposait) — donc pas nouveau en soi. Ce qui change ici :

| Notion | buy-02 | Ici |
|---|---|---|
| Nombre de jobs/pipelines | une seule app, un seul job Jenkins | **5 microservices indépendants**, chacun buildé/scanné séparément dans le même pipeline |
| Exécution des builds | séquentielle (une seule app à la fois) | **en parallèle** (`parallel {}`) — builder plusieurs services ne coûte pas plus de temps qu'un seul |
| Quality Gate | probablement un seul projet SonarQube à vérifier | **un Quality Gate par service** (`travel-plan-auth-service`, `travel-plan-user-service`, ...), chacun avec son propre statut pass/fail |
| Blocage sur le Quality Gate | probablement le step Jenkins `waitForQualityGate` (dépend d'un webhook SonarQube → Jenkins) | **le scanner Maven lui-même** (`-Dsonar.qualitygate.wait=true`) sonde l'API SonarQube en boucle — pas de dépendance à un webhook, et pas de risque de confondre le rapport de deux services scannés en parallèle |

**Pourquoi le blocage a changé de mécanisme** (le point le plus subtil) : le step Jenkins `waitForQualityGate` cherche un fichier `report-task.txt` dans tout le workspace — avec 5 services scannés en parallèle, il en trouve plusieurs et n'en garde arbitrairement qu'un seul, donc les 4 autres services ne sont jamais vraiment vérifiés malgré un statut Jenkins vert. Faire porter le blocage par le process Maven de chaque service (qui reçoit son propre ID de tâche directement dans sa réponse HTTP, sans jamais lire de fichier partagé) élimine ce risque de confusion entre services.

## Infra applicative (`chore/setup-app-infra`)

Secret manager et traçage distribué sont réellement nouveaux. L'isolation des bases, elle, ne l'est **pas** — buy-02 avait déjà des bases MongoDB isolées par service (corrigé après une première version de cette page qui supposait le contraire à tort) :

| Notion | buy-02 | Ici |
|---|---|---|
| Bases de données | MongoDB, isolées par service | **Postgres** (une instance, une base par service) + **Neo4j** (pour `travel-service`) — changement de techno (relationnel + graphe plutôt que documents), pas d'isolation en plus |
| Isolation entre services | déjà le cas (bases Mongo séparées) | même principe, transposé à Postgres : un user dédié par service, qui ne peut pas lire les bases des autres |
| Secrets (mots de passe, tokens) | en dur / variables d'env simples | **HashiCorp Vault** (mode serveur réel, stockage persistant, TLS), chaque service authentifié via sa propre identité AppRole |
| Traçage d'une requête | pas de notion équivalente | **Zipkin** — chaque service instrumenté envoie ses traces, consultables dans une UI dédiée |

**Vault en mode serveur réel** (le point le plus nouveau) : stockage persistant (`file`, survit à un redémarrage), listener TLS, déverrouillage (unseal) via `ansible/playbooks/vault-unseal.yml`. Point de départ en mode dev pendant le développement initial, durci ensuite (voir `08-ansible-deploy-tls.md#pourquoi-vault-ne-tourne-plus-en-mode-dev`) — le mode dev n'est plus utilisé nulle part dans la stack actuelle.

## auth-service (`feat/auth-service-jwt`)

| Notion | buy-02 | Ici |
|---|---|---|
| Secrets (mots de passe, clé de signature) | en dur / variables d'env simples | récupérés dynamiquement depuis **HashiCorp Vault** au démarrage, jamais commités |
| Authentification service-à-service | — (pas de notion équivalente) | **Vault AppRole** : chaque microservice a sa propre identité (`role_id` + `secret_id`), pas un secret partagé entre tous |
| Où vit l'authentification | probablement intégrée au service principal | service **dédié et indépendant** (`auth-service`) : son seul travail est d'émettre/valider des JWT pour tous les autres |
| Schéma de base | génération automatique (`ddl-auto`) probable | **Flyway** : chaque changement de schéma est un fichier SQL versionné (`V1__...sql`), rejouable, traçable |
| Vérification des tokens | un seul service qui fait tout | **stateless** : n'importe quel service peut vérifier un JWT tout seul avec le secret Vault, sans appeler `auth-service` à chaque requête |

**AppRole en deux mots** (concept le plus nouveau) : c'est la façon dont Vault authentifie un *service*, pas un humain. Deux valeurs : `role_id` (fixe, identifie "quel service" — pas secret) et `secret_id` (le vrai secret, à garder confidentiel). Le service envoie les deux à Vault, reçoit un token temporaire en retour, et l'utilise pour lire *uniquement* ses propres secrets — jamais ceux des autres services (imposé par la policy `auth-service-policy`). À retenir pour l'audit : l'équivalent d'un compte de service à accès limité, pas un admin qui a accès à tout.

## user-service (`feat/user-service-crud`)

| Notion | buy-02 | Ici |
|---|---|---|
| Suppression en cascade | probablement gérée à la main (plusieurs requêtes) ou pas du tout | **cascade réelle à deux niveaux** : `ON DELETE CASCADE` en base (migration Flyway) + `cascade = CascadeType.ALL, orphanRemoval = true` côté JPA sur `User.address` |
| Vérification des tokens | un seul service qui fait tout | `user-service` ne fait que **valider** les JWT émis par `auth-service` (`JwtService` sans `generateToken`) — chaque microservice vérifie lui-même, sans appeler `auth-service` |
| Contrôle d'accès | probablement un seul niveau "connecté" | `hasRole("ADMIN")` explicite sur toutes les routes, pas juste "authentifié" |

**Cascade en deux mots** (concept le plus nouveau) : supprimer un `User` supprime automatiquement son `Address` associée, sans code applicatif dédié ni requête séparée. Démontré par un test (`UserRepositoryTest.deletingUserCascadesToAddress`) qui sauvegarde un `User` + `Address`, supprime le `User`, puis vérifie que l'`Address` a disparu de la base. À retenir pour l'audit : la cascade est vérifiée aux deux niveaux (base ET JPA), pas seulement l'un ou l'autre.

## api-gateway (`feat/api-gateway-routing`)

| Notion | buy-02 | Ici |
|---|---|---|
| Point d'entrée unique | probablement chaque service exposé directement | **API Gateway** (`spring-cloud-starter-gateway-server-webmvc`) : un seul point d'entrée, aucun service backend exposé directement au client |
| Répartition de charge | — (pas de notion équivalente, un seul backend) | **load balancing par nom logique de service** (`lb("auth-service")`), résolu via une liste d'instances configurable — ajouter une replica ne change aucune ligne de code |
| Vérification des tokens | un seul endroit qui vérifie tout | **défense en profondeur** : le gateway rejette tôt (authentification, token valide ou pas), chaque service re-vérifie en plus (autorisation, quel rôle a le droit de faire quoi) |
| Traçage d'une requête à travers plusieurs services | probablement un seul service, pas de notion de trace distribuée | **Zipkin** : chaque service (`auth-service`, `user-service`, `api-gateway`) envoie ses spans, une requête qui traverse gateway → service est visible comme une seule trace |

**Load balancing "sans registry" en deux mots** (concept le plus nouveau) : d'habitude le load balancing suppose un service de découverte (Eureka, Consul) où chaque instance s'enregistre/se désenregistre dynamiquement. Ici, `SimpleDiscoveryClient` (Spring Cloud Commons) donne le même mécanisme de résolution par nom (`lb://auth-service` → une des instances déclarées) à partir d'une simple liste dans `application.properties` — sans registry à faire tourner. À retenir pour l'audit : la haute disponibilité (plusieurs instances, bascule automatique) fonctionne déjà, la seule chose qui manque pour un vrai environnement multi-instances est de lancer réellement plusieurs process par service.

## travel-service (`feat/travel-service-crud`)

| Notion | buy-02 | Ici |
|---|---|---|
| Bases de données | MongoDB seul | **Postgres pour la réservation** (voyage, destinations, activités, hébergement, transport, cascade) **+ Neo4j pour les recommandations** (graphe de destinations enchaînées) — deux systèmes, chacun pour ce qu'il fait le mieux |
| Cascade delete/update | intra-MongoDB uniquement | intra-Postgres (comme `user-service`) **et** orchestrée entre Postgres et Neo4j par le code applicatif — pas de contrainte native cross-DB, donc la cohérence est un choix explicite du service, pas un mécanisme de base |
| Traversée de graphe ("quelles destinations vont souvent ensemble") | pas de notion équivalente | requête Cypher à profondeur variable (`ROUTE_TO*1..2`) — ce qu'un JOIN SQL récursif ferait mal et lentement |

**Pourquoi deux bases pour un seul service** (le point le plus nouveau, et celui que l'audit demande explicitement) : il n'existe pas de transaction ACID unique entre Postgres et Neo4j sans 2PC/saga (hors scope ici). `TravelService` écrit d'abord dans Postgres (source de vérité du voyage), puis appelle `TravelGraphSyncService` pour mettre à jour le graphe Neo4j (nœuds `Place` partagés entre tous les voyages, reliés par `ROUTE_TO` avec un compteur `tripCount`). Supprimer un voyage décrémente ce compteur avant de supprimer les lignes Postgres (cascade native) ; si le compteur tombe à zéro, la relation Neo4j disparaît aussi. À retenir pour l'audit : c'est une cohérence *orchestrée par le code*, explicitement présentée comme telle — pas une prétention à une transaction distribuée qui n'existe pas.

## payment-service (`feat/payment-service-crud`)

| Notion | buy-02 | Ici |
|---|---|---|
| Paiement | probablement une simulation ou un enregistrement simple | intégration réelle **Stripe et PayPal**, choisie dynamiquement par moyen de paiement (`PaymentProviderResolver`) via des appels HTTP directs (`RestClient`), pas de SDK officiel |
| Suppression en cascade | probablement systématique | **cascade délibérément absente** entre `Payment` et `PaymentMethod` (`ON DELETE SET NULL`, pas `CASCADE`) — un paiement est un enregistrement financier qui doit survivre à la suppression du moyen de paiement |
| Modification d'un paiement | probablement un `PUT` générique comme les autres entités | **aucun `PUT`/`DELETE`** sur `/api/payments` — un paiement ne se modifie pas, il se rembourse (`POST .../refund`), comme chez un vrai fournisseur de paiement |

**Pourquoi une seule entité "cascade" fait exception** (le point le plus nouveau) : jusqu'ici, chaque suppression en cascade démontrée (`User`/`Address`, `Travel`/`Destination`) l'était parce que l'enfant n'a aucun sens sans le parent. `Payment`/`PaymentMethod` est le contre-exemple volontaire : le paiement doit rester en base même si le moyen de paiement qui l'a produit disparaît (carte expirée retirée, par exemple), donc son FK passe à `NULL` au lieu de cascader — appliqué aux deux niveaux (Flyway et l'annotation Hibernate `@OnDelete(action = OnDeleteAction.SET_NULL)`, pour que le schéma des tests corresponde au schéma réel) et vérifié par un test dédié. À retenir pour l'audit : ce n'est pas un oubli de cascade, c'est la démonstration que la cascade est un choix de modélisation, pas un réflexe systématique.

## Ansible + TLS (`chore/ansible-deploy-tls`)

| Notion | buy-02 | Ici |
|---|---|---|
| Déploiement | probablement manuel (lancer chaque service à la main) | **Ansible** — 3 playbooks (installer Docker, déployer, récupérer les secrets Vault), enchaînés par `site.yml`, rejouables sans casser l'existant |
| Conteneurisation des microservices | — (pas de notion équivalente si buy-02 n'avait qu'une seule app) | chaque microservice a son **Dockerfile** (build multi-stage Maven→JRE, utilisateur non-root) et tourne en conteneur, pas juste l'infra (Postgres/Neo4j/Vault) |
| Répartition de charge entre répliques | un seul processus par service | `deploy.replicas: 2` par microservice dans `docker-compose.yml` — Docker répartit les requêtes entre répliques via son DNS interne, sans changer une ligne de `RouteConfig.java` |
| Chiffrement du trafic | HTTP en clair | **Nginx** en frontal, termine le TLS (certificat auto-signé en dev), seul point exposé sur Internet |

**Le point le plus subtil : pourquoi `deploy.yml` s'exécute deux fois dans `site.yml`.** Chaque microservice a besoin d'identifiants Vault pour démarrer, mais Vault doit d'abord tourner et être initialisé pour que ces identifiants existent — un vrai problème d'œuf et de poule. Premier passage : la stack démarre, les microservices échouent faute d'identifiants (ils redémarrent en boucle sans rien casser). Un playbook dédié récupère alors les vrais identifiants et les écrit dans `.env`. Second passage : Docker Compose ne recrée que les conteneurs dont la configuration a changé (les microservices), qui démarrent cette fois avec de vrais identifiants. À retenir pour l'audit : c'est une dépendance résolue par étapes explicites et idempotentes, pas un script fragile avec un `sleep` au hasard.
