#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
  echo "Missing $ROOT_DIR/.env" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

if [[ -z "${ODSAY_API_KEY:-}" ]]; then
  echo "ODSAY_API_KEY is required" >&2
  exit 1
fi

export ODSAY_ENABLED=true
export TMAP_WALKING_ENABLED="${TMAP_WALKING_ENABLED:-$([[ -n "${SKT_API_KEY:-}" ]] && echo true || echo false)}"
export BUSAN_BIMS_ENABLED="${BUSAN_BIMS_ENABLED:-false}"

SERVER_PORT="${SERVER_PORT:-8080}"
exec ./gradlew bootRun --args="--spring.profiles.active=local --server.port=${SERVER_PORT}"
