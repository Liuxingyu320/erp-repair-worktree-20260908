#!/usr/bin/env bash
set -euo pipefail

pid=''
while IFS= read -r candidate; do
  command="$(tr '\0' ' ' < "/proc/$candidate/cmdline" 2>/dev/null || true)"
  if printf '%s' "$command" | grep -Eq 'erp-modules-oa|erp-oa|/oa/.*\\.jar'; then
    pid="$candidate"
    break
  fi
done < <(pgrep -x java || true)
[[ -n "$pid" ]] || {
  echo "OA_PROCESS_NOT_FOUND"
  exit 2
}

cmdline="$(tr '\0' ' ' < "/proc/$pid/cmdline")"
workdir="$(readlink -f "/proc/$pid/cwd")"
unit="$(systemctl status "$pid" 2>/dev/null | sed -n '1s/.*\\(erp-[^ ]*\\.service\\).*/\\1/p' || true)"
jar="$(printf '%s\n' "$cmdline" | awk '{
  for (i = 1; i <= NF; i++) {
    if ($i ~ /^\// && $i ~ /\.jar$/) {
      print $i;
      exit
    }
  }
}')"

echo "OA_RUNTIME pid=$pid workdir=$workdir unit=${unit:-unknown}"
echo "OA_COMMAND $(printf '%s\n' "$cmdline" | sed -E 's/(-D[^= ]*(password|secret|token)[^= ]*=)[^ ]+/\1<redacted>/Ig')"
if [[ -n "$jar" && -f "$jar" ]]; then
  echo "OA_JAR path=$jar sha256=$(sha256sum "$jar" | awk '{print $1}') bytes=$(wc -c < "$jar")"
else
  echo "OA_JAR_NOT_RESOLVED"
fi
