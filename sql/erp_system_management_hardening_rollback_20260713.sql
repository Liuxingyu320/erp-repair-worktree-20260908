-- 回滚系统管理 R1/R3 新增权限。
-- 只删除由本次脚本创建的菜单，避免误删此前已存在的同名权限。

START TRANSACTION;

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.perms IN (
    'system:role:dataScope',
    'system:operlog:detail',
    'system:salary:emergency',
    'system:user:authRole',
    'system:role:authUser',
    'system:config:refresh',
    'system:dict:refresh',
    'hr:employee:renewal',
    'hr:employee:regularize'
)
  AND m.create_by = 'system_hardening_20260713';

DELETE FROM sys_menu
WHERE perms IN (
    'system:role:dataScope',
    'system:operlog:detail',
    'system:salary:emergency',
    'system:user:authRole',
    'system:role:authUser',
    'system:config:refresh',
    'system:dict:refresh',
    'hr:employee:renewal',
    'hr:employee:regularize'
)
  AND create_by = 'system_hardening_20260713';

COMMIT;

-- R2 回滚边界：must_change_password 至少保留一个版本，应用回滚时不得立即删列。
-- 密码变化不可逆；如需暂停新拦截，请关闭应用配置 erp.security.must-change-password-enabled。

-- R3 回滚边界：sys_audit_archive_batch 可能已形成审计证据，不自动删表；
-- sys.audit.retention.enabled 默认仍为 false，可安全保留。

-- R5 回滚边界：配置、薪资和调拨审批规则的 version 列保持向后兼容，
-- 不自动删除，避免在应用回滚期间丢失并发控制状态。
