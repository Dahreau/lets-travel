#!/bin/sh
set -e

cd "$(dirname "$0")/../infra/ci"

if [ ! -f .env ]; then
    cp .env.example .env
    echo "infra/ci/.env cree a partir de .env.example."
    echo "Edite les mots de passe dans ce fichier, puis relance ce script."
    exit 0
fi

docker compose up -d --build
