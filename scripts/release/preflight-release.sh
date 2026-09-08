#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
ENV_FILE="$REPO_ROOT/docker/.env"
COMPOSE_FILES=(
  "$REPO_ROOT/docker/docker-compose.yml"
  "$REPO_ROOT/docker/docker-compose.ecs-host.yml"
)
CUSTOM_COMPOSE_FILES=false
ARCHIVE=""
EVIDENCE=""

usage() {
  echo "Usage: $0 --archive FILE --evidence FILE [--env-file FILE] [--compose-file FILE ...]"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --archive)
      [[ $# -ge 2 ]] || { usage >&2; exit 2; }
      ARCHIVE="$2"
      shift 2
      ;;
    --evidence)
      [[ $# -ge 2 ]] || { usage >&2; exit 2; }
      EVIDENCE="$2"
      shift 2
      ;;
    --env-file)
      [[ $# -ge 2 ]] || { usage >&2; exit 2; }
      ENV_FILE="$2"
      shift 2
      ;;
    --compose-file)
      [[ $# -ge 2 ]] || { usage >&2; exit 2; }
      if [[ "$CUSTOM_COMPOSE_FILES" != true ]]; then
        COMPOSE_FILES=()
        CUSTOM_COMPOSE_FILES=true
      fi
      COMPOSE_FILES+=("$2")
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
done

[[ -n "$ARCHIVE" && -n "$EVIDENCE" ]] || {
  usage >&2
  exit 2
}
[[ -f "$ENV_FILE" ]] || {
  echo "production environment file is missing: $ENV_FILE" >&2
  exit 1
}
[[ ${#COMPOSE_FILES[@]} -gt 0 ]] || {
  echo "at least one Compose file is required" >&2
  exit 1
}

umask 077
EVIDENCE_DIR="$(dirname "$EVIDENCE")"
mkdir -p "$EVIDENCE_DIR"
rm -f "$EVIDENCE"
TEMP_EVIDENCE="$(mktemp "$EVIDENCE_DIR/.release-preflight.XXXXXX")"
cleanup() {
  rm -f "$TEMP_EVIDENCE"
}
trap cleanup EXIT

NORMALIZED_COMPOSE_FILES=()
for compose_file in "${COMPOSE_FILES[@]}"; do
  [[ -f "$compose_file" ]] || {
    echo "Compose file is missing: $compose_file" >&2
    exit 1
  }
  compose_dir="$(cd -- "$(dirname "$compose_file")" && pwd -P)"
  normalized_compose="$compose_dir/$(basename "$compose_file")"
  NORMALIZED_COMPOSE_FILES+=("$normalized_compose")
  (
    cd "$compose_dir"
    docker compose \
      --env-file "$ENV_FILE" \
      -f "$(basename "$normalized_compose")" \
      config --quiet
  )
done

preflight_args=(
  preflight
  --repo-root "$REPO_ROOT"
  --env-file "$ENV_FILE"
  --archive "$ARCHIVE"
)
for compose_file in "${NORMALIZED_COMPOSE_FILES[@]}"; do
  preflight_args+=(--compose-file "$compose_file")
done
python3 "$SCRIPT_DIR/release_tool.py" "${preflight_args[@]}" >"$TEMP_EVIDENCE"

mv -f "$TEMP_EVIDENCE" "$EVIDENCE"
trap - EXIT

echo "RELEASE_PREFLIGHT_PASSED evidence=$EVIDENCE compose_files=${#NORMALIZED_COMPOSE_FILES[@]}"
