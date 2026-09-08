-- 取消入职劳动合同社保口径与薪酬结构确认书版本的自动映射。
-- A/B 版本改由 HR 在方案中人工选择；社保口径继续作为独立路由条件。
-- 已发布方案版本保持不可变，需由 HR 重新发布后才采用人工选择规则。

UPDATE oa_sign_template
SET social_type = NULL,
    update_time = NOW(),
    remark = CONCAT_WS('；',
        NULLIF(TRIM(BOTH '；' FROM REPLACE(
            REPLACE(COALESCE(remark, ''), '20260719按社保口径固定薪酬确认书版本', ''),
            '20260719薪酬版本改为HR人工选择', '')), ''),
        '20260719薪酬版本改为HR人工选择')
WHERE template_type = 'ONBOARD_SALARY_CONFIRM'
  AND salary_version IN ('A', 'B');
