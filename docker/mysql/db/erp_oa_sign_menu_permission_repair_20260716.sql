-- Repair signing menu IDs that historically overlapped with HR personnel menus.
-- Forward-only, idempotent, and compatible with MySQL 5.7/8.x.

SET @erp_db = DATABASE();

-- Fail closed when the reserved signing range belongs to another feature.
DROP PROCEDURE IF EXISTS assert_sign_menu_id_ownership_20260716;
DELIMITER $$
CREATE PROCEDURE assert_sign_menu_id_ownership_20260716()
BEGIN
    DECLARE sign_role_count int DEFAULT 0;
    DECLARE transfer_menu_count int DEFAULT 0;
    DECLARE offboard_menu_count int DEFAULT 0;
    DECLARE signing_table_count int DEFAULT 0;
    DECLARE signing_support_column_count int DEFAULT 0;
    DECLARE outbox_column_count int DEFAULT 0;
    DECLARE outbox_unique_count int DEFAULT 0;

    SELECT COUNT(*) INTO signing_table_count
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME IN ('sys_sign_hr_state', 'sys_sign_hr_menu_grant',
                          'oa_sign_task', 'oa_sign_task_hr_reassignment',
                          'oa_sign_notification_outbox');
    IF signing_table_count <> 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Signing HR reassignment prerequisite tables are missing';
    END IF;

    SELECT COUNT(*) INTO signing_support_column_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND (
            (TABLE_NAME = 'oa_sign_task'
             AND COLUMN_NAME IN ('task_id', 'assigned_hr_user_id', 'status', 'version'))
         OR (TABLE_NAME = 'oa_sign_task_hr_reassignment'
             AND COLUMN_NAME IN ('task_id', 'old_hr_user_id', 'new_hr_user_id',
                                 'task_status', 'reassigned_time'))
         OR (TABLE_NAME = 'sys_sign_hr_state'
             AND COLUMN_NAME IN ('state_id', 'hr_user_id', 'managed_role_id', 'updated_time'))
         OR (TABLE_NAME = 'sys_sign_hr_menu_grant'
             AND COLUMN_NAME IN ('role_id', 'menu_id', 'hr_user_id', 'created_time'))
       );
    IF signing_support_column_count <> 17 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Signing HR reassignment prerequisite columns are missing';
    END IF;

    SELECT COUNT(*) INTO outbox_column_count
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_notification_outbox'
       AND COLUMN_NAME IN ('outbox_id', 'channel', 'recipient_user_id', 'business_key',
                           'payload_json', 'status', 'retry_count', 'next_retry_time',
                           'last_result', 'last_error', 'version', 'created_time',
                           'updated_time');
    IF outbox_column_count <> 13 OR NOT EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'oa_sign_notification_outbox'
           AND COLUMN_NAME = 'payload_json'
           AND DATA_TYPE = 'json'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Signing notification outbox schema is incompatible';
    END IF;

    SELECT COUNT(*) INTO outbox_unique_count
      FROM (
            SELECT INDEX_NAME,
                   GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS index_columns,
                   MIN(NON_UNIQUE) AS non_unique
              FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'oa_sign_notification_outbox'
               AND INDEX_NAME = 'uk_oa_sign_notification_business'
             GROUP BY INDEX_NAME
           ) outbox_index
     WHERE BINARY outbox_index.index_columns = BINARY 'channel,recipient_user_id,business_key'
       AND outbox_index.non_unique = 0;
    IF outbox_unique_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Signing notification outbox unique key is incompatible';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM sys_menu
         WHERE menu_id = 3000
           AND parent_id = 0
           AND BINARY path = BINARY 'oa'
           AND COALESCE(component, '') = ''
           AND BINARY route_name = BINARY 'OaRoot'
           AND menu_type = 'M'
           AND COALESCE(perms, '') = ''
           AND is_frame = 1
           AND visible = '0'
           AND status = '0'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Expected active OA navigation root menu 3000';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM sys_menu
         WHERE menu_id BETWEEN 9650 AND 9669
           AND NOT COALESCE((
                (menu_id = 9650 AND parent_id = 3000
                    AND BINARY path = BINARY 'sign-task'
                    AND BINARY component = BINARY 'oa/signTask/index'
                    AND BINARY route_name = BINARY 'OaSignTask'
                    AND menu_type = 'C'
                    AND BINARY perms = BINARY 'oa:signTask:list')
             OR (menu_id BETWEEN 9651 AND 9669
                    AND parent_id = 9650
                    AND COALESCE(path, '') = ''
                    AND component IS NULL
                    AND COALESCE(route_name, '') = ''
                    AND menu_type = 'F'
                    AND (
                        (menu_id = 9651 AND BINARY perms = BINARY 'oa:signTask:query')
                     OR (menu_id = 9652 AND BINARY perms = BINARY 'oa:signTask:revalidate')
                     OR (menu_id = 9653 AND BINARY perms = BINARY 'oa:signTask:send')
                     OR (menu_id = 9654 AND BINARY perms = BINARY 'oa:signTask:retry')
                     OR (menu_id = 9655 AND BINARY perms = BINARY 'oa:signTask:cancel')
                     OR (menu_id = 9656 AND BINARY perms = BINARY 'oa:signTask:technicalEvidence')
                     OR (menu_id = 9657 AND BINARY perms = BINARY 'oa:signPackage:list')
                     OR (menu_id = 9658 AND BINARY perms = BINARY 'oa:signPackage:query')
                     OR (menu_id = 9659 AND BINARY perms = BINARY 'oa:signPackage:add')
                     OR (menu_id = 9660 AND BINARY perms = BINARY 'oa:signPackage:send')
                     OR (menu_id = 9661 AND BINARY perms = BINARY 'oa:signPackage:void')
                     OR (menu_id = 9662 AND BINARY perms = BINARY 'oa:signPackage:template')
                     OR (menu_id = 9663 AND BINARY perms = BINARY 'oa:signTask:remind')
                     OR (menu_id = 9664 AND BINARY perms = BINARY 'oa:signTask:resolveRefusal')
                     OR (menu_id = 9665 AND BINARY perms = BINARY 'oa:signTask:resolveExpiry')
                     OR (menu_id = 9666 AND BINARY perms = BINARY 'oa:signCompany:list')
                     OR (menu_id = 9667 AND BINARY perms = BINARY 'oa:signCompany:edit')
                     OR (menu_id = 9668 AND BINARY perms = BINARY 'oa:signSeal:list')
                     OR (menu_id = 9669 AND BINARY perms = BINARY 'oa:signSeal:edit')
                    ))
           ), 0)
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Signing menu range 9650-9669 is owned by another feature';
    END IF;

    SELECT COUNT(*) INTO sign_role_count
      FROM sys_role
     WHERE BINARY role_key = BINARY 'sign_single_hr';
    IF sign_role_count > 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Multiple sign_single_hr roles exist';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM sys_sign_hr_state state
         JOIN sys_role managed_role ON managed_role.role_id = state.managed_role_id
         WHERE state.state_id = 1
           AND NOT COALESCE(BINARY managed_role.role_key = BINARY 'sign_single_hr', 0)
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Managed signing role ID belongs to another role';
    END IF;

    SELECT COUNT(*) INTO transfer_menu_count
      FROM sys_menu
     WHERE BINARY perms = BINARY 'hr:employee:transfer' AND status = '0';
    IF transfer_menu_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Expected one active HR transfer permission menu';
    END IF;

    SELECT COUNT(*) INTO offboard_menu_count
      FROM sys_menu
     WHERE BINARY perms = BINARY 'hr:employee:offboard' AND status = '0';
    IF offboard_menu_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Expected one active HR offboarding permission menu';
    END IF;
END$$
DELIMITER ;

CALL assert_sign_menu_id_ownership_20260716();
DROP PROCEDURE IF EXISTS assert_sign_menu_id_ownership_20260716;

-- A role is eligible for equivalent signing-permission migration only when it owns a
-- non-conflicting signing permission. Collision-only grants on 4600/4611 are not
-- sufficient evidence, and package evidence never grants the task-center entry.
DROP TEMPORARY TABLE IF EXISTS tmp_sign_eligible_role_20260716;
CREATE TEMPORARY TABLE tmp_sign_eligible_role_20260716 (
    role_id bigint NOT NULL,
    PRIMARY KEY (role_id)
) ENGINE=InnoDB;

INSERT IGNORE INTO tmp_sign_eligible_role_20260716 (role_id)
SELECT DISTINCT role_menu.role_id
  FROM sys_role_menu role_menu
  JOIN sys_menu evidence_menu ON evidence_menu.menu_id = role_menu.menu_id
 WHERE evidence_menu.menu_id IN (4520, 4521, 4522, 4523, 4524, 4525,
                                 4605, 4606, 4608, 4609, 4610)
   AND (
        (evidence_menu.menu_id = 4520
            AND evidence_menu.parent_id = 3000
            AND BINARY evidence_menu.path = BINARY 'sign-package'
            AND BINARY evidence_menu.component = BINARY 'oa/signPackage/index'
            AND BINARY evidence_menu.route_name = BINARY 'OaSignPackage'
            AND BINARY evidence_menu.perms = BINARY 'oa:signPackage:list')
     OR (evidence_menu.menu_id BETWEEN 4521 AND 4525
            AND evidence_menu.parent_id = 4520
            AND BINARY evidence_menu.perms = CAST(CASE evidence_menu.menu_id
                WHEN 4521 THEN 'oa:signPackage:query'
                WHEN 4522 THEN 'oa:signPackage:add'
                WHEN 4523 THEN 'oa:signPackage:send'
                WHEN 4524 THEN 'oa:signPackage:void'
                WHEN 4525 THEN 'oa:signPackage:template'
            END AS BINARY)
            AND EXISTS (
                SELECT 1 FROM sys_menu package_root
                 WHERE package_root.menu_id = 4520
                   AND package_root.parent_id = 3000
                   AND BINARY package_root.path = BINARY 'sign-package'
                   AND BINARY package_root.component = BINARY 'oa/signPackage/index'
                   AND BINARY package_root.route_name = BINARY 'OaSignPackage'
                   AND BINARY package_root.perms = BINARY 'oa:signPackage:list'))
     OR (evidence_menu.menu_id IN (4605, 4606, 4608, 4609, 4610)
            AND evidence_menu.parent_id = 4600
            AND BINARY evidence_menu.perms = CAST(CASE evidence_menu.menu_id
                WHEN 4605 THEN 'oa:signTask:retry'
                WHEN 4606 THEN 'oa:signTask:cancel'
                WHEN 4608 THEN 'oa:signPackage:list'
                WHEN 4609 THEN 'oa:signPackage:query'
                WHEN 4610 THEN 'oa:signPackage:add'
            END AS BINARY)
            AND EXISTS (
                SELECT 1 FROM sys_menu task_root
                 WHERE task_root.menu_id = 4600
                   AND task_root.parent_id = 3000
                   AND BINARY task_root.path = BINARY 'sign-task'
                   AND BINARY task_root.component = BINARY 'oa/signTask/index'
                   AND task_root.query IS NULL
                   AND BINARY task_root.route_name = BINARY 'OaSignTask'
                   AND task_root.is_frame = 1
                   AND task_root.is_cache = 0
                   AND task_root.menu_type = 'C'
                   AND BINARY task_root.perms = BINARY 'oa:signTask:list'))
   );

-- Snapshot only equivalent permissions for eligible business roles. Technical-evidence
-- grants are copied separately and never make a role eligible for the business entry.
DROP TEMPORARY TABLE IF EXISTS tmp_sign_role_permission_20260716;
CREATE TEMPORARY TABLE tmp_sign_role_permission_20260716 (
    role_id bigint NOT NULL,
    perms varchar(100) NOT NULL,
    PRIMARY KEY (role_id, perms)
) ENGINE=InnoDB;

INSERT IGNORE INTO tmp_sign_role_permission_20260716 (role_id, perms)
SELECT role_menu.role_id, old_menu.perms
  FROM sys_role_menu role_menu
  JOIN sys_menu old_menu ON old_menu.menu_id = role_menu.menu_id
  JOIN tmp_sign_eligible_role_20260716 eligible ON eligible.role_id = role_menu.role_id
 WHERE (old_menu.menu_id = 4600
            AND old_menu.parent_id = 3000
            AND BINARY old_menu.path = BINARY 'sign-task'
            AND BINARY old_menu.component = BINARY 'oa/signTask/index'
            AND old_menu.query IS NULL
            AND BINARY old_menu.route_name = BINARY 'OaSignTask'
            AND old_menu.is_frame = 1
            AND old_menu.is_cache = 0
            AND old_menu.menu_type = 'C'
            AND BINARY old_menu.perms = BINARY 'oa:signTask:list')
    OR (old_menu.menu_id IN (4601, 4602, 4604, 4605, 4606, 4608, 4609, 4610, 4611)
            AND old_menu.parent_id = 4600
            AND BINARY old_menu.perms = CAST(CASE old_menu.menu_id
                WHEN 4601 THEN 'oa:signTask:query'
                WHEN 4602 THEN 'oa:signTask:revalidate'
                WHEN 4604 THEN 'oa:signTask:send'
                WHEN 4605 THEN 'oa:signTask:retry'
                WHEN 4606 THEN 'oa:signTask:cancel'
                WHEN 4608 THEN 'oa:signPackage:list'
                WHEN 4609 THEN 'oa:signPackage:query'
                WHEN 4610 THEN 'oa:signPackage:add'
                WHEN 4611 THEN 'oa:signPackage:template'
            END AS BINARY))
    OR (old_menu.menu_id = 4520
            AND old_menu.parent_id = 3000
            AND BINARY old_menu.path = BINARY 'sign-package'
            AND BINARY old_menu.component = BINARY 'oa/signPackage/index'
            AND BINARY old_menu.route_name = BINARY 'OaSignPackage'
            AND BINARY old_menu.perms = BINARY 'oa:signPackage:list')
    OR (old_menu.menu_id BETWEEN 4521 AND 4525
            AND old_menu.parent_id = 4520
            AND BINARY old_menu.perms = CAST(CASE old_menu.menu_id
                WHEN 4521 THEN 'oa:signPackage:query'
                WHEN 4522 THEN 'oa:signPackage:add'
                WHEN 4523 THEN 'oa:signPackage:send'
                WHEN 4524 THEN 'oa:signPackage:void'
                WHEN 4525 THEN 'oa:signPackage:template'
            END AS BINARY));

INSERT IGNORE INTO tmp_sign_role_permission_20260716 (role_id, perms)
SELECT role_menu.role_id, old_menu.perms
  FROM sys_role_menu role_menu
  JOIN sys_menu old_menu ON old_menu.menu_id = role_menu.menu_id
 WHERE old_menu.menu_id = 4607
   AND old_menu.parent_id = 4600
   AND old_menu.menu_type = 'F'
   AND old_menu.perms = 'oa:signTask:technicalEvidence';

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, update_by, update_time, remark)
VALUES
    (9650, '合同签约中心', 3000, 11, 'sign-task', 'oa/signTask/index', NULL,
     'OaSignTask', 1, 0, 'C', '0', '0', 'oa:signTask:list', 'form',
     'system', NOW(), 'system', NOW(), '合同签约中心独立菜单ID区间'),
    (9651, '签约任务查询', 9650, 1, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signTask:query', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9652, '签约任务校验', 9650, 2, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signTask:revalidate', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9653, '签约任务发送', 9650, 3, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signTask:send', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9654, '签约任务重试', 9650, 4, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signTask:retry', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9655, '签约任务取消', 9650, 5, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signTask:cancel', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9656, '签约技术证据', 9650, 6, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signTask:technicalEvidence', '#',
     'system', NOW(), 'system', NOW(), '仅审计或系统管理员独立授权'),
    (9657, '签约包列表', 9650, 7, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signPackage:list', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9658, '签约包查询', 9650, 8, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signPackage:query', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9659, '签约包草稿维护', 9650, 9, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signPackage:add', '#',
     'system', NOW(), 'system', NOW(), '人工任务补资料所需；权限拆分前默认授予唯一HR'),
    (9660, '签约包发送', 9650, 10, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signPackage:send', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9661, '签约包撤回', 9650, 11, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signPackage:void', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9662, '签约模板方案', 9650, 12, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signPackage:template', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9663, '签约人工催办', 9650, 13, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signTask:remind', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9664, '拒签处置', 9650, 14, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signTask:resolveRefusal', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9665, '过期处置', 9650, 15, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signTask:resolveExpiry', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9666, '公司主体查看', 9650, 16, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signCompany:list', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9667, '公司主体维护', 9650, 17, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signCompany:edit', '#',
     'system', NOW(), 'system', NOW(), '按上线职责独立授权'),
    (9668, '合同印章查看', 9650, 18, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signSeal:list', '#',
     'system', NOW(), 'system', NOW(), ''),
    (9669, '合同印章维护', 9650, 19, '', NULL, NULL, '',
     1, 0, 'F', '0', '0', 'oa:signSeal:edit', '#',
     'system', NOW(), 'system', NOW(), '按上线职责独立授权')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    query = VALUES(query),
    route_name = VALUES(route_name),
    is_frame = VALUES(is_frame),
    is_cache = VALUES(is_cache),
    menu_type = VALUES(menu_type),
    visible = VALUES(visible),
    status = VALUES(status),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_by = 'system',
    update_time = NOW(),
    remark = VALUES(remark);

-- Preserve the permissions that roles genuinely held before the repair, without granting
-- missing permissions to unrelated roles.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT snapshot.role_id, new_menu.menu_id
  FROM tmp_sign_role_permission_20260716 snapshot
  JOIN sys_menu new_menu
    ON BINARY new_menu.perms = BINARY snapshot.perms
   AND (new_menu.menu_id = 9650 OR new_menu.parent_id = 9650);

-- Keep the legacy sign-package C route independently reachable. A package role is not
-- a task-center role: retaining 4520 (and its existing button grants) must never be used
-- as evidence for granting the task-center root. Add only the already-existing OA parent
-- required by the RuoYi router builder, and only for roles that own the exact legacy entry.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT package_role.role_id, oa_root.menu_id
  FROM sys_role_menu package_role
  JOIN sys_menu package_root
    ON package_root.menu_id = package_role.menu_id
  JOIN sys_menu oa_root
    ON oa_root.menu_id = 3000
   AND oa_root.parent_id = 0
   AND BINARY oa_root.path = BINARY 'oa'
   AND COALESCE(oa_root.component, '') = ''
   AND BINARY oa_root.route_name = BINARY 'OaRoot'
   AND oa_root.menu_type = 'M'
   AND COALESCE(oa_root.perms, '') = ''
   AND oa_root.is_frame = 1
   AND oa_root.visible = '0'
   AND oa_root.status = '0'
 WHERE package_root.menu_id = 4520
   AND package_root.parent_id = 3000
   AND BINARY package_root.path = BINARY 'sign-package'
   AND BINARY package_root.component = BINARY 'oa/signPackage/index'
   AND package_root.query IS NULL
   AND BINARY package_root.route_name = BINARY 'OaSignPackage'
   AND package_root.is_frame = 1
   AND package_root.is_cache = 0
   AND package_root.menu_type = 'C'
   AND package_root.visible = '0'
   AND package_root.status = '0'
   AND BINARY package_root.perms = BINARY 'oa:signPackage:list';

-- Migrate the task-center entry only from an unmistakable legacy task entry. Legacy
-- package-only roles retain equivalent child grants but are not promoted into an HR-only
-- task center that the service layer will reject.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_menu.role_id, 9650
  FROM sys_role_menu role_menu
  JOIN sys_menu old_entry ON old_entry.menu_id = role_menu.menu_id
 WHERE old_entry.menu_id = 4600
   AND old_entry.parent_id = 3000
   AND BINARY old_entry.path = BINARY 'sign-task'
   AND BINARY old_entry.component = BINARY 'oa/signTask/index'
   AND old_entry.query IS NULL
   AND BINARY old_entry.route_name = BINARY 'OaSignTask'
   AND old_entry.is_frame = 1
   AND old_entry.is_cache = 0
   AND old_entry.menu_type = 'C'
   AND BINARY old_entry.perms = BINARY 'oa:signTask:list';

-- Remove links only from exact legacy signing identities. Numeric ranges and LIKE
-- predicates are deliberately avoided because these IDs were reused by other features.
DELETE role_menu
  FROM sys_role_menu role_menu
  JOIN sys_menu old_menu ON old_menu.menu_id = role_menu.menu_id
 WHERE (old_menu.menu_id = 4600
            AND old_menu.parent_id = 3000
            AND BINARY old_menu.path = BINARY 'sign-task'
            AND BINARY old_menu.component = BINARY 'oa/signTask/index'
            AND old_menu.query IS NULL
            AND BINARY old_menu.route_name = BINARY 'OaSignTask'
            AND old_menu.is_frame = 1
            AND old_menu.is_cache = 0
            AND old_menu.menu_type IN ('C', 'M')
            AND BINARY old_menu.perms = BINARY 'oa:signTask:list')
    OR (old_menu.menu_id IN (4601, 4602, 4604, 4605, 4606, 4607, 4608, 4609, 4610, 4611)
            AND old_menu.parent_id = 4600
            AND BINARY old_menu.perms = CAST(CASE old_menu.menu_id
                WHEN 4601 THEN 'oa:signTask:query'
                WHEN 4602 THEN 'oa:signTask:revalidate'
                WHEN 4604 THEN 'oa:signTask:send'
                WHEN 4605 THEN 'oa:signTask:retry'
                WHEN 4606 THEN 'oa:signTask:cancel'
                WHEN 4607 THEN 'oa:signTask:technicalEvidence'
                WHEN 4608 THEN 'oa:signPackage:list'
                WHEN 4609 THEN 'oa:signPackage:query'
                WHEN 4610 THEN 'oa:signPackage:add'
                WHEN 4611 THEN 'oa:signPackage:template'
            END AS BINARY))
    OR (old_menu.menu_id = 4603
            AND old_menu.parent_id = 4600
            AND COALESCE(old_menu.path, '') = ''
            AND old_menu.component IS NULL
            AND old_menu.query IS NULL
            AND COALESCE(old_menu.route_name, '') = ''
            AND old_menu.is_frame = 1
            AND old_menu.is_cache = 0
            AND old_menu.menu_type = 'F'
            AND BINARY old_menu.perms = BINARY 'oa:signTask:confirm');

-- Disable only the legacy task root whose complete route identity is unmistakably
-- signing-owned. The independent legacy sign-package route remains active.
UPDATE sys_menu old_menu
   SET old_menu.visible = '1',
       old_menu.status = '1',
       old_menu.update_by = 'system',
       old_menu.update_time = NOW(),
       old_menu.remark = CASE
           WHEN COALESCE(old_menu.remark, '') LIKE '已由20260716签约菜单前向迁移替代；%'
               THEN old_menu.remark
           ELSE CONCAT('已由20260716签约菜单前向迁移替代；', COALESCE(old_menu.remark, ''))
       END
 WHERE (old_menu.menu_id = 4600
            AND old_menu.parent_id = 3000
            AND BINARY old_menu.path = BINARY 'sign-task'
            AND BINARY old_menu.component = BINARY 'oa/signTask/index'
            AND old_menu.query IS NULL
            AND BINARY old_menu.route_name = BINARY 'OaSignTask'
            AND old_menu.is_frame = 1
            AND old_menu.is_cache = 0
            AND old_menu.menu_type IN ('C', 'M')
            AND BINARY old_menu.perms = BINARY 'oa:signTask:list');

-- The one-time confirmation action was retired rather than remapped. Disable it only
-- when the row still has the complete legacy button identity; HR-owned 4603 is untouched.
UPDATE sys_menu old_menu
   SET old_menu.visible = '1',
       old_menu.status = '1',
       old_menu.update_by = 'system',
       old_menu.update_time = NOW(),
       old_menu.remark = CASE
           WHEN COALESCE(old_menu.remark, '') LIKE '已由20260716签约菜单前向迁移替代；%'
               THEN old_menu.remark
           ELSE CONCAT('已由20260716签约菜单前向迁移替代；', COALESCE(old_menu.remark, ''))
       END
 WHERE old_menu.menu_id = 4603
   AND old_menu.parent_id = 4600
   AND COALESCE(old_menu.path, '') = ''
   AND old_menu.component IS NULL
   AND old_menu.query IS NULL
   AND COALESCE(old_menu.route_name, '') = ''
   AND old_menu.is_frame = 1
   AND old_menu.is_cache = 0
   AND old_menu.menu_type = 'F'
   AND BINARY old_menu.perms = BINARY 'oa:signTask:confirm';

-- Historical tracked IDs may now belong to HR. Remove only the feature ownership record;
-- role links on HR-owned rows were not deleted above.
DELETE FROM sys_sign_hr_menu_grant
 WHERE menu_id BETWEEN 4600 AND 4611;

DROP TEMPORARY TABLE IF EXISTS tmp_sign_role_permission_20260716;
DROP TEMPORARY TABLE IF EXISTS tmp_sign_eligible_role_20260716;

-- Canonical single-HR synchronization. Existing wrapper names are redefined below so
-- SysConfigMapper and already deployed lifecycle code remain compatible.
DROP PROCEDURE IF EXISTS sync_sign_hr_permissions;
DELIMITER $$
CREATE PROCEDURE sync_sign_hr_permissions()
BEGIN
    DECLARE current_hr_user_id bigint DEFAULT NULL;
    DECLARE current_hr_config_value varchar(500) DEFAULT NULL;
    DECLARE previous_hr_user_id bigint DEFAULT NULL;
    DECLARE managed_sign_role_id bigint DEFAULT NULL;
    DECLARE managed_role_count int DEFAULT 0;
    DECLARE managed_role_key varchar(100) DEFAULT NULL;
    DECLARE sign_role_count int DEFAULT 0;
    DECLARE has_resolution_status int DEFAULT 0;

    SELECT hr_user_id, managed_role_id
      INTO previous_hr_user_id, managed_sign_role_id
      FROM sys_sign_hr_state
     WHERE state_id = 1
     FOR UPDATE;

    SELECT config.config_value
      INTO current_hr_config_value
      FROM sys_config config
     WHERE config.config_key = 'sign.hr.user-id'
     ORDER BY config.config_id DESC
     LIMIT 1
     FOR UPDATE;

    IF current_hr_config_value REGEXP '^[1-9][0-9]*$' THEN
        SELECT configured_hr.user_id
          INTO current_hr_user_id
          FROM sys_user configured_hr
         WHERE configured_hr.user_id = CAST(current_hr_config_value AS UNSIGNED)
           AND configured_hr.status = '0'
           AND configured_hr.del_flag = '0'
         LIMIT 1
         FOR UPDATE;
    END IF;

    SELECT COUNT(*) INTO has_resolution_status
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'oa_sign_task'
       AND COLUMN_NAME = 'resolution_status';

    SELECT COUNT(*) INTO sign_role_count
      FROM sys_role
     WHERE BINARY role_key = BINARY 'sign_single_hr';
    IF sign_role_count > 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Multiple sign_single_hr roles exist';
    END IF;

    IF managed_sign_role_id IS NOT NULL THEN
        SELECT COUNT(*), MAX(role_key)
          INTO managed_role_count, managed_role_key
          FROM sys_role
         WHERE role_id = managed_sign_role_id;
        IF managed_role_count = 0 THEN
            SET managed_sign_role_id = NULL;
        ELSEIF NOT COALESCE(BINARY managed_role_key = BINARY 'sign_single_hr', 0) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Managed signing role ID belongs to another role';
        END IF;
    END IF;

    -- Revoke only the OA navigation parent plus 17 business grants owned by the base
    -- synchronizer. Lifecycle and independently administered permissions never enter
    -- this managed set.
    DELETE role_menu
      FROM sys_role_menu role_menu
      JOIN sys_sign_hr_menu_grant managed
        ON managed.role_id = role_menu.role_id
       AND managed.menu_id = role_menu.menu_id
     WHERE managed.menu_id IN (
        3000, 9650, 9651, 9652, 9653, 9654, 9655, 9657, 9658, 9659,
        9660, 9661, 9662, 9663, 9664, 9665, 9666, 9668
     );
    DELETE FROM sys_sign_hr_menu_grant
     WHERE menu_id IN (
        3000, 9650, 9651, 9652, 9653, 9654, 9655, 9657, 9658, 9659,
        9660, 9661, 9662, 9663, 9664, 9665, 9666, 9668
     );

    IF managed_sign_role_id IS NOT NULL THEN
        DELETE FROM sys_role_menu
         WHERE role_id = managed_sign_role_id
           AND menu_id IN (
              3000, 9650, 9651, 9652, 9653, 9654, 9655, 9657, 9658, 9659,
              9660, 9661, 9662, 9663, 9664, 9665, 9666, 9668
           );
    END IF;

    IF current_hr_user_id IS NOT NULL THEN
        IF managed_sign_role_id IS NULL THEN
            SELECT role_id
              INTO managed_sign_role_id
              FROM sys_role
             WHERE BINARY role_key = BINARY 'sign_single_hr'
             LIMIT 1
             FOR UPDATE;
        END IF;

        IF managed_sign_role_id IS NULL THEN
            INSERT INTO sys_role
                (role_name, role_key, role_sort, data_scope, menu_check_strictly,
                 dept_check_strictly, status, del_flag, create_by, create_time, remark)
            VALUES
                ('唯一HR签约', 'sign_single_hr', 90, '5', 1,
                 1, '0', '0', 'system', NOW(), '合同签约中心功能专用角色，仅关联当前配置HR');
            SET managed_sign_role_id = LAST_INSERT_ID();
        END IF;

        UPDATE sys_role
           SET role_name = '唯一HR签约',
               role_key = 'sign_single_hr',
               role_sort = 90,
               data_scope = '5',
               menu_check_strictly = 1,
               dept_check_strictly = 1,
               status = '0',
               del_flag = '0',
               update_by = 'system',
               update_time = NOW(),
               remark = '合同签约中心功能专用角色，仅关联当前配置HR'
         WHERE role_id = managed_sign_role_id;

        IF previous_hr_user_id IS NOT NULL AND previous_hr_user_id <> current_hr_user_id THEN
            DROP TEMPORARY TABLE IF EXISTS tmp_sign_hr_reassignment_task_20260716;
            CREATE TEMPORARY TABLE tmp_sign_hr_reassignment_task_20260716 (
                task_id bigint NOT NULL,
                PRIMARY KEY (task_id)
            ) ENGINE=InnoDB;

            IF has_resolution_status = 1 THEN
                INSERT IGNORE INTO tmp_sign_hr_reassignment_task_20260716 (task_id)
                SELECT task_id
                  FROM oa_sign_task
                 WHERE assigned_hr_user_id <> current_hr_user_id
                   AND (
                       status NOT IN ('SIGNED', 'REFUSED', 'EXPIRED', 'CANCELLED', 'NO_ACTION')
                       OR (status IN ('REFUSED', 'EXPIRED') AND resolution_status = 'OPEN')
                   );
            ELSE
                INSERT IGNORE INTO tmp_sign_hr_reassignment_task_20260716 (task_id)
                SELECT task_id
                  FROM oa_sign_task
                 WHERE assigned_hr_user_id <> current_hr_user_id
                   AND status NOT IN ('SIGNED', 'REFUSED', 'EXPIRED', 'CANCELLED', 'NO_ACTION');
            END IF;

            INSERT INTO oa_sign_task_hr_reassignment
                (task_id, old_hr_user_id, new_hr_user_id, task_status, reassigned_time)
            SELECT task.task_id, task.assigned_hr_user_id, current_hr_user_id, task.status, NOW()
              FROM oa_sign_task task
              JOIN tmp_sign_hr_reassignment_task_20260716 pending
                ON pending.task_id = task.task_id;

            -- An unfinished HR-routed notification must never remain deliverable to the
            -- former HR. Mark the old-recipient row DEAD as explicit reassignment evidence,
            -- then reactivate or clone the same business event for the current HR. SENT rows
            -- are immutable delivery history and are deliberately left untouched.
            UPDATE oa_sign_notification_outbox notification
            JOIN tmp_sign_hr_reassignment_task_20260716 pending
              ON pending.task_id = CAST(JSON_UNQUOTE(JSON_EXTRACT(
                    notification.payload_json, '$.taskId')) AS UNSIGNED)
               SET notification.status = 'DEAD',
                   notification.next_retry_time = NULL,
                   notification.last_result = 'HR_REASSIGNED',
                   notification.last_error = LEFT(CONCAT(
                       'HR_REASSIGNED:', COALESCE(notification.last_error, '')), 1000),
                   notification.version = notification.version + 1,
                   notification.updated_time = NOW()
             WHERE JSON_UNQUOTE(JSON_EXTRACT(
                       notification.payload_json, '$.routeType')) = 'OA_SIGN_HR_TASK'
               AND notification.recipient_user_id <> current_hr_user_id
               AND notification.status IN ('PENDING', 'SENDING', 'RETRY', 'DEAD');

            UPDATE oa_sign_notification_outbox notification
            JOIN tmp_sign_hr_reassignment_task_20260716 pending
              ON pending.task_id = CAST(JSON_UNQUOTE(JSON_EXTRACT(
                    notification.payload_json, '$.taskId')) AS UNSIGNED)
               SET notification.payload_json = JSON_SET(
                       notification.payload_json, '$.hrUserId', current_hr_user_id),
                   notification.status = 'PENDING',
                   notification.retry_count = 0,
                   notification.next_retry_time = NULL,
                   notification.last_result = 'HR_REASSIGNED',
                   notification.last_error = NULL,
                   notification.version = notification.version + 1,
                   notification.updated_time = NOW()
             WHERE JSON_UNQUOTE(JSON_EXTRACT(
                       notification.payload_json, '$.routeType')) = 'OA_SIGN_HR_TASK'
               AND notification.recipient_user_id = current_hr_user_id
               AND notification.status IN ('PENDING', 'RETRY', 'DEAD');

            INSERT IGNORE INTO oa_sign_notification_outbox
                (channel, recipient_user_id, business_key, payload_json, status,
                 retry_count, next_retry_time, last_result, last_error, version,
                 created_time, updated_time)
            SELECT old_notification.channel,
                   current_hr_user_id,
                   old_notification.business_key,
                   JSON_SET(old_notification.payload_json,
                            '$.hrUserId', current_hr_user_id),
                   'PENDING', 0, NULL, 'HR_REASSIGNED', NULL, 0, NOW(), NOW()
              FROM oa_sign_notification_outbox old_notification
              JOIN tmp_sign_hr_reassignment_task_20260716 pending
                ON pending.task_id = CAST(JSON_UNQUOTE(JSON_EXTRACT(
                       old_notification.payload_json, '$.taskId')) AS UNSIGNED)
             WHERE JSON_UNQUOTE(JSON_EXTRACT(
                       old_notification.payload_json, '$.routeType')) = 'OA_SIGN_HR_TASK'
               AND old_notification.recipient_user_id <> current_hr_user_id
               AND old_notification.status = 'DEAD'
               AND old_notification.last_result = 'HR_REASSIGNED';

            UPDATE oa_sign_task task
            JOIN tmp_sign_hr_reassignment_task_20260716 pending
              ON pending.task_id = task.task_id
               SET task.assigned_hr_user_id = current_hr_user_id,
                   task.version = task.version + 1;

            DROP TEMPORARY TABLE IF EXISTS tmp_sign_hr_reassignment_task_20260716;
        END IF;

        INSERT INTO sys_sign_hr_state (state_id, hr_user_id, managed_role_id, updated_time)
        VALUES (1, current_hr_user_id, managed_sign_role_id, NOW())
        ON DUPLICATE KEY UPDATE
            hr_user_id = current_hr_user_id,
            managed_role_id = managed_sign_role_id,
            updated_time = NOW();

        DELETE FROM sys_user_role
         WHERE role_id = managed_sign_role_id
           AND user_id <> current_hr_user_id;
        INSERT IGNORE INTO sys_user_role (user_id, role_id)
        VALUES (current_hr_user_id, managed_sign_role_id);

        -- The RuoYi router builder starts at parent_id=0. Grant the OA directory only
        -- to the dedicated signing role so menu 9650 is reachable in its route tree.
        INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
        SELECT managed_sign_role_id, task_menu.menu_id
          FROM sys_menu task_menu
         WHERE task_menu.menu_id = 3000
           AND task_menu.parent_id = 0
           AND BINARY task_menu.path = BINARY 'oa'
           AND COALESCE(task_menu.component, '') = ''
           AND BINARY task_menu.route_name = BINARY 'OaRoot'
           AND task_menu.menu_type = 'M'
           AND COALESCE(task_menu.perms, '') = ''
           AND task_menu.is_frame = 1
           AND task_menu.visible = '0'
           AND task_menu.status = '0';

        INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
        SELECT managed_sign_role_id, task_menu.menu_id
          FROM sys_menu task_menu
         WHERE task_menu.menu_id IN (
                9650, 9651, 9652, 9653, 9654, 9655, 9657, 9658, 9659,
                9660, 9661, 9662, 9663, 9664, 9665, 9666, 9668
           )
           AND (task_menu.menu_id = 9650 OR task_menu.parent_id = 9650)
           AND task_menu.perms IN (
                'oa:signTask:list', 'oa:signTask:query', 'oa:signTask:revalidate',
                'oa:signTask:send', 'oa:signTask:retry', 'oa:signTask:cancel',
                'oa:signTask:remind', 'oa:signTask:resolveRefusal',
                'oa:signTask:resolveExpiry',
                'oa:signPackage:list', 'oa:signPackage:query', 'oa:signPackage:add',
                'oa:signPackage:send', 'oa:signPackage:void',
                'oa:signPackage:template', 'oa:signCompany:list', 'oa:signSeal:list'
           );

        INSERT INTO sys_sign_hr_menu_grant
            (role_id, menu_id, hr_user_id, created_time)
        SELECT managed_sign_role_id, task_menu.menu_id, current_hr_user_id, NOW()
          FROM sys_menu task_menu
          JOIN sys_role_menu role_menu
            ON role_menu.role_id = managed_sign_role_id
           AND role_menu.menu_id = task_menu.menu_id
         WHERE task_menu.menu_id = 3000
           AND task_menu.parent_id = 0
           AND BINARY task_menu.path = BINARY 'oa'
           AND COALESCE(task_menu.component, '') = ''
           AND BINARY task_menu.route_name = BINARY 'OaRoot'
           AND task_menu.menu_type = 'M'
           AND COALESCE(task_menu.perms, '') = ''
           AND task_menu.is_frame = 1
           AND task_menu.visible = '0'
           AND task_menu.status = '0'
        ON DUPLICATE KEY UPDATE
            hr_user_id = VALUES(hr_user_id),
            created_time = VALUES(created_time);

        INSERT INTO sys_sign_hr_menu_grant
            (role_id, menu_id, hr_user_id, created_time)
        SELECT managed_sign_role_id, task_menu.menu_id, current_hr_user_id, NOW()
          FROM sys_menu task_menu
          JOIN sys_role_menu role_menu
            ON role_menu.role_id = managed_sign_role_id
           AND role_menu.menu_id = task_menu.menu_id
         WHERE (task_menu.menu_id = 9650 OR task_menu.parent_id = 9650)
           AND task_menu.menu_id IN (
                9650, 9651, 9652, 9653, 9654, 9655, 9657, 9658, 9659,
                9660, 9661, 9662, 9663, 9664, 9665, 9666, 9668
           )
           AND task_menu.perms IN (
                'oa:signTask:list', 'oa:signTask:query', 'oa:signTask:revalidate',
                'oa:signTask:send', 'oa:signTask:retry', 'oa:signTask:cancel',
                'oa:signTask:remind', 'oa:signTask:resolveRefusal',
                'oa:signTask:resolveExpiry',
                'oa:signPackage:list', 'oa:signPackage:query', 'oa:signPackage:add',
                'oa:signPackage:send', 'oa:signPackage:void',
                'oa:signPackage:template', 'oa:signCompany:list', 'oa:signSeal:list'
           )
        ON DUPLICATE KEY UPDATE
            hr_user_id = VALUES(hr_user_id),
            created_time = VALUES(created_time);
    ELSEIF managed_sign_role_id IS NOT NULL THEN
        DELETE FROM sys_user_role WHERE role_id = managed_sign_role_id;
    END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_plan;
DELIMITER $$
CREATE PROCEDURE sync_sign_hr_permissions_with_plan()
BEGIN
    CALL sync_sign_hr_permissions();
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_transfer;
DELIMITER $$
CREATE PROCEDURE sync_sign_hr_permissions_with_transfer()
BEGIN
    DECLARE transfer_menu_count int DEFAULT 0;
    DECLARE transfer_menu_id bigint DEFAULT NULL;

    SELECT COUNT(*), MIN(menu_id)
      INTO transfer_menu_count, transfer_menu_id
      FROM sys_menu
     WHERE BINARY perms = BINARY 'hr:employee:transfer' AND status = '0';
    IF transfer_menu_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Expected one active HR transfer permission menu';
    END IF;

    CALL sync_sign_hr_permissions_with_plan();

    INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
    SELECT state.managed_role_id, transfer_menu_id
      FROM sys_sign_hr_state state
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL;

    INSERT INTO sys_sign_hr_menu_grant (role_id, menu_id, hr_user_id, created_time)
    SELECT state.managed_role_id, transfer_menu_id, state.hr_user_id, NOW()
      FROM sys_sign_hr_state state
      JOIN sys_role_menu role_menu
        ON role_menu.role_id = state.managed_role_id
       AND role_menu.menu_id = transfer_menu_id
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL
    ON DUPLICATE KEY UPDATE
        hr_user_id = VALUES(hr_user_id),
        created_time = VALUES(created_time);
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_offboarding;
DELIMITER $$
CREATE PROCEDURE sync_sign_hr_permissions_with_offboarding()
BEGIN
    DECLARE offboard_menu_count int DEFAULT 0;
    DECLARE offboard_menu_id bigint DEFAULT NULL;

    SELECT COUNT(*), MIN(menu_id)
      INTO offboard_menu_count, offboard_menu_id
      FROM sys_menu
     WHERE BINARY perms = BINARY 'hr:employee:offboard' AND status = '0';
    IF offboard_menu_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Expected one active HR offboarding permission menu';
    END IF;

    CALL sync_sign_hr_permissions_with_transfer();

    INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
    SELECT state.managed_role_id, offboard_menu_id
      FROM sys_sign_hr_state state
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL;

    INSERT INTO sys_sign_hr_menu_grant (role_id, menu_id, hr_user_id, created_time)
    SELECT state.managed_role_id, offboard_menu_id, state.hr_user_id, NOW()
      FROM sys_sign_hr_state state
      JOIN sys_role_menu role_menu
        ON role_menu.role_id = state.managed_role_id
       AND role_menu.menu_id = offboard_menu_id
     WHERE state.state_id = 1
       AND state.managed_role_id IS NOT NULL
       AND state.hr_user_id IS NOT NULL
    ON DUPLICATE KEY UPDATE
        hr_user_id = VALUES(hr_user_id),
        created_time = VALUES(created_time);
END$$
DELIMITER ;

-- The release-time synchronization is atomic. Runtime callers invoke the same
-- procedure inside their Spring transaction, so task ownership and outbox routing
-- also commit or roll back together after deployment.
START TRANSACTION;
CALL sync_sign_hr_permissions_with_offboarding();
COMMIT;
