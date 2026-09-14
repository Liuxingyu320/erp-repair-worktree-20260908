-- Run only after the primary HR contract entry has passed acceptance.
-- Retain shared read permissions, history routes, pending signatures and shared company/seal records.
UPDATE sys_menu SET menu_name='历史工资记录', update_by='migration', update_time=sysdate()
WHERE menu_type='C' AND component='oa/salary/index';
UPDATE sys_menu SET menu_name='历史薪资方案', update_by='migration', update_time=sysdate()
WHERE menu_type='C' AND component='system/salary/index';
UPDATE sys_menu SET menu_name='历史劳动合同', update_by='migration', update_time=sysdate()
WHERE menu_type='C' AND component='oa/laborContract/index';
