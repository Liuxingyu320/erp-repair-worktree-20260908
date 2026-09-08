#!/usr/bin/env bash

# Configure Testcontainers from the active Docker CLI context when callers have
# not supplied an explicit endpoint. This keeps Colima/Docker Desktop contexts
# reproducible without changing CI or remote-Docker settings chosen by callers.
erp_configure_testcontainers_docker()
{
    command -v docker >/dev/null 2>&1 || {
        printf '[FAIL] Docker CLI is required for the MySQL integration gate\n' >&2
        return 1
    }
    docker info >/dev/null 2>&1 || {
        printf '[FAIL] Docker daemon is unavailable; MySQL integration gate cannot continue\n' >&2
        return 1
    }

    local resolved_docker_host="${DOCKER_HOST:-}"
    if [[ -z "$resolved_docker_host" ]]; then
        resolved_docker_host="$(
            docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true
        )"
        if [[ -n "$resolved_docker_host" ]]; then
            export DOCKER_HOST="$resolved_docker_host"
        fi
    fi

    if [[ -z "${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-}" \
          && "$resolved_docker_host" == unix://* \
          && "$resolved_docker_host" != 'unix:///var/run/docker.sock' ]]; then
        export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
    fi
}
