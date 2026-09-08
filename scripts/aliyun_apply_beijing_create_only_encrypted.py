#!/usr/bin/env python3
"""Apply the Beijing create-only SQL through encrypted Cloud Assistant chunks.

The ECS host creates an ephemeral RSA private key and returns only its public
key.  The local SQL is gzip-compressed, AES-CBC encrypted with a fresh key,
authenticated with HMAC-SHA256, and transferred in small ciphertext chunks.
Only the ECS host can unwrap the AES/HMAC keys.  The private key, ciphertext,
decrypted SQL, and MySQL option file are destroyed by the remote exit trap.
"""

from __future__ import annotations

import argparse
import base64
import gzip
import hashlib
import hmac
import json
import os
import re
import time
import uuid
from pathlib import Path

from cryptography.hazmat.primitives import hashes, padding, serialization
from cryptography.hazmat.primitives.asymmetric import padding as asymmetric_padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

from compare_local_remote_depts import decode_output, read_credentials, run_command


EXPECTED_DATABASE = "bosserp_stock_state_75c59ee"
EXPECTED_BASELINE = "158/148/153/153/500/148/148"
EXPECTED_POST = "164/154/159/159/508/154/154"
BACKUP_TABLES = (
    "sys_user",
    "sys_user_profile",
    "sys_user_post",
    "sys_user_role",
    "sys_user_shop",
    "hr_employee_no_sequence",
    "hr_employee_position_no_history",
)
CHUNK_CHARACTERS = 9000


def remote_call(
    client: dict[str, str], args: argparse.Namespace, command: str, name: str, timeout: int
) -> tuple[dict, str]:
    result = run_command(client, args.region, args.instance_id, command, name, timeout)
    output = decode_output(result)
    if result.get("ExitCode") not in (0, "0"):
        raise RuntimeError(
            f"remote command failed: {name}; status={result.get('InvocationStatus')}; "
            f"exit={result.get('ExitCode')}; output={output[-4000:]}"
        )
    return result, output


def cleanup_command(transfer_dir: str) -> str:
    return f'''#!/usr/bin/env bash
set -euo pipefail
DIR={json.dumps(transfer_dir)}
case "$DIR" in
  /opt/erp-new-data-imports/beijing-create-only-transfer-*) ;;
  *) echo "CLEANUP_PATH_REFUSED" >&2; exit 2;;
esac
if [[ -d "$DIR" ]]; then
  for file in private.pem public.pem payload.b64 payload.enc wrapped.key key-material.bin payload.sql.gz apply.sql mysql-client.cnf; do
    target="$DIR/$file"
    if [[ -f "$target" ]]; then
      if command -v shred >/dev/null 2>&1; then shred -u "$target" || rm -f "$target"; else rm -f "$target"; fi
    fi
  done
  rmdir "$DIR" 2>/dev/null || true
fi
echo "ENCRYPTED_TRANSFER_CLEANUP_OK"
'''


def apply(args: argparse.Namespace) -> None:
    if args.database != EXPECTED_DATABASE:
        raise SystemExit(f"refusing unexpected database: {args.database}")
    if args.expected_baseline != EXPECTED_BASELINE:
        raise SystemExit(f"refusing unexpected baseline: {args.expected_baseline}")
    if args.expected_post != EXPECTED_POST:
        raise SystemExit(f"refusing unexpected post-state: {args.expected_post}")

    sql_path = Path(args.sql).expanduser().resolve()
    if not sql_path.is_file():
        raise SystemExit(f"SQL file not found: {sql_path}")
    if sql_path.stat().st_mode & 0o077:
        raise SystemExit(f"sensitive SQL permissions must be 0600: {oct(sql_path.stat().st_mode & 0o777)}")

    sql_bytes = sql_path.read_bytes()
    sql_sha = hashlib.sha256(sql_bytes).hexdigest()
    compressed = gzip.compress(sql_bytes, compresslevel=9, mtime=0)
    transfer_id = uuid.uuid4().hex[:16]
    transfer_dir = f"/opt/erp-new-data-imports/beijing-create-only-transfer-{transfer_id}"
    stamp = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime()) + f"-{transfer_id[:8]}"
    backup_dir = f"/opt/erp-new-data-backups/beijing-create-only-{stamp}"
    client = read_credentials()
    initialized = False
    completed = False

    try:
        initialize = f'''#!/usr/bin/env bash
set -euo pipefail
umask 077
DIR={json.dumps(transfer_dir)}
if [[ -e "$DIR" ]]; then echo "TRANSFER_DIR_EXISTS" >&2; exit 2; fi
for command in openssl base64 gzip sha256sum mysql mysqldump python3; do
  command -v "$command" >/dev/null 2>&1 || {{ echo "MISSING_COMMAND_$command" >&2; exit 3; }}
done
mkdir -m 700 "$DIR"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$DIR/private.pem" 2>/dev/null
chmod 600 "$DIR/private.pem"
openssl pkey -in "$DIR/private.pem" -pubout -out "$DIR/public.pem" 2>/dev/null
chmod 600 "$DIR/public.pem"
: > "$DIR/payload.b64"
chmod 600 "$DIR/payload.b64"
echo "TRANSFER_READY id={transfer_id}"
printf 'PUBLIC_KEY_B64='
base64 -w 0 "$DIR/public.pem"
printf '\n'
'''
        _, output = remote_call(
            client, args, initialize, f"beijing-create-key-{transfer_id}", 180
        )
        initialized = True
        match = re.search(r"^PUBLIC_KEY_B64=([A-Za-z0-9+/=]+)$", output, re.MULTILINE)
        if not match:
            raise RuntimeError("remote public key was not returned")
        public_pem = base64.b64decode(match.group(1), validate=True)
        public_key = serialization.load_pem_public_key(public_pem)

        key_material = os.urandom(64)
        encryption_key = key_material[:32]
        mac_key = key_material[32:]
        iv = os.urandom(16)
        padder = padding.PKCS7(128).padder()
        padded = padder.update(compressed) + padder.finalize()
        encryptor = Cipher(algorithms.AES(encryption_key), modes.CBC(iv)).encryptor()
        ciphertext = encryptor.update(padded) + encryptor.finalize()
        payload_hmac = hmac.new(mac_key, ciphertext, hashlib.sha256).hexdigest()
        wrapped_key = public_key.encrypt(
            key_material,
            asymmetric_padding.OAEP(
                mgf=asymmetric_padding.MGF1(algorithm=hashes.SHA256()),
                algorithm=hashes.SHA256(),
                label=None,
            ),
        )
        del key_material, encryption_key, mac_key, padded, compressed, sql_bytes

        encoded = base64.b64encode(ciphertext).decode("ascii")
        chunks = [encoded[index:index + CHUNK_CHARACTERS] for index in range(0, len(encoded), CHUNK_CHARACTERS)]
        print(
            f"encrypted_transfer={transfer_id} chunks={len(chunks)} "
            f"ciphertext_bytes={len(ciphertext)} sql_sha256={sql_sha}"
        )
        for index, chunk in enumerate(chunks, start=1):
            append = f'''#!/usr/bin/env bash
set -euo pipefail
umask 077
DIR={json.dumps(transfer_dir)}
[[ -d "$DIR" && -f "$DIR/private.pem" && -f "$DIR/payload.b64" ]]
printf '%s' {json.dumps(chunk)} >> "$DIR/payload.b64"
echo "CHUNK_OK index={index} total={len(chunks)}"
'''
            remote_call(
                client,
                args,
                append,
                f"beijing-create-chunk-{transfer_id}-{index}",
                120,
            )

        wrapped_b64 = base64.b64encode(wrapped_key).decode("ascii")
        backup_tables = " ".join(BACKUP_TABLES)
        finalize = f'''#!/usr/bin/env bash
set -euo pipefail
umask 077
DIR={json.dumps(transfer_dir)}
BACKUP_DIR={json.dumps(backup_dir)}
DB={json.dumps(args.database)}
EXPECTED_BASELINE={json.dumps(args.expected_baseline)}
EXPECTED_POST={json.dumps(args.expected_post)}
EXPECTED_SQL_SHA={json.dumps(sql_sha)}
EXPECTED_HMAC={json.dumps(payload_hmac)}
IV_HEX={json.dumps(iv.hex())}
WRAPPED_KEY_B64={json.dumps(wrapped_b64)}
PASS_FILE=/root/.erp-mysql-root-pass
BACKUP_TABLES={json.dumps(backup_tables)}

cleanup_sensitive() {{
  case "$DIR" in
    /opt/erp-new-data-imports/beijing-create-only-transfer-*) ;;
    *) return;;
  esac
  for file in private.pem public.pem payload.b64 payload.enc wrapped.key key-material.bin payload.sql.gz apply.sql mysql-client.cnf; do
    target="$DIR/$file"
    if [[ -f "$target" ]]; then
      if command -v shred >/dev/null 2>&1; then shred -u "$target" || rm -f "$target"; else rm -f "$target"; fi
    fi
  done
  rmdir "$DIR" 2>/dev/null || true
}}
trap cleanup_sensitive EXIT

[[ -d "$DIR" && -f "$DIR/private.pem" && -f "$DIR/payload.b64" ]]
mkdir -m 700 "$BACKUP_DIR"
base64 -d "$DIR/payload.b64" > "$DIR/payload.enc"
printf '%s' "$WRAPPED_KEY_B64" | base64 -d > "$DIR/wrapped.key"
openssl pkeyutl -decrypt -inkey "$DIR/private.pem" -in "$DIR/wrapped.key" \
  -pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 -pkeyopt rsa_mgf1_md:sha256 \
  -out "$DIR/key-material.bin" 2>/dev/null
[[ "$(wc -c < "$DIR/key-material.bin" | tr -d ' ')" = "64" ]]
ENC_KEY_HEX="$(dd if="$DIR/key-material.bin" bs=1 count=32 2>/dev/null | od -An -tx1 | tr -d ' \n')"
MAC_KEY_HEX="$(dd if="$DIR/key-material.bin" bs=1 skip=32 count=32 2>/dev/null | od -An -tx1 | tr -d ' \n')"
ACTUAL_HMAC="$(openssl dgst -sha256 -mac HMAC -macopt "hexkey:$MAC_KEY_HEX" "$DIR/payload.enc" | awk '{{print $NF}}')"
if [[ "$ACTUAL_HMAC" != "$EXPECTED_HMAC" ]]; then echo "PAYLOAD_HMAC_MISMATCH" >&2; exit 30; fi
openssl enc -d -aes-256-cbc -K "$ENC_KEY_HEX" -iv "$IV_HEX" \
  -in "$DIR/payload.enc" -out "$DIR/payload.sql.gz"
gzip -t "$DIR/payload.sql.gz"
gzip -dc "$DIR/payload.sql.gz" > "$DIR/apply.sql"
chmod 600 "$DIR/apply.sql"
ACTUAL_SQL_SHA="$(sha256sum "$DIR/apply.sql" | awk '{{print $1}}')"
if [[ "$ACTUAL_SQL_SHA" != "$EXPECTED_SQL_SHA" ]]; then echo "SQL_SHA256_MISMATCH" >&2; exit 31; fi

python3 - "$PASS_FILE" "$DIR/mysql-client.cnf" <<'PY'
import os
import sys
from pathlib import Path

password = Path(sys.argv[1]).read_text(encoding="utf-8").rstrip("\\r\\n")
if not password or "\\n" in password or "\\r" in password:
    raise SystemExit("invalid mysql password file")
escaped = password.replace("\\\\", "\\\\\\\\").replace('"', '\\\\"')
Path(sys.argv[2]).write_text('[client]\\nuser=root\\npassword="' + escaped + '"\\n', encoding="utf-8")
os.chmod(sys.argv[2], 0o600)
PY

MYSQL=(mysql --defaults-extra-file="$DIR/mysql-client.cnf" --protocol=socket --batch --raw --skip-column-names "$DB")
MYSQLDUMP=(mysqldump --defaults-extra-file="$DIR/mysql-client.cnf" --protocol=socket \
  --single-transaction --quick --hex-blob --set-gtid-purged=OFF --column-statistics=0)
ACTUAL_DB="$("${{MYSQL[@]}}" <<'SQL'
SELECT DATABASE();
SQL
)"
if [[ "$ACTUAL_DB" != "$DB" ]]; then echo "DATABASE_GUARD_FAILED actual=$ACTUAL_DB" >&2; exit 32; fi
BASELINE="$("${{MYSQL[@]}}" <<'SQL'
SELECT CONCAT_WS('/',
 (SELECT COUNT(*) FROM sys_user),(SELECT COUNT(*) FROM sys_user_profile),
 (SELECT COUNT(*) FROM sys_user_post),(SELECT COUNT(*) FROM sys_user_role),
 (SELECT COUNT(*) FROM sys_user_shop),(SELECT COUNT(*) FROM hr_employee_position_no_history),
 (SELECT current_value FROM hr_employee_no_sequence WHERE sequence_key='GLOBAL'));
SQL
)"
if [[ "$BASELINE" != "$EXPECTED_BASELINE" ]]; then
  echo "PRODUCTION_BASELINE_DRIFT expected=$EXPECTED_BASELINE actual=$BASELINE" >&2; exit 33
fi

"${{MYSQLDUMP[@]}}" "$DB" $BACKUP_TABLES | gzip -9 > "$BACKUP_DIR/employee-tables-before.sql.gz"
chmod 600 "$BACKUP_DIR/employee-tables-before.sql.gz"
gzip -t "$BACKUP_DIR/employee-tables-before.sql.gz"
sha256sum "$BACKUP_DIR/employee-tables-before.sql.gz" > "$BACKUP_DIR/employee-tables-before.sql.gz.sha256"
chmod 600 "$BACKUP_DIR/employee-tables-before.sql.gz.sha256"

"${{MYSQL[@]}}" < "$DIR/apply.sql" > "$BACKUP_DIR/apply-output.log"
chmod 600 "$BACKUP_DIR/apply-output.log"
grep -q '^CREATE_ONLY_APPLY_OK' "$BACKUP_DIR/apply-output.log"
POST="$("${{MYSQL[@]}}" <<'SQL'
SELECT CONCAT_WS('/',
 (SELECT COUNT(*) FROM sys_user),(SELECT COUNT(*) FROM sys_user_profile),
 (SELECT COUNT(*) FROM sys_user_post),(SELECT COUNT(*) FROM sys_user_role),
 (SELECT COUNT(*) FROM sys_user_shop),(SELECT COUNT(*) FROM hr_employee_position_no_history),
 (SELECT current_value FROM hr_employee_no_sequence WHERE sequence_key='GLOBAL'));
SQL
)"
if [[ "$POST" != "$EXPECTED_POST" ]]; then
  echo "PRODUCTION_POST_STATE_MISMATCH expected=$EXPECTED_POST actual=$POST" >&2; exit 34
fi
AGGREGATE="$("${{MYSQL[@]}}" <<'SQL'
SELECT CONCAT_WS('/',
 (SELECT COUNT(*) FROM sys_user WHERE create_by='beijing0716_placeholder' AND status='1'
   AND del_flag='0' AND credential_state='CHANGE_REQUIRED' AND temporary_password_expires_at IS NULL),
 (SELECT COUNT(*) FROM sys_user_profile WHERE create_by='beijing0716_placeholder'),
 (SELECT COUNT(*) FROM sys_user_post up JOIN sys_user u ON u.user_id=up.user_id WHERE u.create_by='beijing0716_placeholder'),
 (SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u ON u.user_id=ur.user_id WHERE u.create_by='beijing0716_placeholder'),
 (SELECT COUNT(*) FROM sys_user_shop us JOIN sys_user u ON u.user_id=us.user_id WHERE u.create_by='beijing0716_placeholder'),
 (SELECT COUNT(*) FROM hr_employee_position_no_history h JOIN sys_user u ON u.user_id=h.user_id
   WHERE u.create_by='beijing0716_placeholder' AND h.change_source='PROFILE_INSERT'));
SQL
)"
if [[ "$AGGREGATE" != "6/6/6/6/8/6" ]]; then echo "CREATE_ONLY_AGGREGATE_MISMATCH actual=$AGGREGATE" >&2; exit 35; fi

cat > "$BACKUP_DIR/manifest.txt" <<EOF
database=$DB
stamp={stamp}
sql_sha256=$EXPECTED_SQL_SHA
baseline=$BASELINE
post=$POST
aggregate=$AGGREGATE
existing_accounts_transactional_hash=unchanged_21
EOF
chmod 600 "$BACKUP_DIR/manifest.txt"
echo "BEIJING_CREATE_ONLY_OK created_disabled=6 existing_unchanged=21 employee_numbers=E00149-E00154"
echo "BACKUP_OK directory=$BACKUP_DIR"
'''
        if len(finalize.encode("utf-8")) > 15500:
            raise RuntimeError(f"finalize command is too large: {len(finalize.encode('utf-8'))}")
        result, output = remote_call(
            client,
            args,
            finalize,
            f"beijing-create-finalize-{transfer_id}",
            args.timeout,
        )
        completed = True
        print(
            json.dumps(
                {
                    "invocationStatus": result.get("InvocationStatus"),
                    "exitCode": result.get("ExitCode"),
                    "output": output[-8000:],
                },
                ensure_ascii=False,
                indent=2,
            )
        )
    finally:
        if initialized and not completed:
            try:
                remote_call(
                    client,
                    args,
                    cleanup_command(transfer_dir),
                    f"beijing-create-cleanup-{transfer_id}",
                    120,
                )
            except Exception as exc:
                print(f"warning: encrypted transfer cleanup needs review: {type(exc).__name__}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", required=True)
    parser.add_argument("--instance-id", required=True)
    parser.add_argument("--sql", required=True)
    parser.add_argument("--database", default=EXPECTED_DATABASE)
    parser.add_argument("--expected-baseline", default=EXPECTED_BASELINE)
    parser.add_argument("--expected-post", default=EXPECTED_POST)
    parser.add_argument("--timeout", type=int, default=900)
    args = parser.parse_args()
    apply(args)


if __name__ == "__main__":
    main()
