#!/usr/bin/env python3
"""Regression tests for the read-only Nacos production gate."""

from __future__ import annotations

import importlib.util
import json
import subprocess
import tempfile
import threading
import unittest
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


DOCKER_DIR = Path(__file__).resolve().parents[1]
SCRIPT = DOCKER_DIR / "nacos-production-preflight.py"
SPEC = importlib.util.spec_from_file_location("nacos_production_preflight", SCRIPT)
assert SPEC and SPEC.loader
PREFLIGHT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREFLIGHT)

USERNAME = "erp_prod_client"
PASSWORD = "nacos-password-must-never-be-printed"
TOKEN = "nacos-access-token-must-never-be-printed"


class FakeNacosHandler(BaseHTTPRequestHandler):
    server_version = "NacosContractTest"

    def log_message(self, _format, *_args):
        return

    def json_response(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path != "/nacos/v3/auth/user/login":
            self.json_response(404, {"code": 404, "message": "not found"})
            return
        length = int(self.headers.get("Content-Length", "0"))
        form = parse_qs(self.rfile.read(length).decode("utf-8"))
        if (
            form.get("username") != [USERNAME]
            or form.get("password") != [PASSWORD]
            or self.server.reject_auth
        ):
            self.json_response(401, {"code": 401, "message": "unauthorized"})
            return
        self.json_response(
            200,
            {
                "accessToken": TOKEN,
                "tokenTtl": 18000,
                "globalAdmin": self.server.global_admin,
                "username": USERNAME,
            },
        )

    def do_GET(self):
        parsed = urlsplit(self.path)
        if parsed.path == "/nacos/v3/admin/core/state/readiness":
            self.json_response(
                200,
                {
                    "code": 0,
                    "message": "success",
                    "data": "UP" if self.server.ready else "STARTING",
                },
            )
            return
        if parsed.path != "/nacos/v3/client/cs/config":
            self.json_response(404, {"code": 404, "message": "not found"})
            return
        if self.headers.get("Authorization") != f"Bearer {TOKEN}":
            self.json_response(403, {"code": 403, "message": "forbidden"})
            return
        data_id = parse_qs(parsed.query).get("dataId", [""])[0]
        self.server.requested_data_ids.append(data_id)
        if data_id == self.server.missing_data_id:
            self.json_response(404, {"code": 300, "message": "config not found"})
            return
        self.json_response(
            200,
            {
                "code": 0,
                "message": "success",
                "data": {
                    "resultCode": 200,
                    "errorCode": 0,
                    "content": "spring:\n  application:\n    production: true\n",
                    "md5": "0123456789abcdef0123456789abcdef",
                    "success": True,
                },
            },
        )


@contextmanager
def fake_nacos(
    *,
    ready: bool = True,
    reject_auth: bool = False,
    global_admin: bool = False,
    missing=None,
):
    server = ThreadingHTTPServer(("127.0.0.1", 0), FakeNacosHandler)
    server.ready = ready
    server.reject_auth = reject_auth
    server.global_admin = global_admin
    server.missing_data_id = missing
    server.requested_data_ids = []
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield server
    finally:
        server.shutdown()
        thread.join(timeout=5)
        server.server_close()


def write_env(path: Path, port: int, password: str = PASSWORD) -> None:
    path.write_text(
        f"ECS_NACOS_SERVER_ADDR=127.0.0.1:{port}\n"
        f"NACOS_CLIENT_USERNAME={USERNAME}\n"
        f"NACOS_CLIENT_PASSWORD={password}\n",
        encoding="utf-8",
    )
    path.chmod(0o600)


def run_preflight(env_file: Path) -> subprocess.CompletedProcess:
    return subprocess.run(
        [
            "python3",
            str(SCRIPT),
            "--env-file",
            str(env_file),
            "--timeout",
            "2",
        ],
        check=False,
        capture_output=True,
        text=True,
    )


class NacosProductionPreflightTest(unittest.TestCase):
    def test_readiness_auth_and_all_prod_data_ids_pass_without_secret_output(self):
        with tempfile.TemporaryDirectory() as temporary, fake_nacos() as server:
            env_file = Path(temporary) / ".env"
            write_env(env_file, server.server_port)
            result = run_preflight(env_file)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("data_ids=10", result.stdout)
            self.assertEqual(
                set(PREFLIGHT.REQUIRED_PROD_DATA_IDS),
                set(server.requested_data_ids),
            )
            combined = result.stdout + result.stderr
            self.assertNotIn(PASSWORD, combined)
            self.assertNotIn(TOKEN, combined)

    def test_authentication_failure_is_sanitized_and_blocks(self):
        with tempfile.TemporaryDirectory() as temporary, fake_nacos(
            reject_auth=True
        ) as server:
            env_file = Path(temporary) / ".env"
            write_env(env_file, server.server_port)
            result = run_preflight(env_file)
            self.assertEqual(result.returncode, 70)
            self.assertIn("Nacos client authentication", result.stderr)
            self.assertNotIn(PASSWORD, result.stderr)
            self.assertNotIn(TOKEN, result.stderr)

    def test_missing_prod_data_id_names_only_the_missing_contract_item(self):
        missing = "erp-inventory-prod.yml"
        with tempfile.TemporaryDirectory() as temporary, fake_nacos(
            missing=missing
        ) as server:
            env_file = Path(temporary) / ".env"
            write_env(env_file, server.server_port)
            result = run_preflight(env_file)
            self.assertEqual(result.returncode, 70)
            self.assertIn(missing, result.stderr)
            self.assertNotIn(PASSWORD, result.stderr)
            self.assertNotIn(TOKEN, result.stderr)

    def test_global_admin_account_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary, fake_nacos(
            global_admin=True
        ) as server:
            env_file = Path(temporary) / ".env"
            write_env(env_file, server.server_port)
            result = run_preflight(env_file)
            self.assertEqual(result.returncode, 70)
            self.assertIn("must not be a global administrator", result.stderr)
            self.assertNotIn(PASSWORD, result.stderr)
            self.assertNotIn(TOKEN, result.stderr)

    def test_not_ready_cluster_blocks_before_authentication(self):
        with tempfile.TemporaryDirectory() as temporary, fake_nacos(
            ready=False
        ) as server:
            env_file = Path(temporary) / ".env"
            write_env(env_file, server.server_port)
            result = run_preflight(env_file)
            self.assertEqual(result.returncode, 70)
            self.assertIn("reported not ready", result.stderr)
            self.assertEqual([], server.requested_data_ids)


if __name__ == "__main__":
    unittest.main()
