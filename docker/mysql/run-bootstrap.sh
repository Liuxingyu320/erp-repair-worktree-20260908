# This file is sourced by the official mysql:5.7 entrypoint.
# bootstrap-files.list is the only source of execution order.

erp_bootstrap_root=/opt/erp-bootstrap
erp_bootstrap_list="$erp_bootstrap_root/bootstrap-files.list"
erp_bootstrap_sql_dir="$erp_bootstrap_root/sql"

if ! declare -F docker_process_sql >/dev/null 2>&1; then
    printf '[ERROR] official MySQL docker_process_sql helper is unavailable\n' >&2
    return 1
fi

if [[ ! -r "$erp_bootstrap_list" ]]; then
    printf '[ERROR] missing bootstrap list: %s\n' "$erp_bootstrap_list" >&2
    return 1
fi

while IFS= read -r erp_bootstrap_raw || [[ -n "$erp_bootstrap_raw" ]]; do
    erp_bootstrap_name="${erp_bootstrap_raw%$'\r'}"
    [[ -z "$erp_bootstrap_name" || "$erp_bootstrap_name" == \#* ]] && continue

    if [[ ! "$erp_bootstrap_name" =~ ^[A-Za-z0-9._-]+\.sql$ ]]; then
        printf '[ERROR] unsafe bootstrap filename: %s\n' "$erp_bootstrap_name" >&2
        return 1
    fi

    erp_bootstrap_path="$erp_bootstrap_sql_dir/$erp_bootstrap_name"
    if [[ ! -r "$erp_bootstrap_path" ]]; then
        printf '[ERROR] missing bootstrap SQL: %s\n' "$erp_bootstrap_name" >&2
        return 1
    fi

    printf '[BOOTSTRAP] applying %s\n' "$erp_bootstrap_name"
    docker_process_sql < "$erp_bootstrap_path"
done < "$erp_bootstrap_list"

unset erp_bootstrap_root erp_bootstrap_list erp_bootstrap_sql_dir
unset erp_bootstrap_raw erp_bootstrap_name erp_bootstrap_path
