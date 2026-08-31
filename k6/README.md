# Tests de charge (k6)

Couvre l'item audit sur la tenue en charge (recherche, consultation, dashboards) ainsi que les
flux traveler/manager/admin, contre la stack Docker Compose deja lancee.

## Lancement

Sans installation locale de k6 (via l'image Docker officielle - pratique sur Windows/WSL2 avec
Docker Desktop) :

```
docker run --rm -i --add-host=host.docker.internal:host-gateway grafana/k6 run \
  -e BASE_URL=https://host.docker.internal:8443 - < k6/lets-travel-load-test.js
```

Avec k6 installe nativement :

```
k6 run k6/lets-travel-load-test.js
```

Variables d'env optionnelles : `BASE_URL` (defaut `https://localhost:8443`),
`ADMIN_USERNAME` / `ADMIN_PASSWORD` (defauts `admin` / `changeme_dev_only`).

## A savoir

- Le script cree ses propres donnees de test (manager, travelers, voyages) au demarrage
  (`setup()`), sans toucher aux comptes/voyages existants.
- Seuil `p(95) < 5000ms` sur le scenario `browsing_and_search`, qui reprend le libelle exact de
  l'audit ("moins de 5 secondes").
- Le scenario `payment_flow` reste volontairement a tres faible volume : il passe par un vrai
  Stripe en mode test (identifiant de test officiel `pm_card_visa`), pas de sandbox dediee au load testing. Le check
  `payment: created` echoue "normalement" si `STRIPE_SECRET_KEY` (`.env`) est reste a sa valeur
  placeholder (`sk_test_changeme_dev_only`) - remplace-la par une vraie cle de test Stripe
  (gratuite, Stripe Dashboard > Developers > API keys) pour que ce check passe aussi.
