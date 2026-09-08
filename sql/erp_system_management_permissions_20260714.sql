-- Release A permission cutover. No permission is inferred from edit/list grants.

DROP PROCEDURE IF EXISTS sp_system_management_permissions_20260714;
DELIMITER $$
CREATE PROCEDURE sp_system_management_permissions_20260714()
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_count
    FROM sys_menu
    WHERE perms IN ('system:user:resetPwd', 'system:config:query', 'system:operlog:query');
    IF v_count <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'existing high-risk permission cardinality failed';
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM sys_menu
    WHERE perms IN ('system:logininfor:query', 'system:salary:import');
    IF v_count <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'orphan permission cardinality failed';
    END IF;

    DELETE rm
    FROM sys_role_menu rm
    JOIN sys_menu m ON m.menu_id = rm.menu_id
    WHERE m.perms IN ('system:logininfor:query', 'system:salary:import');

    UPDATE sys_menu
    SET status = '1', visible = '1', update_by = 'system', update_time = NOW(),
        remark = CASE perms
            WHEN 'system:logininfor:query' THEN '已停用：当前无独立登录日志详情能力'
            WHEN 'system:salary:import' THEN '已停用：当前无薪资导入页面或接口'
            ELSE remark
        END
    WHERE perms IN ('system:logininfor:query', 'system:salary:import');

    SELECT COUNT(*) INTO v_count
    FROM sys_role_menu rm
    JOIN sys_menu m ON m.menu_id = rm.menu_id
    WHERE m.perms IN ('system:logininfor:query', 'system:salary:import');
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'orphan permission mappings remain';
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM sys_role_menu rm
    JOIN sys_menu m ON m.menu_id = rm.menu_id
    WHERE m.perms IN ('system:config:refresh', 'system:user:pii:read',
                      'system:user:pii:edit', 'system:user:pii:export');
    IF v_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'unapproved new high-risk grants detected';
    END IF;
END$$
DELIMITER ;

CALL sp_system_management_permissions_20260714();
DROP PROCEDURE sp_system_management_permissions_20260714;

SELECT m.perms, m.status, COUNT(rm.role_id) AS role_grants
FROM sys_menu m
LEFT JOIN sys_role_menu rm ON rm.menu_id = m.menu_id
WHERE m.perms IN (
    'system:user:resetPwd', 'system:config:query', 'system:operlog:query',
    'system:logininfor:query', 'system:salary:import', 'system:config:refresh',
    'system:user:pii:read', 'system:user:pii:edit', 'system:user:pii:export'
)
GROUP BY m.menu_id, m.perms, m.status
ORDER BY m.perms;

