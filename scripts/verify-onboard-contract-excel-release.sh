#!/usr/bin/env bash

# Fail-closed source gate for the 20260718 onboarding Excel release.
# Static mode proves local source contracts only. It never claims that template,
# native-MySQL, real-workbook, signed-PDF, hash or audit gates passed.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:---static}"

usage()
{
    cat <<'EOF'
Usage: bash scripts/verify-onboard-contract-excel-release.sh [--static|--readiness]

  --static     Validate the exact 20260718 delta, SQL copies/order, deleted old
               entry paths, default-off flags, focused backend/frontend tests,
               and disabled template publication contracts.
  --readiness  Run --static, then verify the exact clean candidate, Release-A
               binding, two detached signatures, seven external evidence files,
               immutable artifacts, semantic contents, and build provenance.
EOF
}

case "$MODE" in
    --static|--readiness) ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
esac
[[ "$#" -le 1 ]] || { usage >&2; exit 2; }

command -v python3 >/dev/null 2>&1 || {
    echo '[FAIL] python3 is required' >&2
    exit 3
}
command -v openssl >/dev/null 2>&1 || {
    echo '[FAIL] openssl is required for detached-signature verification' >&2
    exit 3
}
command -v docker >/dev/null 2>&1 || {
    echo '[FAIL] docker compose is required to validate effective feature flags' >&2
    exit 3
}
NODE_BIN="${NODE_BIN:-$(command -v node || true)}"
[[ -n "$NODE_BIN" ]] || {
    echo '[FAIL] node is required for artifact and focused frontend release contracts' >&2
    exit 4
}

python3 "$ROOT_DIR/scripts/test_onboard_contract_excel_release.py"
python3 "$ROOT_DIR/scripts/test_verify_onboard_contract_excel_artifacts.py"
python3 "$ROOT_DIR/scripts/test_verify_onboard_contract_excel_attestations.py"
python3 "$ROOT_DIR/scripts/test_verify_onboard_contract_excel_gate_evidence.py"
python3 "$ROOT_DIR/scripts/test_verify_onboard_contract_excel_readiness.py"
bash "$ROOT_DIR/scripts/verify-docker-mysql-bootstrap.sh"
bash -n "$ROOT_DIR/scripts/verify-onboard-contract-excel-release.sh"
bash -n "$ROOT_DIR/scripts/remote_audit_beijing_sign_publish_readonly_20260718.sh"
bash -n "$ROOT_DIR/scripts/remote_publish_beijing_sign_templates_plans_20260718.sh"
python3 "$ROOT_DIR/scripts/test_prepare_onboard_templates_20260718.py"
python3 "$ROOT_DIR/scripts/test_beijing_sign_publish_contract_20260718.py"

for test_file in \
    hrEmployeeMasterFields.test.js \
    hrExistingEmployeeOnboardContract.test.js \
    signScopeIsolation.test.js \
    signPackageModule.test.js \
    unifiedTodoMutationRefresh.test.js \
    onboardReleaseProvenance.test.js
do
    "$NODE_BIN" "$ROOT_DIR/erp-ui/test/$test_file"
done

"$ROOT_DIR/mvnw" -pl erp-modules/erp-oa -am \
    -Dtest='SignScopeHeaderUtilsTest,IdempotentSubmitAspectTest,OaOnboardSignEventFactoryTest,OaSignOnboard*Test,OaSignOnboardImportMigrationTest,OaSignOnboardImportMapperBindingTest,OaTodoMapperBindingTest,OaTodoServiceImplTest,OaSignScope*Test,OaSignPlanScopeTest,OaSignPlanServiceImplTest,OaSignPlanVersionServiceImplTest,*SignScenarioRuleTest,OaSignDocumentServiceTest,OaSignPackagePreflightValidatorTest,OaSignPackageServiceImplTest,OaSignTemplateServiceImplTest,OaSignPackageControllerTest,OaSignTaskControllerTest,OaSignTaskManualInitiationAuditTest,OaSignTaskOrchestratorTest,OaSignTaskServiceImplTest,OaSignTaskBatchSendServiceTest' \
    -Dsurefire.failIfNoSpecifiedTests=false test
"$ROOT_DIR/mvnw" -pl erp-modules/erp-system -am \
    -Dtest='SysSigningProfileSupplement*Test,SigningProfileFactsHashTest,HrSignLifecycleAutomationWiringTest,SignCandidateReadinessTest,SysUserMapperSourceTest,HrEmployeeFieldRegistryTest' \
    -Dsurefire.failIfNoSpecifiedTests=false test

git -C "$ROOT_DIR" diff --check
echo 'ONBOARD_CONTRACT_EXCEL_RELEASE_STATIC_OK migrations=2 templates=9 defaults=off external_gate_contract=exact'

if [[ "$MODE" == '--readiness' ]]; then
    python3 "$ROOT_DIR/scripts/verify_onboard_contract_excel_readiness.py" \
        --root "$ROOT_DIR" \
        --manifest "$ROOT_DIR/scripts/onboard-contract-excel-release-20260718.json"
    echo 'ONBOARD_CONTRACT_EXCEL_RELEASE_READINESS_OK'
fi
