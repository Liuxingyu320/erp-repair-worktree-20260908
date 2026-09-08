#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIST_FILE="$ROOT_DIR/docker/mysql/bootstrap-files.list"
MANUAL_LIST_FILE="$ROOT_DIR/docker/mysql/manual-files.list"
SOURCE_DIR="$ROOT_DIR/sql"
BOOTSTRAP_DIR="$ROOT_DIR/docker/mysql/db"
DOCKERFILE="$ROOT_DIR/docker/mysql/dockerfile"
DOCKERIGNORE="$ROOT_DIR/docker/mysql/.dockerignore"
RUNNER_FILE="$ROOT_DIR/docker/mysql/run-bootstrap.sh"
COMPOSE_FILE="$ROOT_DIR/docker/docker-compose.yml"

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

[[ -r "$LIST_FILE" ]] || fail "missing bootstrap list: $LIST_FILE"
[[ -r "$MANUAL_LIST_FILE" ]] || fail "missing manual SQL allowlist: $MANUAL_LIST_FILE"
[[ -d "$BOOTSTRAP_DIR" ]] || fail "missing bootstrap directory: $BOOTSTRAP_DIR"

expected_ordered="$({
    while IFS= read -r raw || [[ -n "$raw" ]]; do
        name="${raw%$'\r'}"
        [[ -z "$name" || "$name" == \#* ]] && continue
        [[ "$name" =~ ^[A-Za-z0-9._-]+\.sql$ ]] \
            || fail "unsafe bootstrap filename: $name"
        printf '%s\n' "$name"
    done < "$LIST_FILE"
})"
expected="$(printf '%s\n' "$expected_ordered" | sort)"

manual_ordered="$({
    while IFS= read -r raw || [[ -n "$raw" ]]; do
        name="${raw%$'\r'}"
        [[ -z "$name" || "$name" == \#* ]] && continue
        [[ "$name" =~ ^[A-Za-z0-9._-]+\.sql$ ]] \
            || fail "unsafe manual SQL filename: $name"
        printf '%s\n' "$name"
    done < "$MANUAL_LIST_FILE"
})"
manual="$(printf '%s\n' "$manual_ordered" | sort)"

[[ -n "$expected" ]] || fail 'bootstrap list is empty'
[[ "$(printf '%s\n' "$expected" | uniq -d | wc -l | tr -d ' ')" == '0' ]] \
    || fail 'bootstrap list contains duplicate filenames'
[[ -n "$manual" ]] || fail 'manual SQL allowlist is empty'
[[ "$(printf '%s\n' "$manual" | uniq -d | wc -l | tr -d ' ')" == '0' ]] \
    || fail 'manual SQL allowlist contains duplicate filenames'
[[ -z "$(comm -12 <(printf '%s\n' "$expected") <(printf '%s\n' "$manual"))" ]] \
    || fail 'a SQL file cannot be both automatic and manual-only'
[[ "$manual" == 'tea_management_import_20260608.sql' ]] \
    || fail 'manual SQL allowlist must contain only the reviewed destructive tea import'

allowed="$(printf '%s\n%s\n' "$expected" "$manual" | sort)"

actual="$(find "$BOOTSTRAP_DIR" -maxdepth 1 -type f -name '*.sql' \
    -exec basename {} \; | sort)"
[[ "$actual" == "$allowed" ]] \
    || fail 'docker/mysql/db differs from the automatic and manual SQL manifests'

while IFS= read -r name; do
    [[ -r "$SOURCE_DIR/$name" ]] || fail "missing root SQL source: $name"
    cmp -s "$SOURCE_DIR/$name" "$BOOTSTRAP_DIR/$name" \
        || fail "Docker SQL asset differs from root source: $name"
done <<< "$allowed"

while IFS= read -r name; do
    if grep -Eiq '^[[:space:]]*USE[[:space:]]+' "$BOOTSTRAP_DIR/$name"; then
        fail "automatic bootstrap SQL overrides MYSQL_DATABASE: $name"
    fi
    if grep -Eiq '^[[:space:]]*(SOURCE|\\\.)[[:space:]]+' "$BOOTSTRAP_DIR/$name"; then
        fail "automatic bootstrap SQL sources an unlisted file: $name"
    fi
done <<< "$expected"

if printf '%s\n' "$expected" | grep -Fxq 'tea_management_import_20260608.sql'; then
    fail 'destructive tea data import must never run during automatic bootstrap'
fi
grep -Fq 'USE `BossERP_NEW`;' "$BOOTSTRAP_DIR/tea_management_import_20260608.sql" \
    || fail 'manual tea import lost its explicit target-database guard'
grep -Fq 'DELETE FROM inv_product;' "$BOOTSTRAP_DIR/tea_management_import_20260608.sql" \
    || fail 'manual tea import destructive scope changed without allowlist review'

[[ -r "$DOCKERFILE" ]] || fail "missing MySQL Dockerfile: $DOCKERFILE"
[[ -r "$DOCKERIGNORE" ]] || fail "missing MySQL build-context exclusions: $DOCKERIGNORE"
[[ -r "$RUNNER_FILE" ]] || fail "missing ordered bootstrap runner: $RUNNER_FILE"
[[ -r "$COMPOSE_FILE" ]] || fail "missing Docker Compose file: $COMPOSE_FILE"
[[ ! -x "$RUNNER_FILE" ]] \
    || fail 'bootstrap runner must be sourced, not executed, by the MySQL entrypoint'

grep -Fq 'COPY ./db/*.sql /opt/erp-bootstrap/sql/' "$DOCKERFILE" \
    || fail 'Dockerfile must keep bootstrap SQL outside docker-entrypoint-initdb.d'
grep -Fq 'COPY ./bootstrap-files.list /opt/erp-bootstrap/bootstrap-files.list' "$DOCKERFILE" \
    || fail 'Dockerfile must copy the ordered bootstrap list'
grep -Fq 'COPY ./manual-files.list /opt/erp-bootstrap/manual-files.list' "$DOCKERFILE" \
    || fail 'Dockerfile must identify manual-only SQL assets'
grep -Fq 'COPY ./run-bootstrap.sh /docker-entrypoint-initdb.d/00-erp-bootstrap.sh' "$DOCKERFILE" \
    || fail 'Dockerfile must install the single ordered bootstrap runner'
awk '
    /^  erp-mysql:/ { inside=1; next }
    inside && /^  [A-Za-z0-9_-]+:/ { exit }
    inside { print }
' "$COMPOSE_FILE" | grep -Fq 'dockerfile: dockerfile' \
    || fail 'Docker Compose must explicitly select the lowercase MySQL dockerfile'
if grep -Eq '^(ADD|COPY)[[:space:]]+\./db/\*\.sql[[:space:]]+/docker-entrypoint-initdb\.d/?' "$DOCKERFILE"; then
    fail 'Dockerfile must not let the MySQL entrypoint sort SQL filenames implicitly'
fi
if grep -Fq './seed/' "$DOCKERFILE"; then
    fail 'historical data seed must not be copied into the MySQL image'
fi
grep -Fxq 'seed/' "$DOCKERIGNORE" \
    || fail 'historical database seed must be excluded from the Docker build context'
grep -Fxq 'data/' "$DOCKERIGNORE" \
    || fail 'runtime MySQL data must be excluded from the Docker build context'
[[ ! -e "$BOOTSTRAP_DIR/00_BossERP_NEW.sql" ]] \
    || fail 'historical data seed must not be part of the bootstrap snapshot'

grep -Fq 'declare -F docker_process_sql' "$RUNNER_FILE" \
    || fail 'bootstrap runner must require the official docker_process_sql helper'
grep -Fq 'docker_process_sql < "$erp_bootstrap_path"' "$RUNNER_FILE" \
    || fail 'bootstrap runner must apply SQL through docker_process_sql'
grep -Fq 'done < "$erp_bootstrap_list"' "$RUNNER_FILE" \
    || fail 'bootstrap runner must consume bootstrap-files.list in order'
if grep -Fq 'manual-files.list' "$RUNNER_FILE"; then
    fail 'bootstrap runner must never consume the manual SQL allowlist'
fi

line_number()
{
    awk -v target="$1" '
        { sub(/\r$/, "") }
        $0 == target { print NR; exit }
    ' "$LIST_FILE"
}

require_before()
{
    local prerequisite="$1"
    local dependent="$2"
    local prerequisite_line
    local dependent_line
    prerequisite_line="$(line_number "$prerequisite")"
    dependent_line="$(line_number "$dependent")"
    [[ -n "$prerequisite_line" ]] || fail "missing bootstrap prerequisite: $prerequisite"
    [[ -n "$dependent_line" ]] || fail "missing bootstrap dependent: $dependent"
    (( prerequisite_line < dependent_line )) \
        || fail "bootstrap dependency order is invalid: $prerequisite must precede $dependent"
}

# Signing bootstrap dependencies. In particular, evidence must extend the package
# before task-center AFTER clauses run, plan-version needs task-center procedures,
# and A1/A2/A3 are deliberately last in lifecycle -> policy -> permission order.
require_before erp_oa_sign_package_20260702.sql erp_oa_sign_evidence_20260711.sql
require_before erp_oa_sign_evidence_20260711.sql erp_oa_sign_task_center_20260711.sql
require_before erp_oa_sign_plan_20260706.sql erp_oa_sign_plan_version_20260711.sql
require_before erp_oa_sign_task_center_20260711.sql erp_oa_sign_plan_version_20260711.sql
require_before erp_oa_sign_plan_version_20260711.sql erp_oa_sign_template_delivery_policy_20260713.sql
require_before erp_oa_sign_package_renewal_snapshot_20260714.sql erp_oa_sign_final_confirmation_20260714.sql
require_before erp_oa_sign_plan_version_20260711.sql erp_oa_sign_final_confirmation_20260714.sql
require_before erp_oa_sign_template_delivery_policy_20260713.sql erp_oa_sign_template_scenarios_20260714.sql
require_before erp_oa_sign_final_confirmation_20260714.sql erp_oa_sign_package_lifecycle_20260716.sql
require_before erp_oa_sign_task_center_20260711.sql erp_oa_sign_package_lifecycle_20260716.sql
require_before erp_oa_sign_package_lifecycle_20260716.sql erp_oa_sign_document_policy_snapshot_20260716.sql
require_before erp_oa_sign_document_policy_snapshot_20260716.sql erp_oa_sign_menu_permission_repair_20260716.sql
require_before erp_oa_sign_plan_20260706.sql erp_oa_sign_onboard_import_20260718.sql
require_before erp_system_user_notification_20260711.sql erp_system_user_push_delivery_20260717.sql
require_before erp_user_employee_profile_20260706.sql erp_system_sign_profile_supplement_20260718.sql
require_before erp_user_employee_profile_20260706.sql erp_system_sign_candidate_phone_index_20260719.sql

for push_delivery_migration in \
    "$SOURCE_DIR/erp_system_user_push_delivery_20260717.sql" \
    "$BOOTSTRAP_DIR/erp_system_user_push_delivery_20260717.sql"
do
    grep -Fq 'CREATE TABLE IF NOT EXISTS sys_user_push_delivery' "$push_delivery_migration" \
        || fail "mobile push delivery ledger table is missing: $push_delivery_migration"
    grep -Fq 'UNIQUE KEY uk_sys_user_push_delivery_business' "$push_delivery_migration" \
        || fail "mobile push delivery ledger unique key is missing: $push_delivery_migration"
    grep -Fq '(user_id, channel, business_key_hash)' "$push_delivery_migration" \
        || fail "mobile push delivery ledger uniqueness changed: $push_delivery_migration"
    grep -Fq 'payload_hash char(64) NOT NULL' "$push_delivery_migration" \
        || fail "mobile push delivery payload fingerprint is missing: $push_delivery_migration"
done

for policy_migration in \
    "$SOURCE_DIR/erp_oa_sign_document_policy_snapshot_20260716.sql" \
    "$BOOTSTRAP_DIR/erp_oa_sign_document_policy_snapshot_20260716.sql"
do
    grep -Fq 'information_schema.TRIGGERS' "$policy_migration" \
        || fail "document policy migration does not verify triggers: $policy_migration"
    grep -Fq 'JSON_VALID' "$policy_migration" \
        || fail "document policy migration does not validate JSON: $policy_migration"
    if grep -Fq 'information_schema.CHECK_CONSTRAINTS' "$policy_migration"; then
        fail "document policy migration is not MySQL 5.7 compatible: $policy_migration"
    fi
    if grep -Eq 'ADD[[:space:]]+CONSTRAINT.*CHECK[[:space:]]*\(' "$policy_migration"; then
        fail "document policy migration relies on unenforced MySQL 5.7 CHECK clauses: $policy_migration"
    fi
    for trigger_name in \
        trg_oa_sign_template_policy_bi_20260716 \
        trg_oa_sign_template_policy_bu_20260716 \
        trg_oa_sign_plan_version_template_policy_bi_20260716 \
        trg_oa_sign_plan_version_template_policy_bu_20260716 \
        trg_oa_sign_package_document_policy_bi_20260716 \
        trg_oa_sign_package_document_policy_bu_20260716
    do
        grep -Fq "$trigger_name" "$policy_migration" \
            || fail "document policy enforcement trigger is missing: $trigger_name"
    done
done

printf '[PASS] %s automatic Docker SQL files are ordered and byte-identical; %s destructive SQL file is manual-only\n' \
    "$(printf '%s\n' "$expected" | wc -l | tr -d ' ')" \
    "$(printf '%s\n' "$manual" | wc -l | tr -d ' ')"
