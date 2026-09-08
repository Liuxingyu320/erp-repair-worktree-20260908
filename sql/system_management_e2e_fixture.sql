-- System-management browser acceptance fixture.
-- Required session variables (supplied by the guarded runner):
--   @e2e_prefix, @e2e_password_hash
-- This script is allowed only on an erp_system_release_rehearsal_* database.

SET @e2e_marker := CONCAT('system_e2e_', @e2e_prefix);
SET @super_username := CONCAT(@e2e_prefix, '_super');
SET @sysadmin_username := CONCAT(@e2e_prefix, '_sysadmin');
SET @hr_username := CONCAT(@e2e_prefix, '_hr');
SET @shop_username := CONCAT(@e2e_prefix, '_shop');
SET @same_probe_username := CONCAT(@e2e_prefix, '_probe_same');
SET @other_probe_username := CONCAT(@e2e_prefix, '_probe_other');
SET @sysadmin_role_key := CONCAT(@e2e_prefix, '_sysadmin');
SET @hr_role_key := CONCAT(@e2e_prefix, '_hr');
SET @shop_role_key := CONCAT(@e2e_prefix, '_shop');

START TRANSACTION;

-- Idempotent cleanup is deliberately limited to rows carrying this fixture marker.
DELETE p
FROM sys_user_profile p
JOIN sys_user u ON u.user_id = p.user_id
WHERE BINARY COALESCE(p.create_by, '') = BINARY @e2e_marker
   OR BINARY COALESCE(u.create_by, '') = BINARY @e2e_marker;

DELETE sus
FROM sys_user_shop sus
JOIN sys_user u ON u.user_id = sus.user_id
WHERE u.create_by = @e2e_marker;

DELETE sur
FROM sys_user_role sur
JOIN sys_user u ON u.user_id = sur.user_id
WHERE u.create_by = @e2e_marker;

DELETE sup
FROM sys_user_post sup
JOIN sys_user u ON u.user_id = sup.user_id
WHERE u.create_by = @e2e_marker;

DELETE FROM sys_user
WHERE user_id <> 1
  AND create_by = @e2e_marker;

DELETE rm
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE r.create_by = @e2e_marker;

DELETE rd
FROM sys_role_dept rd
JOIN sys_role r ON r.role_id = rd.role_id
WHERE r.create_by = @e2e_marker;

DELETE FROM sys_role
WHERE role_id <> 1
  AND create_by = @e2e_marker;

-- The application hard-codes user_id=1 as the true super administrator. On the
-- isolated clone only, rename it to the run prefix and replace its password.
UPDATE sys_user
SET user_name = @super_username,
    nick_name = 'E2E超级管理员',
    phonenumber = '13800000010',
    sex = '1',
    password = @e2e_password_hash,
    status = '0',
    del_flag = '0',
    must_change_password = '0',
    pwd_update_date = NOW(),
    update_by = @e2e_marker,
    update_time = NOW()
WHERE user_id = 1;

INSERT INTO sys_role
    (role_name, role_key, role_sort, data_scope, menu_check_strictly,
     dept_check_strictly, status, del_flag, create_by, create_time, remark)
VALUES
    ('E2E普通系统管理员', @sysadmin_role_key, 90, '2', 1, 1, '0', '0',
     @e2e_marker, NOW(), '系统管理发布前四角色验收；自定义部门范围'),
    ('E2E人事管理员', @hr_role_key, 91, '1', 1, 1, '0', '0',
     @e2e_marker, NOW(), '系统管理发布前四角色验收；HR专用权限'),
    ('E2E门店负责人', @shop_role_key, 92, '3', 1, 1, '0', '0',
     @e2e_marker, NOW(), '系统管理发布前四角色验收；本部门范围');

SET @sysadmin_role_id := (
    SELECT role_id FROM sys_role WHERE role_key = @sysadmin_role_key LIMIT 1
);
SET @hr_role_id := (
    SELECT role_id FROM sys_role WHERE role_key = @hr_role_key LIMIT 1
);
SET @shop_role_id := (
    SELECT role_id FROM sys_role WHERE role_key = @shop_role_key LIMIT 1
);

INSERT INTO sys_user
    (dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar,
     password, status, del_flag, pwd_update_date, must_change_password,
     create_by, create_time, remark)
VALUES
    (1126, @sysadmin_username, 'E2E普通系统管理员', '00', '', '13800000011', '0', '',
     @e2e_password_hash, '0', '0', NOW(), '0', @e2e_marker, NOW(), '四角色登录账号'),
    (1150, @hr_username, 'E2E人事管理员', '00', '', '13800000012', '1', '',
     @e2e_password_hash, '0', '0', NOW(), '0', @e2e_marker, NOW(), '四角色登录账号'),
    (1129, @shop_username, 'E2E门店负责人', '00', '', '13800000013', '0', '',
     @e2e_password_hash, '0', '0', NOW(), '1', @e2e_marker, NOW(), '首次登录强制改密账号'),
    (1126, @same_probe_username, 'E2E同部门探针', '00', '', '13800000001', '2', '',
     @e2e_password_hash, '0', '0', NOW(), '0', @e2e_marker, NOW(), '数据范围探针，不用于登录'),
    (1127, @other_probe_username, 'E2E跨部门探针', '00', '', '13800000002', '2', '',
     @e2e_password_hash, '0', '0', NOW(), '0', @e2e_marker, NOW(), '数据范围探针，不用于登录');

SET @sysadmin_user_id := (
    SELECT user_id FROM sys_user WHERE user_name = @sysadmin_username LIMIT 1
);
SET @hr_user_id := (
    SELECT user_id FROM sys_user WHERE user_name = @hr_username LIMIT 1
);
SET @shop_user_id := (
    SELECT user_id FROM sys_user WHERE user_name = @shop_username LIMIT 1
);
SET @same_probe_user_id := (
    SELECT user_id FROM sys_user WHERE user_name = @same_probe_username LIMIT 1
);
SET @other_probe_user_id := (
    SELECT user_id FROM sys_user WHERE user_name = @other_probe_username LIMIT 1
);

-- Login identities have complete synthetic self-service profiles so browser
-- acceptance can focus on authorization. The shop account still starts with
-- must_change_password=1 and therefore cannot use business APIs before changing it.
INSERT INTO sys_user_profile
    (user_id, employee_name, phone_number, sex, dept_id, employee_no, employee_status,
     birth_date, id_type, id_number, registered_residence, current_address,
     marital_status, ethnicity, emergency_contact, emergency_contact_relation,
     emergency_contact_phone, bank_name, bank_account, profile_source, create_by, create_time)
VALUES
    (1, 'E2E超级管理员', '13800000010', '1', 101,
     CONCAT('E2E-', @e2e_prefix, '-SUPER'), '正式', '1990-01-01', '居民身份证',
     '110101199001010015', 'E2E测试户籍地址', 'E2E测试现住地址', '未婚', '汉族',
     'E2E联系人', '亲属', '13900000010', 'E2E测试银行', '6222020202020202010',
     'SYSTEM_E2E', @e2e_marker, NOW()),
    (@sysadmin_user_id, 'E2E普通系统管理员', '13800000011', '0', 1126,
     CONCAT('E2E-', @e2e_prefix, '-SYSADMIN'), '正式', '1992-02-02', '居民身份证',
     '110101199202020025', 'E2E测试户籍地址', 'E2E测试现住地址', '未婚', '汉族',
     'E2E联系人', '亲属', '13900000011', 'E2E测试银行', '6222020202020202011',
     'SYSTEM_E2E', @e2e_marker, NOW()),
    (@hr_user_id, 'E2E人事管理员', '13800000012', '1', 1150,
     CONCAT('E2E-', @e2e_prefix, '-HR'), '正式', '1993-03-03', '居民身份证',
     '110101199303030038', 'E2E测试户籍地址', 'E2E测试现住地址', '未婚', '汉族',
     'E2E联系人', '亲属', '13900000012', 'E2E测试银行', '6222020202020202012',
     'SYSTEM_E2E', @e2e_marker, NOW()),
    (@shop_user_id, 'E2E门店负责人', '13800000013', '0', 1129,
     CONCAT('E2E-', @e2e_prefix, '-SHOP'), '正式', '1994-04-04', '居民身份证',
     '110101199404040040', 'E2E测试户籍地址', 'E2E测试现住地址', '未婚', '汉族',
     'E2E联系人', '亲属', '13900000013', 'E2E测试银行', '6222020202020202013',
     'SYSTEM_E2E', @e2e_marker, NOW());

INSERT INTO sys_user_profile
    (user_id, employee_name, phone_number, dept_id, employee_no, employee_status,
     id_type, id_number, current_address, emergency_contact, emergency_contact_phone,
     bank_name, bank_account, profile_source, create_by, create_time)
VALUES
    (@same_probe_user_id, 'E2E同部门探针', '13800000001', 1126,
     CONCAT('E2E-', @e2e_prefix, '-SAME'), '正式', '居民身份证',
     '110101199001010011', 'E2E测试地址一号', 'E2E联系人一', '13900000001',
     'E2E测试银行', '6222020202020202001', 'SYSTEM_E2E', @e2e_marker, NOW()),
    (@other_probe_user_id, 'E2E跨部门探针', '13800000002', 1127,
     CONCAT('E2E-', @e2e_prefix, '-OTHER'), '正式', '居民身份证',
     '110101199001010029', 'E2E测试地址二号', 'E2E联系人二', '13900000002',
     'E2E测试银行', '6222020202020202002', 'SYSTEM_E2E', @e2e_marker, NOW());

INSERT INTO sys_user_role (user_id, role_id)
VALUES
    (@sysadmin_user_id, @sysadmin_role_id),
    (@hr_user_id, @hr_role_id),
    (@shop_user_id, @shop_role_id);

-- Ordinary system administrator: broad system administration, but no emergency
-- salary override, account unlock, destructive log operations, or raw log body.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @sysadmin_role_id, m.menu_id
FROM sys_menu m
WHERE m.status = '0'
  AND (
      m.menu_id IN (1, 108)
      OR m.perms LIKE 'system:%'
  )
  AND COALESCE(m.perms, '') NOT IN (
      'system:salary:emergency',
      'system:logininfor:unlock',
      'system:logininfor:remove',
      'system:operlog:detail',
      'system:operlog:remove'
  );

-- HR receives explicit lifecycle and sensitive-field permissions, including the
-- two catalog entries added by the hardening migration, but no system high-risk rights.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @hr_role_id, m.menu_id
FROM sys_menu m
WHERE m.status = '0'
  AND (m.menu_id = 9605 OR m.perms LIKE 'hr:%');

-- Store manager may operate only the store-authorization surface; service-layer
-- shop scope must still prevent expanding outside the single assigned store.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @shop_role_id, m.menu_id
FROM sys_menu m
WHERE m.status = '0'
  AND (m.menu_id IN (1, 181) OR m.perms IN (
      'system:userShop:list', 'system:userShop:query', 'system:userShop:edit'
  ));

INSERT INTO sys_role_dept (role_id, dept_id)
VALUES (@sysadmin_role_id, 1126);

INSERT INTO sys_user_shop (user_id, dept_id, is_default, create_by, create_time)
VALUES (@shop_user_id, 1129, 'Y', @e2e_marker, NOW());

COMMIT;

SELECT CONCAT_WS('|',
    (SELECT COUNT(*) FROM sys_user WHERE user_name IN (
        @super_username, @sysadmin_username, @hr_username, @shop_username)),
    (SELECT COUNT(*) FROM sys_user WHERE create_by = @e2e_marker),
    (SELECT COUNT(*) FROM sys_role WHERE create_by = @e2e_marker),
    (SELECT COUNT(*) FROM sys_role_menu WHERE role_id IN (
        @sysadmin_role_id, @hr_role_id, @shop_role_id)),
    (SELECT COUNT(*) FROM sys_user_shop WHERE user_id = @shop_user_id),
    (SELECT COUNT(*) FROM sys_user WHERE user_name = @shop_username
        AND must_change_password = '1'),
    (SELECT COUNT(*) FROM sys_user_profile
        WHERE user_id IN (@same_probe_user_id, @other_probe_user_id))
) AS fixture_signature;
