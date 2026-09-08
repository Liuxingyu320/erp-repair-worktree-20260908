#!/usr/bin/env bash

set -Eeuo pipefail

ERP_CI_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
install_dependencies=0
run_mysql_integration=0
checks_only=0

usage()
{
    cat <<'EOF'
Usage: scripts/ci/verify.sh [--install] [--with-mysql] [--checks-only]

  --install      Run npm ci before frontend verification (recommended in clean CI).
  --with-mysql   Run the fail-closed Testcontainers MySQL 5.7/8.0 suite.
  --checks-only  Run repository and migration-manifest checks without builds/tests.
EOF
}

while (( $# > 0 )); do
    case "$1" in
        --install)
            install_dependencies=1
            ;;
        --with-mysql)
            run_mysql_integration=1
            ;;
        --checks-only)
            checks_only=1
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            printf '[FAIL] unknown option: %s\n' "$1" >&2
            usage >&2
            exit 2
            ;;
    esac
    shift
done

require_command()
{
    command -v "$1" >/dev/null 2>&1 || {
        printf '[FAIL] required command is unavailable: %s\n' "$1" >&2
        exit 1
    }
}

require_command git

"$ERP_CI_ROOT/scripts/verify-repository-hygiene.sh"
"$ERP_CI_ROOT/scripts/verify-docker-mysql-bootstrap.sh"
"$ERP_CI_ROOT/scripts/verify-new-business-it-contract.sh"

if (( checks_only == 1 )); then
    printf '[PASS] static repository and database contract checks completed\n'
    exit 0
fi

require_command java
require_command node
require_command npm

[[ -x "$ERP_CI_ROOT/mvnw" ]] || {
    printf '[FAIL] Maven wrapper is missing or not executable\n' >&2
    exit 1
}

node_major="$(node -p "Number(process.versions.node.split('.')[0])")"
if (( node_major < 22 || node_major >= 25 )); then
    printf '[FAIL] Node.js 22-24 is required; current version is %s\n' "$(node --version)" >&2
    exit 1
fi

failures=0

if ! (
    cd "$ERP_CI_ROOT"
    ./mvnw -fae test
); then
    printf '[FAIL] backend verification failed\n' >&2
    failures=$((failures + 1))
fi

frontend_ready=1
if (( install_dependencies == 1 )); then
    if ! (
        cd "$ERP_CI_ROOT/erp-ui"
        npm ci --no-audit --no-fund
    ); then
        printf '[FAIL] frontend dependency installation failed\n' >&2
        failures=$((failures + 1))
        frontend_ready=0
    fi
fi

if (( frontend_ready == 1 )) && ! (
    cd "$ERP_CI_ROOT/erp-ui"
    npm test
); then
    printf '[FAIL] frontend tests failed\n' >&2
    failures=$((failures + 1))
fi

if (( frontend_ready == 1 )) && ! (
    cd "$ERP_CI_ROOT/erp-ui"
    build_commit="${VUE_APP_BUILD_COMMIT:-$(git -C "$ERP_CI_ROOT" rev-parse HEAD)}"
    build_time="${VUE_APP_BUILD_TIME:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
    env VUE_APP_BUILD_COMMIT="$build_commit" VUE_APP_BUILD_TIME="$build_time" \
        npm run build:prod
); then
    printf '[FAIL] frontend production build failed\n' >&2
    failures=$((failures + 1))
fi

if (( run_mysql_integration == 1 )); then
    if ! (
        cd "$ERP_CI_ROOT"
        # Testcontainers does not resolve every non-default Docker context
        # (for example Colima) through the CLI on its own.
        source "$ERP_CI_ROOT/scripts/configure-testcontainers-docker.sh"
        erp_configure_testcontainers_docker
        ./mvnw -Pnew-business-mysql-it verify
    ); then
        printf '[FAIL] MySQL integration verification failed\n' >&2
        failures=$((failures + 1))
    fi
fi

if (( failures > 0 )); then
    printf '[FAIL] ERP verification pipeline completed with %s failed stage(s)\n' \
        "$failures" >&2
    exit 1
fi

printf '[PASS] ERP verification pipeline completed\n'
