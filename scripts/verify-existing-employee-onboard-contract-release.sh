#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 "$ROOT_DIR/scripts/test_existing_employee_onboard_contract_release.py"
bash -n "$ROOT_DIR/scripts/verify-existing-employee-onboard-contract-release.sh"

echo 'EXISTING_EMPLOYEE_ONBOARD_CONTRACT_RELEASE_SOURCE_OK migrations=1 docker=excluded'
