-- HR department-leader identity preflight (read only, MySQL 5.7 compatible).

SELECT DATABASE() AS database_name, VERSION() AS database_version;

SELECT COUNT(*) AS leader_user_id_column_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'sys_dept'
  AND column_name = 'leader_user_id';

SELECT index_name,
       non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS indexed_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'sys_dept'
  AND index_name = 'idx_sys_dept_leader_user_id'
GROUP BY index_name, non_unique;

SELECT COUNT(*) AS invalid_leader_identity_nodes
FROM sys_dept d
LEFT JOIN sys_user u ON u.user_id = d.leader_user_id
LEFT JOIN sys_user_profile p ON p.user_id = u.user_id
WHERE d.del_flag = '0'
  AND d.leader_user_id IS NOT NULL
  AND (
      u.user_id IS NULL OR u.user_id = 1 OR u.del_flag <> '0'
      OR u.status <> '0' OR p.employee_status = '离职'
  );

SELECT COUNT(*) AS legacy_name_without_identity_nodes
FROM sys_dept d
WHERE d.del_flag = '0'
  AND d.leader_user_id IS NULL
  AND NULLIF(TRIM(d.leader), '') IS NOT NULL;

SELECT COUNT(*) AS leader_snapshot_mismatch_nodes
FROM sys_dept d
JOIN sys_user u ON u.user_id = d.leader_user_id
WHERE d.del_flag = '0'
  AND (
      NULLIF(TRIM(d.leader), '') IS NULL
      OR TRIM(d.leader) <> TRIM(u.nick_name)
  );

SELECT COUNT(*) AS unresolved_responsibility_node_p0_count
FROM (
    SELECT DISTINCT COALESCE(
        (
            SELECT candidate.dept_id
            FROM sys_dept candidate
            WHERE candidate.del_flag = '0'
              AND candidate.status = '0'
              AND UPPER(COALESCE(candidate.dept_type, '')) NOT IN ('GROUP','STORE','WAREHOUSE')
              AND (
                  candidate.dept_id = employee_dept.dept_id
                  OR FIND_IN_SET(candidate.dept_id, employee_dept.ancestors)
              )
            ORDER BY
                (LENGTH(COALESCE(candidate.ancestors, ''))
                 - LENGTH(REPLACE(COALESCE(candidate.ancestors, ''), ',', ''))) ASC,
                candidate.dept_id
            LIMIT 1 OFFSET 1
        ),
        (
            SELECT company.dept_id
            FROM sys_dept company
            WHERE company.del_flag = '0'
              AND company.status = '0'
              AND UPPER(COALESCE(company.dept_type, '')) NOT IN ('GROUP','STORE','WAREHOUSE')
              AND (
                  company.dept_id = employee_dept.dept_id
                  OR FIND_IN_SET(company.dept_id, employee_dept.ancestors)
              )
            ORDER BY
                (LENGTH(COALESCE(company.ancestors, ''))
                 - LENGTH(REPLACE(COALESCE(company.ancestors, ''), ',', ''))) ASC,
                company.dept_id
            LIMIT 1
        )
    ) AS responsibility_dept_id
    FROM sys_user employee
    JOIN sys_dept employee_dept
      ON employee_dept.dept_id = employee.dept_id
     AND employee_dept.del_flag = '0'
     AND employee_dept.status = '0'
    LEFT JOIN sys_user_profile employee_profile ON employee_profile.user_id = employee.user_id
    WHERE employee.del_flag = '0'
      AND employee.user_id <> 1
      AND (employee_profile.employee_status IS NULL OR employee_profile.employee_status <> '离职')
) responsibility
JOIN sys_dept owner ON owner.dept_id = responsibility.responsibility_dept_id
LEFT JOIN sys_user leader ON leader.user_id = owner.leader_user_id
LEFT JOIN sys_user_profile leader_profile ON leader_profile.user_id = leader.user_id
WHERE responsibility.responsibility_dept_id IS NOT NULL
  AND (
      owner.leader_user_id IS NULL
      OR leader.user_id IS NULL
      OR leader.user_id = 1
      OR leader.del_flag <> '0'
      OR leader.status <> '0'
      OR leader_profile.employee_status = '离职'
      OR NULLIF(TRIM(owner.leader), '') IS NULL
      OR TRIM(owner.leader) <> TRIM(leader.nick_name)
  );

