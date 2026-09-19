#!/usr/bin/env bash
set -euo pipefail
umask 077

COMPOSE_FILE="${COMPOSE_FILE:-compose.prod.yaml}"
ENV_FILE="${ENV_FILE:-.env.prod}"
BRANCH="${DEPLOY_BRANCH:-main}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE. Create it from .env.prod.example before deploying." >&2
  exit 1
fi
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "Tracked server files have local changes. Reconcile them before deploying." >&2
  exit 1
fi

git fetch origin "$BRANCH"
REVISION="$(git rev-parse "${DEPLOY_REVISION:-origin/$BRANCH}^{commit}")"
git merge --ff-only "$REVISION"
if [ "$(git rev-parse HEAD)" != "$REVISION" ]; then
  echo "Server is ahead of the requested revision; refusing a stale deployment." >&2
  exit 1
fi

compose() { docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }
compose config --quiet

echo "Building application image before replacing the running service..."
compose build app

if ! compose ps --services --filter status=running | grep -qx mariadb; then
  compose up -d --wait mariadb
fi

BACKUP_DIR="${BACKUP_DIR:-backups}"
mkdir -p "$BACKUP_DIR"
BACKUP_FILE="$BACKUP_DIR/mariadb-$(date -u +%Y%m%d-%H%M%S).sql"
echo "Creating required database backup at $BACKUP_FILE..."
if ! compose exec -T mariadb sh -c \
  'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb-dump -u root --single-transaction --routines --events "$MARIADB_DATABASE"' \
  > "$BACKUP_FILE.partial"; then
  echo "Database backup failed. Application deployment stopped." >&2
  exit 1
fi
if [ ! -s "$BACKUP_FILE.partial" ]; then
  echo "Database backup is empty. Application deployment stopped." >&2
  exit 1
fi
mv "$BACKUP_FILE.partial" "$BACKUP_FILE"

echo "Starting the new application without replacing the database..."
compose up -d --no-deps app caddy

HEALTH_URL="${DEPLOY_HEALTH_URL:-https://api.oao365.com/api/health}"
for attempt in $(seq 1 30); do
  if curl --fail --silent --show-error --max-time 5 "$HEALTH_URL" >/dev/null 2>&1; then
    echo "Deployment healthy: $REVISION"
    exit 0
  fi
  sleep 4
done
echo "Health check failed. Inspect app logs; backup retained at $BACKUP_FILE." >&2
exit 1
