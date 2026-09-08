#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
attendance_migrations=(
    erp_oa_attendance_v2_20260820.sql
    erp_oa_attendance_client_idempotency_20260822.sql
    erp_oa_attendance_time_credit_20260823.sql
    erp_attendance_v2_only_20260908.sql
)
name="${attendance_migrations[0]}"
canonical="$repo_root/sql/$name"
docker_copy="$repo_root/docker/mysql/db/$name"
oa_copy="$repo_root/erp-modules/erp-oa/src/main/resources/db/migration/$name"
bootstrap="$repo_root/docker/mysql/bootstrap-files.list"

for migration in "${attendance_migrations[@]}"; do
    migration_canonical="$repo_root/sql/$migration"
    migration_docker="$repo_root/docker/mysql/db/$migration"
    migration_oa="$repo_root/erp-modules/erp-oa/src/main/resources/db/migration/$migration"
    for file in "$migration_canonical" "$migration_docker" "$migration_oa"; do
        test -f "$file" || { echo "missing migration: $file" >&2; exit 1; }
    done
    cmp -s "$migration_canonical" "$migration_docker" || {
        echo "docker migration copy differs from canonical SQL: $migration" >&2
        exit 1
    }
    cmp -s "$migration_canonical" "$migration_oa" || {
        echo "OA resource migration copy differs from canonical SQL: $migration" >&2
        exit 1
    }
done

required_tables=(
    oa_attendance_shift
    oa_attendance_shift_segment
    oa_attendance_site
    oa_attendance_schedule
    oa_attendance_schedule_segment_snapshot
    oa_attendance_punch_challenge
    oa_attendance_punch_event
    oa_attendance_evidence
    oa_attendance_day_result
    oa_attendance_leave_type
    oa_attendance_leave_request
    oa_attendance_leave_segment
    oa_attendance_leave_attachment
    oa_attendance_leave_approval_start_outbox
    oa_attendance_correction_request
    oa_attendance_correction_approval_start_outbox
)

for table in "${required_tables[@]}"; do
    grep -Fq "CREATE TABLE IF NOT EXISTS $table (" "$canonical" || {
        echo "missing repeat-safe table definition: $table" >&2
        exit 1
    }
done

required_contracts=(
    "uk_oa_attendance_schedule_user_day (user_id, business_date)"
    "uk_oa_attendance_schedule_segment_order"
    "idx_oa_attendance_schedule_segment_schedule"
    "uk_oa_attendance_challenge_token (challenge_token)"
    "uk_oa_attendance_punch_challenge (challenge_id)"
    "uk_oa_attendance_punch_request (user_id, client_request_id)"
    "uk_oa_attendance_evidence_event (punch_event_id)"
    "uk_oa_attendance_day_result_user_day (user_id, business_date)"
    "first_in_correction_request_id bigint(20) DEFAULT NULL"
    "last_out_correction_request_id bigint(20) DEFAULT NULL"
    "idx_oa_attendance_day_result_in_correction"
    "idx_oa_attendance_day_result_out_correction"
    "ADD COLUMN scheduled_minutes int NOT NULL DEFAULT 0"
    "ADD COLUMN worked_minutes int NOT NULL DEFAULT 0"
    "ADD COLUMN paid_leave_minutes int NOT NULL DEFAULT 0"
    "ADD COLUMN unpaid_leave_minutes int NOT NULL DEFAULT 0"
    "ADD COLUMN absence_minutes int NOT NULL DEFAULT 0"
    "ADD COLUMN attendance_source_version varchar(40) NOT NULL DEFAULT ''LEGACY_UNVERIFIED''"
    "uk_oa_attendance_leave_outbox_round"
    "uk_oa_attendance_correction_outbox_round"
    "'feature.oa.attendance.v2.enabled',"
    "'false', 'Y', 'system'"
    "'OA_ATTENDANCE_LEAVE'"
    "'OA_ATTENDANCE_CORRECTION'"
    "'BLOCK', 'SKIP_THROUGH'"
    "site_id bigint(20) DEFAULT NULL COMMENT '考勤地点ID；兼容历史草稿可空，新建/更新/发布必须绑定启用地点'"
    "site_name_snapshot varchar(64) DEFAULT NULL"
    "address_snapshot varchar(255) DEFAULT NULL"
    "longitude_snapshot decimal(10,7) DEFAULT NULL"
    "latitude_snapshot decimal(10,7) DEFAULT NULL"
    "coordinate_system_snapshot varchar(16) DEFAULT NULL"
    "radius_meters_snapshot int DEFAULT NULL"
    "max_accuracy_meters_snapshot int DEFAULT NULL"
    "longitude decimal(10,7) NOT NULL COMMENT '提交经度'"
    "latitude decimal(10,7) NOT NULL COMMENT '提交纬度'"
    "resolved_address varchar(255) DEFAULT NULL COMMENT '逆地理地址'"
    "distance_meters decimal(10,2) DEFAULT NULL COMMENT '距围栏中心米；兼容历史拒绝事件可空，成功打卡必须记录'"
    "INSIDE/OUTSIDE/INACCURATE；历史NOT_APPLICABLE仅兼容保留"
    "ALTER TABLE oa_attendance_schedule MODIFY COLUMN site_id bigint(20) DEFAULT NULL"
    "ALTER TABLE oa_attendance_schedule MODIFY COLUMN site_name_snapshot varchar(64) DEFAULT NULL"
    "ALTER TABLE oa_attendance_schedule MODIFY COLUMN address_snapshot varchar(255) DEFAULT NULL"
    "ALTER TABLE oa_attendance_schedule MODIFY COLUMN longitude_snapshot decimal(10,7) DEFAULT NULL"
    "ALTER TABLE oa_attendance_schedule MODIFY COLUMN latitude_snapshot decimal(10,7) DEFAULT NULL"
    "ALTER TABLE oa_attendance_schedule MODIFY COLUMN coordinate_system_snapshot varchar(16) DEFAULT NULL"
    "ALTER TABLE oa_attendance_schedule MODIFY COLUMN radius_meters_snapshot int DEFAULT NULL"
    "ALTER TABLE oa_attendance_schedule MODIFY COLUMN max_accuracy_meters_snapshot int DEFAULT NULL"
    "ALTER TABLE oa_attendance_punch_event MODIFY COLUMN distance_meters decimal(10,2) DEFAULT NULL"
    "ALTER TABLE oa_attendance_punch_event MODIFY COLUMN geofence_status varchar(16) NOT NULL"
    "完成地点围栏、班次排班和真机定位拍照验收后按发布清单开启"
    "发布并冻结班次规则快照"
    "'oa:attendance:list', 'oa:attendance:query', 'oa:attendance:export'"
)

for contract in "${required_contracts[@]}"; do
    grep -Fq "$contract" "$canonical" || {
        echo "missing migration contract: $contract" >&2
        exit 1
    }
done

attendance_flag_seed="$(sed -n "/SELECT 'OA考勤V2总开关'/,/config_key = 'feature.oa.attendance.v2.enabled'/p" "$canonical")"
grep -Fq "'false', 'Y', 'system'" <<< "$attendance_flag_seed" || {
    echo "attendance V2 must seed disabled" >&2
    exit 1
}
if grep -Fq "'true'" <<< "$attendance_flag_seed"; then
    echo "attendance V2 migration must never default the release switch to true" >&2
    exit 1
fi

client_idempotency="$repo_root/sql/${attendance_migrations[1]}"
client_idempotency_contracts=(
    "ADD COLUMN client_request_id varchar(64) DEFAULT NULL"
    "ADD COLUMN client_request_fingerprint char(64) DEFAULT NULL"
    "uk_oa_attendance_leave_client_request (user_id, shop_id, client_request_id)"
    "uk_oa_attendance_correction_client_request (user_id, shop_id, client_request_id)"
)
for contract in "${client_idempotency_contracts[@]}"; do
    grep -Fq "$contract" "$client_idempotency" || {
        echo "missing client-idempotency migration contract: $contract" >&2
        exit 1
    }
done

time_credit="$repo_root/sql/${attendance_migrations[2]}"
time_credit_contracts=(
    "CREATE TABLE IF NOT EXISTS oa_attendance_time_credit_period_lock ("
    "CREATE TABLE IF NOT EXISTS oa_attendance_time_credit_adjustment ("
    "uk_oa_attendance_time_credit_client"
    "uk_oa_attendance_time_credit_reverse"
    "'oa:attendance:time-credit:manage'"
)
for contract in "${time_credit_contracts[@]}"; do
    grep -Fq "$contract" "$time_credit" || {
        echo "missing time-credit migration contract: $contract" >&2
        exit 1
    }
done

if grep -Eq 'DROP[[:space:]]+TABLE([[:space:]]+IF[[:space:]]+EXISTS)?[[:space:]]+`?oa_attendance_record' "$canonical"; then
    echo "migration must not drop legacy oa_attendance_record" >&2
    exit 1
fi

# Sites, published schedules and punch evidence are historical audit records.
# Expand compatibility may preserve nullable columns, but must not erase or
# rewrite records to manufacture a valid geofence state.
preserved_tables=(
    oa_attendance_site
    oa_attendance_schedule
    oa_attendance_punch_event
    oa_attendance_evidence
)
for table in "${preserved_tables[@]}"; do
    if grep -Eiq "(DROP[[:space:]]+TABLE([[:space:]]+IF[[:space:]]+EXISTS)?|TRUNCATE([[:space:]]+TABLE)?)[[:space:]]+\`?${table}\`?" "$canonical"; then
        echo "migration must preserve historical table: $table" >&2
        exit 1
    fi
    if grep -Eiq "(DELETE[[:space:]]+FROM|UPDATE)[[:space:]]+\`?${table}\`?" "$canonical"; then
        echo "migration must not delete or rewrite historical rows: $table" >&2
        exit 1
    fi
done

site_permissions=(
    oa:attendance:site:list
    oa:attendance:site:query
    oa:attendance:site:add
    oa:attendance:site:edit
    oa:attendance:site:remove
)
for permission in "${site_permissions[@]}"; do
    permission_occurrences="$(grep -Fc "'$permission'" "$canonical")"
    if [[ "$permission_occurrences" -lt 2 ]]; then
        echo "attendance-site permission must be seeded and assigned to scoped managers: $permission" >&2
        exit 1
    fi
done

previous_bootstrap_line=0
for migration in "${attendance_migrations[@]}"; do
    bootstrap_count="$(grep -Fxc "$migration" "$bootstrap")"
    test "$bootstrap_count" = "1" || {
        echo "bootstrap manifest must contain $migration exactly once" >&2
        exit 1
    }
    bootstrap_line="$(grep -Fn "$migration" "$bootstrap" | cut -d: -f1)"
    test "$bootstrap_line" -gt "$previous_bootstrap_line" || {
        echo "attendance bootstrap migrations must preserve dependency order: $migration" >&2
        exit 1
    }
    previous_bootstrap_line="$bootstrap_line"
done

for migration in "${attendance_migrations[@]}"; do
    migration_canonical="$repo_root/sql/$migration"
    migration_docker="$repo_root/docker/mysql/db/$migration"
    migration_oa="$repo_root/erp-modules/erp-oa/src/main/resources/db/migration/$migration"
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$migration_canonical" "$migration_docker" "$migration_oa"
    else
        sha256sum "$migration_canonical" "$migration_docker" "$migration_oa"
    fi
done

# Optional real replay against a caller-provided, already bootstrapped disposable
# database.  The guarded name prevents accidental execution against a normal DB.
# Example:
#   ATTENDANCE_V2_VERIFY_DATABASE=attendance_v2_verify_local \
#     ./scripts/verify_attendance_v2_migration.sh --mysql \
#     --defaults-extra-file=/secure/path/mysql.cnf
if [[ "${1:-}" == "--mysql" ]]; then
    shift
    database="${ATTENDANCE_V2_VERIFY_DATABASE:-}"
    if [[ ! "$database" =~ ^attendance_v2_verify_[A-Za-z0-9_]+$ ]]; then
        echo "ATTENDANCE_V2_VERIFY_DATABASE must start with attendance_v2_verify_" >&2
        exit 1
    fi
    command -v mysql >/dev/null 2>&1 || {
        echo "mysql client is required for --mysql replay" >&2
        exit 1
    }
    mysql_options=("$@")
    mysql_scalar() {
        local query="$1"
        mysql "${mysql_options[@]}" --database="$database" \
            --batch --skip-column-names --execute="$query"
    }
    table_row_count_if_present() {
        local table="$1"
        local exists
        exists="$(mysql_scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = '$table'")"
        if [[ "$exists" == "1" ]]; then
            mysql_scalar "SELECT COUNT(*) FROM \`$table\`"
        else
            echo "ABSENT"
        fi
    }

    site_rows_before="$(table_row_count_if_present oa_attendance_site)"
    schedule_rows_before="$(table_row_count_if_present oa_attendance_schedule)"
    punch_rows_before="$(table_row_count_if_present oa_attendance_punch_event)"
    evidence_rows_before="$(table_row_count_if_present oa_attendance_evidence)"

    for replay_round in 1 2; do
        for migration in "${attendance_migrations[@]}"; do
            mysql "${mysql_options[@]}" "$database" < "$repo_root/sql/$migration"
        done
    done

    assert_row_count_preserved() {
        local table="$1"
        local rows_before="$2"
        local rows_after
        rows_after="$(table_row_count_if_present "$table")"
        if [[ "$rows_before" != "ABSENT" && "$rows_before" != "$rows_after" ]]; then
            echo "repeat migration changed historical row count for $table" >&2
            exit 1
        fi
    }
    assert_row_count_preserved oa_attendance_site "$site_rows_before"
    assert_row_count_preserved oa_attendance_schedule "$schedule_rows_before"
    assert_row_count_preserved oa_attendance_punch_event "$punch_rows_before"
    assert_row_count_preserved oa_attendance_evidence "$evidence_rows_before"

    nullable_schedule_columns="$(mysql_scalar "
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'oa_attendance_schedule'
          AND column_name IN (
              'site_id', 'site_name_snapshot', 'address_snapshot',
              'longitude_snapshot', 'latitude_snapshot',
              'coordinate_system_snapshot', 'radius_meters_snapshot',
              'max_accuracy_meters_snapshot'
          )
          AND is_nullable = 'YES'
    ")"
    test "$nullable_schedule_columns" = "8" || {
        echo "schedule site/geofence columns must remain nullable for expand compatibility" >&2
        exit 1
    }

    nullable_distance="$(mysql_scalar "
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'oa_attendance_punch_event'
          AND column_name = 'distance_meters'
          AND is_nullable = 'YES'
    ")"
    test "$nullable_distance" = "1" || {
        echo "punch distance_meters must remain nullable for historical rejected events" >&2
        exit 1
    }

    required_location_columns="$(mysql_scalar "
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'oa_attendance_punch_event'
          AND column_name IN (
              'longitude', 'latitude', 'accuracy_meters', 'coordinate_system'
          )
          AND is_nullable = 'NO'
    ")"
    test "$required_location_columns" = "4" || {
        echo "punch location audit fields must remain required" >&2
        exit 1
    }

    geofence_comment="$(mysql_scalar "
        SELECT column_comment
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'oa_attendance_punch_event'
          AND column_name = 'geofence_status'
    ")"
    [[ "$geofence_comment" == *INACCURATE* && "$geofence_comment" == *历史NOT_APPLICABLE仅兼容保留* ]] || {
        echo "geofence_status must document strict results and legacy-only NOT_APPLICABLE" >&2
        exit 1
    }

    site_permission_count="$(mysql_scalar "
        SELECT COUNT(DISTINCT perms)
        FROM sys_menu
        WHERE perms IN (
            'oa:attendance:site:list', 'oa:attendance:site:query',
            'oa:attendance:site:add', 'oa:attendance:site:edit',
            'oa:attendance:site:remove'
        )
          AND status = '0'
    ")"
    test "$site_permission_count" = "5" || {
        echo "all five attendance-site permissions must be active" >&2
        exit 1
    }

    enabled_legacy_menu_count="$(mysql_scalar "
        SELECT COUNT(*)
        FROM sys_menu
        WHERE perms IN (
            'oa:attendance:list', 'oa:attendance:query',
            'oa:attendance:export'
        )
          AND status = '0'
    ")"
    test "$enabled_legacy_menu_count" = "0" || {
        echo "legacy attendance menu permissions must stay disabled" >&2
        exit 1
    }

    echo "repeat execution passed in disposable database: $database"
elif [[ $# -gt 0 ]]; then
    echo "usage: $0 [--mysql mysql-client-options...]" >&2
    exit 1
fi

echo "attendance V2 migration structure, mirrors and manifest passed"
