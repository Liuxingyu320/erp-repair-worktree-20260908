-- Forward-only company matching facts and mandatory social-type/salary-version mapping.
-- Historical signed packages, published version snapshots and generated files are immutable.

DROP PROCEDURE IF EXISTS add_oa_sign_company_match_column_20260720;
DELIMITER $$
CREATE PROCEDURE add_oa_sign_company_match_column_20260720(
    IN p_column_name varchar(64), IN p_clause text)
BEGIN
    DECLARE v_count bigint DEFAULT 0;
    SELECT COUNT(*) INTO v_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND BINARY TABLE_NAME = BINARY 'oa_sign_onboard_import_row'
       AND BINARY COLUMN_NAME = BINARY p_column_name;
    IF v_count = 0 THEN
        SET @oa_sign_company_match_ddl = CONCAT(
            'ALTER TABLE oa_sign_onboard_import_row ', p_clause);
        PREPARE oa_sign_company_match_stmt FROM @oa_sign_company_match_ddl;
        EXECUTE oa_sign_company_match_stmt;
        DEALLOCATE PREPARE oa_sign_company_match_stmt;
    END IF;
END$$
DELIMITER ;

CALL add_oa_sign_company_match_column_20260720(
    'matched_legal_entity_id',
    'ADD COLUMN matched_legal_entity_id bigint DEFAULT NULL COMMENT ''Excel/HR确认的法律主体ID'' AFTER plan_version_hash');
CALL add_oa_sign_company_match_column_20260720(
    'company_match_mode',
    'ADD COLUMN company_match_mode varchar(48) DEFAULT NULL COMMENT ''EXCEL_AUTO/HR_CONFIRMED/HR_REQUIRED_*'' AFTER matched_legal_entity_id');
CALL add_oa_sign_company_match_column_20260720(
    'company_match_score',
    'ADD COLUMN company_match_score decimal(6,4) DEFAULT NULL COMMENT ''第一名组合匹配分'' AFTER company_match_mode');
CALL add_oa_sign_company_match_column_20260720(
    'company_second_score',
    'ADD COLUMN company_second_score decimal(6,4) DEFAULT NULL COMMENT ''第二名组合匹配分'' AFTER company_match_score');
CALL add_oa_sign_company_match_column_20260720(
    'company_match_policy_version',
    'ADD COLUMN company_match_policy_version varchar(32) DEFAULT NULL COMMENT ''匹配阈值和算法版本'' AFTER company_second_score');
CALL add_oa_sign_company_match_column_20260720(
    'company_master_version',
    'ADD COLUMN company_master_version bigint DEFAULT NULL COMMENT ''公司主数据CAS版本'' AFTER company_match_policy_version');
CALL add_oa_sign_company_match_column_20260720(
    'dept_legal_entity_id',
    'ADD COLUMN dept_legal_entity_id bigint DEFAULT NULL COMMENT ''员工部门绑定公司候选'' AFTER company_master_version');
CALL add_oa_sign_company_match_column_20260720(
    'company_dept_conflict',
    'ADD COLUMN company_dept_conflict tinyint(1) NOT NULL DEFAULT 0 COMMENT ''Excel公司是否覆盖部门候选'' AFTER dept_legal_entity_id');
CALL add_oa_sign_company_match_column_20260720(
    'company_candidates_json',
    'ADD COLUMN company_candidates_json json DEFAULT NULL COMMENT ''排序后的公司候选快照'' AFTER company_dept_conflict');
CALL add_oa_sign_company_match_column_20260720(
    'recommended_seal_id',
    'ADD COLUMN recommended_seal_id bigint DEFAULT NULL COMMENT ''唯一有效或HR确认的合同章'' AFTER company_candidates_json');
CALL add_oa_sign_company_match_column_20260720(
    'seal_recommendation_mode',
    'ADD COLUMN seal_recommendation_mode varchar(48) DEFAULT NULL COMMENT ''印章推荐或HR选择模式'' AFTER recommended_seal_id');
CALL add_oa_sign_company_match_column_20260720(
    'seal_candidates_json',
    'ADD COLUMN seal_candidates_json json DEFAULT NULL COMMENT ''有效合同章候选快照'' AFTER seal_recommendation_mode');

DROP PROCEDURE IF EXISTS add_oa_sign_company_match_column_20260720;

-- Reverse the 20260719 manual-choice template policy without changing template files.
UPDATE oa_sign_template
SET social_type = CASE salary_version
        WHEN 'A' THEN 'SOCIAL_UNINSURED'
        WHEN 'B' THEN 'SOCIAL_INSURED'
        ELSE social_type END,
    update_time = NOW(),
    remark = CONCAT_WS('；',
        NULLIF(TRIM(BOTH '；' FROM REPLACE(COALESCE(remark, ''),
            '20260719薪酬版本改为HR人工选择', '')), ''),
        '20260720按社保口径强制薪酬版本')
WHERE template_type = 'ONBOARD_SALARY_CONFIRM'
  AND salary_version IN ('A', 'B');

-- Prepare mutable source plans for the next normal publish operation. Published snapshots
-- are never rewritten; incompatible published versions are only closed for new matching.
UPDATE oa_sign_plan
SET salary_version = CASE
        WHEN UPPER(TRIM(social_type)) IN ('SOCIAL_INSURED', '有社保') THEN 'B'
        WHEN UPPER(TRIM(social_type)) IN ('SOCIAL_UNINSURED', '无社保') THEN 'A'
        ELSE salary_version END,
    rule_json = CASE WHEN JSON_VALID(rule_json) THEN JSON_SET(rule_json, '$.salaryVersion',
        CASE
            WHEN UPPER(TRIM(social_type)) IN ('SOCIAL_INSURED', '有社保') THEN 'B'
            WHEN UPPER(TRIM(social_type)) IN ('SOCIAL_UNINSURED', '无社保') THEN 'A'
            ELSE JSON_UNQUOTE(JSON_EXTRACT(rule_json, '$.salaryVersion')) END)
        ELSE rule_json END,
    update_time = NOW()
WHERE UPPER(TRIM(scenario)) = 'ONBOARD'
  AND UPPER(TRIM(employment_type)) IN ('LABOR_CONTRACT', '劳动合同')
  AND UPPER(TRIM(social_type)) IN (
      'SOCIAL_INSURED', '有社保', 'SOCIAL_UNINSURED', '无社保');

UPDATE oa_sign_plan_version
SET matching_status = 'DISABLED'
WHERE UPPER(TRIM(scenario)) = 'ONBOARD'
  AND publish_status = 'PUBLISHED'
  AND matching_status = 'ENABLED'
  AND JSON_VALID(rule_json)
  AND UPPER(TRIM(COALESCE(
        JSON_UNQUOTE(JSON_EXTRACT(rule_json, '$.contractTypeCode')),
        JSON_UNQUOTE(JSON_EXTRACT(rule_json, '$.employmentType')))))
      IN ('LABOR_CONTRACT', '劳动合同')
  AND UPPER(TRIM(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rule_json, '$.salaryVersion')), '')))
      <> CASE
          WHEN UPPER(TRIM(COALESCE(
              JSON_UNQUOTE(JSON_EXTRACT(rule_json, '$.socialTypeCode')),
              JSON_UNQUOTE(JSON_EXTRACT(rule_json, '$.socialType')))))
              IN ('SOCIAL_INSURED', '有社保') THEN 'B'
          WHEN UPPER(TRIM(COALESCE(
              JSON_UNQUOTE(JSON_EXTRACT(rule_json, '$.socialTypeCode')),
              JSON_UNQUOTE(JSON_EXTRACT(rule_json, '$.socialType')))))
              IN ('SOCIAL_UNINSURED', '无社保') THEN 'A'
          ELSE '__INVALID__' END;

-- Existing unfinished previews were calculated with another policy and must be rebuilt.
UPDATE oa_sign_onboard_import_row
SET status = 'PLAN_CHANGED_REPREVIEW',
    error_codes_json = CASE
        WHEN JSON_CONTAINS(COALESCE(error_codes_json, JSON_ARRAY()),
                JSON_QUOTE('MATCH_POLICY_CHANGED_REPREVIEW')) = 1
            THEN COALESCE(error_codes_json, JSON_ARRAY())
        ELSE JSON_ARRAY_APPEND(COALESCE(error_codes_json, JSON_ARRAY()), '$',
                'MATCH_POLICY_CHANGED_REPREVIEW') END,
    plan_version_id = NULL,
    plan_version_hash = NULL,
    version = version + 1,
    update_time = NOW()
WHERE task_id IS NULL
  AND status IN ('PREVIEW_READY', 'MATCHED', 'READY_TO_GENERATE', 'NEEDS_HR_DATA',
                 'GENERATE_FAILED', 'PLAN_CHANGED_REPREVIEW');
