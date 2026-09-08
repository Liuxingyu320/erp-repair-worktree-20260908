#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
RELEASE_ID=""
GIT_COMMIT=""
BUILD_TIME=""
OUTPUT_DIR="$REPO_ROOT/output/production-releases"
PREVIOUS_ARCHIVE=""
MIGRATION_MANIFEST=""

usage() {
  echo "Usage: $0 --release-id ID --git-commit SHA --build-time UTC [--output-dir DIR] [--previous ARCHIVE] [--migration-manifest FILE]"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-id)
      [[ $# -ge 2 ]] || { usage >&2; exit 2; }
      RELEASE_ID="$2"
      shift 2
      ;;
    --git-commit)
      [[ $# -ge 2 ]] || { usage >&2; exit 2; }
      GIT_COMMIT="$2"
      shift 2
      ;;
    --build-time)
      [[ $# -ge 2 ]] || { usage >&2; exit 2; }
      BUILD_TIME="$2"
      shift 2
      ;;
    --output-dir)
      [[ $# -ge 2 ]] || { usage >&2; exit 2; }
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --previous)
      [[ $# -ge 2 ]] || { usage >&2; exit 2; }
      PREVIOUS_ARCHIVE="$2"
      shift 2
      ;;
    --migration-manifest)
      [[ $# -ge 2 ]] || { usage >&2; exit 2; }
      MIGRATION_MANIFEST="$2"
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

[[ -n "$RELEASE_ID" && -n "$GIT_COMMIT" && -n "$BUILD_TIME" ]] || {
  usage >&2
  exit 2
}

cd "$REPO_ROOT"

ACTUAL_COMMIT="$(git rev-parse HEAD)"
[[ "$ACTUAL_COMMIT" == "$GIT_COMMIT" ]] || {
  echo "GIT_COMMIT does not match repository HEAD: $ACTUAL_COMMIT" >&2
  exit 1
}
[[ -z "$(git status --porcelain=v1 --untracked-files=all)" ]] || {
  echo "repository must be clean before a production release build" >&2
  exit 1
}

# Maven accepts test selectors and skip controls from several inherited JVM
# environment variables. A production candidate must use only the arguments
# declared by this tracked entrypoint, otherwise a caller can accidentally (or
# deliberately) turn `verify` into a zero-test build.
for build_env_var in MAVEN_ARGS MAVEN_OPTS JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS; do
  if [[ -n "${!build_env_var:-}" ]]; then
    echo "$build_env_var must be unset for a production release build" >&2
    exit 1
  fi
done

python3 "$SCRIPT_DIR/release_tool.py" validate-env \
  --repo-root "$REPO_ROOT" \
  --env-file "$REPO_ROOT/docker/.env.example" \
  --template >/dev/null

JAVA_MAJOR="$(java -version 2>&1 | awk -F'[\".]' '/version/ { print $2; exit }')"
[[ "$JAVA_MAJOR" == 17 ]] || {
  echo "JDK 17 is required; current java major is $JAVA_MAJOR" >&2
  exit 1
}
if [[ -x "$REPO_ROOT/mvnw" ]]; then
  MAVEN="$REPO_ROOT/mvnw"
else
  MAVEN="$(command -v mvn || true)"
fi
[[ -n "$MAVEN" ]] || {
  echo "Maven or the repository Maven wrapper is required" >&2
  exit 1
}
MAVEN_VERSION_OUTPUT="$("$MAVEN" --version 2>&1)"
MAVEN_JAVA_VERSION="$(
  printf '%s\n' "$MAVEN_VERSION_OUTPUT" |
    sed -n 's/^Java version: \([^,]*\).*/\1/p' |
    head -1
)"
[[ "$MAVEN_JAVA_VERSION" == 17 || "$MAVEN_JAVA_VERSION" == 17.* ]] || {
  echo "Maven must run on Java 17; found ${MAVEN_JAVA_VERSION:-unknown}" >&2
  exit 1
}
NODE_MAJOR="$(node -p 'process.versions.node.split(".")[0]')"
(( NODE_MAJOR >= 22 && NODE_MAJOR < 25 )) || {
  echo "Node 22-24 is required by erp-ui/package.json; current major is $NODE_MAJOR" >&2
  exit 1
}

"$MAVEN" clean verify \
  -Dbuild.commit="$GIT_COMMIT" \
  -DskipTests=false \
  -Dmaven.test.skip=false
(
  cd erp-ui
  npm ci
  VUE_APP_BUILD_COMMIT="$GIT_COMMIT" \
    VUE_APP_BUILD_TIME="$BUILD_TIME" \
    VUE_APP_SIGN_EXCEL_IMPORT_ENABLED=true \
    npm run build:prod
)

package_args=(
  package
  --repo-root "$REPO_ROOT"
  --output-dir "$OUTPUT_DIR"
  --release-id "$RELEASE_ID"
  --git-commit "$GIT_COMMIT"
  --build-time "$BUILD_TIME"
)
if [[ -n "$PREVIOUS_ARCHIVE" ]]; then
  package_args+=(--previous "$PREVIOUS_ARCHIVE")
fi
if [[ -n "$MIGRATION_MANIFEST" ]]; then
  package_args+=(--migration-manifest "$MIGRATION_MANIFEST")
fi

python3 "$SCRIPT_DIR/release_tool.py" "${package_args[@]}"
