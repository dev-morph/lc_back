#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-compose.prod.yaml}"
ENV_FILE="${ENV_FILE:-.env.prod}"
BRANCH="${DEPLOY_BRANCH:-main}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE. Create it from .env.prod.example before deploying." >&2
  exit 1
fi

echo "Fetching latest code from origin/$BRANCH..."
git fetch origin "$BRANCH"
git reset --hard "origin/$BRANCH"

echo "Pulling base images..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" pull caddy mariadb || true

RUNNING_MARIADB="$(
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps --services --filter status=running \
    | grep '^mariadb$' || true
)"

if [ -n "$RUNNING_MARIADB" ]; then
  BACKUP_DIR="${BACKUP_DIR:-backups}"
  BACKUP_FILE="$BACKUP_DIR/mariadb-$(date +%Y%m%d-%H%M%S).sql"
  DB_NAME="${MARIADB_DATABASE:-$(grep '^MARIADB_DATABASE=' "$ENV_FILE" | cut -d= -f2-)}"
  DB_ROOT_PASSWORD="${MARIADB_ROOT_PASSWORD:-$(grep '^MARIADB_ROOT_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)}"

  mkdir -p "$BACKUP_DIR"
  echo "Creating MariaDB backup at $BACKUP_FILE..."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mariadb \
    mariadb-dump -u root -p"$DB_ROOT_PASSWORD" "$DB_NAME" \
    > "$BACKUP_FILE" || echo "Backup skipped or failed. Continue deploy."
fi

echo "Building and starting services..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build

echo "Removing unused Docker resources..."
docker image prune -f

echo "Deployment complete."
