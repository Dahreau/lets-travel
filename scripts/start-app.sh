#!/bin/sh
set -e

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
    cp .env.example .env
    echo ".env cree a partir de .env.example."
    echo "Edite les mots de passe dans ce fichier, puis relance ce script."
    exit 0
fi

# Vault demarre TOUJOURS scelle sur un conteneur neuf (etat de scellement en memoire,
# jamais persiste avec le volume de donnees) - sans ce bloc, vault-init (qui exige
# vault a l'etat "healthy") ne demarre jamais et toute la stack reste bloquee derriere
# a chaque redemarrage (meme cause que troubleshooting.md #48, ici pour le flux local
# hors Jenkins/Ansible).
docker compose up -d vault

echo "Attente de Vault..."
rc=1
i=0
while [ "$i" -lt 20 ]; do
    # vault status : code retour 0 = descelle, 2 = scelle (mais process OK), 1 = pas encore
    # joignable - on distingue les 3 sans dependre du format du JSON (fragile, cf. troubleshooting.md).
    if docker compose exec -T vault vault status >/dev/null 2>&1; then
        rc=0
    else
        rc=$?
    fi
    { [ "$rc" = "0" ] || [ "$rc" = "2" ]; } && break
    i=$((i + 1))
    sleep 3
done

if [ "$rc" = "2" ] && [ -f infra/vault/.unseal-key.txt ]; then
    echo "Descellement de Vault..."
    docker compose exec -T vault vault operator unseal "$(cat infra/vault/.unseal-key.txt)" >/dev/null
fi

docker compose up -d --build
