-- HR health-certificate migration preflight (read only, MySQL 5.7 compatible).

SELECT DATABASE() AS database_name, VERSION() AS database_version;

SELECT COUNT(*) AS table_exists
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'hr_employee_health_certificate';

SELECT COUNT(*) AS actual_column_count,
       21 AS expected_column_count,
       CASE WHEN COUNT(*) = 21 THEN 'READY' ELSE 'MISMATCH' END AS column_status
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'hr_employee_health_certificate'
  AND column_name IN (
      'certificate_id','user_id','dept_id_snapshot','certificate_no','issued_date',
      'valid_from','expires_on','issuer_name','attachment_node_id','review_status',
      'current_flag','reviewed_by_user_id','reviewed_by_name','reviewed_time',
      'rejection_reason','version','del_flag','create_by','create_time','update_by','update_time'
  );

SELECT expected.column_name AS missing_column
FROM (
    SELECT 'certificate_id' AS column_name UNION ALL SELECT 'user_id'
    UNION ALL SELECT 'dept_id_snapshot' UNION ALL SELECT 'certificate_no'
    UNION ALL SELECT 'issued_date' UNION ALL SELECT 'valid_from'
    UNION ALL SELECT 'expires_on' UNION ALL SELECT 'issuer_name'
    UNION ALL SELECT 'attachment_node_id' UNION ALL SELECT 'review_status'
    UNION ALL SELECT 'current_flag' UNION ALL SELECT 'reviewed_by_user_id'
    UNION ALL SELECT 'reviewed_by_name' UNION ALL SELECT 'reviewed_time'
    UNION ALL SELECT 'rejection_reason' UNION ALL SELECT 'version'
    UNION ALL SELECT 'del_flag' UNION ALL SELECT 'create_by'
    UNION ALL SELECT 'create_time' UNION ALL SELECT 'update_by'
    UNION ALL SELECT 'update_time'
) expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = 'hr_employee_health_certificate'
 AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL
ORDER BY expected.column_name;

SELECT index_name,
       non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS indexed_columns,
       CASE
           WHEN non_unique = 0
            AND GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') = 'user_id,current_flag'
               THEN 'READY'
           ELSE 'MISMATCH'
       END AS index_status
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'hr_employee_health_certificate'
  AND index_name = 'uk_hr_health_user_current'
GROUP BY index_name, non_unique;

SELECT COUNT(*) AS users_with_multiple_current_certificates
FROM (
    SELECT user_id
    FROM hr_employee_health_certificate
    WHERE current_flag = 'Y' AND del_flag = '0'
    GROUP BY user_id
    HAVING COUNT(*) > 1
) duplicated_current;

SELECT config_key, config_value,
       CASE WHEN config_key = 'feature.hr.health-certificate.enabled'
                  AND LOWER(TRIM(config_value)) NOT IN ('true','1','yes','on','false','0','no','off')
                THEN 'INVALID'
            ELSE 'PRESENT'
       END AS config_status
FROM sys_config
WHERE config_key IN (
    'feature.hr.health-certificate.enabled',
    'todo.health-certificate.warning-days'
)
ORDER BY config_key;

SELECT expected.perms,
       menu.menu_id,
       menu.menu_name,
       COUNT(DISTINCT role_menu.role_id) AS authorized_role_count
FROM (
    SELECT 'hr:healthCertificate:self:edit' AS perms
    UNION ALL SELECT 'hr:healthCertificate:self:submit'
    UNION ALL SELECT 'hr:healthCertificate:list'
    UNION ALL SELECT 'hr:healthCertificate:query'
    UNION ALL SELECT 'hr:healthCertificate:review'
    UNION ALL SELECT 'hr:healthCertificate:remind'
) expected
LEFT JOIN sys_menu menu ON menu.perms = expected.perms
LEFT JOIN sys_role_menu role_menu ON role_menu.menu_id = menu.menu_id
GROUP BY expected.perms, menu.menu_id, menu.menu_name
ORDER BY expected.perms;

