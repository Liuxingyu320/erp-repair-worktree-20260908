-- Employee-facing delivery policy for signing templates and immutable snapshots.
-- Idempotent MySQL migration. Apply after erp_oa_sign_plan_version_20260711.sql.

SET @erp_db = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_template' AND COLUMN_NAME = 'employee_visible') = 0,
    'ALTER TABLE oa_sign_template ADD COLUMN employee_visible char(1) NOT NULL DEFAULT ''Y'' COMMENT ''员工端是否可见（Y是 N否）'' AFTER optional_placeholders', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_template' AND COLUMN_NAME = 'read_confirmation_required') = 0,
    'ALTER TABLE oa_sign_template ADD COLUMN read_confirmation_required char(1) NOT NULL DEFAULT ''N'' COMMENT ''是否要求员工阅读确认（Y是 N否）'' AFTER employee_visible', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document' AND COLUMN_NAME = 'employee_visible') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN employee_visible char(1) NOT NULL DEFAULT ''Y'' COMMENT ''员工端是否可见快照（Y是 N否）'' AFTER document_version', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_package_document' AND COLUMN_NAME = 'read_confirmation_required') = 0,
    'ALTER TABLE oa_sign_package_document ADD COLUMN read_confirmation_required char(1) NOT NULL DEFAULT ''N'' COMMENT ''阅读确认要求快照（Y是 N否）'' AFTER employee_visible', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan_version_template' AND COLUMN_NAME = 'employee_visible') = 0,
    'ALTER TABLE oa_sign_plan_version_template ADD COLUMN employee_visible char(1) NOT NULL DEFAULT ''Y'' COMMENT ''员工端是否可见快照（Y是 N否）'' AFTER sort_order', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @erp_db AND TABLE_NAME = 'oa_sign_plan_version_template' AND COLUMN_NAME = 'read_confirmation_required') = 0,
    'ALTER TABLE oa_sign_plan_version_template ADD COLUMN read_confirmation_required char(1) NOT NULL DEFAULT ''N'' COMMENT ''阅读确认要求快照（Y是 N否）'' AFTER employee_visible', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Internal HR materials are never exposed to employees.
UPDATE oa_sign_template
SET employee_visible = 'N', read_confirmation_required = 'N', employee_sign_required = 'N'
WHERE template_type IN ('ONBOARD_APPLICATION_FORM', 'ONBOARD_BACKGROUND_CHECK', 'ONBOARD_ARCHIVE_CATALOG');

-- Employee-facing signing/read policies for existing template registrations.
UPDATE oa_sign_template
SET employee_visible = 'Y', read_confirmation_required = 'Y', employee_sign_required = 'N'
WHERE template_type = 'ONBOARD_HANDBOOK';
UPDATE oa_sign_template
SET employee_visible = 'Y', read_confirmation_required = 'Y', employee_sign_required = 'Y'
WHERE template_type = 'ONBOARD_OFFER_NOTICE';
UPDATE oa_sign_template
SET employee_visible = 'Y', read_confirmation_required = 'Y'
WHERE employee_sign_required = 'Y';

-- Keep labor-only materials out of service packages, including legacy registrations.
UPDATE oa_sign_template
SET employment_type = '劳动合同'
WHERE template_type IN ('ONBOARD_LABOR_CONTRACT', 'ONBOARD_OFFER_NOTICE', 'ONBOARD_HANDBOOK',
                        'ONBOARD_COMMITMENT', 'ONBOARD_POST_DUTY', 'ONBOARD_HANDBOOK_RECEIPT',
                        'ONBOARD_SALARY_CONFIRM', 'ONBOARD_CONFIDENTIAL_NONCOMPETE');
UPDATE oa_sign_template
SET employment_type = '劳务合同', social_type = NULL
WHERE template_type IN ('ONBOARD_SERVICE_CONTRACT', 'ONBOARD_SERVICE_RECEIPT');

-- Preserve the policy inside immutable published plan versions used for future packages.
UPDATE oa_sign_plan_version_template
SET employee_visible = 'N', read_confirmation_required = 'N', employee_sign_required = 'N'
WHERE template_type IN ('ONBOARD_APPLICATION_FORM', 'ONBOARD_BACKGROUND_CHECK', 'ONBOARD_ARCHIVE_CATALOG');
UPDATE oa_sign_plan_version_template
SET employee_visible = 'Y', read_confirmation_required = 'Y', employee_sign_required = 'N'
WHERE template_type = 'ONBOARD_HANDBOOK';
UPDATE oa_sign_plan_version_template
SET employee_visible = 'Y', read_confirmation_required = 'Y', employee_sign_required = 'Y',
    signature_position_json = COALESCE(NULLIF(signature_position_json, ''), '{"mode":"APPENDED_CONFIRMATION_PAGE"}')
WHERE template_type = 'ONBOARD_OFFER_NOTICE';
UPDATE oa_sign_plan_version_template
SET employee_visible = 'Y', read_confirmation_required = 'Y'
WHERE employee_sign_required = 'Y';

-- Existing generated documents keep their original sign requirement, while visibility/read
-- is backfilled safely. Regenerating a draft creates a fully new policy snapshot.
UPDATE oa_sign_package_document d
LEFT JOIN oa_sign_template t ON t.template_id = d.template_id
LEFT JOIN oa_sign_package p ON p.package_id = d.package_id
SET d.employee_visible = CASE
        WHEN d.template_type IN ('ONBOARD_APPLICATION_FORM', 'ONBOARD_BACKGROUND_CHECK', 'ONBOARD_ARCHIVE_CATALOG') THEN 'N'
        WHEN t.employee_visible IN ('Y', 'N') THEN t.employee_visible
        ELSE 'Y'
    END,
    d.read_confirmation_required = CASE
        WHEN d.template_type IN ('ONBOARD_APPLICATION_FORM', 'ONBOARD_BACKGROUND_CHECK', 'ONBOARD_ARCHIVE_CATALOG') THEN 'N'
        WHEN d.employee_sign_required = 'Y' OR d.template_type = 'ONBOARD_HANDBOOK' THEN 'Y'
        ELSE 'N'
    END,
    d.read_confirmed = CASE
        WHEN p.status IN ('draft', 'pending_sign', 'part_viewed')
             AND (d.employee_sign_required = 'Y' OR d.template_type = 'ONBOARD_HANDBOOK') THEN 'N'
        WHEN d.employee_sign_required = 'Y' OR d.template_type = 'ONBOARD_HANDBOOK' THEN d.read_confirmed
        ELSE 'Y'
    END;

-- Rollback guidance: application rollback is safe after reverting the six mapper/domain fields.
-- Keep these columns and their snapshots in production; dropping them would discard evidence policy.
