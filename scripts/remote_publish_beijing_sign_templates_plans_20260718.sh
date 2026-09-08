#!/usr/bin/env bash
set -Eeuo pipefail

# Intentionally disabled. Publishing signing templates/plans is an attributed
# HR action that must be performed by the configured HR user in the web UI.
# Never reconstruct a Bearer token from Redis/JWT secrets and never mutate the
# signing configuration directly in MySQL.
cat >&2 <<'EOF'
SIGN_PUBLISH_AUTOMATION_DISABLED
reason=publishing_must_be_performed_by_configured_hr_in_authenticated_ui
current_blocker_1=reviewed_v5_template_package_not_completely_registered
current_blocker_2=all_labor_and_service_plans_must_include_onboard_commitment
current_blocker_3=b3_grade_7_plus_must_include_onboard_confidential_noncompete
readonly_audit=scripts/remote_audit_beijing_sign_publish_readonly_20260718.sh
ui_checklist=docs/runbooks/beijing-sign-template-plan-ui-publish-20260718.md
EOF
exit 40
