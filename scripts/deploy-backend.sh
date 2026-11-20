#!/usr/bin/env bash
set -euo pipefail

# Build the backend image locally, stream it to the server, and recreate the
# existing Docker Compose backend service. Configure SSH key authentication
# before running; do not put passwords or production secrets in this file.
: "${DEPLOY_HOST:?Set DEPLOY_HOST to the backend server hostname or IP}"

DEPLOY_USER="${DEPLOY_USER:-root}"
COMPOSE_DIR="${COMPOSE_DIR:-/opt/apps/vikoba}"
COMPOSE_SERVICE="${COMPOSE_SERVICE:-backend}"
DEPLOY_IMAGE="${DEPLOY_IMAGE:-vikoba-backend:1.0.0}"
DEPLOY_PLATFORM="${DEPLOY_PLATFORM:-linux/amd64}"

if [[ "$COMPOSE_DIR" != /* || "$COMPOSE_DIR" == "/" ]]; then
  echo "COMPOSE_DIR must be an absolute directory other than /" >&2
  exit 2
fi

if [[ ! "$COMPOSE_SERVICE" =~ ^[A-Za-z0-9_.-]+$ ]]; then
  echo "COMPOSE_SERVICE contains unsupported characters" >&2
  exit 2
fi

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"

bash ./mvnw -B -DskipTests package
docker build --platform "$DEPLOY_PLATFORM" -t "$DEPLOY_IMAGE" .

target="$DEPLOY_USER@$DEPLOY_HOST"
echo "Transferring $DEPLOY_IMAGE to $target..."
docker save "$DEPLOY_IMAGE" | ssh "$target" docker load

ssh "$target" bash -s -- "$COMPOSE_DIR" "$COMPOSE_SERVICE" "$DEPLOY_IMAGE" <<'REMOTE'
set -euo pipefail
compose_dir="$1"
service="$2"
image="$3"

cd "$compose_dir"
docker image inspect "$image" >/dev/null
docker compose up -d --no-deps --force-recreate --no-build "$service"
docker compose ps "$service"
REMOTE

echo "Deployment finished. Review the service status above and its logs with:" \
  "ssh $target 'cd $COMPOSE_DIR && docker compose logs --tail=100 $COMPOSE_SERVICE'"
