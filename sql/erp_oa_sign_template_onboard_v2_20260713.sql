-- Register the reviewed onboarding signing templates (20260713-v2).
-- Prerequisites, in order:
--   1. erp_oa_sign_package_20260702.sql
--   2. erp_oa_sign_plan_version_20260711.sql
--   3. erp_oa_sign_template_delivery_policy_20260713.sql
-- Copy the 16 files to uploadPath/sign-template/onboard-20260713-v2 before running.
-- Idempotent: rerunning updates the same v2 rows and keeps older variants disabled.

DROP TEMPORARY TABLE IF EXISTS tmp_oa_sign_template_onboard_v2_20260713;
CREATE TEMPORARY TABLE tmp_oa_sign_template_onboard_v2_20260713 (
    template_type varchar(64) NOT NULL,
    template_name varchar(120) NOT NULL,
    template_version varchar(32) NOT NULL,
    scenario varchar(32) NOT NULL,
    employment_type varchar(32) DEFAULT NULL,
    social_type varchar(20) DEFAULT NULL,
    post_level_scope varchar(64) DEFAULT NULL,
    salary_version varchar(20) DEFAULT NULL,
    file_url varchar(500) NOT NULL,
    file_name varchar(200) NOT NULL,
    file_size bigint NOT NULL,
    file_hash varchar(128) NOT NULL,
    required_placeholders varchar(1000) DEFAULT NULL,
    employee_visible char(1) NOT NULL,
    read_confirmation_required char(1) NOT NULL,
    employee_sign_required char(1) NOT NULL,
    signature_position_json longtext DEFAULT NULL,
    sort_order int NOT NULL,
    status char(1) NOT NULL,
    remark varchar(500) DEFAULT NULL
);

INSERT INTO tmp_oa_sign_template_onboard_v2_20260713
    (template_type, template_name, template_version, scenario, employment_type, social_type,
     post_level_scope, salary_version, file_url, file_name, file_size, file_hash,
     required_placeholders, employee_visible, read_confirmation_required,
     employee_sign_required, signature_position_json, sort_order, status, remark)
VALUES
    ('ONBOARD_ARCHIVE_CATALOG', '员工档案目录', '20260713-v2', 'onboard', NULL, NULL, NULL, NULL,
     '/profile/sign-template/onboard-20260713-v2/00_ONBOARD_ARCHIVE_CATALOG.docx', '0 员工档案目录.docx', 21215,
     '3a0ffd8af5d41f89b4fea76990b0a947c3c68ccfdf77609ffb2a1f6aaa3756cf',
     'employeeName,employeeDeptName,postName,entryDate', 'N', 'N', 'N', NULL, 10, '1',
     '20260713改造版；HR内部资料默认停用'),
    ('ONBOARD_HANDBOOK', '员工手册', '20260713-v2', 'onboard', '劳动合同', NULL, NULL, NULL,
     '/profile/sign-template/onboard-20260713-v2/01_ONBOARD_HANDBOOK.docx', '01员工手册.docx', 52202,
     '88317ec1f16b4834c0aae5386633ea4b443730d1c4ec7934fb7c062b4609d9a5',
     NULL, 'Y', 'Y', 'N', NULL, 20, '0', '20260713签约模板合规与易用性改造版'),
    ('ONBOARD_APPLICATION_FORM', '应聘登记表', '20260713-v2', 'onboard', NULL, NULL, NULL, NULL,
     '/profile/sign-template/onboard-20260713-v2/02_ONBOARD_APPLICATION_FORM.xlsx', '1-1应聘登记表.xlsx', 14297,
     '73617bc12b57afa2ea8e3c099bbca02e0e059305fb2c0ca852dca7b769023872',
     'employeeName,employeeIdCard,employeePhone,postName,entryDate', 'N', 'N', 'N', NULL, 30, '1',
     '20260713改造版；HR内部资料默认停用'),
    ('ONBOARD_BACKGROUND_CHECK', '背景调查报告', '20260713-v2', 'onboard', NULL, NULL, NULL, NULL,
     '/profile/sign-template/onboard-20260713-v2/03_ONBOARD_BACKGROUND_CHECK.docx', '1-8背景调查报告.docx', 16215,
     '931c5c2da14f05a800b465f228e17560255b38453444d45979a390ac55b5d7ba',
     'employeeName,postName', 'N', 'N', 'N', NULL, 40, '1',
     '20260713改造版；HR内部资料默认停用'),
    ('ONBOARD_OFFER_NOTICE', '录用通知书', '20260713-v2', 'onboard', '劳动合同', NULL, NULL, NULL,
     '/profile/sign-template/onboard-20260713-v2/04_ONBOARD_OFFER_NOTICE.docx', '2-1录用通知书.docx', 26336,
     '516b13dd0b3e7702285d63aa8c1d3881154a6c68a802976c0515f7787d5b14f7',
     'employeeName,employeeIdCard,employeePhone,employeeAddress,employeeDeptName,postName,entryDate,probationStartDate,probationEndDate,baseSalary,postSalary,fieldAllowance,performanceSalary,salaryTotal,signDate',
     'Y', 'Y', 'Y', '{"mode":"APPENDED_CONFIRMATION_PAGE"}', 50, '0', '20260713签约模板合规与易用性改造版'),
    ('ONBOARD_COMMITMENT', '入职承诺书', '20260713-v2', 'onboard', '劳动合同', NULL, NULL, NULL,
     '/profile/sign-template/onboard-20260713-v2/05_ONBOARD_COMMITMENT.docx', '2-3入职承诺书.docx', 18542,
     'ad2a1119262df7962c25bc35335ff433e4b5c84bd4435343a63df1e23dd5964c',
     'employeeName,employeeIdCard,signDate', 'Y', 'Y', 'Y', '{"mode":"APPENDED_CONFIRMATION_PAGE"}', 60, '0',
     '20260713签约模板合规与易用性改造版'),
    ('ONBOARD_POST_DUTY', '岗位职责确认书（2-4级）', '20260713-v2', 'onboard', '劳动合同', NULL, '2-4', NULL,
     '/profile/sign-template/onboard-20260713-v2/06_ONBOARD_POST_DUTY_2_4.docx', '2-4岗位职责确认书（2-4级）.docx', 20008,
     'd7019811f8979fee07d703e2c51538e07cc012cd803b923ead980fcee146cb22',
     'employeeName,postName,postLevel,signDate', 'Y', 'Y', 'Y', '{"mode":"APPENDED_CONFIRMATION_PAGE"}', 70, '0',
     '20260713签约模板合规与易用性改造版'),
    ('ONBOARD_POST_DUTY', '岗位职责确认书（5-6级）', '20260713-v2', 'onboard', '劳动合同', NULL, '5-6', NULL,
     '/profile/sign-template/onboard-20260713-v2/07_ONBOARD_POST_DUTY_5_6.docx', '2-4岗位职责确认书（5-6级）.docx', 20375,
     'e19ef16c7638344c21b48e85c396e0f1a7633a8b6b5485bbab74d6ec9f336433',
     'employeeName,postName,postLevel,signDate', 'Y', 'Y', 'Y', '{"mode":"APPENDED_CONFIRMATION_PAGE"}', 80, '0',
     '20260713签约模板合规与易用性改造版'),
    ('ONBOARD_POST_DUTY', '岗位职责确认书（7-8级）', '20260713-v2', 'onboard', '劳动合同', NULL, '7-8', NULL,
     '/profile/sign-template/onboard-20260713-v2/08_ONBOARD_POST_DUTY_7_8.docx', '2-4岗位职责确认书（7-8级）.docx', 20329,
     '2dbf30f6d424a824c71d4540e659b04b2c2e7829366f46c35963ad9f64a643e2',
     'employeeName,postName,postLevel,signDate', 'Y', 'Y', 'Y', '{"mode":"APPENDED_CONFIRMATION_PAGE"}', 90, '0',
     '20260713签约模板合规与易用性改造版'),
    ('ONBOARD_LABOR_CONTRACT', '劳动合同', '20260713-v2', 'onboard', '劳动合同', NULL, NULL, NULL,
     '/profile/sign-template/onboard-20260713-v2/09_ONBOARD_LABOR_CONTRACT.docx', '2-5劳动合同.docx', 27055,
     '153b5e57fa859541519366c04df35fb4d7dc8519a431119b4b09d2f5083b3624',
     'employeeName,employeeIdCard,employeePhone,employeeAddress,postName,contractStartDate,contractEndDate,probationStartDate,probationEndDate,baseSalary,signDate',
     'Y', 'Y', 'Y', '{"mode":"APPENDED_CONFIRMATION_PAGE"}', 100, '0', '20260713签约模板合规与易用性改造版'),
    ('ONBOARD_HANDBOOK_RECEIPT', '员工手册签收确认书', '20260713-v2', 'onboard', '劳动合同', NULL, NULL, NULL,
     '/profile/sign-template/onboard-20260713-v2/10_ONBOARD_HANDBOOK_RECEIPT.docx', '2-6员工手册签收确认书.docx', 20383,
     '522fc0fbd7abce3b8a361cdfdf299d9971bf07a736229c41fe8bd6721a90997d',
     'employeeName,employeeIdCard,signDate', 'Y', 'Y', 'Y', '{"mode":"APPENDED_CONFIRMATION_PAGE"}', 110, '0',
     '20260713签约模板合规与易用性改造版'),
    ('ONBOARD_SALARY_CONFIRM', '薪酬结构确认书（A版）', '20260713-v2', 'onboard', '劳动合同', NULL, NULL, 'A',
     '/profile/sign-template/onboard-20260713-v2/11_ONBOARD_SALARY_CONFIRM_A.docx', '2-7薪酬结构确认书（A版）.docx', 22275,
     'c576541022b10ec57896b1405ac113036dead9e233efb7fa3e74ac329d8dc7b5',
     'employeeName,employeeIdCard,baseSalary,postSalary,fieldAllowance,performanceSalary,salaryTotal,signDate',
     'Y', 'Y', 'Y', '{"mode":"APPENDED_CONFIRMATION_PAGE"}', 120, '0', '20260713签约模板合规与易用性改造版'),
    ('ONBOARD_SALARY_CONFIRM', '薪酬结构确认书（B版）', '20260713-v2', 'onboard', '劳动合同', NULL, NULL, 'B',
     '/profile/sign-template/onboard-20260713-v2/12_ONBOARD_SALARY_CONFIRM_B.docx', '2-7薪酬结构确认书（B版）.docx', 21522,
     'f945f1a78410d098f1cce4f36456772dfb4d3f3fd5bbe331cc784eea3e7ecc38',
     'employeeName,employeeIdCard,baseSalary,postSalary,fieldAllowance,performanceSalary,salaryTotal,signDate',
     'Y', 'Y', 'Y', '{"mode":"APPENDED_CONFIRMATION_PAGE"}', 130, '0', '20260713签约模板合规与易用性改造版'),
    ('ONBOARD_SERVICE_CONTRACT', '劳务合同', '20260713-v2', 'onboard', '劳务合同', NULL, NULL, NULL,
     '/profile/sign-template/onboard-20260713-v2/13_ONBOARD_SERVICE_CONTRACT.docx', '2-8劳务合同.docx', 26204,
     '510251a6b4b2a7d19381f611c2a9df94af23e4512043b3e913d06cc528aae932',
     'employeeName,employeeIdCard,employeePhone,employeeAddress,postName,servicePersonType,contractStartDate,contractEndDate,baseSalary,signDate',
     'Y', 'Y', 'Y', '{"mode":"APPENDED_CONFIRMATION_PAGE"}', 140, '0', '20260713签约模板合规与易用性改造版'),
    ('ONBOARD_SERVICE_RECEIPT', '劳务合同签收单', '20260713-v2', 'onboard', '劳务合同', NULL, NULL, NULL,
     '/profile/sign-template/onboard-20260713-v2/14_ONBOARD_SERVICE_RECEIPT.docx', '2-9劳务合同签收单.docx', 22442,
     'df84bc4b63ff1e67e9b95e314abc220eeb10511f806188c057d2f73deb1e7b3e',
     'employeeName,employeeIdCard,servicePersonType,insuranceType,baseSalary,postSalary,fieldAllowance,performanceSalary,salaryTotal,signDate',
     'Y', 'Y', 'Y', '{"mode":"APPENDED_CONFIRMATION_PAGE"}', 150, '0', '20260713签约模板合规与易用性改造版'),
    ('ONBOARD_CONFIDENTIAL_NONCOMPETE', '保密与竞业限制协议（7级及以上）', '20260713-v2', 'onboard', '劳动合同', NULL, '7-8', NULL,
     '/profile/sign-template/onboard-20260713-v2/15_ONBOARD_CONFIDENTIAL_NONCOMPETE.docx', '2-10保密与竞业限制协议（7级及以上）.docx', 21564,
     '97969d1852b72e231d2b0104039346d339b5bbff6e8a90ebc7155e5f64f7a80a',
     'employeeName,employeeIdCard,employeePhone,employeeAddress,postName,postLevel,contractStartDate,signDate',
     'Y', 'Y', 'Y', '{"mode":"APPENDED_CONFIRMATION_PAGE"}', 160, '0', '20260713签约模板合规与易用性改造版');

-- Disable earlier registrations of the same logical variant, but keep them for audit/history.
UPDATE oa_sign_template t
JOIN tmp_oa_sign_template_onboard_v2_20260713 v
  ON CAST(t.template_type AS BINARY) = CAST(v.template_type AS BINARY)
 AND CAST(COALESCE(t.scenario, '') AS BINARY) = CAST(v.scenario AS BINARY)
 AND CAST(COALESCE(t.post_level_scope, '') AS BINARY) = CAST(COALESCE(v.post_level_scope, '') AS BINARY)
 AND CAST(COALESCE(t.salary_version, '') AS BINARY) = CAST(COALESCE(v.salary_version, '') AS BINARY)
SET t.status = '1', t.update_by = 'system', t.update_time = NOW(),
    t.remark = CONCAT(COALESCE(t.remark, ''), IF(COALESCE(t.remark, '') = '', '', '；'), '已由20260713-v2替代')
WHERE CAST(COALESCE(t.template_version, '') AS BINARY) <> CAST(v.template_version AS BINARY)
  AND t.status <> '1';

-- Refresh existing v2 rows so file hashes and delivery policy always match the package.
UPDATE oa_sign_template t
JOIN tmp_oa_sign_template_onboard_v2_20260713 v
  ON CAST(t.template_type AS BINARY) = CAST(v.template_type AS BINARY)
 AND CAST(t.template_version AS BINARY) = CAST(v.template_version AS BINARY)
 AND CAST(COALESCE(t.scenario, '') AS BINARY) = CAST(v.scenario AS BINARY)
 AND CAST(COALESCE(t.post_level_scope, '') AS BINARY) = CAST(COALESCE(v.post_level_scope, '') AS BINARY)
 AND CAST(COALESCE(t.salary_version, '') AS BINARY) = CAST(COALESCE(v.salary_version, '') AS BINARY)
SET t.template_name = v.template_name,
    t.employment_type = v.employment_type,
    t.social_type = v.social_type,
    t.file_url = v.file_url,
    t.file_name = v.file_name,
    t.file_size = v.file_size,
    t.file_hash = v.file_hash,
    t.required_placeholders = v.required_placeholders,
    t.optional_placeholders = NULL,
    t.employee_visible = v.employee_visible,
    t.read_confirmation_required = v.read_confirmation_required,
    t.employee_sign_required = v.employee_sign_required,
    t.signature_position_json = v.signature_position_json,
    t.sort_order = v.sort_order,
    t.status = v.status,
    t.remark = v.remark,
    t.update_by = 'system',
    t.update_time = NOW();

-- Insert missing v2 rows.
INSERT INTO oa_sign_template
    (template_type, template_name, template_version, scenario, employment_type, social_type,
     post_level_scope, salary_version, file_url, file_name, file_size, file_hash,
     required_placeholders, optional_placeholders, employee_visible,
     read_confirmation_required, employee_sign_required, signature_position_json,
     sort_order, status, create_by, create_time, remark)
SELECT v.template_type, v.template_name, v.template_version, v.scenario, v.employment_type,
       v.social_type, v.post_level_scope, v.salary_version, v.file_url, v.file_name,
       v.file_size, v.file_hash, v.required_placeholders, NULL, v.employee_visible,
       v.read_confirmation_required, v.employee_sign_required, v.signature_position_json,
       v.sort_order, v.status, 'system', NOW(), v.remark
FROM tmp_oa_sign_template_onboard_v2_20260713 v
LEFT JOIN oa_sign_template t
  ON CAST(t.template_type AS BINARY) = CAST(v.template_type AS BINARY)
 AND CAST(t.template_version AS BINARY) = CAST(v.template_version AS BINARY)
 AND CAST(COALESCE(t.scenario, '') AS BINARY) = CAST(v.scenario AS BINARY)
 AND CAST(COALESCE(t.post_level_scope, '') AS BINARY) = CAST(COALESCE(v.post_level_scope, '') AS BINARY)
 AND CAST(COALESCE(t.salary_version, '') AS BINARY) = CAST(COALESCE(v.salary_version, '') AS BINARY)
WHERE t.template_id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_oa_sign_template_onboard_v2_20260713;
