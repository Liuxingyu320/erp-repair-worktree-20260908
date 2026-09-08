#!/usr/bin/env python3
"""Static safety tests for the local desktop release E2E harness."""

from __future__ import annotations

import os
import unittest
from unittest.mock import patch

from desktop_release_e2e_common import (
    ALL_EXPLICIT_IDS,
    CREDENTIAL_PATH,
    EVIDENCE_PATH,
    ID_MAX,
    ID_MIN,
    MANIFEST_PATH,
    MARKER_COLUMNS,
    QaSafetyError,
    RUN_ID,
    WRITE_APPROVAL_ENV,
    require_write_approval,
    validate_loopback_base_url,
)
from desktop_release_e2e_prepare import random_password


class DesktopReleaseE2eSafetyTest(unittest.TestCase):
    def test_every_explicit_id_is_inside_reserved_fence(self) -> None:
        self.assertTrue(ALL_EXPLICIT_IDS)
        self.assertTrue(
            all(ID_MIN <= value <= ID_MAX for value in ALL_EXPLICIT_IDS)
        )

    def test_artifacts_are_exact_tmp_paths(self) -> None:
        self.assertEqual(
            {
                str(CREDENTIAL_PATH),
                str(MANIFEST_PATH),
                str(EVIDENCE_PATH),
            },
            {
                "/tmp/codex_qa_20260728_credentials.json",
                "/tmp/codex_qa_20260728_manifest.json",
                "/tmp/codex_qa_20260728_evidence.json",
            },
        )

    def test_write_gate_requires_exact_run_id(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(QaSafetyError):
                require_write_approval()
        with patch.dict(
            os.environ, {WRITE_APPROVAL_ENV: RUN_ID}, clear=True
        ):
            require_write_approval()

    def test_only_loopback_prod_api_is_accepted(self) -> None:
        self.assertEqual(
            validate_loopback_base_url(
                "http://127.0.0.1:1028/prod-api"
            ),
            "http://127.0.0.1:1028/prod-api",
        )
        for unsafe in (
            "https://127.0.0.1/prod-api",
            "http://example.com/prod-api",
            "http://user:pass@127.0.0.1/prod-api",
            "http://127.0.0.1/api",
        ):
            with self.assertRaises(QaSafetyError):
                validate_loopback_base_url(unsafe)

    def test_generated_password_matches_application_contract(self) -> None:
        value = random_password()
        self.assertEqual(len(value), 20)
        self.assertTrue(any(char.islower() for char in value))
        self.assertTrue(any(char.isupper() for char in value))
        self.assertTrue(any(char.isdigit() for char in value))
        self.assertTrue(any(char in "!@#$%^&*_-+=" for char in value))

    def test_marker_scan_never_reads_secret_columns(self) -> None:
        forbidden = ("password", "token", "cookie", "id_number", "id_card")
        for columns in MARKER_COLUMNS.values():
            for column in columns:
                self.assertFalse(
                    any(fragment in column.lower() for fragment in forbidden)
                )


if __name__ == "__main__":
    unittest.main()
