#!/usr/bin/env bash
set -euo pipefail

required_vars=(ERP_QA_RUN_ID ERP_QA_DATABASE ERP_QA_ALLOWED_ORG_ID)
for var_name in "${required_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "QA safety gate: ${var_name} is required" >&2
    exit 2
  fi
done

database_name="$(printf '%s' "${ERP_QA_DATABASE}" | tr '[:upper:]' '[:lower:]')"
case "${database_name}" in
  erp|erp_prod|production|prod|*production*|*erp_prod*)
    echo "QA safety gate: database name is not allowed for write-capable QA" >&2
    exit 3
    ;;
esac

if [[ ! "${ERP_QA_RUN_ID}" =~ ^[A-Za-z0-9._-]{6,64}$ ]]; then
  echo "QA safety gate: ERP_QA_RUN_ID must be 6-64 safe characters" >&2
  exit 4
fi

if [[ ! "${ERP_QA_ALLOWED_ORG_ID}" =~ ^[0-9]+$ ]]; then
  echo "QA safety gate: ERP_QA_ALLOWED_ORG_ID must be a numeric isolated organization id" >&2
  exit 5
fi

echo "QA safety gate passed for isolated database, run id and organization scope"
