# Ansible — déploiement automatisé, replicas, TLS via Nginx

[← Sommaire](00-getting-started.md)

## Lancer en local

Prérequis : Python 3 et Ansible installés sur la machine qui exécute les playbooks (`pip install ansible --break-system-packages` ou `apt install ansible`), et la collection Docker :

```bash
cd ansible
ansible-galaxy collection install -r requirements.yml
```

Copie `.env.example` en `.env` si ce n'est pas déjà fait (le playbook le fait aussi automatiquement au premier lancement), puis mets de vrais mots de passe dedans comme d'habitude.

Lance tout d'un coup :

```bash
cd ansible
ansible-playbook -i inventory.ini playbooks/site.yml --ask-become-pass
```

- `-i inventory.ini` : dit explicitement à Ansible où trouver l'inventaire, plutôt que de compter sur la lecture automatique d'`ansible.cfg` (qu'Ansible refuse par sécurité si le dossier semble "accessible en écriture à tout le monde" — ça arrive sur un disque Windows monté dans WSL, sans rapport avec le projet lui-même).
- `--ask-become-pass` : `install-docker.yml` a besoin des droits root pour installer des paquets — tape ton mot de passe sudo une fois quand demandé.

Ça fait, dans l'ordre : installer Docker si absent, démarrer toute la stack, récupérer les identifiants Vault de chaque service, puis redémarrer les microservices avec ces identifiants. Rejouable sans risque (voir "Pourquoi" plus bas).

Une fois terminé, l'application est accessible en HTTPS :

```bash
curl -k https://localhost/api/travels
```

(`-k` parce que le certificat est auto-signé — normal en local, pas un vrai certificat signé par une autorité publique.)

## Ce qui est construit

| Fichier / dossier                           | Rôle                                                                                |
| ------------------------------------------- | ----------------------------------------------------------------------------------- |
| `backend/*/Dockerfile`                      | build multi-stage (Maven → JRE), utilisateur non-root, un par microservice          |
| `docker-compose.yml`                        | étendu avec les 5 microservices (`deploy.replicas: 2` chacun) + `nginx`             |
| `infra/nginx/nginx.conf`                    | redirige HTTP→HTTPS, termine le TLS, relaie vers `api-gateway`                      |
| `ansible/playbooks/install-docker.yml`      | installe Docker + le plugin Compose si absent                                       |
| `ansible/playbooks/vault-unseal.yml`        | initialise Vault une seule fois (stockage persistant, plus de mode dev) et le déscelle à chaque démarrage |
| `ansible/playbooks/deploy.yml`              | crée `.env`, génère les certificats TLS (Nginx + Vault), pré-tire les images tierces, lance `docker compose up` |
| `ansible/playbooks/fetch-vault-secrets.yml` | récupère/génère les identifiants AppRole de chaque service et les écrit dans `.env` |
| `ansible/playbooks/site.yml`                | enchaîne les quatre dans le bon ordre                                               |
| `infra/vault/config/vault.hcl`              | config serveur Vault réelle (storage `file`, listener TLS) — remplace le mode dev  |
| `infra/postgres/init/init-databases.sh`     | crée + resynchronise (idempotent) chaque user/DB Postgres au démarrage du conteneur |
| `infra/vault/bootstrap.sh`                  | active AppRole, seede le secret JWT partagé + les identifiants Stripe/PayPal        |

## Nouveau par rapport à buy-02

Voir [`nouveautes-vs-buy02.md`](nouveautes-vs-buy02.md#ansible--tls-choreansible-deploy-tls) (déploiement automatisé, replicas, TLS).

## Pourquoi ces choix

### Pourquoi `deploy.yml` tourne deux fois dans `site.yml`

Chaque microservice a besoin d'un identifiant Vault (`VAULT_ROLE_ID`/`VAULT_SECRET_ID`) pour démarrer, mais Vault doit d'abord tourner et être "seedé" par `vault-init` pour que ces identifiants existent. Premier passage : la stack démarre, les microservices échouent à démarrer faute d'identifiants (ils redémarrent en boucle, sans casser le reste). `fetch-vault-secrets.yml` récupère alors les vrais identifiants et les écrit dans `.env`. Second passage de `deploy.yml` : Docker Compose ne recrée que les conteneurs dont la config a changé (les 5 microservices), qui démarrent cette fois avec de vrais identifiants. Pas de `sleep` ni de script fragile — juste une dépendance résolue en deux étapes explicites.

### Pourquoi les tâches Ansible utilisées sont idempotentes, sauf une nuance à connaître

`apt`, `copy` (avec `force: false`), `command` (avec `creates: ...`) et `community.docker.docker_compose_v2` sont tous conçus pour ne rien casser si on les relance : ils vérifient l'état actuel avant d'agir. Seule nuance, à savoir expliquer à l'audit : `fetch-vault-secrets.yml` génère un **nouveau** `secret_id` à chaque exécution (Vault n'en renvoie jamais le même deux fois) — donc la valeur change à chaque relance, mais le résultat final (un identifiant valide dans `.env`, le service peut démarrer) est toujours le même. C'est idempotent dans son effet, pas dans sa valeur exacte — comme changer un mot de passe.

### Pourquoi `api-gateway` déclare explicitement les 2 instances de chaque service (et pas juste le nom DNS partagé)

Première approche testée (et invalidée en pratique) : pointer `api-gateway` vers le nom de service partagé (`http://travel-service:8083`) en comptant sur le DNS round-robin intégré à Docker pour répartir les requêtes entre les 2 répliques. Ça ne marche pas comme prévu : le client HTTP interne garde sa connexion en vie (keep-alive) vers la première IP résolue et ne refait jamais de lookup DNS tant que la connexion reste ouverte — en test de charge réel, 100% du trafic est resté collé à une seule réplique. Le failover, lui, fonctionnait déjà (Docker retire un conteneur arrêté du DNS), mais pas la répartition de charge, pourtant explicitement demandée à l'audit.

Fix : `RouteConfig.java` utilise déjà `spring-cloud-starter-loadbalancer` (filtre `lb(...)`) au-dessus d'un `SimpleDiscoveryClient` — mais celui-ci ne déclarait qu'une seule instance par service, donc rien à répartir. `application.properties` déclare maintenant 2 instances par service, une par nom de conteneur Compose (`travel-plan-app-travel-service-1`/`-2`, stables tant que `deploy.replicas` reste à 2), injectées via `docker-compose.yml`. Le `RoundRobinLoadBalancer` par défaut de Spring Cloud alterne alors réellement entre les deux à chaque appel, chacune gardant sa propre connexion — vérifié via les traces Zipkin (IP différente par réplique) et par coupure d'une réplique en direct (failover confirmé sans interruption).

### Pourquoi Nginx ne fait que le TLS, pas le routage entre microservices

Le routage (JWT, quel service pour quelle route) est déjà géré par `api-gateway`. Ajouter cette logique dans Nginx la dupliquerait à deux endroits différents. Nginx a un rôle unique et simple : être le seul point exposé sur Internet, chiffrer le trafic entrant, et relayer tout vers `api-gateway` qui reste responsable du routage métier.

### Pourquoi `deploy.yml` appelle `docker compose` directement plutôt que le module Ansible dédié

Le module `community.docker.docker_compose_v2` a un bug reproductible sur certaines installations Docker Desktop : son étape interne de vérification post-déploiement (`docker compose images`) plante sur une image multi-plateforme même quand l'image locale est correcte (testé avec tag simple, `--platform` explicite et digest exact — les trois échouent identiquement). `docker compose up -d --remove-orphans` en ligne de commande directe, lui, n'a jamais échoué : c'est exactement la même commande que lancerait un humain, juste sans l'étape de rapport buguée en plus. Le pré-pull explicite par digest (`hashicorp/vault:2.0@sha256:...`) juste avant sert à garantir que l'image correcte est déjà en cache avant que Compose n'y touche.

### Pourquoi le premier passage de `deploy.yml` ne fait pas échouer tout le playbook

`docker compose up` refuse de démarrer `api-gateway` tant qu'un service dont il dépend (`auth-service`, etc.) n'est pas `healthy` — normal au premier passage, ces services n'ont pas encore d'identifiants Vault valides. La variable `tolerate_unhealthy_dependencies` (mise à `true` uniquement pour le premier appel de `deploy.yml` dans `site.yml`) rend cet échec-là silencieux ; le second passage, lui, fait toujours échouer le playbook pour de vrai si quelque chose cloche, pour ne jamais masquer un vrai problème.

### Pourquoi un certificat auto-signé plutôt que Let's Encrypt

Let's Encrypt exige un nom de domaine public résolvable, ce que ce projet n'a pas en local/dev. Un certificat auto-signé chiffre le trafic exactement pareil (même algorithme TLS) — la seule différence est qu'aucune autorité tierce ne garantit l'identité du serveur, ce qui n'a pas d'importance en développement. Passer à Let's Encrypt le jour d'un vrai déploiement ne changerait que la génération du certificat, pas la configuration Nginx. Même certificat auto-signé pour Vault (`infra/vault/certs/`), même raisonnement.

### Pourquoi Vault ne tourne plus en mode dev

Le mode dev (`vault server -dev`) auto-déscelle et perd toutes ses données au moindre redémarrage — commode pour itérer vite, mais un root token en clair dans l'environnement et aucune persistance ne passeraient pas un audit sérieux. `vault-unseal.yml` initialise Vault une seule fois (`vault operator init`, stockage `file` persistant) et le déscelle à chaque démarrage avec une clé stockée dans `infra/vault/.unseal-key.txt` (jamais commitée, permissions 0600). Un seul partage de clé (`-key-shares=1 -key-threshold=1`) plutôt qu'un vrai partage de Shamir multi-opérateurs : ce partage sert à répartir la confiance entre plusieurs humains dans une vraie organisation, ce qui n'a pas de sens ici où un seul pipeline gère tout le cycle de vie — un seuil à 3-sur-5 n'ajouterait que de la complexité à automatiser, pas de sécurité réelle.

### Pourquoi les appels Ansible à Vault passent par `docker compose exec` plutôt que par une requête HTTP

Le port 8200 de Vault n'est plus publié sur l'hôte (durcissement réseau — voir l'audit). La machine qui exécute Ansible (le poste du développeur, ou le conteneur Jenkins) n'est donc pas sur le réseau Docker `app` et ne peut pas joindre `vault:8200` directement. `docker compose exec vault vault ...` exécute le CLI Vault **à l'intérieur** du conteneur, qui se parle à lui-même sur `127.0.0.1` — aucun besoin d'exposer le port, aucun besoin que le contrôleur Ansible valide le certificat TLS de Vault.

### Pourquoi les services Java font confiance au certificat auto-signé de Vault au lieu de le rejeter

Chaque `VaultClient.java` utilise un `HttpClient` configuré pour accepter le certificat de Vault sans le valider contre une autorité publique — même compromis que pour Nginx (voir plus haut), justifié par le fait que ce trafic ne quitte jamais le réseau Docker interne `app`.
