#!/usr/bin/env python3
"""Tests for the parsed v2 external-gate evidence contract."""

from __future__ import annotations

import copy
import hashlib
import hmac
import unittest
from datetime import datetime, timezone
from unittest import mock

import verify_onboard_contract_excel_gate_evidence as verifier


def digest(label: str) -> str:
    return hashlib.sha256(label.encode("utf-8")).hexdigest()


def hmac_digest(label: str) -> str:
    return hmac.new(
        b"test-only-evidence-key",
        label.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()


def template_items() -> list[dict]:
    files = [
        "05_ONBOARD_COMMITMENT.docx",
        "09_ONBOARD_LABOR_CONTRACT.docx",
        "10_ONBOARD_HANDBOOK_RECEIPT.docx",
        "11_ONBOARD_SALARY_CONFIRM_A.docx",
        "12_ONBOARD_SALARY_CONFIRM_B.docx",
        "13_ONBOARD_SERVICE_CONTRACT.docx",
        "14_ONBOARD_SERVICE_RECEIPT.docx",
        "15_ONBOARD_CONFIDENTIAL_NONCOMPETE.docx",
        "16_ONBOARD_MINOR_NONSTUDENT_DECLARATION.docx",
    ]
    types = [
        "ONBOARD_COMMITMENT",
        "ONBOARD_LABOR_CONTRACT",
        "ONBOARD_HANDBOOK_RECEIPT",
        "ONBOARD_SALARY_CONFIRM",
        "ONBOARD_SALARY_CONFIRM",
        "ONBOARD_SERVICE_CONTRACT",
        "ONBOARD_SERVICE_RECEIPT",
        "ONBOARD_CONFIDENTIAL_NONCOMPETE",
        "ONBOARD_MINOR_NONSTUDENT_DECLARATION",
    ]
    return [
        {
            "file": files[position],
            "type": template_type,
            "sha256": digest(f"template-{position}"),
            "size": 10_000 + position,
        }
        for position, template_type in enumerate(types)
    ]


class EvidenceFixture:
    times = {
        "nativeMySqlRehearsal": "2026-07-18T08:10:00Z",
        "templateHrLegalApproval": "2026-07-18T08:20:00Z",
        "templateRegistrationAndPlanPublish": "2026-07-18T08:30:00Z",
        "isolatedThreePersonUat": "2026-07-18T09:10:00Z",
        "selected27WorkbookUat": "2026-07-18T09:30:00Z",
        "finalPdfHashAuditEvidence": "2026-07-18T09:40:00Z",
        "oneScopeGrayApproval": "2026-07-18T09:15:00Z",
    }

    def __init__(self) -> None:
        self.templates = template_items()
        self.source_manifest = {
            "releaseId": verifier.RELEASE_ID,
            "templateCandidates": {
                "version": verifier.TEMPLATE_SET_VERSION,
                "count": 9,
                "items": copy.deepcopy(self.templates),
            },
        }
        self.signed_context = {
            "releaseId": verifier.RELEASE_ID,
            "candidateCommit": digest("candidate")[:40],
            "approvedPatchSha256": digest("patch"),
            "approvedSourceManifestSha256": digest("source-manifest"),
            "sourceApproval": {"sha256": digest("source-approval")},
            "artifactEvidence": {
                "oaJar": {"sha256": digest("oa-jar")},
                "systemJar": {"sha256": digest("system-jar")},
                "frontendDist": {"treeSha256": digest("frontend-tree")},
            },
            "buildAttestation": {
                "builtAt": "2026-07-18T09:00:00Z",
                "approvedAt": "2026-07-18T10:00:00Z",
            },
            "verifiedTimes": {
                "builtAt": "2026-07-18T09:00:00+00:00",
                "buildApprovedAt": "2026-07-18T10:00:00+00:00",
            },
        }
        self.binding = {
            "candidateCommit": self.signed_context["candidateCommit"],
            "approvedPatchSha256": self.signed_context["approvedPatchSha256"],
            "approvedSourceManifestSha256": self.signed_context[
                "approvedSourceManifestSha256"
            ],
            "sourceApprovalSha256": self.signed_context["sourceApproval"]["sha256"],
            "oaJarSha256": self.signed_context["artifactEvidence"]["oaJar"][
                "sha256"
            ],
            "systemJarSha256": self.signed_context["artifactEvidence"]["systemJar"][
                "sha256"
            ],
            "frontendTreeSha256": self.signed_context["artifactEvidence"][
                "frontendDist"
            ]["treeSha256"],
        }
        self.index = {
            "schemaVersion": 2,
            "releaseId": verifier.RELEASE_ID,
            "status": "verified",
            "containsPii": False,
            "binding": copy.deepcopy(self.binding),
            "gates": {},
        }
        self.documents: dict[str, dict] = {}
        for position, gate in enumerate(verifier.GATES):
            metadata = {
                "status": "passed",
                "evidenceId": f"output/evidence/{gate}.json",
                "evidenceSha256": digest(f"evidence-{gate}"),
                "subject": f"Verified release evidence for {gate}",
                "approvedBy": f"approver-{position}@example.invalid",
                "approvedRole": next(iter(verifier.GATE_ROLES[gate])),
                "approvedAt": self.times[gate],
                "environment": sorted(verifier.GATE_ENVIRONMENTS[gate])[0],
            }
            self.index["gates"][gate] = metadata
            self.documents[gate] = {
                "schemaVersion": 2,
                "releaseId": verifier.RELEASE_ID,
                "gate": gate,
                "status": "passed",
                "containsPii": False,
                "subject": metadata["subject"],
                "approvedBy": metadata["approvedBy"],
                "approvedRole": metadata["approvedRole"],
                "approvedAt": metadata["approvedAt"],
                "environment": metadata["environment"],
                "binding": copy.deepcopy(self.binding),
                "result": self.result(gate),
            }

    def result(self, gate: str) -> dict:
        if gate == "nativeMySqlRehearsal":
            return {
                "databaseProduct": "MySQL",
                "databaseVersion": "8.0.39",
                "nativeServer": True,
                "isolatedEnvironment": True,
                "migrationOrder": [item[0] for item in verifier.MIGRATIONS],
                "migrations": [
                    {"file": file_name, "sha256": sha256, "applied": True}
                    for file_name, sha256 in verifier.MIGRATIONS
                ],
                "requiredObjects": list(verifier.REQUIRED_DATABASE_OBJECTS),
                "requiredIndexes": list(verifier.REQUIRED_DATABASE_INDEXES),
                "failedAssertionCount": 0,
                "passedAssertionCount": 18,
                "rehearsalRunRootSha256": digest("mysql-rehearsal"),
            }
        if gate == "templateHrLegalApproval":
            return {
                "templateSetVersion": verifier.TEMPLATE_SET_VERSION,
                "templateCount": 9,
                "templates": copy.deepcopy(self.templates),
                "hrDecision": "approved",
                "legalDecision": "approved",
                "hrApprover": "hr.owner@example.invalid",
                "legalApprover": "legal.owner@example.invalid",
                "hrApprovedAt": "2026-07-18T08:00:00Z",
                "legalApprovedAt": "2026-07-18T08:05:00Z",
            }
        if gate == "templateRegistrationAndPlanPublish":
            registrations = [
                {
                    "file": item["file"],
                    "sourceSha256": item["sha256"],
                    "templateId": 101 + position,
                    "templateVersion": 1,
                    "status": "ENABLED",
                }
                for position, item in enumerate(self.templates)
            ]
            template_ids = {
                item["file"]: 101 + position
                for position, item in enumerate(self.templates)
            }

            def plan(route_code: str, position: int, deployment_class: str) -> dict:
                policy = verifier.ROUTE_POLICIES[route_code]
                files = policy["templateFiles"] + (verifier.MINOR_TEMPLATE_FILE,)
                production = deployment_class == "PRODUCTION_CANDIDATE"
                service_person_type = verifier.SERVICE_PERSON_TYPE_BY_ROUTE.get(
                    route_code
                )
                insurance_type = (
                    "COMMERCIAL_ACCIDENT"
                    if route_code == "B1"
                    else "EMPLOYER_LIABILITY" if route_code == "B3" else None
                )
                return {
                    "planId": 201 + position,
                    "planVersionId": 301 + position,
                    "planVersionSha256": digest(f"plan-{route_code}"),
                    "ruleSnapshotSha256": digest(f"rule-{route_code}"),
                    "status": "PUBLISHED",
                    "matchingStatus": "ENABLED",
                    "scenario": "ONBOARD",
                    "scopeType": "GLOBAL_HR_CATALOG",
                    "shopDeptId": 0,
                    "runtimeEnvironment": (
                        "staging" if production else "isolated-uat"
                    ),
                    "routeCode": route_code,
                    "contractTypeCode": policy["contractTypeCode"],
                    "socialTypeCode": policy["socialTypeCode"],
                    "jobGradeBand": policy["jobGradeBand"],
                    "servicePersonTypeCode": service_person_type,
                    "insuranceTypeCode": insurance_type,
                    "serviceFactRuleSha256": (
                        verifier._service_rule_sha256(
                            route_code, service_person_type, insurance_type
                        )
                        if service_person_type and insurance_type
                        else None
                    ),
                    "deploymentClass": deployment_class,
                    "productionEligible": production,
                    "templateIds": [template_ids[file_name] for file_name in files],
                }

            production_plans = [
                plan(route_code, position, "PRODUCTION_CANDIDATE")
                for position, route_code in enumerate(verifier.PRODUCTION_ROUTES)
            ]
            isolated_plans = [plan("A3", 5, "ISOLATED_UAT_ONLY")]
            return {
                "templateSetVersion": verifier.TEMPLATE_SET_VERSION,
                "registrationCount": 9,
                "registrations": registrations,
                "productionPlanVersionCount": 5,
                "productionPlanVersions": production_plans,
                "isolatedUatPlanVersionCount": 1,
                "isolatedUatPlanVersions": isolated_plans,
                "productionActiveTargetRouteCount": 5,
                "productionTargetRouteConflictCount": 0,
                "productionNonGlobalTargetPlanCount": 0,
                "isolatedUatPlanInProductionCount": 0,
                "productionUnexpectedActiveRouteCount": 0,
                "productionInvalidActiveRuleCount": 0,
                "unregisteredTemplateCount": 0,
                "unboundTemplateCount": 0,
                "productionPublishSnapshotSha256": verifier._canonical_sha256(
                    production_plans
                ),
                "isolatedUatPublishSnapshotSha256": verifier._canonical_sha256(
                    isolated_plans
                ),
            }
        if gate == "isolatedThreePersonUat":
            cases = []
            plan_versions = (306, 305, 302)
            plan_routes = ("A3", "B3", "A5")
            file_to_template_id = {
                item["file"]: 101 + position
                for position, item in enumerate(self.templates)
            }
            for position, policy in enumerate(verifier.UAT_CASE_POLICIES):
                expected_templates = []
                for file_name in policy["templateFiles"]:
                    template_id = file_to_template_id[file_name]
                    template = self.templates[template_id - 101]
                    expected_templates.append(
                        {
                            "templateId": template_id,
                            "templateVersion": 1,
                            "file": template["file"],
                            "type": template["type"],
                            "sourceSha256": template["sha256"],
                        }
                    )
                supplement = policy["scenario"] == "EMPLOYEE_FACTS_SUPPLEMENT"
                cases.append(
                    {
                        "caseAlias": policy["caseAlias"],
                        "scenario": policy["scenario"],
                        "routeCode": policy["routeCode"],
                        "contractTypeCode": policy["contractTypeCode"],
                        "socialTypeCode": policy["socialTypeCode"],
                        "jobGradeBand": policy["jobGradeBand"],
                        "employeeLevel": policy["employeeLevel"],
                        "planVersionId": plan_versions[position],
                        "planVersionSha256": digest(
                            f"plan-{plan_routes[position]}"
                        ),
                        "planDeploymentClass": policy["planDeploymentClass"],
                        "servicePersonTypeCode": verifier.SERVICE_PERSON_TYPE_BY_ROUTE.get(
                            policy["routeCode"]
                        ),
                        "insuranceTypeCode": (
                            "EMPLOYER_LIABILITY"
                            if policy["serviceFactMatchRequired"]
                            else None
                        ),
                        "serviceFactMatchSha256": (
                            verifier._service_rule_sha256(
                                "B3", "RETIRED_REHIRE", "EMPLOYER_LIABILITY"
                            )
                            if policy["serviceFactMatchRequired"]
                            else None
                        ),
                        "expectedTemplates": expected_templates,
                        "actualTemplates": copy.deepcopy(expected_templates),
                        "syntheticData": True,
                        "externalSignedContractAbsentConfirmed": True,
                        "previewPassed": True,
                        "generated": True,
                        "initialDocumentCount": len(expected_templates),
                        "initialReadConfirmedCount": len(expected_templates),
                        "handwrittenSignatureCaptured": True,
                        "companyOptionalAtInitialSign": True,
                        "hrLegalEntitySelected": True,
                        "validSealSelected": True,
                        "finalDocumentCount": len(expected_templates),
                        "finalReadConfirmedCount": len(expected_templates),
                        "finalConfirmed": True,
                        "terminalStatus": "SIGNED",
                        "laborIdentityEmployeeEditable": False,
                        "employeeFactsSupplementRequired": supplement,
                        "employeeFactsSubmitted": supplement,
                        "hrReviewApproved": supplement,
                        "profileSyncSucceeded": supplement,
                        "contractRematchedAfterSupplement": supplement,
                        "minorConditionEvaluated": True,
                        "minorNonstudentApplied": policy[
                            "minorNonstudentApplied"
                        ],
                        "minorIncomeStartYearMonthHrApproved": policy[
                            "minorNonstudentApplied"
                        ],
                    }
                )
            return {
                "caseCount": 3,
                "passedCount": 3,
                "failedCount": 0,
                "oaArtifactStarted": True,
                "systemArtifactStarted": True,
                "oaHealthProbePassed": True,
                "systemHealthProbePassed": True,
                "frontendServed": True,
                "frontendBundleLoaded": True,
                "browserFlowPassed": True,
                "browserConsoleErrorCount": 0,
                "startupProbeRootSha256": digest("startup-probes"),
                "cases": cases,
                "runRootSha256": digest("three-person-uat"),
            }
        if gate == "selected27WorkbookUat":
            version_ids = {
                route_code: 301 + position
                for position, route_code in enumerate(verifier.PRODUCTION_ROUTES)
            }
            file_to_template_id = {
                item["file"]: 101 + position
                for position, item in enumerate(self.templates)
            }

            def registered_template(file_name: str) -> dict:
                template_id = file_to_template_id[file_name]
                template = self.templates[template_id - 101]
                return {
                    "templateId": template_id,
                    "templateVersion": 1,
                    "file": template["file"],
                    "type": template["type"],
                    "sourceSha256": template["sha256"],
                }

            route_plan_usage = []
            resolved_usage = []
            for route_code, employee_count in verifier.SELECTED_ROUTE_COUNTS:
                policy = verifier.ROUTE_POLICIES[route_code]
                version_id = version_ids[route_code]
                route_plan_usage.append(
                    {
                        "routeCode": route_code,
                        "contractTypeCode": policy["contractTypeCode"],
                        "socialTypeCode": policy["socialTypeCode"],
                        "jobGradeBand": policy["jobGradeBand"],
                        "scopeType": "GLOBAL_HR_CATALOG",
                        "shopDeptId": 0,
                        "planVersionId": version_id,
                        "planVersionSha256": digest(f"plan-{route_code}"),
                        "ruleSnapshotSha256": digest(f"rule-{route_code}"),
                        "servicePersonTypeCode": verifier.SERVICE_PERSON_TYPE_BY_ROUTE.get(
                            route_code
                        ),
                        "insuranceTypeCode": (
                            "COMMERCIAL_ACCIDENT"
                            if route_code == "B1"
                            else "EMPLOYER_LIABILITY"
                            if route_code == "B3"
                            else None
                        ),
                        "serviceFactRuleSha256": (
                            verifier._service_rule_sha256(
                                route_code,
                                verifier.SERVICE_PERSON_TYPE_BY_ROUTE[route_code],
                                "COMMERCIAL_ACCIDENT"
                                if route_code == "B1"
                                else "EMPLOYER_LIABILITY",
                            )
                            if route_code.startswith("B")
                            else None
                        ),
                        "employeeCount": employee_count,
                    }
                )
                resolved_templates = tuple(
                    registered_template(file_name)
                    for file_name in policy["templateFiles"]
                )
                document_count = employee_count * len(resolved_templates)
                resolved_usage.append(
                    {
                        "routeCode": route_code,
                        "planVersionId": version_id,
                        "planVersionSha256": digest(f"plan-{route_code}"),
                        "minorNonstudentApplied": False,
                        "templateIds": [
                            item["templateId"] for item in resolved_templates
                        ],
                        "resolvedTemplateSetSha256": verifier._resolved_template_set_sha256(
                            resolved_templates
                        ),
                        "employeeCount": employee_count,
                        "documentsPerPackage": len(resolved_templates),
                        "documentCount": document_count,
                    }
                )
            result = {
                "selectedRowCount": 27,
                "uniqueMatchedCount": 27,
                "unmatchedCount": 0,
                "ambiguousMatchCount": 0,
                "externalSignedConflictCount": 0,
                "planMatchedCount": 27,
                "previewReadyCount": 27,
                "generatedCount": 27,
                "sendAttemptedCount": 27,
                "sentCount": 27,
                "failedCount": 0,
                "excludedTestRowCount": 1,
                "planVersionLockedCount": 27,
                "planVersionDriftCount": 0,
                "minorConditionEvaluatedCount": 27,
                "resolvedTemplateSetVerifiedCount": 27,
                "deferredMissingDataCount": 2,
                "serviceFactsHrApprovedCount": 2,
                "employeeEditableLaborIdentityCount": 0,
                "routeDistribution": [
                    {"routeCode": route_code, "employeeCount": employee_count}
                    for route_code, employee_count in verifier.SELECTED_ROUTE_COUNTS
                ],
                "routePlanUsage": route_plan_usage,
                "resolvedTemplateSetUsage": resolved_usage,
                "minorNonstudentPlacementCount": 0,
                "conditionalPlacementRootSha256": verifier._conditional_placement_summary_sha256(
                    resolved_usage,
                    0,
                    verifier.SELECTED_BASE_DOCUMENT_COUNT,
                ),
                "serviceFactOutcomeRootSha256": verifier._service_fact_summary_sha256(
                    route_plan_usage, 2, 0
                ),
                "expectedDocumentCount": verifier.SELECTED_BASE_DOCUMENT_COUNT,
                "generatedDocumentCount": verifier.SELECTED_BASE_DOCUMENT_COUNT,
                "workbookIncluded": False,
                "rowDetailsIncluded": False,
                "workbookHmacAlgorithm": "HMAC-SHA256",
                "workbookHmacKeyId": verifier.WORKBOOK_HMAC_KEY_ID,
                "workbookHmacSha256": hmac_digest("workbook-bytes"),
                "rowOutcomeRootSha256": digest("pending-row-root"),
            }
            result["rowOutcomeRootSha256"] = (
                verifier._selected_aggregate_summary_sha256(result)
            )
            return result
        if gate == "finalPdfHashAuditEvidence":
            return {
                "packageCount": 27,
                "signedPackageCount": 27,
                "expectedDocumentCount": verifier.SELECTED_BASE_DOCUMENT_COUNT,
                "documentCount": verifier.SELECTED_BASE_DOCUMENT_COUNT,
                "verifiedDocumentCount": verifier.SELECTED_BASE_DOCUMENT_COUNT,
                "packagesWithVerifiedDocumentsCount": 27,
                "packagesWithoutDocumentsCount": 0,
                "mismatchCount": 0,
                "missingFileCount": 0,
                "verifiedPackageRootCount": 27,
                "verifiedAuditChainCount": 27,
                "requiredEventTypes": list(verifier.REQUIRED_EVENT_TYPES),
                "missingRequiredEventCount": 0,
                "missingRequiredEvidenceCount": 0,
                "requiredEvidenceTypes": list(verifier.REQUIRED_EVIDENCE_TYPES),
                "verificationRootSha256": digest("verification-root"),
                "pdfFilesIncluded": False,
                "auditPayloadIncluded": False,
            }
        if gate == "oneScopeGrayApproval":
            return {
                "decision": "approved",
                "scopeType": "SHOP_DEPT",
                "enabledScopeCount": 1,
                "selectedScopeHmacSha256": digest("selected-scope-hmac"),
                "nonSelectedEnabledScopeCount": 0,
                "globalEnable": False,
                "featureFlags": copy.deepcopy(verifier.FEATURE_FLAGS),
                "rollbackSwitchVerified": True,
                "configSnapshotSha256": digest("config-snapshot"),
                "effectiveAt": "2026-07-18T09:20:00Z",
                "expiresAt": "2026-07-19T09:45:00Z",
            }
        raise AssertionError(gate)

    def verify(self) -> None:
        verifier.verify_gate_evidence(
            self.index,
            self.documents,
            self.signed_context,
            self.source_manifest,
        )


class GateEvidenceTests(unittest.TestCase):
    def setUp(self) -> None:
        clock = mock.patch.object(
            verifier,
            "_now",
            return_value=datetime(2026, 7, 18, 20, 0, tzinfo=timezone.utc),
        )
        clock.start()
        self.addCleanup(clock.stop)
        self.fixture = EvidenceFixture()

    def assert_invalid(self, pattern: str = "") -> None:
        with self.assertRaisesRegex(verifier.GateEvidenceError, pattern):
            self.fixture.verify()

    def test_complete_v2_evidence_passes(self) -> None:
        self.fixture.verify()

    def test_top_level_and_envelope_keys_are_exact(self) -> None:
        for target in ("index", "document", "result"):
            with self.subTest(target=target):
                fixture = EvidenceFixture()
                if target == "index":
                    fixture.index["selfApproved"] = True
                elif target == "document":
                    fixture.documents["nativeMySqlRehearsal"]["rawRows"] = []
                else:
                    fixture.documents["nativeMySqlRehearsal"]["result"]["rawSql"] = "SELECT 1"
                with self.assertRaisesRegex(verifier.GateEvidenceError, "keys are not exact"):
                    fixture.verify()

    def test_documents_are_keyed_by_exact_gate_set(self) -> None:
        self.fixture.documents.pop("nativeMySqlRehearsal")
        self.assert_invalid("evidence documents keys are not exact")

    def test_every_binding_field_matches_signed_context(self) -> None:
        for field in verifier.BINDING_KEYS:
            with self.subTest(field=field, location="index"):
                fixture = EvidenceFixture()
                fixture.index["binding"][field] = (
                    digest(f"wrong-{field}")[:40]
                    if field == "candidateCommit"
                    else digest(f"wrong-{field}")
                )
                with self.assertRaisesRegex(verifier.GateEvidenceError, "signed context"):
                    fixture.verify()
            with self.subTest(field=field, location="document"):
                fixture = EvidenceFixture()
                fixture.documents["isolatedThreePersonUat"]["binding"][field] = (
                    digest(f"other-{field}")[:40]
                    if field == "candidateCommit"
                    else digest(f"other-{field}")
                )
                with self.assertRaisesRegex(verifier.GateEvidenceError, "signed context"):
                    fixture.verify()

    def test_index_and_document_metadata_must_match(self) -> None:
        self.fixture.documents["templateHrLegalApproval"]["approvedBy"] = (
            "different@example.invalid"
        )
        self.assert_invalid("approvedBy does not match")

    def test_roles_and_environments_are_gate_specific(self) -> None:
        for field, value in (
            ("approvedRole", "RELEASE_SCOPE_APPROVER"),
            ("environment", "production"),
        ):
            with self.subTest(field=field):
                fixture = EvidenceFixture()
                fixture.index["gates"]["nativeMySqlRehearsal"][field] = value
                fixture.documents["nativeMySqlRehearsal"][field] = value
                with self.assertRaisesRegex(verifier.GateEvidenceError, "not allowed"):
                    fixture.verify()

    def test_evidence_ids_and_hashes_must_be_unique(self) -> None:
        for field in ("evidenceId", "evidenceSha256"):
            with self.subTest(field=field):
                fixture = EvidenceFixture()
                fixture.index["gates"]["templateHrLegalApproval"][field] = fixture.index[
                    "gates"
                ]["nativeMySqlRehearsal"][field]
                with self.assertRaisesRegex(verifier.GateEvidenceError, "must be unique"):
                    fixture.verify()

    def test_boolean_is_not_accepted_as_integer(self) -> None:
        mutations = (
            ("nativeMySqlRehearsal", "passedAssertionCount"),
            ("selected27WorkbookUat", "selectedRowCount"),
            ("finalPdfHashAuditEvidence", "documentCount"),
        )
        for gate, field in mutations:
            with self.subTest(gate=gate, field=field):
                fixture = EvidenceFixture()
                fixture.documents[gate]["result"][field] = True
                with self.assertRaises(verifier.GateEvidenceError):
                    fixture.verify()

    def test_mysql_evidence_rejects_wrong_native_claims(self) -> None:
        mutations = (
            ("databaseProduct", "MariaDB"),
            ("nativeServer", False),
            ("requiredObjects", list(verifier.REQUIRED_DATABASE_OBJECTS[:-1])),
            ("failedAssertionCount", 1),
        )
        for field, value in mutations:
            with self.subTest(field=field):
                fixture = EvidenceFixture()
                fixture.documents["nativeMySqlRehearsal"]["result"][field] = value
                with self.assertRaises(verifier.GateEvidenceError):
                    fixture.verify()
        self.fixture.documents["nativeMySqlRehearsal"]["result"]["migrations"][0][
            "sha256"
        ] = digest("wrong-migration")
        self.assert_invalid(r"migrations\[0\].sha256")

    def test_mysql_version_indexes_and_assertion_floor_are_strict(self) -> None:
        for field, value in (
            ("databaseVersion", "banana"),
            ("databaseVersion", "8.0.39-MariaDB"),
            ("passedAssertionCount", 17),
            ("requiredIndexes", list(verifier.REQUIRED_DATABASE_INDEXES[:-1])),
        ):
            with self.subTest(field=field, value=value):
                fixture = EvidenceFixture()
                fixture.documents["nativeMySqlRehearsal"]["result"][field] = value
                with self.assertRaises(verifier.GateEvidenceError):
                    fixture.verify()

    def test_template_approval_is_bound_to_source_and_separate_people(self) -> None:
        self.fixture.documents["templateHrLegalApproval"]["result"]["templates"][0][
            "sha256"
        ] = digest("substitute-template")
        self.assert_invalid(r"templates\[0\].sha256")

        fixture = EvidenceFixture()
        result = fixture.documents["templateHrLegalApproval"]["result"]
        result["legalApprover"] = result["hrApprover"].upper()
        with self.assertRaisesRegex(verifier.GateEvidenceError, "distinct"):
            fixture.verify()

        fixture = EvidenceFixture()
        result = fixture.documents["templateHrLegalApproval"]["result"]
        result["legalApprover"] = result["hrApprover"] + "\u200b"
        with self.assertRaisesRegex(verifier.GateEvidenceError, "characters are invalid"):
            fixture.verify()

        fixture = EvidenceFixture()
        result = fixture.documents["templateHrLegalApproval"]["result"]
        result["hrApprover"] = "Ａlice@example.invalid"
        result["legalApprover"] = "Alice@example.invalid"
        with self.assertRaisesRegex(verifier.GateEvidenceError, "distinct"):
            fixture.verify()

    def test_template_inner_approval_cannot_follow_envelope(self) -> None:
        self.fixture.documents["templateHrLegalApproval"]["result"][
            "legalApprovedAt"
        ] = "2026-07-18T08:21:00Z"
        self.assert_invalid("internal approvals")

    def test_registration_requires_unique_registered_and_bound_templates(self) -> None:
        result = self.fixture.documents["templateRegistrationAndPlanPublish"]["result"]
        result["registrations"][1]["templateId"] = result["registrations"][0][
            "templateId"
        ]
        self.assert_invalid("templateId values must be unique")

        fixture = EvidenceFixture()
        result = fixture.documents["templateRegistrationAndPlanPublish"]["result"]
        result["productionPlanVersions"][0]["templateIds"][-1] = 108
        with self.assertRaisesRegex(verifier.GateEvidenceError, "route candidate policy"):
            fixture.verify()

    def test_registration_binds_global_route_rules_and_separates_uat_plan(self) -> None:
        mutations = (
            ("productionPlanVersions", 0, "shopDeptId", 1176, "shopDeptId"),
            ("productionPlanVersions", 1, "routeCode", "A4", "routeCode"),
            ("productionPlanVersions", 4, "jobGradeBand", "7-8", "jobGradeBand"),
            (
                "productionPlanVersions",
                3,
                "servicePersonTypeCode",
                "RETIRED_REHIRE",
                "servicePersonTypeCode",
            ),
            (
                "productionPlanVersions",
                3,
                "insuranceTypeCode",
                "UNAPPROVED_INSURANCE",
                "insuranceTypeCode",
            ),
            (
                "productionPlanVersions",
                3,
                "serviceFactRuleSha256",
                None,
                "serviceFactRuleSha256",
            ),
            (
                "isolatedUatPlanVersions",
                0,
                "runtimeEnvironment",
                "staging",
                "runtimeEnvironment",
            ),
            (
                "isolatedUatPlanVersions",
                0,
                "productionEligible",
                True,
                "productionEligible",
            ),
        )
        for collection, position, field, value, pattern in mutations:
            with self.subTest(collection=collection, field=field):
                fixture = EvidenceFixture()
                fixture.documents["templateRegistrationAndPlanPublish"]["result"][
                    collection
                ][position][field] = value
                with self.assertRaisesRegex(verifier.GateEvidenceError, pattern):
                    fixture.verify()

        fixture = EvidenceFixture()
        fixture.documents["templateRegistrationAndPlanPublish"]["result"][
            "productionTargetRouteConflictCount"
        ] = 1
        with self.assertRaisesRegex(verifier.GateEvidenceError, "integer 0"):
            fixture.verify()

        fixture = EvidenceFixture()
        fixture.documents["templateRegistrationAndPlanPublish"]["result"][
            "isolatedUatPlanInProductionCount"
        ] = 1
        with self.assertRaisesRegex(verifier.GateEvidenceError, "integer 0"):
            fixture.verify()

        for field in (
            "productionUnexpectedActiveRouteCount",
            "productionInvalidActiveRuleCount",
        ):
            with self.subTest(metric=field):
                fixture = EvidenceFixture()
                fixture.documents["templateRegistrationAndPlanPublish"]["result"][
                    field
                ] = 1
                with self.assertRaisesRegex(verifier.GateEvidenceError, "integer 0"):
                    fixture.verify()

        fixture = EvidenceFixture()
        result = fixture.documents["templateRegistrationAndPlanPublish"]["result"]
        result["productionPlanVersions"][1]["planVersionSha256"] = result[
            "productionPlanVersions"
        ][0]["planVersionSha256"]
        with self.assertRaisesRegex(verifier.GateEvidenceError, "values must be unique"):
            fixture.verify()

        fixture = EvidenceFixture()
        result = fixture.documents["templateRegistrationAndPlanPublish"]["result"]
        result["isolatedUatPublishSnapshotSha256"] = result[
            "productionPublishSnapshotSha256"
        ]
        with self.assertRaisesRegex(verifier.GateEvidenceError, "[Ss]napshot"):
            fixture.verify()

    def test_three_person_uat_requires_all_synthetic_signed_scenarios(self) -> None:
        result = self.fixture.documents["isolatedThreePersonUat"]["result"]
        result["cases"][2]["scenario"] = "LABOR"
        self.assert_invalid("scenario must be")

        fixture = EvidenceFixture()
        result = fixture.documents["isolatedThreePersonUat"]["result"]
        result["cases"][0]["laborIdentityEmployeeEditable"] = True
        with self.assertRaisesRegex(verifier.GateEvidenceError, "must be false"):
            fixture.verify()

        fixture = EvidenceFixture()
        result = fixture.documents["isolatedThreePersonUat"]["result"]
        result["cases"][1]["finalReadConfirmedCount"] = 3
        with self.assertRaisesRegex(verifier.GateEvidenceError, "final read count"):
            fixture.verify()

    def test_three_person_uat_binds_plans_grade_packages_and_supplement_flow(self) -> None:
        mutations = (
            (0, "employeeLevel", 6, "employeeLevel"),
            (
                0,
                "actualTemplates",
                self.fixture.documents["isolatedThreePersonUat"]["result"]["cases"][0][
                    "actualTemplates"
                ][:-1],
                "actualTemplates",
            ),
            (0, "socialTypeCode", "SOCIAL_UNINSURED", "socialTypeCode"),
            (1, "planVersionSha256", digest("wrong-plan"), "planVersionSha256"),
            (
                1,
                "servicePersonTypeCode",
                "STUDENT_INTERN",
                "servicePersonTypeCode",
            ),
            (
                1,
                "serviceFactMatchSha256",
                digest("wrong-service-facts"),
                "does not match plan",
            ),
            (2, "profileSyncSucceeded", False, "profileSyncSucceeded"),
            (2, "minorNonstudentApplied", False, "minorNonstudentApplied"),
            (
                2,
                "minorIncomeStartYearMonthHrApproved",
                False,
                "minorIncomeStartYearMonthHrApproved",
            ),
        )
        for position, field, value, pattern in mutations:
            with self.subTest(position=position, field=field):
                fixture = EvidenceFixture()
                fixture.documents["isolatedThreePersonUat"]["result"]["cases"][
                    position
                ][field] = value
                with self.assertRaisesRegex(verifier.GateEvidenceError, pattern):
                    fixture.verify()

        fixture = EvidenceFixture()
        result = fixture.documents["isolatedThreePersonUat"]["result"]
        result["cases"][0]["finalDocumentCount"] -= 1
        result["cases"][0]["finalReadConfirmedCount"] -= 1
        with self.assertRaisesRegex(verifier.GateEvidenceError, "final document count"):
            fixture.verify()

    def test_three_person_uat_rejects_salary_variant_and_template_identity_drift(self) -> None:
        fixture = EvidenceFixture()
        plan = fixture.documents["templateRegistrationAndPlanPublish"]["result"][
            "isolatedUatPlanVersions"
        ][0]
        plan["templateIds"][3] = 104
        with self.assertRaisesRegex(verifier.GateEvidenceError, "route candidate policy"):
            fixture.verify()

        for field, value in (
            ("templateId", 999),
            ("templateVersion", 2),
            ("sourceSha256", digest("wrong-template-source")),
        ):
            with self.subTest(field=field):
                fixture = EvidenceFixture()
                case = fixture.documents["isolatedThreePersonUat"]["result"]["cases"][0]
                case["expectedTemplates"][0][field] = value
                case["actualTemplates"][0][field] = value
                with self.assertRaisesRegex(verifier.GateEvidenceError, field):
                    fixture.verify()

    def test_three_person_uat_requires_bound_artifacts_and_browser_smoke(self) -> None:
        for field, value in (
            ("oaArtifactStarted", False),
            ("systemHealthProbePassed", False),
            ("frontendServed", False),
            ("frontendBundleLoaded", False),
            ("browserFlowPassed", False),
            ("browserConsoleErrorCount", 1),
            ("startupProbeRootSha256", "0" * 64),
        ):
            with self.subTest(field=field):
                fixture = EvidenceFixture()
                fixture.documents["isolatedThreePersonUat"]["result"][field] = value
                with self.assertRaises(verifier.GateEvidenceError):
                    fixture.verify()

    def test_27_row_evidence_rejects_partial_send_or_wrong_test_row_count(self) -> None:
        for field, value in (
            ("sentCount", 26),
            ("excludedTestRowCount", 0),
            ("planVersionDriftCount", 1),
            ("resolvedTemplateSetVerifiedCount", 26),
        ):
            with self.subTest(field=field):
                fixture = EvidenceFixture()
                fixture.documents["selected27WorkbookUat"]["result"][field] = value
                with self.assertRaises(verifier.GateEvidenceError):
                    fixture.verify()
        self.fixture.documents["selected27WorkbookUat"]["result"][
            "workbookIncluded"
        ] = True
        self.assert_invalid("workbookIncluded")

    def test_27_and_final_document_counts_are_complete_and_cross_bound(self) -> None:
        fixture = EvidenceFixture()
        selected = fixture.documents["selected27WorkbookUat"]["result"]
        selected["expectedDocumentCount"] = 106
        selected["generatedDocumentCount"] = 106
        with self.assertRaisesRegex(verifier.GateEvidenceError, "at least 107"):
            fixture.verify()

        fixture = EvidenceFixture()
        selected = fixture.documents["selected27WorkbookUat"]["result"]
        selected["generatedDocumentCount"] = 108
        with self.assertRaisesRegex(verifier.GateEvidenceError, "does not match"):
            fixture.verify()

        fixture = EvidenceFixture()
        selected = fixture.documents["selected27WorkbookUat"]["result"]
        selected["expectedDocumentCount"] = 108
        selected["generatedDocumentCount"] = 108
        with self.assertRaisesRegex(
            verifier.GateEvidenceError, "resolvedTemplateSetUsage"
        ):
            fixture.verify()

        fixture = EvidenceFixture()
        selected = fixture.documents["selected27WorkbookUat"]["result"]
        final = fixture.documents["finalPdfHashAuditEvidence"]["result"]
        selected["expectedDocumentCount"] = 108
        selected["generatedDocumentCount"] = 108
        final["expectedDocumentCount"] = 108
        final["documentCount"] = 108
        final["verifiedDocumentCount"] = 108
        with self.assertRaisesRegex(
            verifier.GateEvidenceError, "resolvedTemplateSetUsage"
        ):
            fixture.verify()

    def test_selected_route_plan_usage_is_bound_to_fixed_workbook_routes(self) -> None:
        mutations = (
            (0, "planVersionSha256", digest("wrong-distribution-plan"), "registration"),
            (0, "shopDeptId", 1176, "shopDeptId"),
            (1, "jobGradeBand", "2-4", "jobGradeBand"),
            (3, "insuranceTypeCode", "EMPLOYER_LIABILITY", "insuranceTypeCode"),
            (0, "employeeCount", 18, "workbook route"),
        )
        for position, field, value, pattern in mutations:
            with self.subTest(field=field):
                fixture = EvidenceFixture()
                fixture.documents["selected27WorkbookUat"]["result"][
                    "routePlanUsage"
                ][position][field] = value
                with self.assertRaisesRegex(verifier.GateEvidenceError, pattern):
                    fixture.verify()

        fixture = EvidenceFixture()
        usage = fixture.documents["selected27WorkbookUat"]["result"][
            "routePlanUsage"
        ]
        usage[1]["planVersionId"] = usage[0]["planVersionId"]
        with self.assertRaisesRegex(verifier.GateEvidenceError, "must be unique"):
            fixture.verify()

        fixture = EvidenceFixture()
        route_distribution = fixture.documents["selected27WorkbookUat"]["result"][
            "routeDistribution"
        ]
        route_distribution[0]["employeeCount"] = 18
        with self.assertRaisesRegex(verifier.GateEvidenceError, "integer 19"):
            fixture.verify()

    def test_resolved_template_usage_controls_conditional_document_count(self) -> None:
        mutations = (
            (0, "resolvedTemplateSetSha256", digest("wrong-set"), "incorrect"),
            (0, "documentCount", 75, "resolved package count"),
            (0, "employeeCount", 18, "employee count does not match route"),
        )
        for position, field, value, pattern in mutations:
            with self.subTest(field=field):
                fixture = EvidenceFixture()
                fixture.documents["selected27WorkbookUat"]["result"][
                    "resolvedTemplateSetUsage"
                ][position][field] = value
                if field == "employeeCount":
                    fixture.documents["selected27WorkbookUat"]["result"][
                        "resolvedTemplateSetUsage"
                    ][position]["documentCount"] = value * 4
                with self.assertRaisesRegex(verifier.GateEvidenceError, pattern):
                    fixture.verify()

        fixture = EvidenceFixture()
        resolved = fixture.documents["selected27WorkbookUat"]["result"][
            "resolvedTemplateSetUsage"
        ][0]
        resolved["templateIds"].append(109)
        with self.assertRaisesRegex(verifier.GateEvidenceError, "templateIds"):
            fixture.verify()

        fixture = EvidenceFixture()
        selected = fixture.documents["selected27WorkbookUat"]["result"]
        selected["minorNonstudentPlacementCount"] = 1
        with self.assertRaisesRegex(verifier.GateEvidenceError, "resolved usage"):
            fixture.verify()

        fixture = EvidenceFixture()
        b1 = fixture.documents["selected27WorkbookUat"]["result"][
            "resolvedTemplateSetUsage"
        ][3]
        b1["minorNonstudentApplied"] = True
        with self.assertRaisesRegex(verifier.GateEvidenceError, "service identity"):
            fixture.verify()

    def test_minor_placement_split_adds_exactly_one_document(self) -> None:
        fixture = EvidenceFixture()
        selected = fixture.documents["selected27WorkbookUat"]["result"]
        non_minor = selected["resolvedTemplateSetUsage"][0]
        non_minor["employeeCount"] = 18
        non_minor["documentCount"] = 72

        template_ids = [101, 102, 103, 104, 109]
        resolved_templates = tuple(
            {
                "templateId": template_id,
                "templateVersion": 1,
                "file": fixture.templates[template_id - 101]["file"],
                "type": fixture.templates[template_id - 101]["type"],
                "sourceSha256": fixture.templates[template_id - 101]["sha256"],
            }
            for template_id in template_ids
        )
        selected["resolvedTemplateSetUsage"].append(
            {
                "routeCode": "A4",
                "planVersionId": 301,
                "planVersionSha256": digest("plan-A4"),
                "minorNonstudentApplied": True,
                "templateIds": template_ids,
                "resolvedTemplateSetSha256": verifier._resolved_template_set_sha256(
                    resolved_templates
                ),
                "employeeCount": 1,
                "documentsPerPackage": 5,
                "documentCount": 5,
            }
        )
        selected["minorNonstudentPlacementCount"] = 1
        selected["expectedDocumentCount"] = 108
        selected["generatedDocumentCount"] = 108
        selected["conditionalPlacementRootSha256"] = (
            verifier._conditional_placement_summary_sha256(
                selected["resolvedTemplateSetUsage"], 1, 108
            )
        )
        selected["rowOutcomeRootSha256"] = (
            verifier._selected_aggregate_summary_sha256(selected)
        )
        final = fixture.documents["finalPdfHashAuditEvidence"]["result"]
        final["expectedDocumentCount"] = 108
        final["documentCount"] = 108
        final["verifiedDocumentCount"] = 108
        fixture.verify()

    def test_selected_aggregate_roots_are_recomputed_from_the_claimed_counts(self) -> None:
        fixture = EvidenceFixture()
        selected = fixture.documents["selected27WorkbookUat"]["result"]
        selected["workbookHmacKeyId"] = "unregistered-key-id"
        selected["rowOutcomeRootSha256"] = (
            verifier._selected_aggregate_summary_sha256(selected)
        )
        with self.assertRaisesRegex(
            verifier.GateEvidenceError, "not the registered release key"
        ):
            fixture.verify()

        fixture = EvidenceFixture()
        selected = fixture.documents["selected27WorkbookUat"]["result"]
        selected["workbookHmacSha256"] = hmac_digest("different-workbook")
        with self.assertRaisesRegex(
            verifier.GateEvidenceError, "rowOutcomeRootSha256 is incorrect"
        ):
            fixture.verify()

        fixture = EvidenceFixture()
        selected = fixture.documents["selected27WorkbookUat"]["result"]
        non_minor = selected["resolvedTemplateSetUsage"][0]
        non_minor["employeeCount"] = 18
        non_minor["documentCount"] = 72
        selected["resolvedTemplateSetUsage"].append(
            {
                "routeCode": "A4",
                "planVersionId": 301,
                "planVersionSha256": digest("plan-A4"),
                "minorNonstudentApplied": True,
                "templateIds": [101, 102, 103, 104, 109],
                "resolvedTemplateSetSha256": verifier._resolved_template_set_sha256(
                    tuple(
                        {
                            "templateId": template_id,
                            "templateVersion": 1,
                            "file": fixture.templates[template_id - 101]["file"],
                            "type": fixture.templates[template_id - 101]["type"],
                            "sourceSha256": fixture.templates[template_id - 101][
                                "sha256"
                            ],
                        }
                        for template_id in [101, 102, 103, 104, 109]
                    )
                ),
                "employeeCount": 1,
                "documentsPerPackage": 5,
                "documentCount": 5,
            }
        )
        selected["minorNonstudentPlacementCount"] = 1
        selected["expectedDocumentCount"] = 108
        selected["generatedDocumentCount"] = 108
        with self.assertRaisesRegex(
            verifier.GateEvidenceError, "conditionalPlacementRootSha256 is incorrect"
        ):
            fixture.verify()

    def test_final_evidence_requires_27_complete_packages_and_exact_types(self) -> None:
        for field, value in (
            ("packageCount", 26),
            ("expectedDocumentCount", 108),
            ("verifiedDocumentCount", 108),
            ("packagesWithVerifiedDocumentsCount", 26),
            ("packagesWithoutDocumentsCount", 1),
            ("missingRequiredEvidenceCount", 1),
            ("pdfFilesIncluded", True),
        ):
            with self.subTest(field=field):
                fixture = EvidenceFixture()
                fixture.documents["finalPdfHashAuditEvidence"]["result"][field] = value
                with self.assertRaises(verifier.GateEvidenceError):
                    fixture.verify()
        self.fixture.documents["finalPdfHashAuditEvidence"]["result"][
            "requiredEventTypes"
        ].reverse()
        self.assert_invalid("requiredEventTypes")

    def test_repeated_placeholder_hashes_and_commit_are_rejected(self) -> None:
        fixture = EvidenceFixture()
        fixture.documents["selected27WorkbookUat"]["result"][
            "workbookHmacSha256"
        ] = "0" * 64
        with self.assertRaises(verifier.GateEvidenceError):
            fixture.verify()

        fixture = EvidenceFixture()
        fixture.signed_context["candidateCommit"] = "a" * 40
        fixture.binding["candidateCommit"] = "a" * 40
        fixture.index["binding"]["candidateCommit"] = "a" * 40
        for document in fixture.documents.values():
            document["binding"]["candidateCommit"] = "a" * 40
        with self.assertRaises(verifier.GateEvidenceError):
            fixture.verify()

    def test_gray_evidence_requires_one_scope_exact_flags_and_expiry(self) -> None:
        mutations = (
            ("globalEnable", True),
            ("scopeType", "COMPANY"),
            ("enabledScopeCount", 2),
        )
        for field, value in mutations:
            with self.subTest(field=field):
                fixture = EvidenceFixture()
                fixture.documents["oneScopeGrayApproval"]["result"][field] = value
                with self.assertRaises(verifier.GateEvidenceError):
                    fixture.verify()
        fixture = EvidenceFixture()
        fixture.documents["oneScopeGrayApproval"]["result"]["featureFlags"][
            "oa.sign.expiry.enabled"
        ] = True
        with self.assertRaisesRegex(verifier.GateEvidenceError, "must be false"):
            fixture.verify()
        self.fixture.documents["oneScopeGrayApproval"]["result"][
            "expiresAt"
        ] = "2026-07-18T09:20:00Z"
        self.assert_invalid("must follow effectiveAt")

    def test_gray_window_must_be_current_and_cover_batch_final_and_build(self) -> None:
        mutations = (
            ("effectiveAt", "2026-07-18T20:06:00Z", "still in the future"),
            ("expiresAt", "2026-07-18T19:59:59Z", "has expired"),
        )
        for field, value, pattern in mutations:
            with self.subTest(field=field):
                fixture = EvidenceFixture()
                fixture.documents["oneScopeGrayApproval"]["result"][field] = value
                with self.assertRaisesRegex(verifier.GateEvidenceError, pattern):
                    fixture.verify()

        fixture = EvidenceFixture()
        gate = "selected27WorkbookUat"
        fixture.index["gates"][gate]["approvedAt"] = "2026-07-18T09:19:59Z"
        fixture.documents[gate]["approvedAt"] = "2026-07-18T09:19:59Z"
        with self.assertRaisesRegex(verifier.GateEvidenceError, "approved gray window"):
            fixture.verify()

        fixture = EvidenceFixture()
        fixture.signed_context["buildAttestation"]["approvedAt"] = (
            "2026-07-19T10:00:00Z"
        )
        fixture.signed_context["verifiedTimes"]["buildApprovedAt"] = (
            "2026-07-19T10:00:00Z"
        )
        with self.assertRaisesRegex(verifier.GateEvidenceError, "Build Attestation"):
            fixture.verify()

    def test_gate_approval_chronology_is_enforced(self) -> None:
        mutations = (
            (
                "nativeMySqlRehearsal",
                "2026-07-18T08:31:00Z",
                "native MySQL rehearsal",
            ),
            (
                "templateHrLegalApproval",
                "2026-07-18T08:31:00Z",
                "HR/legal template approval",
            ),
            (
                "templateRegistrationAndPlanPublish",
                "2026-07-18T09:11:00Z",
                "registration/publish must precede isolated",
            ),
            (
                "isolatedThreePersonUat",
                "2026-07-18T09:16:00Z",
                "isolated three-person UAT must precede",
            ),
        )
        for gate, timestamp, pattern in mutations:
            with self.subTest(gate=gate):
                fixture = EvidenceFixture()
                fixture.index["gates"][gate]["approvedAt"] = timestamp
                fixture.documents[gate]["approvedAt"] = timestamp
                if gate == "templateHrLegalApproval":
                    result = fixture.documents[gate]["result"]
                    result["hrApprovedAt"] = "2026-07-18T08:30:30Z"
                    result["legalApprovedAt"] = "2026-07-18T08:30:30Z"
                with self.assertRaisesRegex(verifier.GateEvidenceError, pattern):
                    fixture.verify()

    def test_post_build_gates_cannot_precede_artifact_build(self) -> None:
        for gate in (
            "isolatedThreePersonUat",
            "selected27WorkbookUat",
            "finalPdfHashAuditEvidence",
            "oneScopeGrayApproval",
        ):
            with self.subTest(gate=gate):
                fixture = EvidenceFixture()
                fixture.index["gates"][gate]["approvedAt"] = "2026-07-18T08:59:59Z"
                fixture.documents[gate]["approvedAt"] = "2026-07-18T08:59:59Z"
                with self.assertRaisesRegex(verifier.GateEvidenceError, "precedes"):
                    fixture.verify()

    def test_build_approval_must_follow_all_gate_approvals(self) -> None:
        self.fixture.signed_context["buildAttestation"]["approvedAt"] = (
            "2026-07-18T09:35:00Z"
        )
        self.fixture.signed_context["verifiedTimes"]["buildApprovedAt"] = (
            "2026-07-18T09:35:00+00:00"
        )
        self.assert_invalid("gate approvedAt follows")

    def test_verified_build_times_must_match_signed_attestation(self) -> None:
        self.fixture.signed_context["verifiedTimes"]["builtAt"] = (
            "2026-07-18T09:00:01Z"
        )
        self.assert_invalid("verifiedTimes disagree")

    def test_source_template_contract_is_strict_and_unique(self) -> None:
        self.fixture.source_manifest["templateCandidates"]["items"][0][
            "extra"
        ] = True
        self.assert_invalid("keys are not exact")

        fixture = EvidenceFixture()
        fixture.source_manifest["templateCandidates"]["items"][1]["sha256"] = fixture.source_manifest[
            "templateCandidates"
        ]["items"][0]["sha256"]
        with self.assertRaisesRegex(verifier.GateEvidenceError, "must be unique"):
            fixture.verify()

    def test_pii_and_placeholder_metadata_are_rejected(self) -> None:
        self.fixture.documents["nativeMySqlRehearsal"]["containsPii"] = True
        self.assert_invalid("containsPii")

        fixture = EvidenceFixture()
        gate = "nativeMySqlRehearsal"
        fixture.index["gates"][gate]["approvedBy"] = "TODO"
        fixture.documents[gate]["approvedBy"] = "TODO"
        with self.assertRaisesRegex(verifier.GateEvidenceError, "placeholder"):
            fixture.verify()


if __name__ == "__main__":
    unittest.main()
