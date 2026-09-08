-- Read-only deployment preflight for erp_oa_sign_labor_contract_placement_v7_20260809.sql.
-- Every result must match the expected value documented in the alias. This file performs
-- no DDL or DML and deliberately returns only aggregate/configuration evidence.

SELECT COUNT(*) AS expected_2_reviewed_v4_source_versions
  FROM oa_sign_plan_version
 WHERE (version_id = 1000000002 AND plan_id = 49
          AND version_hash = 'f79d4fb655ae6b4ea186fb13609c0e06cd3bd89a40f0abbac7e91cd4243db0b7')
    OR (version_id = 1000000005 AND plan_id = 36
          AND version_hash = 'd115f4088e103587d395a73e09fc4cef93550e19b2b91dc870a6abc3f231e605');

SELECT COUNT(*) AS expected_1_exact_v7_template
  FROM oa_sign_template
 WHERE template_id = 92
   AND template_type = 'ONBOARD_LABOR_CONTRACT'
   AND template_version = '20260721-v7'
   AND file_hash = '1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558'
   AND status = '0';

SELECT COUNT(*) AS expected_8_source_template_snapshots
  FROM oa_sign_plan_version_template
 WHERE plan_version_id IN (1000000002, 1000000005);

SELECT COUNT(*) AS expected_2_reviewed_v4_labor_bindings
  FROM oa_sign_plan_version_template
 WHERE (plan_version_id = 1000000002
          AND template_id = 81
          AND template_version = '20260718-v4-draft'
          AND source_file_hash = 'fdbe2df12eb3704370126e09f2c26fd7cd09e0a06db1e0fe960f42f9513f2118')
    OR (plan_version_id = 1000000005
          AND template_id = 81
          AND template_version = '20260718-v4-draft'
          AND source_file_hash = 'fdbe2df12eb3704370126e09f2c26fd7cd09e0a06db1e0fe960f42f9513f2118');

SELECT SUM(version_id IN (1000000002, 1000000005)) AS source_enabled_count,
       SUM(version_hash IN (
           'bd07d71829535d12a26f5a4637a5e4f73fe49e0ad1340287fa92959cd0b85396',
           'eda0fcd21b70b302ca1edd43facb0d5c4b03e7a593b9a5f8a82d5147f0c87d40'))
           AS candidate_enabled_count,
       SUM(version_id NOT IN (1000000002, 1000000005)
           AND version_hash NOT IN (
               'bd07d71829535d12a26f5a4637a5e4f73fe49e0ad1340287fa92959cd0b85396',
               'eda0fcd21b70b302ca1edd43facb0d5c4b03e7a593b9a5f8a82d5147f0c87d40'))
           AS expected_0_other_enabled_count
  FROM oa_sign_plan_version
 WHERE plan_id IN (49, 36)
   AND publish_status = 'PUBLISHED'
   AND matching_status = 'ENABLED';

SELECT COUNT(*) AS expected_0_candidate_plan_field_mismatches
  FROM (
      SELECT 1000000002 AS source_id,
             'bd07d71829535d12a26f5a4637a5e4f73fe49e0ad1340287fa92959cd0b85396'
                 AS candidate_hash
      UNION ALL
      SELECT 1000000005,
             'eda0fcd21b70b302ca1edd43facb0d5c4b03e7a593b9a5f8a82d5147f0c87d40'
  ) mapping
  JOIN oa_sign_plan_version source ON source.version_id = mapping.source_id
  JOIN oa_sign_plan_version target
    ON target.plan_id = source.plan_id AND target.version_hash = mapping.candidate_hash
 WHERE NOT (target.plan_name <=> source.plan_name)
    OR NOT (target.scenario <=> source.scenario)
    OR NOT (target.shop_dept_id <=> source.shop_dept_id)
    OR NOT (target.legal_entity_id <=> source.legal_entity_id)
    OR NOT (target.legal_entity_name <=> source.legal_entity_name)
    OR NOT (target.rule_json <=> source.rule_json)
    OR NOT (target.default_values_json <=> source.default_values_json)
    OR NOT (target.sign_deadline_days <=> source.sign_deadline_days)
    OR NOT (target.reminder_policy_json <=> source.reminder_policy_json)
    OR NOT (target.auto_send_condition_json <=> source.auto_send_condition_json)
    OR target.version_no <= source.version_no
    OR target.publish_status <> 'PUBLISHED'
    OR target.matching_status NOT IN ('DISABLED', 'ENABLED');

-- Reviewed future-path inventory is an exact release baseline: plans 35/36/49/66 must each
-- contribute exactly one enabled labor binding. A missing plan, duplicate binding, or new plan
-- requires a fresh review rather than silently shrinking or expanding this set.
SELECT pv.plan_id, pv.version_id, pv.version_hash, pv.publish_status, pv.matching_status,
       pvt.template_id, pvt.template_version, pvt.source_file_hash
  FROM oa_sign_plan_version pv
  JOIN oa_sign_plan_version_template pvt
    ON pvt.plan_version_id = pv.version_id
   AND pvt.template_type = 'ONBOARD_LABOR_CONTRACT'
 WHERE pv.publish_status = 'PUBLISHED'
   AND pv.matching_status = 'ENABLED'
 ORDER BY pv.plan_id, pv.version_id, pvt.id;

SELECT COUNT(*) AS expected_4_enabled_labor_bindings
  FROM oa_sign_plan_version pv
  JOIN oa_sign_plan_version_template pvt
    ON pvt.plan_version_id = pv.version_id
   AND pvt.template_type = 'ONBOARD_LABOR_CONTRACT'
 WHERE pv.publish_status = 'PUBLISHED'
   AND pv.matching_status = 'ENABLED';

SELECT COUNT(*) AS expected_0_missing_or_ambiguous_reviewed_labor_plans
  FROM (
      SELECT expected.plan_id
        FROM (
            SELECT 35 AS plan_id UNION ALL SELECT 36
            UNION ALL SELECT 49 UNION ALL SELECT 66
        ) expected
        LEFT JOIN (
            SELECT pv.plan_id, COUNT(*) AS binding_count
              FROM oa_sign_plan_version pv
              JOIN oa_sign_plan_version_template pvt
                ON pvt.plan_version_id = pv.version_id
               AND pvt.template_type = 'ONBOARD_LABOR_CONTRACT'
             WHERE pv.publish_status = 'PUBLISHED'
               AND pv.matching_status = 'ENABLED'
             GROUP BY pv.plan_id
        ) actual ON actual.plan_id = expected.plan_id
       WHERE COALESCE(actual.binding_count, 0) <> 1
  ) reviewed_plan_mismatches;

SELECT COUNT(*) AS expected_0_unknown_enabled_labor_bindings
  FROM oa_sign_plan_version pv
  JOIN oa_sign_plan_version_template pvt
    ON pvt.plan_version_id = pv.version_id
   AND pvt.template_type = 'ONBOARD_LABOR_CONTRACT'
 WHERE pv.publish_status = 'PUBLISHED'
   AND pv.matching_status = 'ENABLED'
   AND NOT (
       (pv.plan_id IN (35, 36, 49, 66)
        AND pvt.template_id = 92
        AND pvt.template_version = '20260721-v7'
        AND pvt.source_file_hash =
            '1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558')
       OR (pv.version_id = 1000000002 AND pv.plan_id = 49
        AND pvt.template_id = 81
        AND pvt.template_version = '20260718-v4-draft'
        AND pvt.source_file_hash =
            'fdbe2df12eb3704370126e09f2c26fd7cd09e0a06db1e0fe960f42f9513f2118')
       OR (pv.version_id = 1000000005 AND pv.plan_id = 36
        AND pvt.template_id = 81
        AND pvt.template_version = '20260718-v4-draft'
        AND pvt.source_file_hash =
            'fdbe2df12eb3704370126e09f2c26fd7cd09e0a06db1e0fe960f42f9513f2118'));

-- HB-01: every reviewed active plan must expose exactly one employee-visible handbook or
-- handbook receipt. This checks the future package template set, not only already-created rows.
SELECT pv.plan_id, pv.version_id,
       (SELECT COUNT(*)
          FROM oa_sign_plan_version_template handbook
         WHERE handbook.plan_version_id = pv.version_id
           AND UPPER(TRIM(COALESCE(handbook.employee_visible, 'N'))) = 'Y'
           AND handbook.template_type IN
               ('ONBOARD_HANDBOOK', 'ONBOARD_HANDBOOK_RECEIPT'))
           AS employee_visible_handbook_count
  FROM oa_sign_plan_version pv
  JOIN oa_sign_plan_version_template labor
    ON labor.plan_version_id = pv.version_id
   AND labor.template_type = 'ONBOARD_LABOR_CONTRACT'
 WHERE pv.publish_status = 'PUBLISHED'
   AND pv.matching_status = 'ENABLED'
 ORDER BY pv.plan_id, pv.version_id;

SELECT COUNT(*) AS expected_0_active_plan_handbook_mismatches
  FROM oa_sign_plan_version pv
  JOIN oa_sign_plan_version_template labor
    ON labor.plan_version_id = pv.version_id
   AND labor.template_type = 'ONBOARD_LABOR_CONTRACT'
 WHERE pv.publish_status = 'PUBLISHED'
   AND pv.matching_status = 'ENABLED'
   AND (SELECT COUNT(*)
          FROM oa_sign_plan_version_template handbook
         WHERE handbook.plan_version_id = pv.version_id
           AND UPPER(TRIM(COALESCE(handbook.employee_visible, 'N'))) = 'Y'
           AND handbook.template_type IN
               ('ONBOARD_HANDBOOK', 'ONBOARD_HANDBOOK_RECEIPT')) <> 1;

-- ATT-01: exact, employee-visible future document sets drive all four checklist marks.
-- Output is limited to plan/type counts and contains no employee or legal-entity values.
SELECT pv.plan_id, pvt.template_type, COUNT(*) AS total_count,
       SUM(CASE WHEN UPPER(TRIM(COALESCE(pvt.employee_visible, 'N'))) = 'Y'
                THEN 1 ELSE 0 END) AS employee_visible_count
  FROM oa_sign_plan_version pv
  JOIN oa_sign_plan_version_template pvt ON pvt.plan_version_id = pv.version_id
 WHERE pv.publish_status = 'PUBLISHED'
   AND pv.matching_status = 'ENABLED'
   AND pv.plan_id IN (35, 36, 49, 66)
 GROUP BY pv.plan_id, pvt.template_type
 ORDER BY pv.plan_id, pvt.template_type;

SELECT COUNT(*) AS expected_0_active_plan_required_document_set_mismatches
  FROM (
      SELECT pv.plan_id
        FROM oa_sign_plan_version pv
        JOIN oa_sign_plan_version_template item ON item.plan_version_id = pv.version_id
       WHERE pv.publish_status = 'PUBLISHED'
         AND pv.matching_status = 'ENABLED'
         AND pv.plan_id IN (35, 36, 49, 66)
       GROUP BY pv.plan_id, pv.version_id
      HAVING COUNT(*) <> IF(pv.plan_id = 66, 5, 4)
          OR SUM(item.template_type = 'ONBOARD_COMMITMENT') <> 1
          OR SUM(item.template_type = 'ONBOARD_COMMITMENT'
                 AND UPPER(TRIM(COALESCE(item.employee_visible, 'N'))) = 'Y') <> 1
          OR SUM(item.template_type = 'ONBOARD_LABOR_CONTRACT') <> 1
          OR SUM(item.template_type = 'ONBOARD_LABOR_CONTRACT'
                 AND UPPER(TRIM(COALESCE(item.employee_visible, 'N'))) = 'Y') <> 1
          OR SUM(item.template_type IN
                 ('ONBOARD_HANDBOOK', 'ONBOARD_HANDBOOK_RECEIPT')) <> 1
          OR SUM(item.template_type IN
                 ('ONBOARD_HANDBOOK', 'ONBOARD_HANDBOOK_RECEIPT')
                 AND UPPER(TRIM(COALESCE(item.employee_visible, 'N'))) = 'Y') <> 1
          OR SUM(item.template_type = 'ONBOARD_SALARY_CONFIRM') <> 1
          OR SUM(item.template_type = 'ONBOARD_SALARY_CONFIRM'
                 AND UPPER(TRIM(COALESCE(item.employee_visible, 'N'))) = 'Y') <> 1
          OR SUM(item.template_type = 'ONBOARD_CONFIDENTIAL_NONCOMPETE')
                 <> IF(pv.plan_id = 66, 1, 0)
          OR SUM(item.template_type = 'ONBOARD_CONFIDENTIAL_NONCOMPETE'
                 AND UPPER(TRIM(COALESCE(item.employee_visible, 'N'))) = 'Y')
                 <> IF(pv.plan_id = 66, 1, 0)
  ) invalid_reviewed_template_sets;

-- REP-01: output only aggregate completeness, never legal-entity names or representatives.
SELECT COUNT(*) AS active_legal_entity_count,
       SUM(CASE WHEN NULLIF(TRIM(legal_entity_name), '') IS NULL
                      OR NULLIF(TRIM(legal_representative), '') IS NULL
                      OR NULLIF(TRIM(registered_address), '') IS NULL
                THEN 1 ELSE 0 END) AS expected_0_incomplete_active_legal_entities
  FROM sys_legal_entity
 WHERE status = '0';

SELECT template_id, template_version, file_url, file_hash
  FROM oa_sign_template
 WHERE template_id = 92
   AND template_type = 'ONBOARD_LABOR_CONTRACT';

SELECT COUNT(DISTINCT p.package_id) AS expected_0_nonterminal_v4_packages
  FROM oa_sign_package p
  LEFT JOIN oa_sign_package_document d ON d.package_id = p.package_id
 WHERE (p.plan_version_id IN (1000000002, 1000000005)
        OR (d.template_type = 'ONBOARD_LABOR_CONTRACT'
            AND d.template_version_snapshot = '20260718-v4-draft'))
   AND LOWER(TRIM(p.status)) NOT IN ('signed', 'voided', 'refused', 'expired');

SELECT p.status, COUNT(DISTINCT p.package_id) AS package_count
  FROM oa_sign_package p
  JOIN oa_sign_package_document d ON d.package_id = p.package_id
 WHERE d.template_type = 'ONBOARD_LABOR_CONTRACT'
   AND d.template_version_snapshot = '20260718-v4-draft'
 GROUP BY p.status
 ORDER BY p.status;

-- PII-free inventory of every non-terminal, employee-visible labor contract. SQL is only the
-- aggregate pre-filter; the release Java verifier must still open the source/review/signature/
-- seal/final-candidate files, parse both policy JSON snapshots and recompute every candidate root.
-- No positive version/hash/policy predicate is allowed in the WHERE clause.
SELECT inventory.package_status, inventory.signing_sequence,
       inventory.template_version, inventory.source_file_hash,
       inventory.document_policy_mode, inventory.signature_policy_mode,
       inventory.seal_policy_mode, inventory.signature_sequence_policy,
       inventory.seal_sequence_policy, inventory.evidence_state,
       inventory.final_confirmation_status, inventory.signature_evidence_state,
       inventory.final_candidate_state, inventory.employee_visible_handbook_state,
       CASE WHEN inventory.evidence_state = 'COMPLETE'
                  AND inventory.employee_visible_handbook_state = 'INCLUDED'
                  AND inventory.template_version = '20260721-v7'
                  AND inventory.source_file_hash =
                      '1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558'
                  AND inventory.document_policy_mode = 'SNAPSHOT_V1'
                  AND (
                      (inventory.signing_sequence = 'COMPANY_FIRST'
                       AND inventory.package_status IN
                           ('draft', 'pending_sign', 'part_viewed')
                       AND inventory.final_confirmation_status = 'PREPARED_NOT_SENT'
                       AND inventory.final_candidate_state = 'COMPLETE'
                       AND ((inventory.signature_policy_mode =
                                  'APPENDED_CONFIRMATION_PAGE'
                             AND inventory.seal_policy_mode =
                                  'APPENDED_CONFIRMATION_PAGE')
                            OR (inventory.signature_policy_mode = 'PLACED_MULTI'
                             AND inventory.seal_policy_mode = 'PLACED_MULTI'
                             AND inventory.signature_sequence_policy =
                                  'COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT'
                             AND inventory.seal_sequence_policy =
                                  inventory.signature_sequence_policy)))
                      OR
                      (inventory.signing_sequence = 'SIGNATURE_FIRST'
                       AND inventory.signature_policy_mode = 'PLACED_MULTI'
                       AND inventory.seal_policy_mode = 'PLACED_MULTI'
                       AND inventory.signature_sequence_policy =
                           'SIGNATURE_FIRST_BODY_PLACEMENT'
                       AND inventory.seal_sequence_policy =
                           inventory.signature_sequence_policy
                       AND (
                           (inventory.package_status IN
                                ('draft', 'pending_sign', 'part_viewed')
                            AND inventory.final_confirmation_status = '<MISSING>'
                            AND inventory.signature_evidence_state = 'INCOMPLETE'
                            AND inventory.final_candidate_state = 'INCOMPLETE')
                           OR (inventory.package_status = 'pending_company'
                            AND inventory.signature_evidence_state = 'COMPLETE'
                            AND ((inventory.final_confirmation_status = 'WAITING_COMPANY'
                                  AND inventory.final_candidate_state = 'INCOMPLETE')
                                 OR (inventory.final_confirmation_status =
                                         'PREPARED_NOT_SENT'
                                  AND inventory.final_candidate_state = 'COMPLETE')))
                           OR (inventory.package_status = 'pending_final_confirm'
                            AND inventory.signature_evidence_state = 'COMPLETE'
                            AND inventory.final_confirmation_status = 'PENDING'
                            AND inventory.final_candidate_state = 'COMPLETE'))))
            THEN 'SUPPORTED' ELSE 'UNSUPPORTED' END AS continuation_state,
       COUNT(DISTINCT inventory.package_id) AS package_count
  FROM (
      SELECT p.package_id, LOWER(TRIM(p.status)) AS package_status,
             UPPER(TRIM(COALESCE(p.signing_sequence, '<MISSING>')))
                 AS signing_sequence,
             COALESCE(d.template_version_snapshot, '<MISSING>') AS template_version,
             LOWER(COALESCE(pvt.source_file_hash, '<MISSING>')) AS source_file_hash,
             UPPER(TRIM(COALESCE(d.document_policy_mode, '<MISSING>')))
                 AS document_policy_mode,
             UPPER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
                 d.signature_position_json, '$.mode')), '<MISSING>'))
                 AS signature_policy_mode,
             UPPER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
                 d.company_seal_position_json, '$.mode')), '<MISSING>'))
                 AS seal_policy_mode,
             UPPER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
                 d.signature_position_json, '$.signingSequencePolicy')), '<MISSING>'))
                 AS signature_sequence_policy,
             UPPER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
                 d.company_seal_position_json, '$.signingSequencePolicy')), '<MISSING>'))
                 AS seal_sequence_policy,
             UPPER(COALESCE(NULLIF(TRIM(p.final_confirmation_status), ''), '<MISSING>'))
                 AS final_confirmation_status,
             CASE WHEN d.source_file_url_snapshot IS NOT NULL
                        AND TRIM(d.source_file_url_snapshot) <> ''
                        AND d.review_pdf_url IS NOT NULL
                        AND TRIM(d.review_pdf_url) <> ''
                        AND d.review_pdf_hash REGEXP '^[0-9a-fA-F]{64}$'
                        AND UPPER(TRIM(COALESCE(d.employee_sign_required, 'N'))) = 'Y'
                        AND UPPER(TRIM(COALESCE(d.company_seal_required, 'N'))) = 'Y'
                  THEN 'COMPLETE' ELSE 'INCOMPLETE' END AS evidence_state,
             CASE WHEN (NULLIF(TRIM(p.signature_sample_file_url), '') IS NOT NULL
                               AND p.signature_sample_hash REGEXP '^[0-9a-fA-F]{64}$'
                               AND p.signature_sample_time IS NOT NULL)
                            OR (COALESCE(p.initial_signed_time, p.signed_time) IS NOT NULL
                               AND EXISTS (
                                   SELECT 1 FROM oa_sign_package_document required_doc
                                    WHERE required_doc.package_id = p.package_id
                                      AND UPPER(TRIM(COALESCE(
                                          required_doc.employee_visible, 'N'))) = 'Y'
                                      AND UPPER(TRIM(COALESCE(
                                          required_doc.employee_sign_required, 'N'))) = 'Y')
                               AND NOT EXISTS (
                                   SELECT 1 FROM oa_sign_package_document required_doc
                                    WHERE required_doc.package_id = p.package_id
                                      AND UPPER(TRIM(COALESCE(
                                          required_doc.employee_visible, 'N'))) = 'Y'
                                      AND UPPER(TRIM(COALESCE(
                                          required_doc.employee_sign_required, 'N'))) = 'Y'
                                      AND (NULLIF(TRIM(
                                          required_doc.signature_file_url), '') IS NULL
                                           OR required_doc.signature_hash NOT REGEXP
                                               '^[0-9a-fA-F]{64}$')))
                  THEN 'COMPLETE' ELSE 'INCOMPLETE' END AS signature_evidence_state,
             CASE WHEN p.legal_entity_id_snapshot IS NOT NULL
                        AND NULLIF(TRIM(p.legal_entity_name_snapshot), '') IS NOT NULL
                        AND NULLIF(TRIM(p.legal_representative_snapshot), '') IS NOT NULL
                        AND p.company_frozen_time IS NOT NULL
                        AND NULLIF(TRIM(p.seal_image_url_snapshot), '') IS NOT NULL
                        AND p.seal_image_hash_snapshot REGEXP '^[0-9a-fA-F]{64}$'
                        AND NULLIF(TRIM(p.final_document_version), '') IS NOT NULL
                        AND p.final_document_root_hash REGEXP '^[0-9a-fA-F]{64}$'
                        AND p.final_generated_time IS NOT NULL
                        AND EXISTS (
                            SELECT 1 FROM oa_sign_package_document visible
                             WHERE visible.package_id = p.package_id
                               AND UPPER(TRIM(COALESCE(
                                   visible.employee_visible, 'N'))) = 'Y')
                        AND NOT EXISTS (
                            SELECT 1 FROM oa_sign_package_document visible
                             WHERE visible.package_id = p.package_id
                               AND UPPER(TRIM(COALESCE(
                                   visible.employee_visible, 'N'))) = 'Y'
                               AND (visible.final_document_version IS NULL
                                    OR visible.final_document_version <>
                                        p.final_document_version
                                    OR NULLIF(TRIM(visible.final_pdf_url), '') IS NULL
                                    OR visible.final_pdf_hash NOT REGEXP
                                        '^[0-9a-fA-F]{64}$'
                                    OR visible.final_content_hash NOT REGEXP
                                        '^[0-9a-fA-F]{64}$'))
                  THEN 'COMPLETE' ELSE 'INCOMPLETE' END AS final_candidate_state,
             CASE WHEN EXISTS (
                        SELECT 1 FROM oa_sign_package_document sibling
                         WHERE sibling.package_id = p.package_id
                           AND UPPER(TRIM(COALESCE(
                               sibling.employee_visible, 'N'))) = 'Y'
                           AND sibling.template_type IN
                               ('ONBOARD_HANDBOOK', 'ONBOARD_HANDBOOK_RECEIPT'))
                  THEN 'INCLUDED' ELSE 'ABSENT' END
                 AS employee_visible_handbook_state
        FROM oa_sign_package p
        JOIN oa_sign_package_document d
          ON d.package_id = p.package_id
         AND UPPER(TRIM(COALESCE(d.employee_visible, 'N'))) = 'Y'
         AND d.template_type = 'ONBOARD_LABOR_CONTRACT'
        LEFT JOIN oa_sign_plan_version_template pvt
          ON pvt.plan_version_id = p.plan_version_id
         AND pvt.template_type = d.template_type
       WHERE LOWER(TRIM(p.status)) NOT IN ('signed', 'voided', 'refused', 'expired')
  ) inventory
 GROUP BY inventory.package_status, inventory.signing_sequence,
          inventory.template_version, inventory.source_file_hash,
          inventory.document_policy_mode, inventory.signature_policy_mode,
          inventory.seal_policy_mode, inventory.signature_sequence_policy,
          inventory.seal_sequence_policy, inventory.evidence_state,
          inventory.final_confirmation_status, inventory.signature_evidence_state,
          inventory.final_candidate_state, inventory.employee_visible_handbook_state,
          continuation_state
 ORDER BY continuation_state, inventory.package_status, inventory.signing_sequence,
          inventory.template_version, inventory.source_file_hash,
          inventory.signature_policy_mode, inventory.seal_policy_mode;

-- This scalar repeats the same fail-closed lifecycle predicate so an automated wrapper can
-- require exactly zero unsupported packages without interpreting the grouped report.
SELECT COUNT(*) AS expected_0_unsupported_nonterminal_labor_packages
  FROM (
      SELECT checked.package_id,
             CASE WHEN checked.evidence_state = 'COMPLETE'
                       AND checked.employee_visible_handbook_state = 'INCLUDED'
                       AND checked.template_version = '20260721-v7'
                       AND checked.source_file_hash =
                           '1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558'
                       AND checked.document_policy_mode = 'SNAPSHOT_V1'
                       AND (
                           (checked.signing_sequence = 'COMPANY_FIRST'
                            AND checked.package_status IN
                                ('draft', 'pending_sign', 'part_viewed')
                            AND checked.final_confirmation_status = 'PREPARED_NOT_SENT'
                            AND checked.final_candidate_state = 'COMPLETE'
                            AND ((checked.signature_policy_mode =
                                       'APPENDED_CONFIRMATION_PAGE'
                                  AND checked.seal_policy_mode =
                                       'APPENDED_CONFIRMATION_PAGE')
                                 OR (checked.signature_policy_mode = 'PLACED_MULTI'
                                  AND checked.seal_policy_mode = 'PLACED_MULTI'
                                  AND checked.signature_sequence_policy =
                                       'COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT'
                                  AND checked.seal_sequence_policy =
                                       checked.signature_sequence_policy)))
                           OR
                           (checked.signing_sequence = 'SIGNATURE_FIRST'
                            AND checked.signature_policy_mode = 'PLACED_MULTI'
                            AND checked.seal_policy_mode = 'PLACED_MULTI'
                            AND checked.signature_sequence_policy =
                                'SIGNATURE_FIRST_BODY_PLACEMENT'
                            AND checked.seal_sequence_policy =
                                checked.signature_sequence_policy
                            AND ((checked.package_status IN
                                      ('draft', 'pending_sign', 'part_viewed')
                                  AND checked.final_confirmation_status = '<MISSING>'
                                  AND checked.signature_evidence_state = 'INCOMPLETE'
                                  AND checked.final_candidate_state = 'INCOMPLETE')
                                 OR (checked.package_status = 'pending_company'
                                  AND checked.signature_evidence_state = 'COMPLETE'
                                  AND ((checked.final_confirmation_status = 'WAITING_COMPANY'
                                        AND checked.final_candidate_state = 'INCOMPLETE')
                                       OR (checked.final_confirmation_status =
                                               'PREPARED_NOT_SENT'
                                        AND checked.final_candidate_state = 'COMPLETE')))
                                 OR (checked.package_status = 'pending_final_confirm'
                                  AND checked.signature_evidence_state = 'COMPLETE'
                                  AND checked.final_confirmation_status = 'PENDING'
                                  AND checked.final_candidate_state = 'COMPLETE'))))
                  THEN 'SUPPORTED' ELSE 'UNSUPPORTED' END AS continuation_state
        FROM (
            SELECT p.package_id, LOWER(TRIM(p.status)) AS package_status,
                   UPPER(TRIM(COALESCE(p.signing_sequence, '<MISSING>')))
                       AS signing_sequence,
                   COALESCE(d.template_version_snapshot, '<MISSING>') AS template_version,
                   LOWER(COALESCE(pvt.source_file_hash, '<MISSING>')) AS source_file_hash,
                   UPPER(TRIM(COALESCE(d.document_policy_mode, '<MISSING>')))
                       AS document_policy_mode,
                   UPPER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
                       d.signature_position_json, '$.mode')), '<MISSING>'))
                       AS signature_policy_mode,
                   UPPER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
                       d.company_seal_position_json, '$.mode')), '<MISSING>'))
                       AS seal_policy_mode,
                   UPPER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
                       d.signature_position_json, '$.signingSequencePolicy')), '<MISSING>'))
                       AS signature_sequence_policy,
                   UPPER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
                       d.company_seal_position_json, '$.signingSequencePolicy')), '<MISSING>'))
                       AS seal_sequence_policy,
                   UPPER(COALESCE(NULLIF(TRIM(p.final_confirmation_status), ''),
                       '<MISSING>'))
                       AS final_confirmation_status,
                   CASE WHEN NULLIF(TRIM(d.source_file_url_snapshot), '') IS NOT NULL
                              AND NULLIF(TRIM(d.review_pdf_url), '') IS NOT NULL
                              AND d.review_pdf_hash REGEXP '^[0-9a-fA-F]{64}$'
                              AND UPPER(TRIM(COALESCE(
                                  d.employee_sign_required, 'N'))) = 'Y'
                              AND UPPER(TRIM(COALESCE(
                                  d.company_seal_required, 'N'))) = 'Y'
                        THEN 'COMPLETE' ELSE 'INCOMPLETE' END AS evidence_state,
                   CASE WHEN (NULLIF(TRIM(p.signature_sample_file_url), '') IS NOT NULL
                                     AND p.signature_sample_hash REGEXP '^[0-9a-fA-F]{64}$'
                                     AND p.signature_sample_time IS NOT NULL)
                                  OR (COALESCE(p.initial_signed_time, p.signed_time) IS NOT NULL
                                     AND EXISTS (
                                         SELECT 1 FROM oa_sign_package_document required_doc
                                          WHERE required_doc.package_id = p.package_id
                                            AND UPPER(TRIM(COALESCE(
                                                required_doc.employee_visible, 'N'))) = 'Y'
                                            AND UPPER(TRIM(COALESCE(
                                                required_doc.employee_sign_required, 'N'))) = 'Y')
                                     AND NOT EXISTS (
                                         SELECT 1 FROM oa_sign_package_document required_doc
                                          WHERE required_doc.package_id = p.package_id
                                            AND UPPER(TRIM(COALESCE(
                                                required_doc.employee_visible, 'N'))) = 'Y'
                                            AND UPPER(TRIM(COALESCE(
                                                required_doc.employee_sign_required, 'N'))) = 'Y'
                                            AND (NULLIF(TRIM(
                                                required_doc.signature_file_url), '') IS NULL
                                                 OR required_doc.signature_hash NOT REGEXP
                                                     '^[0-9a-fA-F]{64}$')))
                        THEN 'COMPLETE' ELSE 'INCOMPLETE' END
                       AS signature_evidence_state,
                   CASE WHEN p.legal_entity_id_snapshot IS NOT NULL
                              AND NULLIF(TRIM(p.legal_entity_name_snapshot), '') IS NOT NULL
                              AND NULLIF(TRIM(p.legal_representative_snapshot), '') IS NOT NULL
                              AND p.company_frozen_time IS NOT NULL
                              AND NULLIF(TRIM(p.seal_image_url_snapshot), '') IS NOT NULL
                              AND p.seal_image_hash_snapshot REGEXP '^[0-9a-fA-F]{64}$'
                              AND NULLIF(TRIM(p.final_document_version), '') IS NOT NULL
                              AND p.final_document_root_hash REGEXP '^[0-9a-fA-F]{64}$'
                              AND p.final_generated_time IS NOT NULL
                              AND EXISTS (
                                  SELECT 1 FROM oa_sign_package_document visible
                                   WHERE visible.package_id = p.package_id
                                     AND UPPER(TRIM(COALESCE(
                                         visible.employee_visible, 'N'))) = 'Y')
                              AND NOT EXISTS (
                                  SELECT 1 FROM oa_sign_package_document visible
                                   WHERE visible.package_id = p.package_id
                                     AND UPPER(TRIM(COALESCE(
                                         visible.employee_visible, 'N'))) = 'Y'
                                     AND (visible.final_document_version IS NULL
                                          OR visible.final_document_version <>
                                              p.final_document_version
                                          OR NULLIF(TRIM(visible.final_pdf_url), '') IS NULL
                                          OR visible.final_pdf_hash NOT REGEXP
                                              '^[0-9a-fA-F]{64}$'
                                          OR visible.final_content_hash NOT REGEXP
                                              '^[0-9a-fA-F]{64}$'))
                        THEN 'COMPLETE' ELSE 'INCOMPLETE' END AS final_candidate_state,
                   CASE WHEN EXISTS (
                              SELECT 1 FROM oa_sign_package_document sibling
                               WHERE sibling.package_id = p.package_id
                                 AND UPPER(TRIM(COALESCE(
                                     sibling.employee_visible, 'N'))) = 'Y'
                                 AND sibling.template_type IN
                                     ('ONBOARD_HANDBOOK', 'ONBOARD_HANDBOOK_RECEIPT'))
                        THEN 'INCLUDED' ELSE 'ABSENT' END
                       AS employee_visible_handbook_state
              FROM oa_sign_package p
              JOIN oa_sign_package_document d
                ON d.package_id = p.package_id
               AND UPPER(TRIM(COALESCE(d.employee_visible, 'N'))) = 'Y'
               AND d.template_type = 'ONBOARD_LABOR_CONTRACT'
              LEFT JOIN oa_sign_plan_version_template pvt
                ON pvt.plan_version_id = p.plan_version_id
               AND pvt.template_type = d.template_type
             WHERE LOWER(TRIM(p.status)) NOT IN
                   ('signed', 'voided', 'refused', 'expired')
        ) checked
  ) inventory
 WHERE inventory.continuation_state = 'UNSUPPORTED';

SELECT plan_id, version_id, version_no, version_hash, publish_status, matching_status
  FROM oa_sign_plan_version
 WHERE plan_id IN (49, 36)
 ORDER BY plan_id, version_no, version_id;

-- PII-free historical inventory only. This aggregation is not an export approval: the mandatory
-- Java release verifier must subsequently execute the exact formal export dry-run for every row
-- represented here, verify actual files/policies/anchors, clean derivatives and compare DB hashes.
SELECT historical.template_version, historical.source_file_hash,
       historical.policy_mode, historical.employee_visibility_state,
       historical.representative_state, historical.handbook_state,
       historical.root_state, COUNT(*) AS document_count
  FROM (
      SELECT COALESCE(d.template_version_snapshot, '<MISSING>') AS template_version,
             COALESCE(pvt.source_file_hash, '<MISSING>') AS source_file_hash,
             COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
                 d.signature_position_json, '$.mode')),
                 d.document_policy_mode, '<MISSING>') AS policy_mode,
             CASE WHEN UPPER(TRIM(COALESCE(d.employee_visible, 'N'))) = 'Y'
                  THEN 'EMPLOYEE_VISIBLE' ELSE 'HIDDEN' END
                 AS employee_visibility_state,
             CASE WHEN p.legal_representative_snapshot IS NULL
                        OR TRIM(p.legal_representative_snapshot) = ''
                  THEN 'REQUIRES_AUDITED_REPAIR' ELSE 'FROZEN' END
                 AS representative_state,
             CASE WHEN EXISTS (
                        SELECT 1 FROM oa_sign_package_document sibling
                         WHERE sibling.package_id = p.package_id
                           AND UPPER(TRIM(COALESCE(
                               sibling.employee_visible, 'N'))) = 'Y'
                           AND sibling.template_type IN
                               ('ONBOARD_HANDBOOK', 'ONBOARD_HANDBOOK_RECEIPT'))
                  THEN 'INCLUDED' ELSE 'ABSENT' END AS handbook_state,
             CASE WHEN UPPER(TRIM(COALESCE(d.employee_visible, 'N'))) = 'Y'
                        AND p.final_document_root_hash REGEXP '^[0-9a-fA-F]{64}$'
                        AND p.final_archive_root_hash REGEXP '^[0-9a-fA-F]{64}$'
                        AND EXISTS (
                            SELECT 1 FROM oa_sign_final_confirmation c
                             WHERE c.package_id = p.package_id
                               AND c.employee_id = p.employee_id
                               AND c.final_document_version = p.final_document_version
                               AND LOWER(c.document_root_hash) =
                                   LOWER(p.final_document_root_hash)
                               AND UNIX_TIMESTAMP(c.confirmed_time) =
                                   UNIX_TIMESTAMP(p.final_confirmed_time))
                  THEN 'ROOT_AND_CONFIRMATION_PRESENT' ELSE 'INCOMPLETE' END
                 AS root_state
        FROM oa_sign_package p
        JOIN oa_sign_package_document d ON d.package_id = p.package_id
        LEFT JOIN oa_sign_plan_version_template pvt
          ON pvt.plan_version_id = p.plan_version_id
         AND pvt.template_type = d.template_type
       WHERE LOWER(TRIM(p.status)) = 'signed'
         AND UPPER(TRIM(COALESCE(p.final_confirmation_status, ''))) = 'CONFIRMED'
         AND d.template_type = 'ONBOARD_LABOR_CONTRACT'
  ) historical
 GROUP BY template_version, source_file_hash, policy_mode, employee_visibility_state,
          representative_state, handbook_state, root_state
 ORDER BY template_version, source_file_hash, policy_mode,
          employee_visibility_state, representative_state, handbook_state, root_state;
