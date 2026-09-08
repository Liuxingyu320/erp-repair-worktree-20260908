-- Conditional matching rollback for the REV-06 immutable v7 publication.
-- This script never deletes the new versions or changes any package/document/signing fact.
-- It fails closed once any package references a candidate version. Code and template assets
-- must be rolled back in the same approved maintenance window before this script is used.

DROP PROCEDURE IF EXISTS rollback_oa_sign_labor_v7_20260809;
DELIMITER $$
CREATE PROCEDURE rollback_oa_sign_labor_v7_20260809()
BEGIN
    DECLARE v_candidate_49 bigint DEFAULT NULL;
    DECLARE v_candidate_36 bigint DEFAULT NULL;
    DECLARE v_candidate_49_count bigint DEFAULT 0;
    DECLARE v_candidate_36_count bigint DEFAULT 0;
    DECLARE v_count bigint DEFAULT 0;
    DECLARE v_source_enabled bigint DEFAULT 0;
    DECLARE v_candidate_enabled bigint DEFAULT 0;
    DECLARE v_other_enabled bigint DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

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
    IF v_candidate_49_count <> 1 OR v_candidate_36_count <> 1
            OR v_candidate_49 IS NULL OR v_candidate_36 IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 rollback candidates are missing or non-unique';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_plan_version
     WHERE (version_id = 1000000002 AND plan_id = 49
              AND version_hash = 'f79d4fb655ae6b4ea186fb13609c0e06cd3bd89a40f0abbac7e91cd4243db0b7'
              AND publish_status = 'PUBLISHED')
        OR (version_id = 1000000005 AND plan_id = 36
              AND version_hash = 'd115f4088e103587d395a73e09fc4cef93550e19b2b91dc870a6abc3f231e605'
              AND publish_status = 'PUBLISHED')
     FOR UPDATE;
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 rollback source versions are missing or drifted';
    END IF;

    SELECT COUNT(*) INTO v_source_enabled
      FROM oa_sign_plan_version
     WHERE version_id IN (1000000002, 1000000005)
       AND publish_status = 'PUBLISHED'
       AND matching_status = 'ENABLED';
    SELECT COUNT(*) INTO v_candidate_enabled
      FROM oa_sign_plan_version
     WHERE version_id IN (v_candidate_49, v_candidate_36)
       AND publish_status = 'PUBLISHED'
       AND matching_status = 'ENABLED';
    SELECT COUNT(*) INTO v_other_enabled
      FROM oa_sign_plan_version
     WHERE plan_id IN (49, 36)
       AND publish_status = 'PUBLISHED'
       AND matching_status = 'ENABLED'
       AND version_id NOT IN (
           1000000002, 1000000005, v_candidate_49, v_candidate_36);
    IF v_other_enabled <> 0
            OR NOT ((v_source_enabled = 0 AND v_candidate_enabled = 2)
                    OR (v_source_enabled = 2 AND v_candidate_enabled = 0)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 rollback matching state is unsafe or ambiguous';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM oa_sign_package
     WHERE plan_version_id IN (v_candidate_49, v_candidate_36);
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 rollback blocked because candidate packages exist';
    END IF;

    UPDATE oa_sign_plan_version
       SET matching_status = 'DISABLED'
     WHERE version_id IN (v_candidate_49, v_candidate_36)
       AND publish_status = 'PUBLISHED';
    UPDATE oa_sign_plan_version
       SET matching_status = 'ENABLED'
     WHERE version_id IN (1000000002, 1000000005)
       AND publish_status = 'PUBLISHED';

    SELECT COUNT(*) INTO v_source_enabled
      FROM oa_sign_plan_version
     WHERE version_id IN (1000000002, 1000000005)
       AND publish_status = 'PUBLISHED'
       AND matching_status = 'ENABLED';
    SELECT COUNT(*) INTO v_candidate_enabled
      FROM oa_sign_plan_version
     WHERE version_id IN (v_candidate_49, v_candidate_36)
       AND publish_status = 'PUBLISHED'
       AND matching_status = 'ENABLED';
    SELECT COUNT(*) INTO v_other_enabled
      FROM oa_sign_plan_version
     WHERE plan_id IN (49, 36)
       AND publish_status = 'PUBLISHED'
       AND matching_status = 'ENABLED'
       AND version_id NOT IN (
           1000000002, 1000000005, v_candidate_49, v_candidate_36);
    IF v_source_enabled <> 2 OR v_candidate_enabled <> 0 OR v_other_enabled <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'REV06 rollback matching switch was incomplete';
    END IF;

    COMMIT;
END$$
DELIMITER ;

CALL rollback_oa_sign_labor_v7_20260809();
DROP PROCEDURE IF EXISTS rollback_oa_sign_labor_v7_20260809;
