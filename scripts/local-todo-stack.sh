#!/usr/bin/env bash

set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UI_DIR="$ROOT_DIR/erp-ui"
UI_CLI="$UI_DIR/node_modules/@vue/cli-service/bin/vue-cli-service.js"
NODE_BIN="/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node"
JAVA_BIN="${JAVA_BIN:-$(command -v java 2>/dev/null || true)}"
CURL_BIN="${CURL_BIN:-$(command -v curl 2>/dev/null || true)}"
SYSTEM_PORT="${LOCAL_TODO_SYSTEM_PORT:-9201}"
AUTH_PORT="${LOCAL_TODO_AUTH_PORT:-9200}"
OA_PORT="${LOCAL_TODO_OA_PORT:-9204}"
INVENTORY_PORT="${LOCAL_TODO_INVENTORY_PORT:-9205}"
GATEWAY_PORT="${LOCAL_TODO_GATEWAY_PORT:-8080}"
UI_PORT="${LOCAL_TODO_UI_PORT:-1025}"
DEFAULT_PORT_PROFILE="9201-9200-9204-9205-8080-1025"
PORT_PROFILE="$SYSTEM_PORT-$AUTH_PORT-$OA_PORT-$INVENTORY_PORT-$GATEWAY_PORT-$UI_PORT"
if [ "$PORT_PROFILE" = "$DEFAULT_PORT_PROFILE" ]; then
  RUNTIME_DIR="$ROOT_DIR/output/local-todo-stack"
else
  RUNTIME_DIR="$ROOT_DIR/output/local-todo-stack-$PORT_PROFILE"
fi
START_TIMEOUT_SECONDS="${LOCAL_TODO_START_TIMEOUT_SECONDS:-90}"
STOP_TIMEOUT_SECONDS="${LOCAL_TODO_STOP_TIMEOUT_SECONDS:-30}"
HEALTH_TIMEOUT_SECONDS="${LOCAL_TODO_HEALTH_TIMEOUT_SECONDS:-5}"
LOG_LINES="${LOCAL_TODO_LOG_LINES:-200}"

SERVICES=(system auth oa inventory gateway ui)
STOP_ORDER=(ui gateway inventory oa auth system)
STARTED_THIS_RUN=()

usage()
{
  cat <<'USAGE'
用法：
  ./scripts/local-todo-stack.sh preflight
  ./scripts/local-todo-stack.sh start
  ./scripts/local-todo-stack.sh status
  ./scripts/local-todo-stack.sh health
  ./scripts/local-todo-stack.sh logs <system|auth|oa|inventory|gateway|ui>
  ./scripts/local-todo-stack.sh restart <system|auth|oa|inventory|gateway|ui>
  ./scripts/local-todo-stack.sh stop [system|auth|oa|inventory|gateway|ui]

可选环境变量：
  TODO_GATEWAY_BEARER_TOKEN      登录后的访问令牌；仅 health 业务探针读取
  TODO_GATEWAY_SHOP_DEPT_ID      业务探针使用的门店组织 ID
  LOCAL_TODO_START_TIMEOUT_SECONDS
  LOCAL_TODO_STOP_TIMEOUT_SECONDS
  LOCAL_TODO_HEALTH_TIMEOUT_SECONDS
  LOCAL_TODO_LOG_LINES
  LOCAL_TODO_SYSTEM_PORT         默认 9201
  LOCAL_TODO_AUTH_PORT           默认 9200
  LOCAL_TODO_OA_PORT             默认 9204
  LOCAL_TODO_INVENTORY_PORT      默认 9205
  LOCAL_TODO_GATEWAY_PORT        默认 8080
  LOCAL_TODO_UI_PORT             默认 1025
USAGE
}

service_port()
{
  case "$1" in
    system) printf '%s\n' "$SYSTEM_PORT" ;;
    auth) printf '%s\n' "$AUTH_PORT" ;;
    oa) printf '%s\n' "$OA_PORT" ;;
    inventory) printf '%s\n' "$INVENTORY_PORT" ;;
    gateway) printf '%s\n' "$GATEWAY_PORT" ;;
    ui) printf '%s\n' "$UI_PORT" ;;
    *) return 1 ;;
  esac
}

service_label()
{
  case "$1" in
    system) printf '%s\n' 'erp-system' ;;
    auth) printf '%s\n' 'erp-auth' ;;
    oa) printf '%s\n' 'erp-oa' ;;
    inventory) printf '%s\n' 'erp-inventory' ;;
    gateway) printf '%s\n' 'erp-gateway' ;;
    ui) printf '%s\n' 'erp-ui' ;;
    *) return 1 ;;
  esac
}

service_jar()
{
  case "$1" in
    system) printf '%s\n' "$ROOT_DIR/erp-modules/erp-system/target/erp-modules-system.jar" ;;
    auth) printf '%s\n' "$ROOT_DIR/erp-auth/target/erp-auth.jar" ;;
    oa) printf '%s\n' "$ROOT_DIR/erp-modules/erp-oa/target/erp-modules-oa.jar" ;;
    inventory) printf '%s\n' "$ROOT_DIR/erp-modules/erp-inventory/target/erp-modules-inventory.jar" ;;
    gateway) printf '%s\n' "$ROOT_DIR/erp-gateway/target/erp-gateway.jar" ;;
    ui) return 1 ;;
    *) return 1 ;;
  esac
}

service_module_dir()
{
  case "$1" in
    system) printf '%s\n' "$ROOT_DIR/erp-modules/erp-system" ;;
    auth) printf '%s\n' "$ROOT_DIR/erp-auth" ;;
    oa) printf '%s\n' "$ROOT_DIR/erp-modules/erp-oa" ;;
    inventory) printf '%s\n' "$ROOT_DIR/erp-modules/erp-inventory" ;;
    gateway) printf '%s\n' "$ROOT_DIR/erp-gateway" ;;
    *) return 1 ;;
  esac
}

jar_is_fresh()
{
  local service="$1"
  local jar
  local module_dir
  local source_dir
  jar="$(service_jar "$service")"
  module_dir="$(service_module_dir "$service")"

  for source_dir in "$module_dir/src" "$ROOT_DIR/erp-common/erp-common-core/src"; do
    if [ -d "$source_dir" ] && find "$source_dir" -type f -newer "$jar" -print -quit | grep -q .; then
      return 1
    fi
  done
  if [ "$module_dir/pom.xml" -nt "$jar" ] || [ "$ROOT_DIR/pom.xml" -nt "$jar" ]; then
    return 1
  fi
}

service_identity()
{
  if [ "$1" = "ui" ]; then
    printf '%s\n' "$UI_CLI"
  else
    service_jar "$1"
  fi
}

pid_file()
{
  printf '%s\n' "$RUNTIME_DIR/$1.pid"
}

log_file()
{
  printf '%s\n' "$RUNTIME_DIR/$1.log"
}

is_known_service()
{
  local candidate="$1"
  local service
  for service in "${SERVICES[@]}"; do
    if [ "$candidate" = "$service" ]; then
      return 0
    fi
  done
  return 1
}

require_service()
{
  if [ "$#" -ne 1 ] || ! is_known_service "$1"; then
    printf '必须指定有效服务：system、auth、oa、inventory、gateway 或 ui。\n' >&2
    usage >&2
    return 2
  fi
}

is_positive_integer()
{
  case "$1" in
    ''|*[!0-9]*|0) return 1 ;;
    *) return 0 ;;
  esac
}

validate_timeouts()
{
  local name
  local value
  for name in START_TIMEOUT_SECONDS STOP_TIMEOUT_SECONDS HEALTH_TIMEOUT_SECONDS LOG_LINES; do
    eval "value=\${$name}"
    if ! is_positive_integer "$value"; then
      printf '%s 必须是正整数，当前值：%s\n' "$name" "$value" >&2
      return 1
    fi
  done
}

validate_ports()
{
  local name
  local value
  local numeric
  local -a values=()
  local index
  local other_index

  for name in SYSTEM_PORT AUTH_PORT OA_PORT INVENTORY_PORT GATEWAY_PORT UI_PORT; do
    eval "value=\${$name}"
    case "$value" in
      ''|0|0*|*[!0-9]*|??????*)
        printf '%s 必须是 1-65535 的整数端口，当前值：%s\n' "$name" "$value" >&2
        return 1
        ;;
    esac
    numeric=$((10#$value))
    if [ "$numeric" -lt 1 ] || [ "$numeric" -gt 65535 ]; then
      printf '%s 必须是 1-65535 的整数端口，当前值：%s\n' "$name" "$value" >&2
      return 1
    fi
    values+=("$numeric")
  done

  for ((index = 0; index < ${#values[@]}; index += 1)); do
    for ((other_index = index + 1; other_index < ${#values[@]}; other_index += 1)); do
      if [ "${values[$index]}" -eq "${values[$other_index]}" ]; then
        printf '六个目标服务端口必须互不相同，重复端口：%s\n' "${values[$index]}" >&2
        return 1
      fi
    done
  done
}

validate_configuration()
{
  validate_timeouts
  validate_ports
}

read_pid()
{
  local file
  local pid
  file="$(pid_file "$1")"
  [ -f "$file" ] || return 1
  IFS= read -r pid < "$file" || true
  case "$pid" in
    ''|*[!0-9]*) return 2 ;;
    *) printf '%s\n' "$pid" ;;
  esac
}

pid_is_running()
{
  ps -p "$1" >/dev/null 2>&1
}

process_command()
{
  ps -p "$1" -o command= 2>/dev/null | sed 's/^[[:space:]]*//'
}

verify_owned_pid()
{
  local service="$1"
  local pid="$2"
  local expected
  local command

  pid_is_running "$pid" || return 1
  expected="$(service_identity "$service")"
  command="$(ps -p "$pid" -o command= 2>/dev/null || true)"
  case "$command" in
    *"$expected"*) return 0 ;;
    *) return 1 ;;
  esac
}

port_pids()
{
  lsof -nP -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null | sort -u || true
}

port_is_open()
{
  local port="$1"
  nc -z -G 1 127.0.0.1 "$port" >/dev/null 2>&1 ||
    nc -z -w 1 127.0.0.1 "$port" >/dev/null 2>&1
}

redact_log_stream()
{
  sed -E \
    -e 's/((--)?[^[:space:],;}]*([Pp]assword|[Tt]oken|[Ss]ecret|[Aa]uthorization|[Cc]ookie)[^=:[:space:],;}]*[=:][[:space:]]*)[^,;}[:space:]]*/\1[REDACTED]/g' \
    -e 's/((--)?[^[:space:]=:]*([Pp]assword|[Tt]oken|[Ss]ecret|[Aa]uthorization|[Cc]ookie)[^[:space:]=:]*[[:space:]]+)[^[:space:]]+/\1[REDACTED]/g'
}

print_port_owners()
{
  local port="$1"
  local pid
  local command
  local pids
  pids="$(port_pids "$port")"
  [ -n "$pids" ] || return 0

  while IFS= read -r pid; do
    [ -n "$pid" ] || continue
    command="$(process_command "$pid" || true)"
    printf '  PID %s: %s\n' "$pid" "${command:-<无法读取命令>}" | redact_log_stream
  done <<< "$pids"
}

check_dependency_port()
{
  local name="$1"
  local port="$2"
  if port_is_open "$port"; then
    printf '[OK] %s 127.0.0.1:%s 可连接。\n' "$name" "$port"
    return 0
  fi
  printf '[FAIL] %s 127.0.0.1:%s 不可连接。\n' "$name" "$port" >&2
  return 1
}

check_target_port()
{
  local service="$1"
  local port
  local pids
  local pid=""
  port="$(service_port "$service")"
  pids="$(port_pids "$port")"

  if [ -z "$pids" ]; then
    printf '[OK] %s 端口 %s 可用。\n' "$(service_label "$service")" "$port"
    return 0
  fi

  if pid="$(read_pid "$service" 2>/dev/null)" && verify_owned_pid "$service" "$pid"; then
    if printf '%s\n' "$pids" | grep -Fxq "$pid"; then
      printf '[OK] %s 已由本脚本管理（PID %s，端口 %s）。\n' \
        "$(service_label "$service")" "$pid" "$port"
      return 0
    fi
  fi

  printf '[FAIL] %s 端口 %s 被非本脚本进程占用；不会接管或停止。\n' \
    "$(service_label "$service")" "$port" >&2
  print_port_owners "$port" >&2
  return 1
}

preflight()
{
  local failures=0
  local command
  local service
  local jar

  validate_configuration || failures=$((failures + 1))

  for command in java curl find lsof nc ps sed grep tail; do
    if command -v "$command" >/dev/null 2>&1; then
      printf '[OK] 命令可用：%s\n' "$command"
    else
      printf '[FAIL] 缺少本机命令：%s\n' "$command" >&2
      failures=$((failures + 1))
    fi
  done

  if [ -n "$JAVA_BIN" ] && [ -x "$JAVA_BIN" ]; then
    printf '[OK] Java：%s\n' "$JAVA_BIN"
  else
    printf '[FAIL] Java 不可执行；可通过 JAVA_BIN 指定。\n' >&2
    failures=$((failures + 1))
  fi

  if [ -x "$NODE_BIN" ]; then
    printf '[OK] Node：%s\n' "$NODE_BIN"
  else
    printf '[FAIL] 固定 Node 不可执行：%s\n' "$NODE_BIN" >&2
    failures=$((failures + 1))
  fi

  if [ -f "$UI_CLI" ] && [ -d "$UI_DIR/node_modules" ]; then
    printf '[OK] 前端依赖和 Vue CLI 已就绪。\n'
  else
    printf '[FAIL] 缺少 erp-ui/node_modules 或 Vue CLI。\n' >&2
    failures=$((failures + 1))
  fi

  for service in system auth oa inventory gateway; do
    jar="$(service_jar "$service")"
    if [ ! -f "$jar" ]; then
      printf '[FAIL] 缺少 JAR：%s\n' "$jar" >&2
      failures=$((failures + 1))
    elif ! jar_is_fresh "$service"; then
      printf '[FAIL] JAR 早于当前源码或构建配置，请重新打包：%s\n' "$jar" >&2
      failures=$((failures + 1))
    else
      printf '[OK] JAR 已存在且不早于源码：%s\n' "$jar"
    fi
  done

  check_dependency_port MySQL 3306 || failures=$((failures + 1))
  check_dependency_port Redis 6379 || failures=$((failures + 1))

  for service in "${SERVICES[@]}"; do
    check_target_port "$service" || failures=$((failures + 1))
  done

  if [ "$failures" -ne 0 ]; then
    printf '预检失败：%s 项问题。未启动、未停止任何进程。\n' "$failures" >&2
    return 1
  fi
  printf '预检通过：本机依赖、构建产物和目标端口满足启动条件。\n'
}

ensure_runtime_dir()
{
  umask 077
  mkdir -p "$RUNTIME_DIR"
  chmod 700 "$RUNTIME_DIR"
}

remove_stale_pid_file()
{
  local service="$1"
  local file
  local pid=""
  file="$(pid_file "$service")"
  [ -f "$file" ] || return 0

  if pid="$(read_pid "$service" 2>/dev/null)" && pid_is_running "$pid"; then
    return 0
  fi
  rm -f "$file"
}

show_last_log()
{
  local file
  file="$(log_file "$1")"
  [ -f "$file" ] || return 0
  printf '%s\n' "--- $(service_label "$1") 最近日志（已脱敏）---" >&2
  tail -n 40 "$file" | redact_log_stream >&2
}

wait_for_port()
{
  local service="$1"
  local pid="$2"
  local port
  local elapsed=0
  port="$(service_port "$service")"

  while [ "$elapsed" -lt "$START_TIMEOUT_SECONDS" ]; do
    if ! pid_is_running "$pid"; then
      printf '[FAIL] %s 启动进程已退出（PID %s）。\n' "$(service_label "$service")" "$pid" >&2
      show_last_log "$service"
      return 1
    fi
    if port_is_open "$port"; then
      printf '[OK] %s 已监听 127.0.0.1:%s（PID %s）。\n' \
        "$(service_label "$service")" "$port" "$pid"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done

  printf '[FAIL] %s 在 %s 秒内未监听端口 %s。\n' \
    "$(service_label "$service")" "$START_TIMEOUT_SECONDS" "$port" >&2
  show_last_log "$service"
  return 1
}

start_service()
{
  local service="$1"
  local file
  local log
  local pid=""
  local port
  local pids
  local jar
  local -a java_args=()

  file="$(pid_file "$service")"
  log="$(log_file "$service")"
  port="$(service_port "$service")"

  if pid="$(read_pid "$service" 2>/dev/null)" && verify_owned_pid "$service" "$pid"; then
    if port_is_open "$port"; then
      printf '[SKIP] %s 已运行（PID %s）。\n' "$(service_label "$service")" "$pid"
      return 0
    fi
    printf '[FAIL] %s 的受管进程存在但未监听端口 %s；请先查看日志。\n' \
      "$(service_label "$service")" "$port" >&2
    return 1
  fi

  remove_stale_pid_file "$service"
  pids="$(port_pids "$port")"
  if [ -n "$pids" ]; then
    printf '[FAIL] %s 端口 %s 已被占用；不会接管。\n' "$(service_label "$service")" "$port" >&2
    print_port_owners "$port" >&2
    return 1
  fi

  : >> "$log"
  if [ "$service" = "ui" ]; then
    (
      cd "$UI_DIR"
      exec nohup env BROWSER=none NODE_ENV=development \
        LOCAL_TODO_GATEWAY_URL="http://127.0.0.1:$GATEWAY_PORT" \
        "$NODE_BIN" "$UI_CLI" serve --host 0.0.0.0 --port "$port"
    ) >> "$log" 2>&1 < /dev/null &
  else
    jar="$(service_jar "$service")"
    java_args=(
      '--spring.profiles.active=local'
      "--server.port=$port"
    )
    case "$service" in
      system)
        java_args+=(
          "--spring.cloud.discovery.client.simple.instances.erp-system[0].uri=http://127.0.0.1:$SYSTEM_PORT"
        )
        ;;
      auth)
        java_args+=(
          "--spring.cloud.discovery.client.simple.instances.erp-system[0].uri=http://127.0.0.1:$SYSTEM_PORT"
        )
        ;;
      oa)
        java_args+=(
          "--spring.cloud.discovery.client.simple.instances.erp-oa[0].uri=http://127.0.0.1:$OA_PORT"
          "--spring.cloud.discovery.client.simple.instances.erp-system[0].uri=http://127.0.0.1:$SYSTEM_PORT"
          "--spring.cloud.discovery.client.simple.instances.erp-inventory[0].uri=http://127.0.0.1:$INVENTORY_PORT"
        )
        ;;
      inventory)
        java_args+=(
          "--spring.cloud.discovery.client.simple.instances.erp-inventory[0].uri=http://127.0.0.1:$INVENTORY_PORT"
          "--spring.cloud.discovery.client.simple.instances.erp-system[0].uri=http://127.0.0.1:$SYSTEM_PORT"
        )
        ;;
      gateway)
        java_args+=(
          '--security.captcha.enabled=false'
        )
        ;;
    esac
    (
      cd "$ROOT_DIR"
      exec nohup "$JAVA_BIN" -jar "$jar" "${java_args[@]}"
    ) >> "$log" 2>&1 < /dev/null &
  fi
  pid=$!

  printf '%s\n' "$pid" > "$file.tmp"
  chmod 600 "$file.tmp"
  mv "$file.tmp" "$file"
  STARTED_THIS_RUN+=("$service")

  wait_for_port "$service" "$pid"
}

stop_service()
{
  local service="$1"
  local file
  local pid=""
  local expected
  local command
  local elapsed=0
  local port

  file="$(pid_file "$service")"
  if [ ! -f "$file" ]; then
    port="$(service_port "$service")"
    if port_is_open "$port"; then
      printf '[REFUSE] %s 没有 PID 文件但端口 %s 正在使用；不会发送停止信号。\n' \
        "$(service_label "$service")" "$port" >&2
      print_port_owners "$port" >&2
      return 1
    fi
    printf '[SKIP] %s 没有 PID 文件。\n' "$(service_label "$service")"
    return 0
  fi
  if ! pid="$(read_pid "$service" 2>/dev/null)"; then
    printf '[REFUSE] %s PID 文件内容无效；为避免误停，不自动删除：%s\n' \
      "$(service_label "$service")" "$file" >&2
    return 1
  fi
  if ! pid_is_running "$pid"; then
    rm -f "$file"
    printf '[OK] %s 的陈旧 PID 文件已清理（进程 %s 不存在）。\n' \
      "$(service_label "$service")" "$pid"
    return 0
  fi

  expected="$(service_identity "$service")"
  command="$(ps -p "$pid" -o command= 2>/dev/null || true)"
  case "$command" in
    *"$expected"*) ;;
    *)
      printf '[REFUSE] PID %s 的命令与 %s 身份不匹配；不会停止。\n' \
        "$pid" "$(service_label "$service")" >&2
      printf '  期望包含：%s\n' "$expected" >&2
      printf '  实际命令：%s\n' "${command:-<无法读取>}" | redact_log_stream >&2
      return 1
      ;;
  esac

  kill -TERM "$pid"
  while pid_is_running "$pid" && [ "$elapsed" -lt "$STOP_TIMEOUT_SECONDS" ]; do
    sleep 1
    elapsed=$((elapsed + 1))
  done

  if pid_is_running "$pid"; then
    command="$(ps -p "$pid" -o command= 2>/dev/null || true)"
    case "$command" in
      *"$expected"*) kill -KILL "$pid" ;;
      *)
        printf '[REFUSE] 等待期间 PID %s 身份发生变化；不会继续停止。\n' "$pid" >&2
        return 1
        ;;
    esac
  fi

  elapsed=0
  while pid_is_running "$pid" && [ "$elapsed" -lt 5 ]; do
    sleep 1
    elapsed=$((elapsed + 1))
  done
  if pid_is_running "$pid"; then
    printf '[FAIL] %s 进程 %s 仍在运行，保留 PID 文件。\n' \
      "$(service_label "$service")" "$pid" >&2
    return 1
  fi

  rm -f "$file"
  printf '[OK] %s 已停止（PID %s）。\n' "$(service_label "$service")" "$pid"
}

cleanup_started_this_run()
{
  local index
  local service
  printf '启动未完成，只回收本次新启动且身份匹配的进程。\n' >&2
  index=$((${#STARTED_THIS_RUN[@]} - 1))
  while [ "$index" -ge 0 ]; do
    service="${STARTED_THIS_RUN[$index]}"
    stop_service "$service" || true
    index=$((index - 1))
  done
}

start_all()
{
  local service
  preflight || return 1
  ensure_runtime_dir
  for service in "${SERVICES[@]}"; do
    if ! start_service "$service"; then
      cleanup_started_this_run
      return 1
    fi
  done
  printf '本机待办联调栈启动完成。请执行 health 检查三层健康状态。\n'
}

stop_all()
{
  local service
  local failures=0
  for service in "${STOP_ORDER[@]}"; do
    stop_service "$service" || failures=$((failures + 1))
  done
  if [ "$failures" -ne 0 ]; then
    printf '停止完成，但有 %s 个进程因身份或退出检查失败而保留。\n' "$failures" >&2
    return 1
  fi
  printf '本机待办联调栈已按反向依赖顺序停止。\n'
}

status_service()
{
  local service="$1"
  local port
  local pid=""
  local pids
  port="$(service_port "$service")"
  pids="$(port_pids "$port")"

  if pid="$(read_pid "$service" 2>/dev/null)"; then
    if verify_owned_pid "$service" "$pid"; then
      if printf '%s\n' "$pids" | grep -Fxq "$pid"; then
        printf '[RUNNING] %-13s PID %-7s port %s\n' "$(service_label "$service")" "$pid" "$port"
      else
        printf '[STARTING/UNHEALTHY] %-13s PID %-7s port %s 未监听\n' \
          "$(service_label "$service")" "$pid" "$port"
      fi
      return 0
    fi
    if pid_is_running "$pid"; then
      printf '[MISMATCH] %-13s PID %-7s 身份不匹配，未采取操作\n' \
        "$(service_label "$service")" "$pid"
      return 0
    fi
    printf '[STALE] %-13s PID %-7s 不存在；status 不修改 PID 文件\n' \
      "$(service_label "$service")" "$pid"
    return 0
  fi

  if [ -n "$pids" ]; then
    printf '[UNMANAGED] %-13s port %s 被占用\n' "$(service_label "$service")" "$port"
    print_port_owners "$port"
  else
    printf '[STOPPED] %-13s port %s 可用\n' "$(service_label "$service")" "$port"
  fi
}

status_all()
{
  local service
  for service in "${SERVICES[@]}"; do
    status_service "$service"
  done
}

HTTP_BODY=""
HTTP_CODE=""

fetch_http()
{
  local url="$1"
  local response
  if ! response="$("$CURL_BIN" --silent --show-error \
      --connect-timeout "$HEALTH_TIMEOUT_SECONDS" \
      --max-time "$HEALTH_TIMEOUT_SECONDS" \
      --write-out $'\n%{http_code}' "$url" 2>/dev/null)"; then
    return 1
  fi
  HTTP_CODE="${response##*$'\n'}"
  HTTP_BODY="${response%$'\n'*}"
}

http_code_is_success()
{
  case "$1" in
    2??) return 0 ;;
    *) return 1 ;;
  esac
}

health_service()
{
  local service="$1"
  local port
  local url
  port="$(service_port "$service")"

  if ! port_is_open "$port"; then
    printf '[FAIL:L1] %s 未监听端口 %s。\n' "$(service_label "$service")" "$port" >&2
    return 1
  fi
  printf '[OK:L1] %s 端口 %s 正在监听。\n' "$(service_label "$service")" "$port"

  if [ "$service" = "ui" ]; then
    url="http://127.0.0.1:$port/"
    if fetch_http "$url" && http_code_is_success "$HTTP_CODE"; then
      printf '[OK:L2] %s 首页返回 HTTP %s。\n' "$(service_label "$service")" "$HTTP_CODE"
      return 0
    fi
    printf '[FAIL:L2] %s 首页健康请求失败（HTTP %s）。\n' \
      "$(service_label "$service")" "${HTTP_CODE:-000}" >&2
    return 1
  fi

  url="http://127.0.0.1:$port/actuator/health"
  if fetch_http "$url" && http_code_is_success "$HTTP_CODE" &&
      printf '%s' "$HTTP_BODY" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    printf '[OK:L2] %s actuator 状态为 UP（HTTP %s）。\n' \
      "$(service_label "$service")" "$HTTP_CODE"
    return 0
  fi
  printf '[FAIL:L2] %s actuator 未返回 2xx + UP（HTTP %s）。\n' \
    "$(service_label "$service")" "${HTTP_CODE:-000}" >&2
  return 1
}

fetch_authenticated_summary()
{
  local endpoint="$1"
  local token="$2"
  local shop_dept_id="${TODO_GATEWAY_SHOP_DEPT_ID:-}"
  local config
  local response

  case "$token" in
    *$'\n'*|*'"'*)
      printf '[FAIL:L3] TODO_GATEWAY_BEARER_TOKEN 含不安全字符，拒绝执行。\n' >&2
      return 1
      ;;
  esac
  case "$shop_dept_id" in
    *$'\n'*|*'"'*)
      printf '[FAIL:L3] TODO_GATEWAY_SHOP_DEPT_ID 含不安全字符，拒绝执行。\n' >&2
      return 1
      ;;
  esac

  config="header = \"Authorization: Bearer $token\""
  if [ -n "$shop_dept_id" ]; then
    config="$config"$'\n'"header = \"X-Shop-Dept-Id: $shop_dept_id\""
  fi

  if ! response="$(printf '%s\n' "$config" | "$CURL_BIN" --config - \
      --silent --show-error \
      --connect-timeout "$HEALTH_TIMEOUT_SECONDS" \
      --max-time "$HEALTH_TIMEOUT_SECONDS" \
      --write-out $'\n%{http_code}' "http://127.0.0.1:$GATEWAY_PORT$endpoint" 2>/dev/null)"; then
    return 1
  fi
  HTTP_CODE="${response##*$'\n'}"
  HTTP_BODY="${response%$'\n'*}"
}

validate_todo_summary()
{
  printf '%s' "$1" | "$NODE_BIN" -e '
const fs = require("fs")
try {
  const response = JSON.parse(fs.readFileSync(0, "utf8"))
  const data = response && response.data
  const valid = response && Number(response.code) === 200 &&
    data && typeof data === "object" &&
    typeof data.source === "string" &&
    Number.isFinite(Number(data.total)) &&
    Array.isArray(data.recent) &&
    data.typeCounts && typeof data.typeCounts === "object"
  process.exit(valid ? 0 : 1)
} catch (_) {
  process.exit(1)
}'
}

business_probe()
{
  local endpoint="$1"
  local token="$2"
  if fetch_authenticated_summary "$endpoint" "$token" &&
      http_code_is_success "$HTTP_CODE" && validate_todo_summary "$HTTP_BODY"; then
    printf '[OK:L3] %s 返回合法待办汇总结构（HTTP %s）。\n' "$endpoint" "$HTTP_CODE"
    return 0
  fi
  printf '[FAIL:L3] %s 未返回合法待办汇总结构（HTTP %s）。\n' \
    "$endpoint" "${HTTP_CODE:-000}" >&2
  return 1
}

health_all()
{
  local service
  local endpoint
  local failures=0
  local token="${TODO_GATEWAY_BEARER_TOKEN:-}"

  if [ -z "$CURL_BIN" ] || [ ! -x "$CURL_BIN" ]; then
    printf '[FAIL] curl 不可执行，无法执行健康检查。\n' >&2
    return 1
  fi
  if [ ! -x "$NODE_BIN" ]; then
    printf '[FAIL] 固定 Node 不可执行，无法校验业务响应。\n' >&2
    return 1
  fi

  for service in "${SERVICES[@]}"; do
    health_service "$service" || failures=$((failures + 1))
  done

  if [ -z "$token" ]; then
    printf '[SKIP:L3] 未设置 TODO_GATEWAY_BEARER_TOKEN；登录后网关业务探针未执行。\n'
    printf '基础健康检查完成，但不能据此判定三 Provider 业务链路全绿。\n'
  else
    for endpoint in /inventory/todo/summary /oa/todo/summary /system/todo/summary; do
      business_probe "$endpoint" "$token" || failures=$((failures + 1))
    done
  fi

  if [ "$failures" -ne 0 ]; then
    printf '健康检查失败：%s 个检查未通过。\n' "$failures" >&2
    return 1
  fi
  if [ -n "$token" ]; then
    printf '三层健康检查全部通过。\n'
  else
    printf '端口与应用健康层通过；认证业务层已明确跳过。\n'
  fi
}

follow_logs()
{
  local service="$1"
  local file
  file="$(log_file "$service")"
  if [ ! -f "$file" ]; then
    printf '日志不存在：%s\n' "$file" >&2
    return 1
  fi
  printf '显示 %s 最近 %s 行并持续跟随；输出经过常见凭证脱敏。\n' \
    "$(service_label "$service")" "$LOG_LINES"
  tail -n "$LOG_LINES" -f "$file" | redact_log_stream
}

restart_service()
{
  local service="$1"
  preflight || return 1
  ensure_runtime_dir
  stop_service "$service" || return 1
  STARTED_THIS_RUN=()
  if ! start_service "$service"; then
    cleanup_started_this_run
    return 1
  fi
  printf '%s 重启完成。\n' "$(service_label "$service")"
}

main()
{
  local action="${1:-}"
  validate_configuration || return 1

  case "$action" in
    preflight)
      [ "$#" -eq 1 ] || { usage >&2; return 2; }
      preflight
      ;;
    start)
      [ "$#" -eq 1 ] || { usage >&2; return 2; }
      start_all
      ;;
    status)
      [ "$#" -eq 1 ] || { usage >&2; return 2; }
      status_all
      ;;
    health)
      [ "$#" -eq 1 ] || { usage >&2; return 2; }
      health_all
      ;;
    logs)
      require_service "${2:-}" || return $?
      [ "$#" -eq 2 ] || { usage >&2; return 2; }
      follow_logs "$2"
      ;;
    restart)
      require_service "${2:-}" || return $?
      [ "$#" -eq 2 ] || { usage >&2; return 2; }
      restart_service "$2"
      ;;
    stop)
      if [ "$#" -eq 1 ]; then
        stop_all
      else
        require_service "${2:-}" || return $?
        [ "$#" -eq 2 ] || { usage >&2; return 2; }
        stop_service "$2"
      fi
      ;;
    -h|--help|help)
      usage
      ;;
    *)
      usage >&2
      return 2
      ;;
  esac
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
