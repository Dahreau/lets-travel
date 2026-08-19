# admin-dashboard — Angular, DA "Zone01"

[← Sommaire](00-getting-started.md)

## Lancer en local (dev)

Prérequis : `api-gateway` tourne (donc les 4 autres services + Vault + Zipkin
derrière lui — voir [`05-api-gateway.md`](05-api-gateway.md)), sur son port par
défaut `:8080`.

```bash
cd frontend
npm install
npm start   # = ng serve, avec proxy.conf.json (/api -> http://localhost:8080)
```

Ouvre `http://localhost:4200`. Le frontend n'appelle jamais le gateway en
absolu (`http://localhost:8080/...`) : toutes les requêtes visent `/api/...`
en relatif, et c'est soit le proxy `ng serve` (dev), soit nginx (prod, voir plus
bas) qui les redirige vers le gateway. Le navigateur ne voit donc qu'une seule
origine — **aucun CORS n'a été ajouté côté Java**, ni n'est nécessaire.

Identifiants par défaut : `admin` / la valeur de `DEFAULT_ADMIN_PASSWORD` côté
`auth-service` (`changeme_dev_only` si non surchargée — voir
[`03-auth-service.md`](03-auth-service.md)).

## Build / conteneur (prod)

```bash
cd frontend
docker build -t travel-plan-admin-frontend .
docker run -p 8080:80 travel-plan-admin-frontend
```

Deux étages (`node:22-alpine` build → `nginx:1.27-alpine` serve). `nginx.conf`
sert les fichiers statiques buildés, fait le fallback SPA (`try_files ... /index.html`)
et reverse-proxy `/api/` vers `http://api-gateway:8080/api/` — ce nom ne résout
que si ce conteneur tourne sur le même réseau Docker qu'un service
`api-gateway`. `docker-compose.yml` déclare bien les 5 microservices depuis
`chore/ansible-deploy-tls`, mais aucun service `frontend` n'y est encore ajouté
— ce conteneur reste pour l'instant buildable/lançable seul (câblage complet à
faire dans une prochaine étape, qui devra aussi décider comment il s'articule
avec le `nginx` d'edge TLS déjà présent dans `docker-compose.yml`, cf.
`08-ansible-deploy-tls.md`). Le `resolver 127.0.0.11` + variable dans
`location /api/` force une résolution DNS à chaque requête plutôt qu'au
démarrage — sans ça nginx refuserait carrément de démarrer tant
qu'`api-gateway` n'existe pas (`host not found in upstream`), testé et corrigé
pendant la construction de cette page.

## Stack

- **Angular 21**, composants standalone, nouvelle syntaxe de contrôle de flux
  (`@if`/`@for`), Signals pour l'état local/session. Dernière version qui
  tourne avec le Node installé ici (22.14) — `@angular/cli@latest` (v22) exige
  Node ≥22.22.3 et refuse de démarrer sinon.
- Pas de kit UI (Material/PrimeNG) : une petite couche de composants maison
  (`shared/ui/`) pour coller précisément à la DA sans lutter contre un design
  system générique — badge, spinner, confirm-dialog, page-header.
- **Reactive Forms** (`FormArray` imbriqués) pour le formulaire Travel
  (destinations → activités/hébergement, transports).
- `@fontsource/jetbrains-mono` — police auto-hébergée (pas de CDN à
  l'exécution), déclarée dans `angular.json` (`styles`), pas via `@import` CSS.
- Tests : Vitest (builder `@angular/build:unit-test`, défaut d'Angular 21 —
  plus de Karma/Jasmine).

## Ce qui est construit

| Dossier | Rôle |
|---|---|
| `core/auth/` | `auth.ts` (login/me/logout, signals), `auth-guard.ts`, `auth-interceptor.ts` (attache le Bearer, déloggue sur 401), `jwt-util.ts` (décodage local du payload, sans lib externe) |
| `core/notifications/` | `toast.ts` + `toast-outlet.ts` — erreurs API / confirmations |
| `core/http/api-error.ts` | extrait le message d'erreur du corps `{timestamp, status, error, message}` renvoyé par chaque `ApiExceptionHandler` backend |
| `layout/shell/` | sidebar + topbar + `<router-outlet>`, sidebar en drawer sous 860px |
| `features/login/` | formulaire login |
| `features/dashboard/` | compteurs users/travels/payments/payment-methods |
| `features/users/`, `features/travels/`, `features/payments/` | un service HTTP + list/form par entité, CRUD complet (sauf `Payment` : create + refund seulement, pas d'update/delete côté API) |
| `shared/ui/` | badge (couleur par valeur de statut/rôle), spinner, confirm-dialog, page-header |

Chaque suppression affiche un message qui reflète le vrai comportement de
cascade vérifié dans les migrations SQL backend : `User` → adresse supprimée
avec lui, `Travel` → toutes ses destinations/activités/hébergements/transports
supprimés, `PaymentMethod` → les paiements existants sont conservés (leur
`payment_method_id` passe juste à `NULL`, `ON DELETE SET NULL` côté
`payment-service`).

## Direction artistique "Zone01"

Dark mode uniquement (pas de bascule clair/sombre — choix assumé, pas un
oubli), police monospace partout, accent vert terminal (`--accent: #39ff8c`),
badges de statut colorés, labels façon prompt (`$ users`), sidebar `~/admin`.
Tokens dans `src/styles.scss` (`:root { --bg, --surface, --accent, ... }`) +
classes utilitaires globales (`.btn`, `.input`, `.badge-*`, `.table`, ...)
plutôt que des composants Angular wrapper pour chaque champ de formulaire —
plus simple à intégrer avec `formControlName` que des `ControlValueAccessor`
maison, pour un projet de cette taille.

## Tests

`ng test` (Vitest) : `auth`, `auth-guard`, `auth-interceptor`, `jwt-util`,
les 3 services CRUD (`UsersService`, `TravelsService`, `PaymentsService`,
`PaymentMethodsService`) via `HttpTestingController`, `Login` (validation,
login + `/me` enchaînés) et `TravelForm` (ajout/suppression dynamique de
destinations/activités/transports, toggle hébergement). 37 tests, tous verts.

## Vérifié pendant la construction de cette page

- `ng build` : compile sans erreur.
- `ng test` : 37/37.
- `docker build` + `docker run` (sans `api-gateway` sur le réseau) : le
  conteneur démarre, sert `index.html`, le fallback SPA fonctionne
  (`GET /users` → `200`), et l'appel proxyé `/api/...` répond `502` proprement
  (upstream absent) au lieu de faire planter nginx — c'est ce test qui a
  révélé le bug de résolution DNS corrigé ci-dessus.

**Non vérifié à ce stade : le parcours complet contre les vrais
microservices.** En essayant de le faire tourner (`docker compose up` pour
l'infra, puis chaque service via `./mvnw spring-boot:run` avec ses identifiants
Vault, comme décrit dans les pages 03-07), `auth-service` et `user-service`
plantaient tous les deux au démarrage : `Schema validation: missing table
[admins]` / `[addresses]` — Flyway ne s'exécutait jamais malgré une config
correcte et le fichier de migration présent sur le classpath compilé. Diagnostic
posé à l'époque : aucun jar Spring Boot 4.1.0 ne contenait d'autoconfiguration
Flyway (contrairement à JPA/Hibernate, déplacée vers
`org.springframework.boot.hibernate.autoconfigure` dans cette version).

Ce diagnostic est confirmé par le correctif arrivé depuis sur `main`
(`chore/ansible-deploy-tls`) : les 4 `pom.xml` concernés remplacent
`org.flywaydb:flyway-core` par `org.springframework.boot:spring-boot-starter-flyway`
— exactement le module dédié qui manquait. Le parcours complet (login réel →
CRUD via le gateway) reste à revérifier après ce correctif, pas encore refait
depuis le rebase de cette branche sur `main`.

## Pourquoi ces choix

### Pourquoi Angular plutôt que React/Vue
`00-getting-started.md` mentionnait déjà "une fois le projet Angular généré"
avant même que cette branche existe — cohérence avec le plan déjà documenté,
confirmé explicitement avant de commencer.

### Pourquoi un reverse-proxy (dev ET prod) plutôt que du CORS côté gateway
Le gateway n'a aucune configuration CORS aujourd'hui. Plutôt que d'en ajouter
côté Java pour un besoin purement frontend, le proxy `ng serve` en dev et
nginx en prod font que le navigateur ne voit jamais qu'une seule origine — le
même mécanisme des deux côtés, zéro ligne Java à toucher, zéro
préflight `OPTIONS` à gérer.

### Pourquoi pas de kit UI (Material/PrimeNG)
La DA "Zone01" (terminal, monospace, accent néon sur fond noir) demandée est
suffisamment spécifique pour qu'un design system générique demande plus de
surcharge (variables CSS à écraser partout) que d'en écrire une petite
douzaine de classes utilitaires + 4 composants dumb maison.

### Pourquoi des sélecteurs (owner, moyen de paiement, voyage) plutôt que des champs UUID libres
Les DTO backend (`TravelRequest.ownerId`, `PaymentRequest.paymentMethodId`,
etc.) attendent des UUID bruts, mais un admin ne les connaît pas par cœur. Les
formulaires Travel/Payment/PaymentMethod chargent les listes
users/travels/payment-methods et présentent des `<select>` lisibles
(nom + email), qui soumettent bien l'UUID attendu.
