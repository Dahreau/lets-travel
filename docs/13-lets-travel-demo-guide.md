# Guide de démo pour l'oral d'audit — lets-travel

[← Sommaire](00-getting-started.md)

Ce guide remplace, pour **lets-travel**, l'ancien `10-audit-demo-guide.md` (qui documentait
`travel-plan`, le projet précédent — champs `ownerId`, pas d'Elasticsearch/Neo4j/abonnements/
feedback/reports côté Functional. Gardé pour référence historique, mais ne pas s'y fier pour
cet audit-ci). Une entrée par item de `docs/lets-travel_audit.md` (même formulation), avec
réponse courte + preuve code + manip UI/API à faire en vrai. Chaque comportement décrit ici a
été testé en direct sur la stack le 28/08/2026.

## Prérequis

Stack up (`./scripts/start-app.sh`, puis `docker compose ps` → tout `Healthy`). Ouvrir
`https://localhost:8443` (avertissement de certificat auto-signé à accepter une fois).

Comptes disponibles pour la démo :

- **Admin** (déjà seedé) : `admin` / mot de passe = `DEFAULT_ADMIN_PASSWORD` dans `.env`.
- **Traveler** : inscription libre sur `/register` — crée toujours un compte `TRAVELER`
  (impossible de s'auto-inscrire comme Manager, voir plus bas la limitation connue).
- **Manager** : **aucun moyen de créer un compte Manager fonctionnel depuis le site** pour
  l'instant (voir "Limitation connue" en fin de doc). En attendant, réutiliser le manager déjà
  créé par les tests e2e : lancer `cd e2e && npx playwright test` une fois, puis lire
  `e2e/.fixtures/run.json` → `managerUsername`/`managerPassword`.

Pour générer rapidement plusieurs voyages variés (pays/prix/durée différents, utile pour
démontrer les recommandations) : `./scripts/seed-lets-travel-demo.sh` (réutilise le manager
e2e ci-dessus par défaut, relançable sans risque de doublon).

---

## Comprehension

### 1. Comment Elasticsearch contribue à la recherche et à l'autocomplete

Un seul index `travels` (mapping dynamique, pas de mapping custom). La recherche
(`GET /api/travels/search?q=...`) fait un `multi_match` sur `title`/`cities`/`countries` ;
l'autocomplete (`GET /api/travels/autocomplete?q=...`) fait un `multi_match` en mode
`bool_prefix` sur `title`/`cities`. Code : `TravelSearchService.java`. Chaque `create`/`update`/
`delete` de voyage réindexe immédiatement (`TravelService.java` lignes 66-67, 92-93, 101-102).

**Point important testé en live** : la recherche matche aussi sur les **villes des
destinations**, pas seulement le titre — chercher "paris" remonte tous les voyages qui passent
par Paris même si "Paris" n'apparaît pas dans leur titre. C'est voulu (l'énoncé demande une
recherche "across all travel details").

**Si un voyage tout juste créé n'apparaît pas immédiatement en recherche** : Elasticsearch a un
délai de rafraîchissement (~1 seconde) avant qu'un document soit cherchable. Retenter la
recherche 1-2 secondes après la création suffit. Vérifier aussi que le nom de ville tapé à la
création est bien celui recherché (accents/langue : "Barcelone" ≠ "Barcelona" pour un
`multi_match` standard).

### 2. Le rôle de Neo4j dans les suggestions personnalisées

Graphe séparé de Postgres : `RecommendationRepository` (Cypher). Chaque abonnement/avis
alimente le graphe en temps réel (`SubscriptionService`/`FeedbackService`, via
`RecommendationSyncService`). Voir la section "Comment marchent les recommandations" plus bas
pour l'algorithme en clair.

### 3. Scalabilité et indépendance d'Elasticsearch/Neo4j

Conteneurs Docker séparés, volumes séparés (`elasticsearch_data`, `neo4j_data`), aucune
dépendance opérationnelle avec Postgres ni l'un avec l'autre — `docker-compose.yml`.

### 4. Cohérence des données entre Postgres/Neo4j/Elasticsearch

Écriture synchrone, dans la même transaction que le `save()` Postgres : `TravelService.create()`/
`.update()` appellent `searchService.index()` et `recommendationSyncService.upsertTravel()` juste
après `travelRepository.save()`, dans une méthode `@Transactional`. Si l'écriture ES ou Neo4j
échoue, toute la requête échoue (rollback Postgres inclus) — pas de désynchronisation silencieuse.

### 5. Fonctionnalités et permissions par rôle

Hiérarchie explicite : `RoleHierarchy` (`travel-service/.../SecurityConfig.java` lignes 94-100) —
`ADMIN` hérite de `TRAVEL_MANAGER`, qui hérite de `TRAVELER`. Reflète directement l'énoncé
("Admin ... all actions available to Travel Managers and Travelers").

**Nuance testée en live, importante à savoir dire à l'oral** : l'ADMIN par défaut (créé au
démarrage) n'a **pas de fiche `User`/traveler liée** (`userId` null) — c'est volontaire, un
compte de supervision n'a pas d'historique personnel. Résultat concret : l'ADMIN a bien le
*droit* d'appeler n'importe quelle route Traveler (RBAC/RoleHierarchy), mais une action
**personnelle** comme "voir mes recommandations" ou "mon tableau de bord Traveler" échoue avec
une erreur 400 explicite ("Un profil traveler lié est requis...") plutôt qu'un plantage — parce
que ces actions supposent une identité de voyageur avec un historique, ce que l'ADMIN n'a pas
par construction. Ce n'est pas un manque de permission (le RBAC est bon), c'est l'absence
d'identité "voyageur" pour ce compte précis. Un ADMIN qui veut tester le parcours Traveler
complet doit le faire avec un vrai compte Traveler (auto-inscription).

---

## Functional

### 6-7. Recherche et autocomplete Elasticsearch

Testé en live : chercher "paris" en étant connecté (n'importe quel rôle) remonte tous les
voyages passant par Paris, résultats quasi instantanés. Preuve code : `TravelSearchService.java`
lignes 43-54 (voir item 1 ci-dessus pour le point sur le délai d'indexation).

### 8. Recommandations Neo4j (précision, historique)

**Comment ça marche, en clair** : pour un Traveler donné, on regarde tous les voyages auxquels
il a participé ou qu'il a notés. Chaque voyage "aimé" a un poids : 1 point s'il y a juste
participé (pas de note), ou `(note - 3)` s'il l'a noté — donc 4-5★ pèse plus qu'une simple
participation, 3★ est neutre, 1-2★ devient négatif et est exclu du calcul. Pour chaque autre
voyage disponible (jamais participé), son score = somme des poids des voyages "aimés" qui lui
ressemblent sur au moins un des 3 champs : `country`, `priceRange` (BUDGET <500€, STANDARD
<1500€, PREMIUM ≥1500€) ou `durationRange` (SHORT ≤3j, MEDIUM ≤7j, LONG >7j). Les 10 meilleurs
scores sont recommandés. Code : `RecommendationRepository.recommendTravelIds()`.

**Testé en live avec 2 comptes différents** (user10 : 4 étoiles sur un voyage abonné + 3 autres
abonnements ; user11 : 2 étoiles "NUL A CHIER" sur un voyage partagé avec user10 + d'autres
abonnements différents) : les listes de recommandations des deux comptes sont bien différentes
— comportement confirmé conforme à l'énoncé (Neo4j "evaluating user feedback and past
participation" + test avec 2 comptes différents demandé explicitement par l'audit).

Pour bien démontrer ce point à l'oral, utiliser `./scripts/seed-lets-travel-demo.sh` d'abord
(voyages avec pays/prix/durées variés), puis créer 2 comptes Traveler qui s'abonnent/notent des
sous-ensembles différents de ces voyages.

### 9. Dashboard Admin complet

`/dashboard` en étant connecté ADMIN : classement managers (score = note moyenne×10 + revenu/100
- signalements×5), classement voyages, revenu mensuel estimé (6 derniers mois), file de
modération des signalements. Couvre les 5 puces de la section Admin de l'énoncé.

**Point testé en live à expliquer si on te pousse dessus** : le "revenu des derniers mois"
affiché est une **estimation** (prix du voyage × nombre d'abonnés actifs, groupé par mois
d'abonnement), **pas une réconciliation avec les vrais paiements Stripe/PayPal** — documenté
directement dans le code (`AdminStatsService.java`, commentaire sur `monthlyRevenue()`). C'est
pour ça que ce chiffre ne correspondra jamais exactement à ce qu'affiche le dashboard Stripe :
Stripe totalise les vrais paiements capturés (et en mode test, potentiellement mélangés avec
d'anciens tests d'un autre projet sur le même compte Stripe test) ; l'app calcule une estimation
métier à partir des abonnements Postgres. Les deux chiffres mesurent des choses différentes,
volontairement — c'est un choix assumé (dupliquer le call réseau vers payment-service pour
chaque ligne du dashboard aurait été plus lent et plus fragile), pas un bug.

De même, "0€" sur tous les mois sauf le mois courant est normal si toutes tes données de test
ont été créées aujourd'hui (tous les abonnements retombent dans le même mois calendaire).

### 10. Statistiques Travel Manager

Dashboard Manager (`/dashboard` en étant connecté TRAVEL_MANAGER) : nombre de voyages, nombre de
voyageurs distincts, revenu estimé, détail par voyage (abonnés, note moyenne, nb d'avis). Code :
`ManagerStatsService.myStats()`.

### 11. Dashboard Traveler (recommandations + historique)

Tout sur une seule page (`/dashboard` en étant connecté TRAVELER) : recommandations, historique
d'abonnements, avis laissés, signalements déposés, moyens de paiement. Code :
`dashboard.ts` → `loadTravelerDashboard()`.

### 12. Navigation et abonnement Traveler

`/browse` : recherche + liste de tous les voyages + bouton "s'abonner"/"abonné" par ligne.
**Un abonnement sert à** : réserver sa place (visible dans "historique d'abonnements"), pouvoir
ensuite payer le voyage, et alimenter le moteur de recommandations (voir item 8) — c'est la
"participation" qui nourrit les recos.

### 13. Cutoff d'annulation à 3 jours

`SubscriptionService.isPastCancellationCutoff()` : annulation refusée si on est à moins de 3
jours du départ (`CANCELLATION_CUTOFF_DAYS = 3`). **Pour tester** : s'abonner à un voyage dont le
`startDate` est dans 1-2 jours (le script de seed en crée un exprès, "Depart imminent"), puis
essayer de se désabonner → doit être refusé avec un message explicite. Un voyage dont le départ
est dans plus de 3 jours doit, lui, se désabonner normalement. (Aucun rapport avec un voyage aux
dates invalides — la validation `dateRangeValid` qui bloque la création d'un voyage buggé est un
sujet complètement différent, voir item "Validation des entrées" plus bas.)

### 14. Paiement sécurisé, plusieurs méthodes

`/payment-methods/new` : Stripe et PayPal (`ProviderType.java`). Pour Stripe en test, utiliser
`pm_card_visa` (ID de test permanent fourni par Stripe, jamais une vraie carte — voir
[docs.stripe.com/testing](https://docs.stripe.com/testing)). Payer un voyage abonné :
`/payments/new` ou directement depuis la page détail du voyage (`/browse/:id`).

### 15-16. Feedback : soumission et visibilité Manager/Admin

**Un avis ne peut être laissé qu'après la fin du voyage** (`FeedbackService.submit()` vérifie
`LocalDate.now() >= travel.getEndDate()`), et seulement sur un voyage auquel on a été abonné.
Testé en live : impossible de laisser un avis sur un voyage qui n'est pas encore terminé (le
formulaire d'avis n'apparaît/ne fonctionne que sur les voyages déjà passés) — comportement
voulu, pas un bug. Le script de seed crée un voyage déjà terminé exprès pour tester ça tout de
suite sans attendre. Visible ensuite par le Manager propriétaire (page "abonnés & feedback") et
l'Admin (dashboard).

### 17-19. Manager : gestion de ses voyages, abonnés, analytics

Dashboard Manager → "+ nouveau voyage" (uniquement visible sur `/dashboard`, pas encore sur la
page `/travels` — les deux pointent vers le même formulaire `/travels/new`). Page
"abonnés & feedback" d'un voyage (lien direct depuis le dashboard Manager ou Admin) : liste des
abonnés avec bouton désabonner, feedbacks reçus.

### 20. Profil Traveler complet

Dashboard Traveler : compteurs (participations, avis, désabonnements, signalements) +
historique détaillé de chaque catégorie, tout sur une page.

### 21-22. Login et RBAC

BCrypt + JWT (clé Vault) + rate-limit nginx sur `/api/auth/login` (5/min). RBAC vérifié à deux
niveaux : matchers Spring Security par rôle **et** contrôles explicites dans les services pour
les actions "propriétaire uniquement" (ex : un Manager ne peut voir/gérer que ses propres
voyages, même s'il a techniquement le droit d'appeler la route).

### 23. SSL/TLS

**Chiffré de bout en bout, sans exception** : navigateur↔nginx, nginx↔gateway, gateway↔chacun
des 4 microservices (`server.ssl.bundle=internal-services` sur chaque service +
`GatewayHttpClientConfig.java` côté gateway), services↔Postgres (`sslmode=require`),
travel-service↔Neo4j (`bolt+ssc://`). Vérifiable : `grep -rn "ssl" backend/*/src/main/resources/
application-docker.properties`.

### 24. Secrets et données sensibles

Vault (JWT, Stripe, PayPal) — jamais en dur dans le code ni dans les fichiers commités
(`.env`/certs/clé de descellement dans `.gitignore`).

### 25. Mécanisme de repli en cas de panne

`CircuitBreaker.java` (payment-service) : coupe les appels vers travel-service après 5 échecs
consécutifs, réessaie après 30s. Retry automatique borné aux pannes transitoires (502/503/504)
avant d'ouvrir le circuit. Code : `TravelServiceClient.java`.

### 26-27. UI responsive et navigation

Sidebar qui se transforme en menu burger sous 860px (`shell.scss`). Formulaires qui passent en
1 colonne sous 720px. Testé en live : redimensionner la fenêtre ou passer en mode mobile dans
les DevTools.

### 28. Conformité protection des données

Page `/politique-de-confidentialite` (données collectées, base légale, durée de conservation,
droits RGPD). Export JSON du profil et suppression de compte disponibles depuis "mon compte".

### 29-30. Lisibilité et séparation du code

Découpage identique dans les 5 microservices : `web/` (controllers), `service/`, `repository/`,
`domain/`, `security/`, plus des packages dédiés (`graph/`, `search/`, `provider/`).

### 31-32. Injection SQL et XSS

Toutes les requêtes (`@Query` JPQL et Cypher) utilisent des paramètres liés (`:param`,
`$param`), jamais de concaténation de valeur utilisateur. Aucun `innerHTML`/
`bypassSecurityTrust` dans le frontend Angular (sanitization par défaut) + headers CSP/
X-Frame-Options/X-Content-Type-Options (`infra/nginx/nginx-main.conf`).

### 33. Mots de passe chiffrés

`BCryptPasswordEncoder` (`auth-service/.../SecurityConfig.java`).

---

## Validation des entrées (bonus, pas un item de l'audit mais utile à montrer)

Essayer de créer un voyage avec des dates incohérentes (ex : date de fin avant date de début, ou
`checkOut` avant `checkIn` sur un hébergement) est **rejeté avec un message explicite** avant
même d'atteindre la base de données (`@AssertTrue` sur `TravelRequest`/`DestinationRequest`/
`AccommodationRequest`). Bonne chose à montrer si on te demande "que se passe-t-il en cas de
données invalides ?" — la validation fonctionne, ce n'est pas un bug si tu n'arrives pas à créer
un voyage buggé exprès.

## Limitation connue (pas un item de l'audit, mais à savoir)

Le formulaire Admin "Créer un utilisateur" (`/users/new`) ne crée qu'une fiche profil
(`POST /api/users`) — il n'appelle jamais `POST /api/auth/accounts`, la route qui crée
réellement des identifiants de connexion. Résultat : impossible de créer, depuis le site, un
compte Manager (ou Admin) qui puisse se connecter. Un Traveler peut toujours s'auto-inscrire
(`/register`) ; un Manager, non — il faut passer par l'API directement
(`POST /api/auth/accounts`, admin-only) ou réutiliser le manager déjà créé par les tests e2e.
Non demandé explicitement par l'audit, donc pas un écart coché — mais bon à savoir expliquer si
la question "comment un Manager obtient-il un compte ?" arrive à l'oral.
