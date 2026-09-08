#!/usr/bin/env python3
"""Activate the six Beijing signing accounts through the production API.

The one-time passwords are encrypted on ECS to an ephemeral local RSA key.
Cloud Assistant output therefore contains ciphertext only.  The decrypted CSV
is written locally with mode 0600 and the remote plaintext is shredded.
"""

from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import hmac
import io
import json
import os
import re
import time
from pathlib import Path

from cryptography.hazmat.primitives import hashes, padding, serialization
from cryptography.hazmat.primitives.asymmetric import padding as asym_padding
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

from compare_local_remote_depts import decode_output, read_credentials, run_command


EXPECTED_USERS = {
    1095: ("E00149", "E00149", "李曼"),
    1096: ("E00150", "E00150", "马毓谦"),
    1097: ("E00151", "E00151", "任艳琪"),
    1098: ("E00152", "E00152", "何顺琪"),
    1099: ("E00153", "E00153", "刘捷"),
    1100: ("E00154", "E00154", "苏余玉"),
}


REMOTE_TEMPLATE = r'''#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

DB="bosserp_stock_state_75c59ee"
ENV_FILE="/opt/erp-new/.env"
PASS_FILE="/root/.erp-mysql-root-pass"
STAMP="__STAMP__"
WORK_DIR="/opt/erp-new-data-imports/account-activation-${STAMP}"
BACKUP_DIR="/opt/erp-new-data-backups/account-activation-${STAMP}"
PUBLIC_KEY_B64='__PUBLIC_KEY_B64__'
EMITTED=0

case "$WORK_DIR" in
  /opt/erp-new-data-imports/account-activation-*) ;;
  *) echo "WORK_PATH_REFUSED" >&2; exit 2 ;;
esac
case "$BACKUP_DIR" in
  /opt/erp-new-data-backups/account-activation-*) ;;
  *) echo "BACKUP_PATH_REFUSED" >&2; exit 2 ;;
esac
[[ ! -e "$WORK_DIR" && ! -e "$BACKUP_DIR" ]]
mkdir -m 700 "$WORK_DIR" "$BACKUP_DIR"

cleanup_sensitive() {
  case "$WORK_DIR" in
    /opt/erp-new-data-imports/account-activation-*) ;;
    *) return ;;
  esac
  for file in mysql.cnf public.pem jwt-candidates.tsv probe.json response.json credentials.jsonl \
      key-material.bin wrapped.key iv.bin credentials.enc; do
    target="$WORK_DIR/$file"
    if [[ -f "$target" ]]; then
      if command -v shred >/dev/null 2>&1; then
        shred -u "$target" 2>/dev/null || rm -f "$target"
      else
        rm -f "$target"
      fi
    fi
  done
  rmdir "$WORK_DIR" 2>/dev/null || true
}

encrypt_and_emit() {
  [[ -s "$WORK_DIR/credentials.jsonl" ]]
  printf '%s' "$PUBLIC_KEY_B64" | base64 -d > "$WORK_DIR/public.pem"
  openssl pkey -pubin -in "$WORK_DIR/public.pem" -noout >/dev/null 2>&1
  openssl rand 64 > "$WORK_DIR/key-material.bin"
  openssl rand 16 > "$WORK_DIR/iv.bin"
  enc_key_hex="$(dd if="$WORK_DIR/key-material.bin" bs=1 count=32 2>/dev/null | od -An -tx1 | tr -d ' \n')"
  mac_key_hex="$(dd if="$WORK_DIR/key-material.bin" bs=1 skip=32 count=32 2>/dev/null | od -An -tx1 | tr -d ' \n')"
  iv_hex="$(od -An -tx1 "$WORK_DIR/iv.bin" | tr -d ' \n')"
  openssl enc -aes-256-cbc -K "$enc_key_hex" -iv "$iv_hex" \
    -in "$WORK_DIR/credentials.jsonl" -out "$WORK_DIR/credentials.enc"
  hmac_hex="$(openssl dgst -sha256 -mac HMAC -macopt "hexkey:$mac_key_hex" \
    "$WORK_DIR/credentials.enc" | awk '{print $NF}')"
  openssl pkeyutl -encrypt -pubin -inkey "$WORK_DIR/public.pem" \
    -pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 \
    -pkeyopt rsa_mgf1_md:sha256 -in "$WORK_DIR/key-material.bin" \
    -out "$WORK_DIR/wrapped.key" 2>/dev/null
  printf 'CREDENTIAL_WRAPPED_KEY_B64='; base64 -w 0 "$WORK_DIR/wrapped.key"; printf '\n'
  printf 'CREDENTIAL_IV_HEX=%s\n' "$iv_hex"
  printf 'CREDENTIAL_HMAC_HEX=%s\n' "$hmac_hex"
  printf 'CREDENTIAL_CIPHERTEXT_B64='; base64 -w 0 "$WORK_DIR/credentials.enc"; printf '\n'
  EMITTED=1
}

on_exit() {
  rc=$?
  trap - EXIT
  if [[ "$EMITTED" == "0" && -s "$WORK_DIR/credentials.jsonl" ]]; then
    echo "ACCOUNT_ACTIVATION_PARTIAL credentials_encrypted=1" >&2
    encrypt_and_emit || echo "PARTIAL_CREDENTIAL_ENCRYPTION_FAILED" >&2
  fi
  cleanup_sensitive
  exit "$rc"
}
trap on_exit EXIT

for command in python3 mysql mysqldump gzip sha256sum redis-cli curl openssl base64; do
  command -v "$command" >/dev/null 2>&1 || { echo "MISSING_COMMAND_${command}" >&2; exit 3; }
done
[[ -r "$ENV_FILE" && -r "$PASS_FILE" ]]

while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  [[ -z "$line" || "$line" == \#* ]] && continue
  key="${line%%=*}"
  value="${line#*=}"
  if [[ "$line" != *=* || ! "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "INVALID_ENV_ASSIGNMENT" >&2; exit 4
  fi
  if [[ ${#value} -ge 2 ]]; then
    first="${value:0:1}"; last="${value: -1}"
    if [[ ( "$first" == '"' && "$last" == '"' ) || ( "$first" == "'" && "$last" == "'" ) ]]; then
      value="${value:1:${#value}-2}"
    fi
  fi
  export "$key=$value"
done < "$ENV_FILE"
: "${ERP_JWT_SECRET:?ERP_JWT_SECRET is required}"

if [[ "$(redis-cli --raw PING 2>/dev/null || true)" == "PONG" ]]; then
  redis=(redis-cli --raw)
else
  : "${REDIS_PASSWORD:?REDIS_PASSWORD is required}"
  redis=(redis-cli --no-auth-warning -a "$REDIS_PASSWORD" --raw)
fi
[[ "$("${redis[@]}" PING)" == "PONG" ]]

python3 - "$PASS_FILE" "$WORK_DIR/mysql.cnf" <<'PY'
import os, sys
from pathlib import Path
password = Path(sys.argv[1]).read_text(encoding="utf-8").rstrip("\r\n")
if not password or "\n" in password or "\r" in password:
    raise SystemExit("INVALID_MYSQL_PASSWORD_FILE")
escaped = password.replace("\\", "\\\\").replace('"', '\\"')
Path(sys.argv[2]).write_text('[client]\nuser=root\npassword="' + escaped + '"\n', encoding="utf-8")
os.chmod(sys.argv[2], 0o600)
PY

mysql_cmd=(mysql --defaults-extra-file="$WORK_DIR/mysql.cnf" --protocol=socket --batch --raw --skip-column-names "$DB")
dump_cmd=(mysqldump --defaults-extra-file="$WORK_DIR/mysql.cnf" --protocol=socket \
  --single-transaction --quick --hex-blob --set-gtid-purged=OFF --column-statistics=0)

actual_db="$("${mysql_cmd[@]}" -e 'SELECT DATABASE()')"
[[ "$actual_db" == "$DB" ]] || { echo "DATABASE_GUARD_FAILED" >&2; exit 10; }

prestate="$("${mysql_cmd[@]}" <<'SQL'
SELECT CONCAT_WS('/', COUNT(*), SUM(create_by='beijing0716_placeholder'),
  SUM(status='1' AND del_flag='0'), SUM(credential_state='CHANGE_REQUIRED'),
  SUM(temporary_password_expires_at IS NULL))
FROM sys_user WHERE user_id IN (1095,1096,1097,1098,1099,1100);
SQL
)"
[[ "$prestate" == "6/6/6/6/6" ]] || {
  echo "ACCOUNT_PRESTATE_REFUSED expected=6/6/6/6/6 actual=$prestate" >&2; exit 11;
}

"${dump_cmd[@]}" "$DB" sys_user \
  --where='user_id IN (1095,1096,1097,1098,1099,1100)' \
  | gzip -9 > "$BACKUP_DIR/sys_user-before.sql.gz"
chmod 600 "$BACKUP_DIR/sys_user-before.sql.gz"
gzip -t "$BACKUP_DIR/sys_user-before.sql.gz"
sha256sum "$BACKUP_DIR/sys_user-before.sql.gz" > "$BACKUP_DIR/sys_user-before.sql.gz.sha256"
chmod 600 "$BACKUP_DIR/sys_user-before.sql.gz.sha256"

admin_token_id=""
while IFS= read -r candidate; do
  candidate="${candidate#\"}"; candidate="${candidate%\"}"
  if [[ -n "$candidate" && "$("${redis[@]}" EXISTS "login_tokens:${candidate}")" == "1" ]]; then
    admin_token_id="$candidate"
    break
  fi
done < <("${redis[@]}" SMEMBERS user_login_tokens:1)
[[ -n "$admin_token_id" ]] || { echo "LIVE_ADMIN_SESSION_REQUIRED" >&2; exit 12; }

python3 - "$admin_token_id" "$WORK_DIR/jwt-candidates.tsv" <<'PY'
import base64, hashlib, hmac, json, os, sys
token_id, output = sys.argv[1:]
secret = os.environ["ERP_JWT_SECRET"]
def b64url(value):
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")
header = b64url(json.dumps({"alg":"HS512"}, separators=(",", ":")).encode())
payload = b64url(json.dumps({"user_key":token_id,"user_id":1,"username":"admin"},
                            separators=(",", ":")).encode())
signing_input = (header + "." + payload).encode("ascii")
keys = [("utf8", secret.encode("utf-8"))]
try:
    decoded = base64.b64decode(secret + "=" * (-len(secret) % 4), validate=True)
    if decoded and decoded != keys[0][1]: keys.append(("base64", decoded))
except Exception:
    pass
with open(output, "w", encoding="ascii") as handle:
    for label, key in keys:
        signature = b64url(hmac.new(key, signing_input, hashlib.sha512).digest())
        handle.write(label + "\t" + header + "." + payload + "." + signature + "\n")
os.chmod(output, 0o600)
PY

admin_jwt=""
while IFS=$'\t' read -r jwt_kind jwt_value; do
  http_code="$(curl --silent --show-error --output "$WORK_DIR/probe.json" \
    --write-out '%{http_code}' --max-time 15 \
    -H "Authorization: Bearer $jwt_value" \
    'http://127.0.0.1:8080/system/user/list?pageNum=1&pageSize=1')"
  if [[ "$http_code" =~ ^2[0-9][0-9]$ ]] && python3 - "$WORK_DIR/probe.json" <<'PY'
import json, sys
try:
    payload = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception:
    raise SystemExit(1)
raise SystemExit(0 if payload.get("code") == 200 else 1)
PY
  then
    admin_jwt="$jwt_value"
    break
  fi
done < "$WORK_DIR/jwt-candidates.tsv"
[[ -n "$admin_jwt" ]] || { echo "ADMIN_JWT_VALIDATION_FAILED" >&2; exit 13; }

: > "$WORK_DIR/credentials.jsonl"
chmod 600 "$WORK_DIR/credentials.jsonl"

for pair in 1095:E00149 1096:E00150 1097:E00151 1098:E00152 1099:E00153 1100:E00154; do
  user_id="${pair%%:*}"
  user_name="${pair#*:}"
  http_code="$(curl --silent --show-error --output "$WORK_DIR/response.json" \
    --write-out '%{http_code}' --max-time 30 --request PUT \
    -H "Authorization: Bearer $admin_jwt" -H 'Content-Type: application/json' \
    --data "{\"userId\":${user_id}}" \
    'http://127.0.0.1:8080/system/user/resetPwd')"
  [[ "$http_code" =~ ^2[0-9][0-9]$ ]] || { echo "RESET_HTTP_FAILED user=$user_id" >&2; exit 20; }
  python3 - "$WORK_DIR/response.json" "$WORK_DIR/credentials.jsonl" "$user_id" "$user_name" <<'PY'
import json, os, sys
response_path, output_path, expected_id, expected_name = sys.argv[1:]
try:
    payload = json.load(open(response_path, encoding="utf-8"))
    credential = payload["data"]["temporaryCredential"]
    valid = (payload.get("code") == 200
             and int(credential["userId"]) == int(expected_id)
             and credential["userName"] == expected_name
             and isinstance(credential["temporaryPassword"], str)
             and len(credential["temporaryPassword"]) >= 12
             and credential.get("expiresAt"))
except Exception:
    valid = False
if not valid:
    raise SystemExit("RESET_RESPONSE_INVALID user=" + expected_id)
record = {key: credential[key] for key in
          ("userId", "userName", "temporaryPassword", "expiresAt")}
with open(output_path, "a", encoding="utf-8") as handle:
    handle.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n")
os.chmod(output_path, 0o600)
PY

  http_code="$(curl --silent --show-error --output "$WORK_DIR/response.json" \
    --write-out '%{http_code}' --max-time 30 --request PUT \
    -H "Authorization: Bearer $admin_jwt" -H 'Content-Type: application/json' \
    --data "{\"userId\":${user_id},\"status\":\"0\"}" \
    'http://127.0.0.1:8080/system/user/changeStatus')"
  [[ "$http_code" =~ ^2[0-9][0-9]$ ]] || { echo "ENABLE_HTTP_FAILED user=$user_id" >&2; exit 21; }
  python3 - "$WORK_DIR/response.json" "$user_id" <<'PY'
import json, sys
try:
    payload = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception:
    raise SystemExit("ENABLE_RESPONSE_INVALID user=" + sys.argv[2])
if payload.get("code") != 200:
    raise SystemExit("ENABLE_RESPONSE_REJECTED user=" + sys.argv[2])
PY
done

poststate="$("${mysql_cmd[@]}" <<'SQL'
SELECT CONCAT_WS('/', COUNT(*), SUM(status='0' AND del_flag='0'),
  SUM(credential_state='TEMPORARY'), SUM(temporary_password_expires_at > NOW()),
  SUM(update_by='admin'))
FROM sys_user WHERE user_id IN (1095,1096,1097,1098,1099,1100);
SQL
)"
[[ "$poststate" == "6/6/6/6/6" ]] || {
  echo "ACCOUNT_POSTSTATE_MISMATCH expected=6/6/6/6/6 actual=$poststate" >&2; exit 30;
}

cat > "$BACKUP_DIR/manifest.txt" <<EOF
database=$DB
stamp=$STAMP
targets=1095,1096,1097,1098,1099,1100
prestate=$prestate
poststate=$poststate
mutation_path=system-user-api
EOF
chmod 600 "$BACKUP_DIR/manifest.txt"

encrypt_and_emit
echo "ACCOUNT_ACTIVATION_OK active=6 temporary_credentials=6"
echo "BACKUP_OK directory=$BACKUP_DIR"
'''


def extract(name: str, output: str) -> str:
    match = re.search(rf"^{re.escape(name)}=([^\r\n]+)$", output, re.MULTILINE)
    if not match:
        raise RuntimeError(f"missing encrypted result field: {name}")
    return match.group(1)


def decrypt_records(private_key: rsa.RSAPrivateKey, output: str) -> list[dict]:
    wrapped = base64.b64decode(extract("CREDENTIAL_WRAPPED_KEY_B64", output), validate=True)
    iv = bytes.fromhex(extract("CREDENTIAL_IV_HEX", output))
    expected_mac = bytes.fromhex(extract("CREDENTIAL_HMAC_HEX", output))
    ciphertext = base64.b64decode(extract("CREDENTIAL_CIPHERTEXT_B64", output), validate=True)
    key_material = private_key.decrypt(
        wrapped,
        asym_padding.OAEP(
            mgf=asym_padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None,
        ),
    )
    if len(key_material) != 64:
        raise RuntimeError("invalid decrypted key material")
    actual_mac = hmac.new(key_material[32:], ciphertext, hashlib.sha256).digest()
    if not hmac.compare_digest(actual_mac, expected_mac):
        raise RuntimeError("credential ciphertext authentication failed")
    decryptor = Cipher(algorithms.AES(key_material[:32]), modes.CBC(iv)).decryptor()
    padded = decryptor.update(ciphertext) + decryptor.finalize()
    unpadder = padding.PKCS7(128).unpadder()
    plaintext = unpadder.update(padded) + unpadder.finalize()
    records = [json.loads(line) for line in plaintext.decode("utf-8").splitlines() if line]
    for record in records:
        user_id = int(record.get("userId", 0))
        if user_id not in EXPECTED_USERS:
            raise RuntimeError(f"unexpected credential user id: {user_id}")
        if record.get("userName") != EXPECTED_USERS[user_id][1]:
            raise RuntimeError(f"unexpected credential username for user {user_id}")
        if len(str(record.get("temporaryPassword", ""))) < 12 or not record.get("expiresAt"):
            raise RuntimeError(f"invalid credential payload for user {user_id}")
    return sorted(records, key=lambda item: int(item["userId"]))


def write_secure_csv(records: list[dict], output_path: Path) -> None:
    if output_path.exists():
        raise FileExistsError(f"refusing to overwrite existing credential file: {output_path}")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = output_path.with_name(f".{output_path.name}.{os.getpid()}.tmp")
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with io.TextIOWrapper(os.fdopen(fd, "wb", closefd=True), encoding="utf-8-sig", newline="") as handle:
            writer = csv.writer(handle)
            writer.writerow(("姓名", "员工编号", "登录账号", "临时密码", "失效时间", "首次登录操作"))
            for record in records:
                employee_no, expected_username, name = EXPECTED_USERS[int(record["userId"])]
                writer.writerow((
                    name,
                    employee_no,
                    expected_username,
                    record["temporaryPassword"],
                    record["expiresAt"],
                    "登录后立即修改密码，再补全个人资料",
                ))
            handle.flush()
            os.fsync(handle.buffer.fileno())
        os.replace(temporary, output_path)
        os.chmod(output_path, 0o600)
    except Exception:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass
        raise


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--output")
    args = parser.parse_args()

    private_key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    public_pem = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    stamp = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
    command = REMOTE_TEMPLATE.replace("__STAMP__", stamp).replace(
        "__PUBLIC_KEY_B64__", base64.b64encode(public_pem).decode("ascii")
    )
    if len(command.encode("utf-8")) > 16000:
        raise RuntimeError(f"remote command is too large: {len(command.encode('utf-8'))} bytes")

    result = run_command(
        read_credentials(), args.region, args.instance_id, command,
        f"activate-beijing-accounts-{stamp}", args.timeout,
    )
    output = decode_output(result)
    records: list[dict] = []
    if "CREDENTIAL_CIPHERTEXT_B64=" in output:
        records = decrypt_records(private_key, output)
        if args.output:
            output_path = Path(args.output).expanduser().resolve()
        else:
            output_path = Path.home() / "Desktop" / f"北京区域签约账号-{stamp[:8]}-{stamp[9:15]}.csv"
        write_secure_csv(records, output_path)
        print(f"credential_file={output_path}")
        print(f"credential_records={len(records)} mode={oct(output_path.stat().st_mode & 0o777)}")

    safe_lines = [
        line for line in output.splitlines()
        if not line.startswith((
            "CREDENTIAL_WRAPPED_KEY_B64=", "CREDENTIAL_IV_HEX=",
            "CREDENTIAL_HMAC_HEX=", "CREDENTIAL_CIPHERTEXT_B64=",
        ))
    ]
    if safe_lines:
        print("\n".join(safe_lines[-40:]))
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(1)
    if len(records) != len(EXPECTED_USERS) or "ACCOUNT_ACTIVATION_OK" not in output:
        raise RuntimeError("account activation did not return all six credentials")


if __name__ == "__main__":
    main()
