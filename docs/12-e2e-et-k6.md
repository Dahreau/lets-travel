# Tests e2e (Playwright) et tests de charge (k6)

[← Sommaire](00-getting-started.md)

Répond aux deux items de l'énoncé/audit longtemps mis de côté (voir `nouveautes-vs-travel-plan.md`
pour l'historique de la décision) : tests end-to-end et tenue en charge. Les deux tournent contre
la stack Docker Compose déjà lancée (`./scripts/start-app.sh` ou équivalent), pas contre un
environnement dédié.

## Prérequis

- **k6** : binaire natif, pas un paquet npm.
  - Windows (PowerShell) : `winget install k6`
  - macOS : `brew install k6`
  - Linux, **ou Windows via WSL2** : installer dans le shell Linux lui-même (voir ci-dessous) —
    un `k6` installe cote Windows par `winget` n'est PAS visible depuis un terminal WSL2, ce
    sont deux PATH distincts. Comme ce projet se pilote depuis WSL2 (docker compose, ansible),
    c'est la commande a utiliser sur une machine Windows classique :
    ```bash
    curl -fsSL https://dl.k6.io/key.gpg | sudo gpg --dearmor -o /usr/share/keyrings/k6-archive-keyring.gpg
    echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
    sudo apt-get update
    sudo apt-get install k6
    ```
  - Sans rien installer de plus : image Docker officielle (voir plus bas), suffisant puisque
    Docker Desktop est déjà un prérequis du projet.
- **Playwright** : rien à installer à part `npm install` dans `e2e/` — le `postinstall`
  télécharge Chromium tout seul, comme pour Karma/Puppeteer côté frontend.

## Tests de charge (k6)

```bash
k6 run k6/lets-travel-load-test.js
```

Alternative 100% Docker, sans installer k6 nativement — joindre le réseau Docker Compose de la
stack et appeler nginx par son nom de service plutôt que par le port publié sur l'hôte :

```bash
docker run --rm -i --network lets-travel-app_app grafana/k6 run \
  -e BASE_URL=https://nginx - < k6/lets-travel-load-test.js
```

(`lets-travel-app_app` = nom du réseau créé par Compose à partir de `name: lets-travel-app`
dans `docker-compose.yml` — à vérifier avec `docker network ls` si la commande échoue.)

Variables d'env optionnelles : `BASE_URL` (défaut `https://localhost:8443`),
`ADMIN_USERNAME` / `ADMIN_PASSWORD` (défauts `admin` / `changeme_dev_only`).

Détail des scénarios, seuils et données de test créées automatiquement : `k6/README.md`.

## Tests e2e (Playwright)

```bash
cd e2e
npm install
npx playwright test
```

Variables d'env optionnelles : `E2E_BASE_URL`, `ADMIN_USERNAME`, `ADMIN_PASSWORD` (mêmes
défauts que ci-dessus).

Détail des parcours couverts (traveler, manager, admin) : `e2e/README.md`.

## À savoir

- Les deux suites créent leurs propres données de test (comptes, voyages) au démarrage, sans
  toucher à l'existant.
- Les tests e2e s'exécutent en série (pas de parallélisation) : plusieurs specs se connectent
  via `/api/auth/login`, qui est rate-limité côté nginx (5 requêtes/minute) — la
  parallélisation causerait de faux échecs sans rapport avec le projet.
