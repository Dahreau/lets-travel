# Problèmes rencontrés — isolation & CI/CD (retour d'expérience)

[← Sommaire](00-getting-started.md)

Ce fichier liste, dans l'ordre où on les a rencontrés, les problèmes réels tombés pendant la mise en place de l'isolation `lets-travel` / `travel-plan` et du pipeline CI/CD — y compris les petits. Utile pour l'audit : montrer qu'on sait *pourquoi* ça a cassé et *comment* on l'a diagnostiqué compte souvent plus que de prétendre que tout a marché du premier coup.

## 1. Collision de ports/noms entre les deux stacks Docker

**Problème** : `lets-travel` est dérivé de `travel-plan`, donc au départ les deux repos utilisaient exactement les mêmes ports hôte (Postgres `5434`, Neo4j `7474`/`7687`, Nginx `80`/`443`, Jenkins `8090`, SonarQube `9000`) et le même nom de projet Compose (`travel-plan-app`, `travel-plan-ci`). Lancer `lets-travel` en gardant `travel-plan` up en parallèle aurait fait planter le `docker compose up` du second (ports déjà pris) et/ou renommé/écrasé les conteneurs du premier (même nom de projet = Compose les considère comme "les siens").

**Solution** : renommage complet des deux `name:` Compose (`lets-travel-app`, `lets-travel-ci`) et décalage de tous les ports publiés côté `lets-travel` (`5435`, `7475`/`7688`, `8080`/`8443`, `8091`, `9001`) — voir [`01-ci-cd.md`](01-ci-cd.md) et [`02-app-infra.md`](02-app-infra.md) pour le détail. Les instances `travel-plan` restent intactes, sur leurs ports/noms d'origine, avec leur historique.

## 2. Le certificat TLS interne ne "sait" plus qui il est censé couvrir

**Problème** : le certificat auto-signé Nginx/inter-services embarque un `SAN` (Subject Alternative Name) listant les noms DNS des conteneurs auxquels il doit correspondre — et ces noms dépendent du nom de projet Compose (`travel-plan-app-auth-service-1`, etc.). En renommant le projet en `lets-travel-app`, l'ancien certificat (généré une première fois pour `travel-plan`) devient invalide pour les nouveaux noms de conteneurs.

**Solution** : `ansible/playbooks/deploy.yml` génère le certificat avec `creates: <chemin>` — un garde-fou qui dit à Ansible "ne régénère pas si le fichier existe déjà". Comme l'ancien certificat existait toujours (généré du temps où c'était `travel-plan-app`), il fallait le supprimer à la main pour forcer Ansible à en générer un nouveau avec les bons noms au prochain déploiement. À refaire à chaque fois que le nom de projet Compose change.

## 3. `HOST_REPO_PATH` dans `infra/ci/.env` pointait encore vers l'ancien repo

**Problème** : ce chemin sert au conteneur Jenkins (Docker-outside-of-Docker) à retrouver le vrai chemin hôte du repo pour ses bind-mounts pendant le déploiement — sans ça, le stage `Deploy` du pipeline monte le mauvais dossier. Le fichier `.env` avait été copié depuis `travel-plan` sans que cette valeur soit mise à jour : elle pointait toujours vers `.../travel-plan` au lieu de `.../lets-travel`.

**Solution** : correction directe de la valeur dans `infra/ci/.env` (fichier non commité, contient des secrets — jamais dans Git). Sans rapport direct avec l'isolation des ports, mais repéré en même temps en vérifiant ce fichier.

## 4. Croire avoir mergé une branche alors qu'on a juste changé de branche

**Problème** : après avoir travaillé sur `chore/isolate-dev-ports`, `git checkout main` suivi d'un `git status` propre ("up to date with origin/main") a donné l'impression que la branche avait été mergée. En réalité, changer de branche ne merge rien — `main` était resté sur son ancien commit, et la branche de travail restait à part, seulement poussée sur GitHub sans PR mergée.

**Comment on l'a détecté** : `git log --all --oneline`, `git branch -a` et `git log origin/main` vs `git log origin/chore/isolate-dev-ports` montrent clairement deux historiques qui divergent encore. Rien n'était perdu, juste pas fusionné.

**À retenir** : `git status` "clean" sur `main` veut seulement dire "aucune modif locale non commitée sur la branche actuelle" — ça ne dit rien sur l'état des autres branches. Merger se fait explicitement (PR GitHub ou `git merge`), jamais implicitement en changeant de branche.

## 5. La stack `lets-travel` n'avait en réalité jamais démarré

**Problème** : en vérifiant l'isolation des ports après le merge, on a découvert que `infra/nginx/certs/` et `infra/internal-tls/certs/` ne contenaient que des `.gitkeep` — aucun certificat généré. `./scripts/start-app.sh` fait un simple `docker compose up`, qui aurait monté un dossier vide à la place d'un fichier de certificat attendu (Nginx et les services auraient planté au démarrage).

**Solution retenue** : plutôt que lancer Ansible à la main pour ce premier démarrage, on a choisi de finaliser le job Jenkins d'abord — son stage `Deploy` appelle déjà `ansible-playbook` (voir [`08-ansible-deploy-tls.md`](08-ansible-deploy-tls.md)), donc le premier vrai démarrage sert aussi à valider que le pipeline CI/CD fonctionne de bout en bout, plutôt que de faire le travail deux fois.

## 6. `mvnw` a perdu son bit exécutable — Jenkins échoue avec `Permission denied` (exit 126)

**Problème** : le build Jenkins échouait sur les 5 microservices avec `./mvnw: Permission denied`. Diagnostic : `git ls-files --stage backend/*/mvnw` montrait le mode `100644` (non-exécutable) côté `lets-travel`, contre `100755` (exécutable) côté `travel-plan` — pour le *même* blob (hash identique, donc contenu strictement identique). Cause probable : Git ne suit pas fiablement le bit d'exécution Unix sur un checkout via WSL2 sur un disque Windows monté (`/mnt/d/...`) — le bit s'est perdu au moment du premier commit du fichier dans `lets-travel`.

**Solution** : `git update-index --chmod=+x backend/*/mvnw` sur les 5 fichiers — ne change que le mode suivi par Git (métadonnée), aucune ligne de contenu modifiée (vérifiable : le hash du blob reste le même). Poussé sur `fix/mvnw-permissions`, corrige le build sans toucher au code.

**Comment vérifier soi-même** : comparer `git ls-files --stage backend/*/mvnw` entre les deux repos (mêmes hash de blob, modes différents) est la preuve la plus directe — pas besoin de deviner, la commande le montre noir sur blanc.
