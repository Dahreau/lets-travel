# Tests end-to-end (Playwright)

Rejoue de vrais parcours utilisateur (inscription, login, navigation, abonnement, paiement,
creation de voyage, dashboards) dans un vrai navigateur, contre la stack Docker Compose deja
lancee (`docker-compose up`).

## Installation (une seule fois)

```
cd e2e
npm install
```

Le `postinstall` telecharge automatiquement Chromium (et ses dependances systeme) - aucune
etape manuelle supplementaire, comme pour Karma/Puppeteer cote frontend.

## Lancement

```
npx playwright test
```

Variables d'env optionnelles :
- `E2E_BASE_URL` (defaut `https://localhost:8443`)
- `ADMIN_USERNAME` / `ADMIN_PASSWORD` (defauts `admin` / `changeme_dev_only`)

## A savoir

- Les tests s'executent en serie (`workers: 1`) : plusieurs specs se connectent via
  `/api/auth/login`, qui est rate-limite cote nginx (5 requetes/minute) - la parallelisation
  causerait de faux echecs sans rapport avec le projet.
- `tests/support/global-setup.ts` cree un manager et un voyage de test via l'API avant la
  suite ; `test.describe.serial` reutilise ensuite la meme session (page) par fichier pour
  eviter de multiplier les logins.
- Un rapport HTML est genere automatiquement en cas d'echec (`npx playwright show-report`).
