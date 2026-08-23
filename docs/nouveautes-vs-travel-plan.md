# Nouveautés par rapport à travel-plan

[← Sommaire](00-getting-started.md)

Ce document explique les concepts introduits dans Let's Travel qui n'existaient pas dans le
projet précédent (travel-plan), branche par branche. L'idée n'est pas de tout redocumenter,
mais de donner les clés pour comprendre *pourquoi* certains choix ont été faits, notamment
pour la partie sécurité/autorisation qui est plus riche ici.

## `feat/travel-manager-role` — généralisation des rôles et propriété des voyages

### Avant (travel-plan)

Dans travel-plan, il n'y avait que deux rôles : `USER` et `ADMIN`. Un `Travel` n'appartenait
à personne en particulier côté métier : n'importe quel `ADMIN` pouvait tout gérer, et un
`USER` ne pouvait que consulter/s'inscrire.

### Ce que Let's Travel ajoute

Let's Travel introduit un troisième rôle, `TRAVEL_MANAGER`, entre `TRAVELER` (équivalent de
l'ancien `USER`) et `ADMIN`. Un Travel Manager peut créer et gérer **ses propres** voyages,
mais pas ceux des autres managers — contrairement à un ADMIN qui peut tout gérer.

Deux mécanismes rendent ça possible, absents de travel-plan :

**1. `RoleHierarchy` de Spring Security** (`SecurityConfig.roleHierarchy()`). Plutôt que de
dupliquer les règles d'accès pour chaque rôle, on déclare une hiérarchie :
`ADMIN` implique `TRAVEL_MANAGER` implique `TRAVELER`. Un ADMIN passe donc automatiquement
tous les contrôles `hasRole(TRAVELER_ROLE)` ou `hasRole(TRAVEL_MANAGER_ROLE)` sans qu'on ait
besoin d'écrire `hasAnyRole(ADMIN, TRAVEL_MANAGER, TRAVELER)` partout. C'est une fonctionnalité
Spring Security standard (disponible nativement pour `hasRole`/`hasAnyRole` et `@PreAuthorize`
depuis Spring Security 6.3), pas quelque chose développé à la main.

**2. La propriété des ressources (`ownerId` → `managerId`)**. La `RoleHierarchy` seule ne
suffit pas : elle dit "un Travel Manager peut accéder aux routes de gestion des voyages", mais
pas "seulement les siens". Ce deuxième niveau de contrôle — vérifier que
`travel.getManagerId().equals(caller.userId())` — est fait à la main dans `TravelService`,
parce que `HttpSecurity` (la configuration déclarative des routes) ne connaît pas encore
l'identité de la ressource visée au moment où elle décide d'autoriser ou non la requête.

Au passage, `Travel.ownerId` a été renommé `Travel.managerId` pour refléter ce changement de
sens : ce n'est plus juste "qui a créé l'entrée", c'est "qui est responsable du voyage".

**3. Généralisation `User` → `Account`.** Pour que l'authentification gère les trois rôles de
façon uniforme (et pas seulement admin/user), le modèle de compte a été généralisé côté
`auth-service`. Un compte porte un rôle (`TRAVELER`, `TRAVEL_MANAGER` ou `ADMIN`), et le JWT
émis contient ce rôle ainsi que le `userId` associé (quand il existe — voir plus bas pourquoi
ce n'est pas toujours le cas).

## `feat/traveler-subscriptions` — abonnement d'un Traveler à un voyage

### Avant (travel-plan)

Cette fonctionnalité n'existait pas du tout : rien ne permettait à un utilisateur de
s'inscrire à un voyage, ni à un gestionnaire de voir qui était inscrit.

### Ce que Let's Travel ajoute

Un nouveau domaine `Subscription` dans `travel-service`, avec trois routes :
`POST /api/travels/{id}/subscriptions` (s'abonner), `DELETE .../subscriptions/{id}`
(se désabonner) et `GET .../subscriptions` (lister les abonnés, réservé au manager
propriétaire + admin).

Quelques choix qui valent la peine d'être expliqués :

**Relation JPA réelle vs référence cross-service.** `Subscription.travel` est une vraie
relation `@ManyToOne` vers l'entité `Travel` (avec `FOREIGN KEY ... ON DELETE CASCADE` en
base), parce que `Subscription` et `Travel` vivent dans la même base de données
(`travel-service`). En revanche, `Subscription.travelerId` reste un simple UUID, sans FK,
parce qu'il pointe vers `User` dans `user-service` — une autre base de données, donc pas de
clé étrangère possible. C'est le même pattern que `Travel.managerId`, déjà utilisé pour
référencer `user-service` depuis `travel-service`.

**Pourquoi un ADMIN ne peut pas s'abonner.** Le compte ADMIN par défaut (créé au démarrage de
`auth-service`) n'a pas de fiche `User` associée dans `user-service` — il n'a donc pas de
`userId`. Comme un abonnement doit être rattaché à un traveler concret, tenter de s'abonner
avec un compte sans `userId` renvoie une erreur 400 (`InvalidSubscriptionRequestException`)
plutôt qu'une exception technique.

**Le délai de 3 jours avant annulation.** Demandé explicitement par l'énoncé : on ne peut plus
annuler son abonnement à moins de 3 jours du départ du voyage
(`LocalDate.now().isAfter(travel.getStartDate().minusDays(3))`). Cette règle s'applique de la
même façon à tout le monde — traveler, manager ou admin — il n'y a pas de passe-droit.

**Qui peut annuler l'abonnement de qui.** Un ADMIN peut toujours annuler n'importe quel
abonnement. Un Travel Manager peut annuler un abonnement, mais seulement sur un voyage dont il
est le propriétaire (`travel.getManagerId()`). Un Traveler ne peut annuler que son propre
abonnement. Toute autre combinaison renvoie un 403 (`ForbiddenException`).

**Idempotence du désabonnement.** Annuler un abonnement déjà annulé ne renvoie pas d'erreur —
c'est un no-op silencieux. Ça évite des erreurs 409 inutiles côté frontend si l'utilisateur
clique deux fois, ou si deux requêtes arrivent en parallèle.

**Ordre des règles dans `SecurityConfig`.** Point technique important si vous retouchez ce
fichier un jour : les règles spécifiques aux routes `/subscriptions` sont déclarées **avant**
les règles génériques `/api/travels/**`, parce que Spring Security évalue les matchers dans
l'ordre de déclaration et s'arrête au premier qui correspond. Sans ça, la règle générique
`DELETE /api/travels/**` (réservée à ADMIN/TRAVEL_MANAGER) interceptait en premier un
`DELETE .../subscriptions/{id}` et empêchait un simple Traveler d'annuler son propre
abonnement.

## `feat/travel-pricing-and-traveler-payment` — prix réel du voyage et premier appel inter-service

### Avant (travel-plan)

`Travel` n'avait pas de prix du tout : `PaymentRequest` demandait directement un `amount` et
une `currency` au client, et `payment-service` chargeait ce montant tel quel auprès de
Stripe/PayPal, sans jamais le confronter à quoi que ce soit côté `travel-service`. N'importe
quel appelant pouvait donc payer le montant de son choix pour n'importe quel voyage — un vrai
trou de sécurité qu'on a choisi de corriger ici plutôt que de le reproduire.

### Ce que Let's Travel ajoute

**Un vrai prix persisté sur `Travel`.** `price` (`BigDecimal`) et `currency` (`String`,
migration `V4__add_price_to_travels.sql`) sont désormais des champs réels de `Travel`, fixés
par le Travel Manager à la création/modification (`TravelRequest.price`/`currency`,
`@NotNull @Positive`). Volontairement laissés `NULL`-ables en base et en JPA (pas de
`NOT NULL`/`DEFAULT` dans la migration, pas de `nullable = false` sur l'entité) pour ne pas
casser les voyages déjà existants sous `spring.jpa.hibernate.ddl-auto=validate` — un voyage créé
avant cette migration a `price = NULL` jusqu'à ce qu'un manager l'édite.

**Le premier appel HTTP inter-service du projet.** Jusqu'ici, aucun microservice n'appelait
directement un autre microservice de façon synchrone — seul `api-gateway` savait faire du
load balancing entre répliques (voir `spring-cloud-starter-loadbalancer` dans son `pom.xml`).
`payment-service` reproduit exactement ce même mécanisme (2 instances déclarées par service,
`RestClient.Builder` cloné + `@LoadBalanced`, bundle SSL hérité en profil docker — voir
`client/TravelServiceClientConfig.java`) pour appeler `GET /api/travels/{id}` sur
`travel-service` et récupérer le prix réel avant de facturer quoi que ce soit. `amount` et
`currency` ont été retirés de `PaymentRequest` : le montant vient uniquement de
`travel-service`, plus jamais du client. Si le voyage n'a pas encore de prix (`price`/`currency`
nuls), la création du paiement échoue en 409 (`TravelPriceNotSetException`) plutôt que de
deviner un montant.

**Propagation du JWT plutôt qu'un compte de service.** Le header `Authorization` du traveler
appelant est transmis tel quel de `payment-service` vers `travel-service`
(`PaymentController.create` reçoit `@RequestHeader(AUTHORIZATION)` et le fait suivre) — pas de
mécanisme séparé de JWT technique/service-account. C'est ce même JWT que `travel-service`
valide déjà pour ses propres routes. Conséquence directe : les routes `GET /api/travels/**` de
`travel-service`, jusque-là réservées à `ADMIN`, ont dû être ouvertes à `TRAVELER` (déjà
nécessaire pour la consultation des abonnements, voir section précédente — cette branche
réutilise la même ouverture).

**RBAC ajouté à `payment-service`, quasi inexistant avant.** `payment-service` n'avait ni
`userId` dans son JWT, ni `AuthenticatedUser`, ni `RoleHierarchy` : tout était `ADMIN`-only
(`anyRequest().hasRole("ADMIN")`). Cette branche réplique le mécanisme déjà en place dans
`travel-service` — `resolveOwnerId()` (`PaymentService`/`PaymentMethodService`, copie de
`TravelService.resolveManagerId()`) force `ownerId = caller.userId()` pour tout appelant non-ADMIN,
et exige un `ownerId` explicite pour un ADMIN. `PaymentMethodService.findAll()` filtre
désormais par propriétaire (`findByOwnerId`) sauf pour un ADMIN. `PaymentService.findAll()`
(liste complète non filtrée) reste volontairement ADMIN-only — filtrer la liste complète des
paiements n'était pas nécessaire pour fermer le trou de sécurité visé par cette branche.
