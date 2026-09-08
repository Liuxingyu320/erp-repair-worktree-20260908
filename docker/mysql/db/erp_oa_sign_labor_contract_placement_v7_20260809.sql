-- Immutable publication of the audited exact-v7 labor-contract template for the two
-- still-active v4 plans. This migration never updates an existing published snapshot,
-- package, document, signature, seal, archive, event or hash.
--
-- Fail-closed prerequisites:
--   * source plan versions 1000000002 / 1000000005 remain the reviewed v4 snapshots;
--   * template 92 remains exact-v7 with the pinned source SHA-256;
--   * no non-terminal package/document still depends on either v4 source version;
--   * there is no third enabled matching version for either source plan.
--
-- On success a new immutable version is created per plan (if absent), its complete
-- template set is copied with only ONBOARD_LABOR_CONTRACT replaced by exact-v7, and
-- both matching switches are changed in one transaction. Re-running validates and
-- preserves the already-published result.

DROP PROCEDURE IF EXISTS publish_oa_sign_labor_v7_20260809;
DELIMITER $$
CREATE PROCEDURE publish_oa_sign_labor_v7_20260809()
BEGIN
    DECLARE v_v7_template_id bigint DEFAULT NULL;
    DECLARE v_candidate_49 bigint DEFAULT NULL;
    DECLARE v_candidate_36 bigint DEFAULT NULL;
    DECLARE v_candidate_49_count bigint DEFAULT 0;
    DECLARE v_candidate_36_count bigint DEFAULT 0;
    DECLARE v_next_49 int DEFAULT NULL;
    DECLARE v_next_36 int DEFAULT NULL;
    DECLARE v_count bigint DEFAULT 0;
    DECLARE v_source_enabled bigint DEFAULT 0;
    DECLARE v_candidate_enabled bigint DEFAULT 0;
    DECLARE v_other_enabled bigint DEFAULT 0;
    DECLARE v_already_applied tinyint DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_plan_version
     WHERE (version_id = 1000000002 AND plan_id = 49
              AND version_hash = 'f79d4fb655ae6b4ea186fb13609c0e06cd3bd89a40f0abbac7e91cd4243db0b7')
        OR (version_id = 1000000005 AND plan_id = 36
              AND version_hash = 'd115f4088e103587d395a73e09fc4cef93550e19b2b91dc870a6abc3f231e605')
     FOR UPDATE;
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 source plan snapshots drifted';
    END IF;

    SELECT COUNT(*), MAX(template_id) INTO v_count, v_v7_template_id
      FROM oa_sign_template
     WHERE template_id = 92
       AND template_type = 'ONBOARD_LABOR_CONTRACT'
       AND template_version = '20260721-v7'
       AND file_hash = '1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558'
       AND status = '0'
     FOR UPDATE;
    IF v_count <> 1 OR v_v7_template_id <> 92 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 exact-v7 template is missing or drifted';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_plan_version_template
     WHERE (plan_version_id = 1000000002
              AND template_type = 'ONBOARD_LABOR_CONTRACT'
              AND template_id = 81
              AND template_version = '20260718-v4-draft'
              AND source_file_hash = 'fdbe2df12eb3704370126e09f2c26fd7cd09e0a06db1e0fe960f42f9513f2118')
        OR (plan_version_id = 1000000005
              AND template_type = 'ONBOARD_LABOR_CONTRACT'
              AND template_id = 81
              AND template_version = '20260718-v4-draft'
              AND source_file_hash = 'fdbe2df12eb3704370126e09f2c26fd7cd09e0a06db1e0fe960f42f9513f2118')
     FOR UPDATE;
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 v4 bindings are missing or drifted';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_plan_version_template
     WHERE plan_version_id IN (1000000002, 1000000005);
    IF v_count <> 8 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 source template sets are incomplete';
    END IF;

    SELECT SUM(plan_id = 49),
           MAX(CASE WHEN plan_id = 49 THEN version_id END),
           SUM(plan_id = 36),
           MAX(CASE WHEN plan_id = 36 THEN version_id END)
      INTO v_candidate_49_count, v_candidate_49,
           v_candidate_36_count, v_candidate_36
      FROM oa_sign_plan_version
     WHERE (plan_id = 49
              AND version_hash = 'bd07d71829535d12a26f5a4637a5e4f73fe49e0ad1340287fa92959cd0b85396')
        OR (plan_id = 36
              AND version_hash = 'eda0fcd21b70b302ca1edd43facb0d5c4b03e7a593b9a5f8a82d5147f0c87d40')
     FOR UPDATE;
    SET v_candidate_49_count = COALESCE(v_candidate_49_count, 0);
    SET v_candidate_36_count = COALESCE(v_candidate_36_count, 0);
    IF v_candidate_49_count > 1 OR v_candidate_36_count > 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 candidate plan version hash is not unique';
    END IF;

    SELECT COUNT(*) INTO v_source_enabled
      FROM oa_sign_plan_version
     WHERE version_id IN (1000000002, 1000000005)
       AND publish_status = 'PUBLISHED'
       AND matching_status = 'ENABLED';
    SELECT COUNT(*) INTO v_candidate_enabled
      FROM oa_sign_plan_version
     WHERE ((plan_id = 49
              AND version_hash = 'bd07d71829535d12a26f5a4637a5e4f73fe49e0ad1340287fa92959cd0b85396')
         OR (plan_id = 36
              AND version_hash = 'eda0fcd21b70b302ca1edd43facb0d5c4b03e7a593b9a5f8a82d5147f0c87d40'))
       AND publish_status = 'PUBLISHED'
       AND matching_status = 'ENABLED';
    SELECT COUNT(*) INTO v_other_enabled
      FROM oa_sign_plan_version
     WHERE plan_id IN (49, 36)
       AND publish_status = 'PUBLISHED'
       AND matching_status = 'ENABLED'
       AND version_id NOT IN (1000000002, 1000000005)
       AND version_hash NOT IN (
           'bd07d71829535d12a26f5a4637a5e4f73fe49e0ad1340287fa92959cd0b85396',
           'eda0fcd21b70b302ca1edd43facb0d5c4b03e7a593b9a5f8a82d5147f0c87d40');
    IF v_other_enabled <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 unexpected enabled plan version exists';
    END IF;
    SET v_already_applied = IF(v_source_enabled = 0 AND v_candidate_enabled = 2, 1, 0);
    IF v_already_applied = 0 AND NOT (v_source_enabled = 2 AND v_candidate_enabled = 0) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 matching state is partially switched';
    END IF;

    -- Reviewed future-path inventory is a closed set at this release boundary. Every one of
    -- plans 35/36/49/66 must have exactly one enabled labor binding; missing plans, duplicate
    -- bindings, or newly introduced plans require a new review instead of being ignored.
    SELECT COUNT(*) INTO v_count
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
      ) missing_or_ambiguous_reviewed_plans;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 reviewed enabled labor plan set is incomplete or ambiguous';
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM oa_sign_plan_version pv
      JOIN oa_sign_plan_version_template pvt
        ON pvt.plan_version_id = pv.version_id
       AND pvt.template_type = 'ONBOARD_LABOR_CONTRACT'
     WHERE pv.publish_status = 'PUBLISHED'
       AND pv.matching_status = 'ENABLED';
    IF v_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 enabled labor binding count must equal four';
    END IF;
    SELECT COUNT(*) INTO v_count
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
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 active labor plan handbook set is incomplete or ambiguous';
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM (
          SELECT pv.plan_id
            FROM oa_sign_plan_version pv
            JOIN oa_sign_plan_version_template item
              ON item.plan_version_id = pv.version_id
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
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 active plan required document set is invalid';
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM sys_legal_entity
     WHERE status = '0';
    IF v_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 no active legal entity is available for signing';
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM sys_legal_entity
     WHERE status = '0'
       AND (NULLIF(TRIM(legal_entity_name), '') IS NULL
            OR NULLIF(TRIM(legal_representative), '') IS NULL
            OR NULLIF(TRIM(registered_address), '') IS NULL);
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 active legal entity evidence is incomplete';
    END IF;

    -- Every binding in that exact set must use exact-v7 or one of the two exact v4 sources
    -- that this transaction replaces.
    SELECT COUNT(*) INTO v_count
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
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 unknown enabled labor plan/template binding exists';
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM (
          SELECT pv.plan_id
            FROM oa_sign_plan_version pv
            JOIN oa_sign_plan_version_template pvt
              ON pvt.plan_version_id = pv.version_id
             AND pvt.template_type = 'ONBOARD_LABOR_CONTRACT'
           WHERE pv.publish_status = 'PUBLISHED'
             AND pv.matching_status = 'ENABLED'
           GROUP BY pv.plan_id
          HAVING COUNT(*) <> 1
      ) ambiguous_enabled_labor_plans;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 enabled labor plan binding is ambiguous';
    END IF;

    -- This assertion deliberately also runs after an already-applied publication. A delayed
    -- transaction or an explicit old-version package must never be hidden by idempotent replay.
    SELECT COUNT(DISTINCT p.package_id) INTO v_count
      FROM oa_sign_package p
      LEFT JOIN oa_sign_package_document d ON d.package_id = p.package_id
     WHERE (p.plan_version_id IN (1000000002, 1000000005)
            OR (d.template_type = 'ONBOARD_LABOR_CONTRACT'
                AND d.template_version_snapshot = '20260718-v4-draft'))
       AND LOWER(TRIM(p.status)) NOT IN ('signed', 'voided', 'refused', 'expired');
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 non-terminal v4 packages require void-and-restart review';
    END IF;

    IF v_candidate_49 IS NULL THEN
        SELECT COALESCE(MAX(version_no), 0) + 1 INTO v_next_49
          FROM oa_sign_plan_version WHERE plan_id = 49;
        INSERT INTO oa_sign_plan_version (
            plan_id, plan_name, version_no, scenario, shop_dept_id,
            legal_entity_id, legal_entity_name, rule_json, default_values_json,
            sign_deadline_days, reminder_policy_json, auto_send_condition_json,
            publish_status, matching_status, published_by_user_id, published_by,
            published_time, version_hash, create_time
        )
        SELECT plan_id, plan_name, v_next_49, scenario, shop_dept_id,
               legal_entity_id, legal_entity_name, rule_json, default_values_json,
               sign_deadline_days, reminder_policy_json, auto_send_condition_json,
               'PUBLISHED', 'DISABLED', published_by_user_id,
               LEFT(CONCAT(published_by, '/REV06'), 64), NOW(),
               'bd07d71829535d12a26f5a4637a5e4f73fe49e0ad1340287fa92959cd0b85396', NOW()
          FROM oa_sign_plan_version WHERE version_id = 1000000002;
        SET v_candidate_49 = LAST_INSERT_ID();
    END IF;

    IF v_candidate_36 IS NULL THEN
        SELECT COALESCE(MAX(version_no), 0) + 1 INTO v_next_36
          FROM oa_sign_plan_version WHERE plan_id = 36;
        INSERT INTO oa_sign_plan_version (
            plan_id, plan_name, version_no, scenario, shop_dept_id,
            legal_entity_id, legal_entity_name, rule_json, default_values_json,
            sign_deadline_days, reminder_policy_json, auto_send_condition_json,
            publish_status, matching_status, published_by_user_id, published_by,
            published_time, version_hash, create_time
        )
        SELECT plan_id, plan_name, v_next_36, scenario, shop_dept_id,
               legal_entity_id, legal_entity_name, rule_json, default_values_json,
               sign_deadline_days, reminder_policy_json, auto_send_condition_json,
               'PUBLISHED', 'DISABLED', published_by_user_id,
               LEFT(CONCAT(published_by, '/REV06'), 64), NOW(),
               'eda0fcd21b70b302ca1edd43facb0d5c4b03e7a593b9a5f8a82d5147f0c87d40', NOW()
          FROM oa_sign_plan_version WHERE version_id = 1000000005;
        SET v_candidate_36 = LAST_INSERT_ID();
    END IF;

    -- A matching version_hash is not sufficient evidence. Every business snapshot field must
    -- equal its reviewed source; only version number and publication audit fields may differ.
    SELECT COUNT(*) INTO v_count
      FROM (
          SELECT 1000000002 AS source_id, v_candidate_49 AS candidate_id,
                 'bd07d71829535d12a26f5a4637a5e4f73fe49e0ad1340287fa92959cd0b85396'
                     AS expected_hash
          UNION ALL
          SELECT 1000000005, v_candidate_36,
                 'eda0fcd21b70b302ca1edd43facb0d5c4b03e7a593b9a5f8a82d5147f0c87d40'
      ) mapping
      JOIN oa_sign_plan_version source ON source.version_id = mapping.source_id
      LEFT JOIN oa_sign_plan_version target ON target.version_id = mapping.candidate_id
     WHERE target.version_id IS NULL
        OR NOT (target.plan_id <=> source.plan_id)
        OR NOT (target.plan_name <=> source.plan_name)
        OR NOT (target.scenario <=> source.scenario)
        OR NOT (target.shop_dept_id <=> source.shop_dept_id)
        OR NOT (target.legal_entity_id <=> source.legal_entity_id)
        OR NOT (target.legal_entity_name <=> source.legal_entity_name)
        OR NOT (target.rule_json <=> source.rule_json)
        OR NOT (target.default_values_json <=> source.default_values_json)
        OR NOT (target.sign_deadline_days <=> source.sign_deadline_days)
        OR NOT (target.reminder_policy_json <=> source.reminder_policy_json)
        OR NOT (target.auto_send_condition_json <=> source.auto_send_condition_json)
        OR NOT (target.published_by_user_id <=> source.published_by_user_id)
        OR target.version_no <= source.version_no
        OR target.publish_status <> 'PUBLISHED'
        OR target.matching_status <> IF(v_already_applied = 1, 'ENABLED', 'DISABLED')
        OR target.version_hash <> mapping.expected_hash;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 candidate plan snapshot fields are inconsistent';
    END IF;

    INSERT INTO oa_sign_plan_version_template (
        plan_version_id, template_id, template_version, template_type, template_name,
        source_file_url, source_file_hash, required_placeholders, sort_order,
        employee_visible, read_confirmation_required, employee_sign_required,
        signature_position_json, company_seal_position_json, company_seal_required,
        match_condition_json, create_time
    )
    SELECT candidates.candidate_id,
           CASE WHEN source.template_type = 'ONBOARD_LABOR_CONTRACT'
                THEN exact_v7.template_id ELSE source.template_id END,
           CASE WHEN source.template_type = 'ONBOARD_LABOR_CONTRACT'
                THEN exact_v7.template_version ELSE source.template_version END,
           source.template_type,
           CASE WHEN source.template_type = 'ONBOARD_LABOR_CONTRACT'
                THEN exact_v7.template_name ELSE source.template_name END,
           CASE WHEN source.template_type = 'ONBOARD_LABOR_CONTRACT'
                THEN exact_v7.file_url ELSE source.source_file_url END,
           CASE WHEN source.template_type = 'ONBOARD_LABOR_CONTRACT'
                THEN exact_v7.file_hash ELSE source.source_file_hash END,
           CASE WHEN source.template_type = 'ONBOARD_LABOR_CONTRACT'
                THEN exact_v7.required_placeholders ELSE source.required_placeholders END,
           source.sort_order,
           CASE WHEN source.template_type = 'ONBOARD_LABOR_CONTRACT'
                THEN exact_v7.employee_visible ELSE source.employee_visible END,
           CASE WHEN source.template_type = 'ONBOARD_LABOR_CONTRACT'
                THEN exact_v7.read_confirmation_required
                ELSE source.read_confirmation_required END,
           CASE WHEN source.template_type = 'ONBOARD_LABOR_CONTRACT'
                THEN exact_v7.employee_sign_required ELSE source.employee_sign_required END,
           CASE WHEN source.template_type = 'ONBOARD_LABOR_CONTRACT'
                THEN exact_v7.signature_position_json ELSE source.signature_position_json END,
           CASE WHEN source.template_type = 'ONBOARD_LABOR_CONTRACT'
                THEN exact_v7.company_seal_position_json
                ELSE source.company_seal_position_json END,
           CASE WHEN source.template_type = 'ONBOARD_LABOR_CONTRACT'
                THEN exact_v7.company_seal_required ELSE source.company_seal_required END,
           source.match_condition_json, NOW()
      FROM (
          SELECT 1000000002 AS source_id, v_candidate_49 AS candidate_id
          UNION ALL
          SELECT 1000000005, v_candidate_36
      ) candidates
      JOIN oa_sign_plan_version_template source
        ON source.plan_version_id = candidates.source_id
      JOIN oa_sign_template exact_v7 ON exact_v7.template_id = v_v7_template_id
     WHERE NOT EXISTS (
          SELECT 1 FROM oa_sign_plan_version_template existing
           WHERE existing.plan_version_id = candidates.candidate_id);

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_plan_version_template
     WHERE plan_version_id IN (v_candidate_49, v_candidate_36);
    IF v_count <> 8 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 candidate template sets are incomplete';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM (
          SELECT 1000000002 AS source_id, v_candidate_49 AS candidate_id
          UNION ALL
          SELECT 1000000005, v_candidate_36
      ) candidates
      JOIN oa_sign_plan_version_template source
        ON source.plan_version_id = candidates.source_id
      LEFT JOIN oa_sign_plan_version_template target
        ON target.plan_version_id = candidates.candidate_id
       AND target.template_type = source.template_type
      LEFT JOIN oa_sign_template exact_v7 ON exact_v7.template_id = v_v7_template_id
     WHERE target.id IS NULL
        OR NOT (target.sort_order <=> source.sort_order)
        OR NOT (target.match_condition_json <=> source.match_condition_json)
        OR (source.template_type <> 'ONBOARD_LABOR_CONTRACT' AND (
               NOT (target.template_id <=> source.template_id)
            OR NOT (target.template_version <=> source.template_version)
            OR NOT (target.template_name <=> source.template_name)
            OR NOT (target.source_file_url <=> source.source_file_url)
            OR NOT (target.source_file_hash <=> source.source_file_hash)
            OR NOT (target.required_placeholders <=> source.required_placeholders)
            OR NOT (target.employee_visible <=> source.employee_visible)
            OR NOT (target.read_confirmation_required <=> source.read_confirmation_required)
            OR NOT (target.employee_sign_required <=> source.employee_sign_required)
            OR NOT (target.signature_position_json <=> source.signature_position_json)
            OR NOT (target.company_seal_position_json <=> source.company_seal_position_json)
            OR NOT (target.company_seal_required <=> source.company_seal_required)))
        OR (source.template_type = 'ONBOARD_LABOR_CONTRACT' AND (
               NOT (target.template_id <=> exact_v7.template_id)
            OR NOT (target.template_version <=> exact_v7.template_version)
            OR NOT (target.template_name <=> exact_v7.template_name)
            OR NOT (target.source_file_url <=> exact_v7.file_url)
            OR NOT (target.source_file_hash <=> exact_v7.file_hash)
            OR NOT (target.required_placeholders <=> exact_v7.required_placeholders)
            OR NOT (target.employee_visible <=> exact_v7.employee_visible)
            OR NOT (target.read_confirmation_required <=> exact_v7.read_confirmation_required)
            OR NOT (target.employee_sign_required <=> exact_v7.employee_sign_required)
            OR NOT (target.signature_position_json <=> exact_v7.signature_position_json)
            OR NOT (target.company_seal_position_json <=> exact_v7.company_seal_position_json)
            OR NOT (target.company_seal_required <=> exact_v7.company_seal_required)));
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 candidate template snapshots are inconsistent';
    END IF;

    UPDATE oa_sign_plan_version
       SET matching_status = 'DISABLED'
     WHERE version_id IN (1000000002, 1000000005)
       AND publish_status = 'PUBLISHED';
    UPDATE oa_sign_plan_version
       SET matching_status = 'ENABLED'
     WHERE version_id IN (v_candidate_49, v_candidate_36)
       AND publish_status = 'PUBLISHED';

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_plan_version
     WHERE version_id IN (v_candidate_49, v_candidate_36)
       AND matching_status = 'ENABLED'
       AND publish_status = 'PUBLISHED';
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 candidate activation was incomplete';
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM oa_sign_plan_version
     WHERE version_id IN (1000000002, 1000000005)
       AND matching_status = 'ENABLED';
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 v4 deactivation was incomplete';
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM oa_sign_plan_version pv
      JOIN oa_sign_plan_version_template pvt
        ON pvt.plan_version_id = pv.version_id
       AND pvt.template_type = 'ONBOARD_LABOR_CONTRACT'
     WHERE pv.publish_status = 'PUBLISHED'
       AND pv.matching_status = 'ENABLED'
       AND (pv.plan_id NOT IN (35, 36, 49, 66)
            OR pvt.template_id <> 92
            OR pvt.template_version <> '20260721-v7'
            OR pvt.source_file_hash <>
                '1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558');
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 post-switch enabled labor binding is not exact-v7';
    END IF;
    SELECT COUNT(*) INTO v_count
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
      ) post_missing_or_ambiguous_reviewed_plans;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 post-switch reviewed labor plan set is incomplete';
    END IF;
    SELECT COUNT(*) INTO v_count
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
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 post-switch active labor plan handbook set is invalid';
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM (
          SELECT pv.plan_id
            FROM oa_sign_plan_version pv
            JOIN oa_sign_plan_version_template item
              ON item.plan_version_id = pv.version_id
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
      ) post_invalid_reviewed_template_sets;
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 post-switch active plan required document set is invalid';
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM sys_legal_entity
     WHERE status = '0';
    IF v_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 post-switch active legal entity set is empty';
    END IF;
    SELECT COUNT(*) INTO v_count
      FROM sys_legal_entity
     WHERE status = '0'
       AND (NULLIF(TRIM(legal_entity_name), '') IS NULL
            OR NULLIF(TRIM(legal_representative), '') IS NULL
            OR NULLIF(TRIM(registered_address), '') IS NULL);
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 post-switch active legal entity evidence is incomplete';
    END IF;

    COMMIT;
END$$
DELIMITER ;

CALL publish_oa_sign_labor_v7_20260809();
DROP PROCEDURE IF EXISTS publish_oa_sign_labor_v7_20260809;
