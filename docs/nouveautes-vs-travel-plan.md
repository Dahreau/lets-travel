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

## `feat/traveler-experience` — feedback, signalement, inscription publique traveler

### Ce que Let's Travel ajoute

**Feedback sur voyage.** Nouvelle entité `Feedback` (`travel-service`, migration
`V5__create_feedbacks_and_reports_tables.sql`), même pattern que `Subscription` (vraie relation
JPA vers `Travel`, `travelerId` en UUID nu vers `user-service`). `POST /api/travels/{id}/feedbacks`
(TRAVELER) exige une participation prouvée (`SubscriptionRepository.existsByTravel_IdAndTravelerId`,
n'importe quel statut — actif ou annulé, l'important est d'avoir été inscrit) et que le voyage
soit terminé (`travel.endDate` déjà passé — l'énoncé parle de "leur expérience de voyage", donc
après coup), un seul avis par `(travel, traveler)` (contrôle applicatif + index unique en base,
défense en profondeur). `GET /api/travels/{id}/feedbacks` réservé au Travel Manager propriétaire
+ Admin, pour le contrôle qualité (item audit dédié).

**Signalement (report) d'un manager ou d'un autre traveler.** Nouvelle entité `Report`, même
service/migration. `POST /api/travels/{id}/reports` (TRAVELER, doit avoir participé au travel)
accepte `reportedType` (`MANAGER`/`TRAVELER`) + `reportedId`, avec cohérence vérifiée côté
serveur : pour `MANAGER`, `reportedId` doit être le manager de CE travel ; pour `TRAVELER`,
`reportedId` doit être un autre abonné du MEME travel (jamais soi-même). `GET /api/reports`
réservé à l'Admin seul (modération globale, tous travels confondus) — volontairement PAS
accessible au Travel Manager concerné, sinon le mécanisme de signalement perdrait son sens. Le
comptage public de signalements par manager (page publique manager, item backlog séparé) n'est
pas construit ici — ce sera un endpoint dédié quand la branche dashboard manager sera abordée.

**Inscription publique traveler, en 2 appels plutôt qu'un appel inter-service orchestré.**
Décision de conception à noter : contrairement au flux payment-service → travel-service
(branche précédente), l'inscription publique N'introduit PAS un 2e appel HTTP inter-service.
`POST /api/users/register` (`user-service`, `permitAll`, rôle toujours forcé à `TRAVELER` —
`UserRegistrationRequest` n'a pas de champ `role`) crée d'abord le profil `User` ; l'appelant
utilise l'`id` renvoyé pour appeler ensuite `POST /api/auth/register` (`auth-service`,
`permitAll`, `RegisterRequest.userId`) qui crée l'`Account` (rôle forcé `TRAVELER`) et renvoie
directement un JWT de connexion (même réponse que `/login` — évite un aller-retour de plus).
Choix délibéré plutôt que de répliquer le pattern `TravelServiceClient` une 3e fois : au moment
de l'inscription, l'appelant n'a par définition aucun JWT encore émis, donc `auth-service`
aurait dû s'auto-émettre un token technique `ADMIN` pour appeler l'endpoint `POST /api/users`
existant (`ADMIN`-only) — mécanisme de compte de service qui n'existe nulle part ailleurs dans
le projet et qui aurait ajouté un vrai risque (transaction non atomique entre 2 bases, JWT
technique à sécuriser) pour un gain minime. Le flux en 2 appels publics est strictement le même
niveau de confiance que l'existant : `POST /api/auth/accounts` (admin-only, préexistant)
n'a jamais vérifié que le `userId` fourni existe réellement côté `user-service` non plus.
**Limite connue, assumée** : pas d'atomicité entre les 2 appels — un `User` peut se retrouver
sans `Account` si le 2e appel échoue (ex : username déjà pris, détecté explicitement côté
`auth-service` avant l'insertion pour renvoyer 409 plutôt qu'un 500 issu de la contrainte unique
DB). Acceptable pour ce projet : pas de nettoyage automatique prévu, à traiter manuellement si
ça arrive en pratique.

## `feat/manager-frontend` — dashboard manager, gestion des abonnés, profil public

### Avant (travel-plan)

Cette fonctionnalité n'existait pas : il n'y avait qu'un rôle `ADMIN`/`USER`, donc pas de
notion de "tableau de bord d'un gestionnaire" ni de page publique consultable avant de
s'abonner à un voyage.

### Ce que Let's Travel ajoute

**Tableau de bord privé du manager (`GET /api/travels/managers/me/stats`).** Nouveau
`ManagerStatsService`/`ManagerStatsController` dans `travel-service` : nombre de voyages
(`countByManagerId`), nombre de voyageurs actifs distincts tous voyages confondus
(`countDistinctTravelersByManagerIdAndStatus`) et un revenu **estimé**. Volontairement une
estimation (prix × nombre d'abonnés actifs, sommé par voyage) et pas une réconciliation avec
les vrais paiements de `payment-service` : croiser les deux bases pour un simple tableau de
bord aurait ajouté un 2e appel inter-service (après celui de
`feat/travel-pricing-and-traveler-payment`) pour un gain marginal — un paiement remboursé ou
en échec resterait de toute façon compté ici, ce qui est documenté comme limite assumée plutôt
que traité. Réservé au manager connecté lui-même (vérifié dans le service, pas seulement par
`SecurityConfig`) : un `ADMIN` n'a pas de fiche `User` propre, donc pas de "ses" voyages à
afficher — appeler cette route avec un token `ADMIN` renvoie un 403 explicite plutôt que des
statistiques vides trompeuses.

**Page publique manager (`GET /api/travels/managers/{managerId}/public-stats`).** Demandée par
l'énoncé (section Traveler : "Access a Travel Manager page to view statistics, past travel
ratings, and the number of reports"). Ouverte à tout utilisateur authentifié (pas de contrôle
de propriété : c'est une fiche publique), volontairement sans vérifier que `managerId` existe
réellement — un id inconnu renvoie juste des compteurs à zéro/`null`, pas une 404, pour ne pas
révéler par effet de bord si un id correspond ou non à un compte réel. `averageRating` reste
`null` tant qu'aucun feedback n'existe, jamais `0` (qui laisserait croire à une mauvaise note
plutôt qu'à une absence de donnée). C'est l'endpoint que `feat/traveler-experience` avait
explicitement laissé de côté (voir section précédente).

**Frontend : un même Angular admin-tool, maintenant partagé avec les Travel Managers.**
Jusqu'ici, l'app Angular de ce repo n'était qu'un back-office `ADMIN` (badge "admin" en dur
dans la topbar, aucun traveler ni manager ne s'y connectait). Cette branche l'ouvre aux comptes
`TRAVEL_MANAGER` sans dupliquer l'app : la route `/dashboard` bascule son contenu selon le rôle
connecté (`Dashboard.isManager`) plutôt que d'introduire une route dédiée, et la barre latérale
(`Shell.navItems`) n'affiche les outils `users`/`travels`/`payments`/`payment-methods` qu'à un
`ADMIN` — un manager n'a que `dashboard`, qui devient sa propre vue (stats + ses voyages, avec
liens vers l'édition et vers la nouvelle page `/manager/travels/:id` — abonnés + feedback de CE
voyage, gardée par `managerGuard`). La page publique (`/manager/:managerId`) n'a pas de garde
de rôle particulière (cohérent avec la route backend, ouverte à tout authentifié) ; en
attendant que `feat/traveler-frontend` ajoute un vrai parcours de navigation/abonnement, le
tableau `/travels` sert de point d'entrée provisoire (la colonne manager y est déjà un lien
vers cette page).

**Trois bugs de dérive frontend/backend découverts et corrigés au passage** (voir
`troubleshooting.md` #14-#16, pas des ajouts "bonus" mais des blocages pour cette branche même) :
le champ `ownerId`/`managerId` et l'absence de `price`/`currency` dans le formulaire de voyage,
l'absence du rôle `TRAVEL_MANAGER` dans le formulaire de création d'utilisateur, et
`GET /api/users/{id}` fermé aux managers côté `user-service` (nécessaire pour afficher le
profil d'un abonné dans la liste). Sans ces trois corrections, il n'y avait tout simplement
aucun moyen de créer un compte manager, de lui faire créer un voyage valide, ni d'afficher qui
y est abonné.

**Ce qui reste volontairement absent de cette branche.** Pas de recalcul de `estimatedRevenue`
en cas de remboursement (voir plus haut), pas de pagination sur la liste d'abonnés/feedback
(hors scope audit), et pas de lien de navigation traveler → profil manager en dehors du tableau
`/travels` — ce sera ajouté naturellement par `feat/traveler-frontend` quand le parcours de
navigation/abonnement du Traveler sera construit.
