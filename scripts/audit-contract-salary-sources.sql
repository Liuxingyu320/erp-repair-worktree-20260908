-- Read-only evidence export, MySQL 5.7+. Use mysql --batch --raw --skip-column-names.
-- Run against one consistent database snapshot after checking these schema columns exist.
-- Output contains salaries; keep the export and report in restricted storage (umask 077).
SET SESSION TRANSACTION READ ONLY;
START TRANSACTION WITH CONSISTENT SNAPSHOT;
SELECT JSON_OBJECT('kind','PROFILE','employeeId',p.user_id,'salary',JSON_OBJECT(
 'baseSalary',p.base_salary,'postSalary',p.post_salary,'fieldAllowance',p.field_allowance,
 'performanceSalary',p.performance_salary,'salaryTotal',p.salary_total))
FROM sys_user_profile p JOIN sys_user u ON u.user_id=p.user_id WHERE u.del_flag='0';
SELECT JSON_OBJECT('kind','AUDIT','employeeId',a.employee_id,'id',a.audit_id,'at',a.create_time,
 'batchId',a.source_batch_id,'rowId',a.source_row_id,'rowVersion',a.source_version,
 'operatorId',a.operator_user_id,'salary',a.salary_json,
 'evidenceValid',IF(r.employee_id=a.employee_id AND r.batch_id=a.source_batch_id
  AND r.version>=a.source_version AND b.file_sha256=a.source_file_sha256
  AND r.match_type IN ('ID_NUMBER','PHONE_AND_NAME')
  AND NULLIF(p.id_number,'')=JSON_UNQUOTE(JSON_EXTRACT(r.snapshot_json,'$.idNumber'))
  AND NULLIF(u.phonenumber,'')=JSON_UNQUOTE(JSON_EXTRACT(r.snapshot_json,'$.phone')),1,0))
FROM sys_employee_salary_import_audit a
LEFT JOIN oa_sign_onboard_import_row r ON r.row_id=a.source_row_id
LEFT JOIN oa_sign_onboard_import_batch b ON b.batch_id=a.source_batch_id
LEFT JOIN sys_user_profile p ON p.user_id=a.employee_id LEFT JOIN sys_user u ON u.user_id=a.employee_id;
SELECT JSON_OBJECT('kind','ACTION','employeeId',a.employee_id,'id',a.action_id,
 'at',COALESCE(a.actual_confirm_time,a.create_time),'effectiveDate',a.effective_date,
 'type',a.action_type,'status',a.business_status,'before',a.before_snapshot_json,'after',a.after_snapshot_json)
FROM sys_hr_lifecycle_action a WHERE a.action_type IN ('REGULARIZATION','TRANSFER');
SELECT JSON_OBJECT('kind','CONTRACT','employeeId',p.employee_id,'id',p.package_id,
 'at',p.signed_time,'status',p.status,
 'identityValid',IF(NULLIF(p.employee_id_card_snapshot,'')=f.id_number
  AND NULLIF(p.employee_phone_snapshot,'')=u.phonenumber,1,0),
 'salary',JSON_OBJECT('baseSalary',p.base_salary,'postSalary',p.post_salary,
  'fieldAllowance',p.field_allowance,'performanceSalary',p.performance_salary,'salaryTotal',p.salary_total))
FROM oa_sign_package p LEFT JOIN sys_user_profile f ON f.user_id=p.employee_id
LEFT JOIN sys_user u ON u.user_id=p.employee_id WHERE p.status='SIGNED';
COMMIT;
