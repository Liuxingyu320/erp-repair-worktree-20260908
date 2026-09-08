-- ERP unified approval center: repeat-safe, fail-closed seed and permissions.
-- Schema is supplied by erp_unified_approval_schema_20260716.sql.
-- This automatic migration never enables inventory native-approval flags and
-- never switches inventory templates/rules to NATIVE. Cutover requires a
-- separate, explicitly approved release after validation and UAT.

-- 内置审批业务目录。重复执行时不覆盖管理员后续发布状态或引擎切换。
INSERT INTO approval_template
    (business_code, template_name, business_source, engine_mode, definition_mode,
     legacy_adapter_code, callback_service, template_status,
     create_by, create_time, remark)
SELECT 'OA_PURCHASE', 'OA采购申请', 'oa', 'NATIVE', 'LIMITED',
       NULL, 'erp-oa', 'DRAFT', 'system', NOW(),
       '财务负责人配置并通过覆盖检查后方可发布'
WHERE NOT EXISTS (
    SELECT 1 FROM approval_template WHERE business_code = 'OA_PURCHASE'
);

INSERT INTO approval_template
    (business_code, template_name, business_source, engine_mode, definition_mode,
     legacy_adapter_code, callback_service, template_status,
     create_by, create_time, remark)
SELECT 'INV_TRANSFER', '调拨审批', 'inventory', 'LEGACY', 'FIXED',
       'INV_TRANSFER', 'erp-inventory', 'ACTIVE', 'system', NOW(),
       '固定四级审批链；存量实例继续由库存旧引擎处理'
WHERE NOT EXISTS (
    SELECT 1 FROM approval_template WHERE business_code = 'INV_TRANSFER'
);

INSERT INTO approval_template
    (business_code, template_name, business_source, engine_mode, definition_mode,
     legacy_adapter_code, callback_service, template_status,
     create_by, create_time, remark)
SELECT 'INV_STOCK_CHECK', '库存盘点审批', 'inventory', 'LEGACY', 'FIXED',
       'INV_STOCK_CHECK', 'erp-inventory', 'ACTIVE', 'system', NOW(),
       '迁移前由库存盘点旧审批链处理'
WHERE NOT EXISTS (
    SELECT 1 FROM approval_template WHERE business_code = 'INV_STOCK_CHECK'
);

INSERT INTO approval_template
    (business_code, template_name, business_source, engine_mode, definition_mode,
     legacy_adapter_code, callback_service, template_status,
     create_by, create_time, remark)
SELECT 'HR_HEALTH_CERTIFICATE', '健康证审核', 'system', 'LEGACY', 'FIXED',
       'HR_HEALTH_CERTIFICATE', 'erp-system', 'ACTIVE', 'system', NOW(),
       '迁移前由健康证业务状态审核逻辑处理'
WHERE NOT EXISTS (
    SELECT 1 FROM approval_template WHERE business_code = 'HR_HEALTH_CERTIFICATE'
);

-- 健康证新申请直接使用统一引擎。候选人仅来自锚点组织授权范围内的在职权限持有人。
SET @health_approval_template_id := (
    SELECT template_id FROM approval_template
    WHERE business_code = 'HR_HEALTH_CERTIFICATE' LIMIT 1
);

-- Capture the one seed-owned legacy shape that may be promoted. A later
-- administrator DISABLED state or any manual template edit is never reopened.
SET @health_seed_template_promotable := (
    SELECT IF(EXISTS (
        SELECT 1 FROM approval_template seed_template
        WHERE seed_template.template_id = @health_approval_template_id
          AND seed_template.template_name = '健康证审核'
          AND seed_template.business_source = 'system'
          AND seed_template.engine_mode = 'LEGACY'
          AND seed_template.definition_mode = 'FIXED'
          AND seed_template.legacy_adapter_code = 'HR_HEALTH_CERTIFICATE'
          AND seed_template.callback_service = 'erp-system'
          AND seed_template.template_status = 'ACTIVE'
          AND seed_template.create_by = 'system'
          AND COALESCE(NULLIF(seed_template.update_by, ''), 'system') =
              'system'
          AND seed_template.remark = '迁移前由健康证业务状态审核逻辑处理'
    ), 1, 0)
);

INSERT INTO approval_rule
    (template_id, rule_code, rule_name, scope_type, scope_id, scope_name,
     business_subtype, rule_status, current_version_id, latest_version_no,
     lock_version, create_by, create_time, remark)
SELECT @health_approval_template_id, 'HR_HEALTH_CERT_DEFAULT', '健康证默认审核',
       'ALL', NULL, NULL, 'ALL', 'DRAFT', NULL, 1, 0, 'system', NOW(),
       '按hr:healthCertificate:review权限和授权组织范围解析'
WHERE @health_approval_template_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule
      WHERE rule_code = 'HR_HEALTH_CERT_DEFAULT'
  );

SET @health_approval_rule_id := (
    SELECT rule_id FROM approval_rule
    WHERE rule_code = 'HR_HEALTH_CERT_DEFAULT'
      AND template_id = @health_approval_template_id
    LIMIT 1
);

-- Only the untouched seed DRAFT may advance. ACTIVE/DISABLED or a selector
-- edited by an administrator remains exactly as configured on every rerun.
SET @health_seed_rule_activatable := (
    SELECT IF(EXISTS (
        SELECT 1 FROM approval_rule seed_rule
        WHERE seed_rule.rule_id = @health_approval_rule_id
          AND seed_rule.rule_code = 'HR_HEALTH_CERT_DEFAULT'
          AND seed_rule.rule_name = '健康证默认审核'
          AND seed_rule.scope_type = 'ALL'
          AND seed_rule.scope_id IS NULL
          AND seed_rule.scope_name IS NULL
          AND seed_rule.business_subtype = 'ALL'
          AND seed_rule.rule_status = 'DRAFT'
          AND seed_rule.current_version_id IS NULL
          AND seed_rule.latest_version_no = 1
          AND seed_rule.lock_version = 0
          AND seed_rule.create_by = 'system'
          AND COALESCE(NULLIF(seed_rule.update_by, ''), 'system') = 'system'
          AND seed_rule.remark =
              '按hr:healthCertificate:review权限和授权组织范围解析'
    ), 1, 0)
);

SET @health_definition_snapshot :=
    '{"schemaVersion":1,"versionNo":1,"selector":{"ruleCode":"HR_HEALTH_CERT_DEFAULT","ruleName":"健康证默认审核","scopeType":"ALL","scopeId":null,"scopeName":null,"businessSubtype":"ALL"},"conditions":[],"nodes":[{"nodeOrder":1,"nodeCode":"HEALTH_CERT_REVIEWER","nodeName":"健康证审核人","strategyType":"BUSINESS_STRATEGY","strategyCode":"PERMISSION_HOLDER","strategyConfig":{"permissionKey":"hr:healthCertificate:review"},"approvalMode":"ANY_ONE","requiredCount":1,"missingPolicy":"BLOCK","selfPolicy":"BLOCK","returnAllowed":"1","rejectAllowed":"0"}]}';

INSERT INTO approval_rule_version
    (rule_id, version_no, version_status, definition_snapshot,
     definition_checksum, published_by_user_id, published_by_name,
     published_time, lock_version, create_by, create_time, remark)
SELECT @health_approval_rule_id, 1, 'PUBLISHED', @health_definition_snapshot,
       SHA2(@health_definition_snapshot, 256), NULL, 'system', NOW(), 0,
       'system', NOW(), '内置健康证审核版本'
WHERE @health_approval_rule_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule_version
      WHERE rule_id = @health_approval_rule_id AND version_no = 1
  );

SET @health_approval_version_id := (
    SELECT version_id FROM approval_rule_version
    WHERE rule_id = @health_approval_rule_id AND version_no = 1 LIMIT 1
);

-- 兼容早期只创建了草稿的重跑场景；已被实例引用的版本绝不改写。
SET @health_seed_version_mutable := (
    SELECT IF(EXISTS (
        SELECT 1
        FROM approval_rule_version seed_version
        WHERE seed_version.version_id = @health_approval_version_id
          AND seed_version.version_status = 'DRAFT'
          AND seed_version.create_by = 'system'
          AND COALESCE(NULLIF(seed_version.update_by, ''), 'system') =
              'system'
          AND (
              seed_version.definition_snapshot IS NULL
              OR seed_version.definition_snapshot = ''
              OR (
                  BINARY seed_version.definition_snapshot =
                      BINARY @health_definition_snapshot
                  AND BINARY seed_version.definition_checksum =
                      BINARY SHA2(@health_definition_snapshot, 256)
              )
          )
          AND NOT EXISTS (
              SELECT 1 FROM approval_instance used_instance
              WHERE used_instance.rule_version_id = seed_version.version_id
          )
          AND NOT EXISTS (
              SELECT 1 FROM approval_rule_condition custom_condition
              WHERE custom_condition.version_id = seed_version.version_id
          )
          AND NOT EXISTS (
              SELECT 1 FROM approval_version_node custom_node
              WHERE custom_node.version_id = seed_version.version_id
                AND (
                    custom_node.node_order <> 1
                    OR custom_node.node_code <> 'HEALTH_CERT_REVIEWER'
                    OR custom_node.node_name <> '健康证审核人'
                    OR custom_node.strategy_type <> 'BUSINESS_STRATEGY'
                    OR custom_node.strategy_code <> 'PERMISSION_HOLDER'
                    OR BINARY custom_node.strategy_config <>
                        BINARY '{"permissionKey":"hr:healthCertificate:review"}'
                    OR custom_node.approval_mode <> 'ANY_ONE'
                    OR custom_node.required_count <> 1
                    OR custom_node.missing_policy <> 'BLOCK'
                    OR custom_node.self_policy <> 'BLOCK'
                    OR custom_node.return_allowed <> '1'
                    OR custom_node.reject_allowed <> '0'
                    OR custom_node.create_by <> 'system'
                    OR COALESCE(NULLIF(custom_node.update_by, ''),
                                'system') <> 'system'
                    OR custom_node.remark <>
                        '按权限与锚点授权组织范围解析'
                )
          )
    ), 1, 0)
);

UPDATE approval_rule_version
SET version_status = 'PUBLISHED',
    definition_snapshot = @health_definition_snapshot,
    definition_checksum = SHA2(@health_definition_snapshot, 256),
    published_by_name = 'system', published_time = NOW(),
    lock_version = lock_version + 1,
    update_by = 'system', update_time = NOW(),
    remark = '内置健康证审核版本'
WHERE version_id = @health_approval_version_id
  AND @health_seed_version_mutable = 1;

INSERT INTO approval_version_node
    (version_id, node_order, node_code, node_name, strategy_type,
     strategy_code, strategy_config, approval_mode, required_count,
     missing_policy, self_policy, return_allowed, reject_allowed,
     create_by, create_time, remark)
SELECT @health_approval_version_id, 1, 'HEALTH_CERT_REVIEWER', '健康证审核人',
       'BUSINESS_STRATEGY', 'PERMISSION_HOLDER',
       '{"permissionKey":"hr:healthCertificate:review"}',
       'ANY_ONE', 1, 'BLOCK', 'BLOCK', '1', '0', 'system', NOW(),
       '按权限与锚点授权组织范围解析'
WHERE @health_approval_version_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_version_node
      WHERE version_id = @health_approval_version_id
        AND node_code = 'HEALTH_CERT_REVIEWER'
  );

UPDATE approval_version_node
SET node_order = 1,
    node_name = '健康证审核人',
    strategy_type = 'BUSINESS_STRATEGY',
    strategy_code = 'PERMISSION_HOLDER',
    strategy_config = '{"permissionKey":"hr:healthCertificate:review"}',
    approval_mode = 'ANY_ONE', required_count = 1,
    missing_policy = 'BLOCK', self_policy = 'BLOCK',
    return_allowed = '1', reject_allowed = '0',
    update_by = 'system', update_time = NOW(),
    remark = '按权限与锚点授权组织范围解析'
WHERE version_id = @health_approval_version_id
  AND node_code = 'HEALTH_CERT_REVIEWER'
  AND @health_seed_version_mutable = 1;

UPDATE approval_rule
SET rule_status = 'ACTIVE',
    current_version_id = @health_approval_version_id,
    latest_version_no = GREATEST(latest_version_no, 1),
    update_by = 'system', update_time = NOW()
WHERE rule_id = @health_approval_rule_id
  AND @health_seed_rule_activatable = 1
  AND @health_approval_version_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM approval_rule_version published_version
      WHERE published_version.version_id = @health_approval_version_id
        AND published_version.version_status = 'PUBLISHED'
  )
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule_condition published_condition
      WHERE published_condition.version_id = @health_approval_version_id
  )
  AND 1 = (
      SELECT COUNT(*) FROM approval_version_node published_node_count
      WHERE published_node_count.version_id = @health_approval_version_id
  )
  AND EXISTS (
      SELECT 1 FROM approval_version_node published_node
      WHERE published_node.version_id = @health_approval_version_id
        AND published_node.node_order = 1
        AND published_node.node_code = 'HEALTH_CERT_REVIEWER'
        AND published_node.strategy_type = 'BUSINESS_STRATEGY'
        AND published_node.strategy_code = 'PERMISSION_HOLDER'
        AND published_node.strategy_config =
            '{"permissionKey":"hr:healthCertificate:review"}'
        AND published_node.approval_mode = 'ANY_ONE'
        AND published_node.required_count = 1
        AND published_node.missing_policy = 'BLOCK'
        AND published_node.self_policy = 'BLOCK'
        AND published_node.return_allowed = '1'
        AND published_node.reject_allowed = '0'
  );

UPDATE approval_template
SET engine_mode = 'NATIVE', legacy_adapter_code = NULL,
    template_status = 'ACTIVE', update_by = 'system', update_time = NOW(),
    remark = '新申请使用统一审批；无有效审核人时禁止提交'
WHERE template_id = @health_approval_template_id
  AND @health_seed_template_promotable = 1
  AND EXISTS (
      SELECT 1 FROM approval_rule active_rule
      JOIN approval_rule_version active_version
        ON active_version.version_id = active_rule.current_version_id
      WHERE active_rule.rule_id = @health_approval_rule_id
        AND active_rule.rule_status = 'ACTIVE'
        AND active_version.version_status = 'PUBLISHED'
  );

-- Inventory native approval definitions. Existing legacy instances are not
-- copied and continue to be served by their inventory engines.
SET @stock_check_template_id := (
    SELECT template_id FROM approval_template
    WHERE business_code = 'INV_STOCK_CHECK' LIMIT 1
);
SET @transfer_template_id := (
    SELECT template_id FROM approval_template
    WHERE business_code = 'INV_TRANSFER' LIMIT 1
);

INSERT INTO approval_rule
    (template_id, rule_code, rule_name, scope_type, scope_id, scope_name,
     business_subtype, rule_status, current_version_id, latest_version_no,
     lock_version, create_by, create_time, remark)
SELECT @stock_check_template_id, 'INV_STOCK_CHECK_DEFAULT', '库存盘点默认审批',
       'ALL', NULL, NULL, 'ALL', 'DRAFT', NULL, 1, 0,
       'system', NOW(), '运营总监按锚点组织责任范围解析'
WHERE @stock_check_template_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule
      WHERE rule_code = 'INV_STOCK_CHECK_DEFAULT'
  );

INSERT INTO approval_rule
    (template_id, rule_code, rule_name, scope_type, scope_id, scope_name,
     business_subtype, rule_status, current_version_id, latest_version_no,
     lock_version, create_by, create_time, remark)
SELECT @transfer_template_id, 'INV_TRANSFER_FIXED', '调拨固定四级审批',
       'ALL', NULL, NULL, 'ALL', 'DRAFT', NULL, 1, 0,
       'system', NOW(), '四级负责人、三级负责人、运营总监、总经理'
WHERE @transfer_template_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule
      WHERE rule_code = 'INV_TRANSFER_FIXED'
  );

SET @stock_check_rule_id := (
    SELECT rule_id FROM approval_rule
    WHERE template_id = @stock_check_template_id
      AND rule_code = 'INV_STOCK_CHECK_DEFAULT' LIMIT 1
);
SET @transfer_rule_id := (
    SELECT rule_id FROM approval_rule
    WHERE template_id = @transfer_template_id
      AND rule_code = 'INV_TRANSFER_FIXED' LIMIT 1
);

SET @stock_check_snapshot :=
    '{"schemaVersion":1,"versionNo":1,"selector":{"ruleCode":"INV_STOCK_CHECK_DEFAULT","ruleName":"库存盘点默认审批","scopeType":"ALL","scopeId":null,"scopeName":null,"businessSubtype":"ALL"},"conditions":[],"nodes":[{"nodeOrder":1,"nodeCode":"OPERATIONS_DIRECTOR","nodeName":"运营总监","strategyType":"RESPONSIBILITY_POST","strategyCode":"RESPONSIBILITY_POST","strategyConfig":{"postCode":"yyzj","permission":"inv:stockCheck:approve"},"approvalMode":"UNIQUE_BEST","requiredCount":1,"missingPolicy":"BLOCK","selfPolicy":"BLOCK","returnAllowed":"1","rejectAllowed":"1"}]}';

SET @transfer_snapshot :=
    '{"schemaVersion":1,"versionNo":1,"selector":{"ruleCode":"INV_TRANSFER_FIXED","ruleName":"调拨固定四级审批","scopeType":"ALL","scopeId":null,"scopeName":null,"businessSubtype":"ALL"},"conditions":[],"nodes":[{"nodeOrder":1,"nodeCode":"L4_MANAGER","nodeName":"四级负责人","strategyType":"BUSINESS_STRATEGY","strategyCode":"TRANSFER_LEVEL4_MANAGER","strategyConfig":{},"approvalMode":"UNIQUE_BEST","requiredCount":1,"missingPolicy":"SKIP_WARN","selfPolicy":"SKIP_SELF_AND_LOWER","returnAllowed":"1","rejectAllowed":"1"},{"nodeOrder":2,"nodeCode":"L3_MANAGER","nodeName":"三级负责人","strategyType":"BUSINESS_STRATEGY","strategyCode":"TRANSFER_LEVEL3_MANAGER","strategyConfig":{},"approvalMode":"UNIQUE_BEST","requiredCount":1,"missingPolicy":"SKIP_WARN","selfPolicy":"SKIP_SELF_AND_LOWER","returnAllowed":"1","rejectAllowed":"1"},{"nodeOrder":3,"nodeCode":"OPERATIONS_DIRECTOR","nodeName":"运营总监","strategyType":"BUSINESS_STRATEGY","strategyCode":"TRANSFER_OPERATIONS_DIRECTOR","strategyConfig":{"postCode":"yyzj"},"approvalMode":"UNIQUE_BEST","requiredCount":1,"missingPolicy":"BLOCK","selfPolicy":"SKIP_SELF_AND_LOWER","returnAllowed":"1","rejectAllowed":"1"},{"nodeOrder":4,"nodeCode":"GENERAL_MANAGER","nodeName":"总经理","strategyType":"BUSINESS_STRATEGY","strategyCode":"TRANSFER_GENERAL_MANAGER","strategyConfig":{"postCode":"zjl","applicantForbidden":true},"approvalMode":"UNIQUE_BEST","requiredCount":1,"missingPolicy":"BLOCK","selfPolicy":"BLOCK","returnAllowed":"1","rejectAllowed":"1"}]}';

INSERT INTO approval_rule_version
    (rule_id, version_no, version_status, definition_snapshot,
     definition_checksum, published_by_user_id, published_by_name,
     published_time, lock_version, create_by, create_time, remark)
SELECT @stock_check_rule_id, 1, 'DRAFT', @stock_check_snapshot,
       SHA2(@stock_check_snapshot, 256), NULL, NULL, NULL, 0,
       'system', NOW(), '内置盘点运营总监审批版本'
WHERE @stock_check_rule_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule_version
      WHERE rule_id = @stock_check_rule_id AND version_no = 1
  );

INSERT INTO approval_rule_version
    (rule_id, version_no, version_status, definition_snapshot,
     definition_checksum, published_by_user_id, published_by_name,
     published_time, lock_version, create_by, create_time, remark)
SELECT @transfer_rule_id, 1, 'DRAFT', @transfer_snapshot,
       SHA2(@transfer_snapshot, 256), NULL, NULL, NULL, 0,
       'system', NOW(), '内置调拨固定四级审批版本'
WHERE @transfer_rule_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule_version
      WHERE rule_id = @transfer_rule_id AND version_no = 1
  );

SET @stock_check_version_id := (
    SELECT version_id FROM approval_rule_version
    WHERE rule_id = @stock_check_rule_id AND version_no = 1 LIMIT 1
);
SET @transfer_version_id := (
    SELECT version_id FROM approval_rule_version
    WHERE rule_id = @transfer_rule_id AND version_no = 1 LIMIT 1
);

INSERT INTO approval_version_node
    (version_id, node_order, node_code, node_name, strategy_type,
     strategy_code, strategy_config, approval_mode, required_count,
     missing_policy, self_policy, return_allowed, reject_allowed,
     create_by, create_time, remark)
SELECT @stock_check_version_id, 1, 'OPERATIONS_DIRECTOR', '运营总监',
       'RESPONSIBILITY_POST', 'RESPONSIBILITY_POST',
       '{"postCode":"yyzj","permission":"inv:stockCheck:approve"}',
       'UNIQUE_BEST', 1, 'BLOCK', 'BLOCK', '1', '1',
       'system', NOW(), '按锚点组织责任范围解析运营总监'
WHERE @stock_check_version_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_version_node
      WHERE version_id = @stock_check_version_id
        AND node_code = 'OPERATIONS_DIRECTOR'
  );

INSERT INTO approval_version_node
    (version_id, node_order, node_code, node_name, strategy_type,
     strategy_code, strategy_config, approval_mode, required_count,
     missing_policy, self_policy, return_allowed, reject_allowed,
     create_by, create_time, remark)
SELECT @transfer_version_id, node_order, node_code, node_name,
       'BUSINESS_STRATEGY', strategy_code, strategy_config,
       'UNIQUE_BEST', 1, missing_policy, self_policy, '1', '1',
       'system', NOW(), '系统固定调拨四级链'
FROM (
    SELECT 1 node_order, 'L4_MANAGER' node_code, '四级负责人' node_name,
           'TRANSFER_LEVEL4_MANAGER' strategy_code, '{}' strategy_config,
           'SKIP_WARN' missing_policy, 'SKIP_SELF_AND_LOWER' self_policy
    UNION ALL
    SELECT 2, 'L3_MANAGER', '三级负责人',
           'TRANSFER_LEVEL3_MANAGER', '{}', 'SKIP_WARN',
           'SKIP_SELF_AND_LOWER'
    UNION ALL
    SELECT 3, 'OPERATIONS_DIRECTOR', '运营总监',
           'TRANSFER_OPERATIONS_DIRECTOR', '{"postCode":"yyzj"}',
           'BLOCK', 'SKIP_SELF_AND_LOWER'
    UNION ALL
    SELECT 4, 'GENERAL_MANAGER', '总经理',
           'TRANSFER_GENERAL_MANAGER',
           '{"postCode":"zjl","applicantForbidden":true}',
           'BLOCK', 'BLOCK'
) fixed_transfer_node
WHERE @transfer_version_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_version_node existing_node
      WHERE existing_node.version_id = @transfer_version_id
        AND existing_node.node_code = fixed_transfer_node.node_code
  );

-- Publish only the exact built-in shapes. An administrator-created or already
-- used version is never rewritten by a repeat run.
UPDATE approval_rule_version
SET version_status = 'PUBLISHED',
    definition_snapshot = @stock_check_snapshot,
    definition_checksum = SHA2(@stock_check_snapshot, 256),
    published_by_name = 'system', published_time = NOW(),
    lock_version = lock_version + 1,
    update_by = 'system', update_time = NOW()
WHERE version_id = @stock_check_version_id
  AND version_status = 'DRAFT'
  AND NOT EXISTS (
      SELECT 1 FROM approval_instance used_instance
      WHERE used_instance.rule_version_id = @stock_check_version_id
  )
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule_condition custom_condition
      WHERE custom_condition.version_id = @stock_check_version_id
  )
  AND 1 = (
      SELECT COUNT(*) FROM approval_version_node shape_node
      WHERE shape_node.version_id = @stock_check_version_id
  )
  AND EXISTS (
      SELECT 1 FROM approval_version_node shape_node
      WHERE shape_node.version_id = @stock_check_version_id
        AND shape_node.node_order = 1
        AND shape_node.node_code = 'OPERATIONS_DIRECTOR'
        AND shape_node.strategy_type = 'RESPONSIBILITY_POST'
        AND shape_node.strategy_code = 'RESPONSIBILITY_POST'
        AND shape_node.strategy_config =
            '{"postCode":"yyzj","permission":"inv:stockCheck:approve"}'
        AND shape_node.approval_mode = 'UNIQUE_BEST'
        AND shape_node.required_count = 1
        AND shape_node.missing_policy = 'BLOCK'
        AND shape_node.self_policy = 'BLOCK'
        AND shape_node.return_allowed = '1'
        AND shape_node.reject_allowed = '1'
  );

UPDATE approval_rule_version
SET version_status = 'PUBLISHED',
    definition_snapshot = @transfer_snapshot,
    definition_checksum = SHA2(@transfer_snapshot, 256),
    published_by_name = 'system', published_time = NOW(),
    lock_version = lock_version + 1,
    update_by = 'system', update_time = NOW()
WHERE version_id = @transfer_version_id
  AND version_status = 'DRAFT'
  AND NOT EXISTS (
      SELECT 1 FROM approval_instance used_instance
      WHERE used_instance.rule_version_id = @transfer_version_id
  )
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule_condition custom_condition
      WHERE custom_condition.version_id = @transfer_version_id
  )
  AND 4 = (
      SELECT COUNT(*) FROM approval_version_node shape_node
      WHERE shape_node.version_id = @transfer_version_id
  )
  AND 4 = (
      SELECT COUNT(*) FROM approval_version_node shape_node
      WHERE shape_node.version_id = @transfer_version_id
        AND shape_node.strategy_type = 'BUSINESS_STRATEGY'
        AND shape_node.approval_mode = 'UNIQUE_BEST'
        AND shape_node.required_count = 1
        AND shape_node.return_allowed = '1'
        AND shape_node.reject_allowed = '1'
        AND (
            (shape_node.node_order = 1
             AND shape_node.node_code = 'L4_MANAGER'
             AND shape_node.strategy_code = 'TRANSFER_LEVEL4_MANAGER'
             AND shape_node.strategy_config = '{}'
             AND shape_node.missing_policy = 'SKIP_WARN'
             AND shape_node.self_policy = 'SKIP_SELF_AND_LOWER')
            OR
            (shape_node.node_order = 2
             AND shape_node.node_code = 'L3_MANAGER'
             AND shape_node.strategy_code = 'TRANSFER_LEVEL3_MANAGER'
             AND shape_node.strategy_config = '{}'
             AND shape_node.missing_policy = 'SKIP_WARN'
             AND shape_node.self_policy = 'SKIP_SELF_AND_LOWER')
            OR
            (shape_node.node_order = 3
             AND shape_node.node_code = 'OPERATIONS_DIRECTOR'
             AND shape_node.strategy_code = 'TRANSFER_OPERATIONS_DIRECTOR'
             AND shape_node.strategy_config = '{"postCode":"yyzj"}'
             AND shape_node.missing_policy = 'BLOCK'
             AND shape_node.self_policy = 'SKIP_SELF_AND_LOWER')
            OR
            (shape_node.node_order = 4
             AND shape_node.node_code = 'GENERAL_MANAGER'
             AND shape_node.strategy_code = 'TRANSFER_GENERAL_MANAGER'
             AND shape_node.strategy_config =
                 '{"postCode":"zjl","applicantForbidden":true}'
             AND shape_node.missing_policy = 'BLOCK'
             AND shape_node.self_policy = 'BLOCK')
        )
  );

-- Inventory responsibility coverage is evaluated only by the explicit cutover workflow.
-- The inventory expand migration creates both keys disabled. Keep them
-- disabled in automatic releases; validation data is seeded for later manual
-- cutover only.
INSERT INTO sys_config
    (config_name, config_key, config_value, config_type,
     create_by, create_time, remark)
SELECT 'OA采购功能开关', 'feature.oa.purchase.enabled',
       'false', 'Y', 'system', NOW(),
       'OA统一审批清理、规则发布与UAT完成后由管理员人工开启'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key = 'feature.oa.purchase.enabled'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type,
     create_by, create_time, remark)
SELECT '盘点统一审批开关',
       'feature.inventory.stock-check-native-approval.enabled',
       'false', 'Y', 'system', NOW(), '固定规则发布前失败关闭'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key = 'feature.inventory.stock-check-native-approval.enabled'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type,
     create_by, create_time, remark)
SELECT '调拨统一审批开关',
       'feature.inventory.transfer-native-approval.enabled',
       'false', 'Y', 'system', NOW(), '固定规则发布前失败关闭'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key = 'feature.inventory.transfer-native-approval.enabled'
);

-- 只创建一个管理员入口。审批管理前端已交付，本迁移幂等启用菜单。
SET @approval_system_parent_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE menu_name = '系统管理' AND menu_type = 'M'
    ORDER BY menu_id
    LIMIT 1
);
SET @approval_system_parent_id := COALESCE(@approval_system_parent_id, 1);

DROP PROCEDURE IF EXISTS ensure_approval_admin_menu;
DELIMITER $$

CREATE PROCEDURE ensure_approval_admin_menu(
    IN p_preferred_id bigint,
    IN p_menu_name varchar(50),
    IN p_parent_id bigint,
    IN p_order_num int,
    IN p_path varchar(200),
    IN p_component varchar(255),
    IN p_route_name varchar(50),
    IN p_menu_type char(1),
    IN p_perms varchar(100),
    IN p_icon varchar(100),
    IN p_remark varchar(500)
)
BEGIN
    DECLARE v_menu_id bigint DEFAULT NULL;

    SELECT MAX(menu_id)
      INTO v_menu_id
      FROM sys_menu
     WHERE (p_component IS NOT NULL
            AND BINARY component = BINARY p_component)
        OR (p_perms IS NOT NULL
            AND BINARY perms = BINARY p_perms);

    IF v_menu_id IS NULL THEN
        IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = p_preferred_id) THEN
            SET v_menu_id = p_preferred_id;
        ELSE
            SELECT COALESCE(MAX(menu_id), 0) + 1 INTO v_menu_id FROM sys_menu;
        END IF;

        INSERT INTO sys_menu
            (menu_id, menu_name, parent_id, order_num, path, component, query,
             route_name, is_frame, is_cache, menu_type, visible, status,
             perms, icon, create_by, create_time, remark)
        VALUES
            (v_menu_id, p_menu_name, p_parent_id, p_order_num, p_path,
             p_component, NULL, p_route_name, 1, 0, p_menu_type, '0', '0',
             p_perms, p_icon, 'system', NOW(), p_remark);
    END IF;
END$$

DELIMITER ;

CALL ensure_approval_admin_menu(
    9700, '审批管理', @approval_system_parent_id, 90, 'approval',
    'approval/manage/index', 'ApprovalManage', 'C',
    'approval:template:list', 'guide', '统一审批管理'
);

SET @approval_admin_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE component = 'approval/manage/index'
    ORDER BY menu_id LIMIT 1
);

CALL ensure_approval_admin_menu(9701, '审批模板查询', @approval_admin_menu_id, 1, '', NULL, '', 'F', 'approval:template:query', '#', '');
CALL ensure_approval_admin_menu(9702, '审批模板编辑', @approval_admin_menu_id, 2, '', NULL, '', 'F', 'approval:template:edit', '#', '');
CALL ensure_approval_admin_menu(9703, '审批模板发布', @approval_admin_menu_id, 3, '', NULL, '', 'F', 'approval:template:publish', '#', '');
CALL ensure_approval_admin_menu(9704, '审批实例列表', @approval_admin_menu_id, 4, '', NULL, '', 'F', 'approval:instance:list', '#', '');
CALL ensure_approval_admin_menu(9705, '审批实例查询', @approval_admin_menu_id, 5, '', NULL, '', 'F', 'approval:instance:query', '#', '');
CALL ensure_approval_admin_menu(9706, '审批实例终止', @approval_admin_menu_id, 6, '', NULL, '', 'F', 'approval:instance:terminate', '#', '');
CALL ensure_approval_admin_menu(9707, '审批任务改派', @approval_admin_menu_id, 7, '', NULL, '', 'F', 'approval:task:reassign', '#', '');
CALL ensure_approval_admin_menu(9708, '配置检查列表', @approval_admin_menu_id, 8, '', NULL, '', 'F', 'approval:validation:list', '#', '');
CALL ensure_approval_admin_menu(9709, '执行配置检查', @approval_admin_menu_id, 9, '', NULL, '', 'F', 'approval:validation:run', '#', '');
CALL ensure_approval_admin_menu(9710, '审批回调重试', @approval_admin_menu_id, 10, '', NULL, '', 'F', 'approval:callback:replay', '#', '');

-- 幂等重跑时也会启用本迁移创建的入口和权限按钮。
UPDATE sys_menu
SET visible = '0', status = '0', update_by = 'system', update_time = NOW()
WHERE component = 'approval/manage/index' OR perms LIKE 'approval:%';

DROP PROCEDURE IF EXISTS ensure_approval_admin_menu;

-- 仅给有效超级管理员授权；普通员工不会获得审批管理菜单。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_info.role_id, approval_menu.menu_id
FROM sys_role role_info
JOIN sys_menu approval_menu
  ON approval_menu.component = 'approval/manage/index'
  OR approval_menu.perms LIKE 'approval:%'
WHERE role_info.role_key = 'admin'
  AND role_info.status = '0'
  AND role_info.del_flag = '0';

-- Retire the duplicate OA todo/done pages while retaining the business
-- permission used by native approval candidate resolution. OA purchase
-- application/detail stays available.
SET @oa_purchase_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE component = 'oa/purchase/index'
       OR perms = 'oa:purchase:list'
    ORDER BY CASE WHEN component = 'oa/purchase/index' THEN 0 ELSE 1 END,
             menu_id
    LIMIT 1
);

UPDATE sys_menu
SET visible = '0', status = '0', update_by = 'system', update_time = NOW()
WHERE component = 'oa/purchase/index'
   OR perms IN ('oa:purchase:list', 'oa:purchase:query',
                'oa:purchase:add', 'oa:purchase:export');

-- The 20260713 retirement migration backed up and removed every purchase
-- role-menu link together with the old todo/done pages. Restore only the
-- still-active purchase page and oa:purchase:* permissions to the same active
-- roles; retired oa:todo:* / oa:done:* entries are deliberately excluded.
SET @restore_oa_purchase_roles_sql := IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_unified_todo_role_menu_backup_20260713'
    ),
    CONCAT(
        'INSERT IGNORE INTO sys_role_menu (role_id, menu_id) ',
        'SELECT DISTINCT backup_role.role_id, backup_role.menu_id ',
        'FROM sys_unified_todo_role_menu_backup_20260713 backup_role ',
        'JOIN sys_menu purchase_menu ON purchase_menu.menu_id = backup_role.menu_id ',
        'JOIN sys_role active_role ON active_role.role_id = backup_role.role_id ',
        'WHERE (purchase_menu.component = ''oa/purchase/index'' ',
        'OR LOWER(COALESCE(purchase_menu.perms, '''')) LIKE ''oa:purchase:%'') ',
        'AND purchase_menu.status = ''0'' ',
        'AND active_role.del_flag = ''0'' AND active_role.status = ''0'''
    ),
    'SELECT 1'
);
PREPARE restore_oa_purchase_roles_stmt FROM @restore_oa_purchase_roles_sql;
EXECUTE restore_oa_purchase_roles_stmt;
DEALLOCATE PREPARE restore_oa_purchase_roles_stmt;

DROP TEMPORARY TABLE IF EXISTS tmp_native_oa_approve_roles;
CREATE TEMPORARY TABLE tmp_native_oa_approve_roles (
    role_id bigint NOT NULL,
    PRIMARY KEY (role_id)
) ENGINE=InnoDB;

INSERT IGNORE INTO tmp_native_oa_approve_roles (role_id)
SELECT DISTINCT role_menu.role_id
FROM sys_role_menu role_menu
JOIN sys_menu permission_menu ON permission_menu.menu_id = role_menu.menu_id
WHERE permission_menu.perms IN ('oa:todo:list', 'oa:todo:approve');

DROP TEMPORARY TABLE IF EXISTS tmp_retired_oa_task_menu;
CREATE TEMPORARY TABLE tmp_retired_oa_task_menu (
    menu_id bigint NOT NULL,
    PRIMARY KEY (menu_id)
) ENGINE=InnoDB;

INSERT IGNORE INTO tmp_retired_oa_task_menu (menu_id)
SELECT menu_id
FROM sys_menu
WHERE LOWER(COALESCE(component, '')) IN
          ('oa/todo/index', 'oa/done/index')
   OR (LOWER(COALESCE(perms, '')) LIKE 'oa:todo:%'
       AND LOWER(COALESCE(perms, '')) <> 'oa:todo:approve')
   OR LOWER(COALESCE(perms, '')) LIKE 'oa:done:%';

DELETE role_menu
FROM sys_role_menu role_menu
JOIN tmp_retired_oa_task_menu retired_menu
  ON retired_menu.menu_id = role_menu.menu_id;

UPDATE sys_menu retired_menu
JOIN tmp_retired_oa_task_menu retired_id
  ON retired_id.menu_id = retired_menu.menu_id
SET retired_menu.visible = '1', retired_menu.status = '1',
    retired_menu.update_by = 'system', retired_menu.update_time = NOW(),
    retired_menu.remark = concat_ws('；', nullif(retired_menu.remark, ''),
        '已由统一工作台和统一审批代替');

SET @oa_approve_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'oa:todo:approve'
    ORDER BY menu_id LIMIT 1
);
SET @oa_approve_menu_id := COALESCE(
    @oa_approve_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu menu_seed)
);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query,
     route_name, is_frame, is_cache, menu_type, visible, status,
     perms, icon, create_by, create_time, remark)
SELECT @oa_approve_menu_id, 'OA审批处理',
       COALESCE(@oa_purchase_menu_id, 0), 90, '', NULL, NULL, '',
       1, 0, 'F', '1', '0', 'oa:todo:approve', '#',
       'system', NOW(), '统一审批候选人业务权限，不单独展示页面'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'oa:todo:approve'
);

UPDATE sys_menu
SET parent_id = COALESCE(@oa_purchase_menu_id, parent_id),
    menu_type = 'F', visible = '1', status = '0',
    update_by = 'system', update_time = NOW(),
    remark = '统一审批候选人业务权限，不单独展示页面'
WHERE perms = 'oa:todo:approve';

SET @oa_approve_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'oa:todo:approve'
    ORDER BY menu_id LIMIT 1
);

-- Restore exactly the roles that owned the old OA task permission/list. If a
-- previous release already removed those links, recover its audited snapshot.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT authorized_role.role_id, @oa_approve_menu_id
FROM tmp_native_oa_approve_roles authorized_role
JOIN sys_role role_info ON role_info.role_id = authorized_role.role_id
WHERE @oa_approve_menu_id IS NOT NULL
  AND role_info.del_flag = '0' AND role_info.status = '0';

SET @restore_oa_approve_roles_sql := IF(
    EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_unified_todo_role_menu_backup_20260713'
    ) AND @oa_approve_menu_id IS NOT NULL,
    CONCAT(
        'INSERT IGNORE INTO sys_role_menu (role_id, menu_id) ',
        'SELECT DISTINCT backup_role.role_id, ', @oa_approve_menu_id, ' ',
        'FROM sys_unified_todo_role_menu_backup_20260713 backup_role ',
        'JOIN sys_menu old_permission ON old_permission.menu_id = backup_role.menu_id ',
        'JOIN sys_role active_role ON active_role.role_id = backup_role.role_id ',
        'WHERE old_permission.perms = ''oa:todo:approve'' ',
        'AND active_role.del_flag = ''0'' AND active_role.status = ''0'''
    ),
    'SELECT 1'
);
PREPARE restore_oa_approve_roles_stmt FROM @restore_oa_approve_roles_sql;
EXECUTE restore_oa_approve_roles_stmt;
DEALLOCATE PREPARE restore_oa_approve_roles_stmt;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_info.role_id, @oa_approve_menu_id
FROM sys_role role_info
WHERE @oa_approve_menu_id IS NOT NULL
  AND role_info.role_key = 'admin'
  AND role_info.del_flag = '0' AND role_info.status = '0';

-- Runtime route contract: a business approval F permission alone does not make
-- its M/C route visible. Grant only the corresponding read page and every
-- enabled M/C ancestor to each active role that already owns the approval
-- permission. No additional F action permission is granted here.
DROP TEMPORARY TABLE IF EXISTS tmp_approval_required_route;
CREATE TEMPORARY TABLE tmp_approval_required_route (
    required_permission varchar(100) NOT NULL,
    page_menu_id bigint NOT NULL,
    PRIMARY KEY (required_permission)
) ENGINE=InnoDB;

INSERT IGNORE INTO tmp_approval_required_route
    (required_permission, page_menu_id)
SELECT 'oa:todo:approve', menu_id
FROM sys_menu
WHERE component = 'oa/purchase/index'
  AND menu_type = 'C' AND status = '0'
ORDER BY menu_id
LIMIT 1;

INSERT IGNORE INTO tmp_approval_required_route
    (required_permission, page_menu_id)
SELECT 'inv:transfer:approve', menu_id
FROM sys_menu
WHERE component = 'inventory/transfer/index'
  AND menu_type = 'C' AND status = '0'
ORDER BY CASE WHEN route_name = 'WarehouseTransfer' THEN 0 ELSE 1 END,
         menu_id
LIMIT 1;

INSERT IGNORE INTO tmp_approval_required_route
    (required_permission, page_menu_id)
SELECT 'inv:stockCheck:approve', menu_id
FROM sys_menu
WHERE component = 'inventory/stockCheck/index'
  AND menu_type = 'C' AND status = '0'
ORDER BY menu_id
LIMIT 1;

INSERT IGNORE INTO tmp_approval_required_route
    (required_permission, page_menu_id)
SELECT 'hr:healthCertificate:review', menu_id
FROM sys_menu
WHERE component = 'hr/healthCertificate/index'
  AND menu_type = 'C' AND status = '0'
ORDER BY menu_id
LIMIT 1;

DROP TEMPORARY TABLE IF EXISTS tmp_approval_route_roles;
CREATE TEMPORARY TABLE tmp_approval_route_roles (
    role_id bigint NOT NULL,
    page_menu_id bigint NOT NULL,
    PRIMARY KEY (role_id, page_menu_id)
) ENGINE=InnoDB;

INSERT IGNORE INTO tmp_approval_route_roles (role_id, page_menu_id)
SELECT DISTINCT role_menu.role_id, route.page_menu_id
FROM sys_role_menu role_menu
JOIN sys_menu approval_permission
  ON approval_permission.menu_id = role_menu.menu_id
JOIN tmp_approval_required_route route
  ON BINARY route.required_permission = BINARY approval_permission.perms
JOIN sys_role active_role ON active_role.role_id = role_menu.role_id
WHERE active_role.del_flag = '0' AND active_role.status = '0'
  AND approval_permission.status = '0';

DROP TEMPORARY TABLE IF EXISTS tmp_approval_route_grants;
CREATE TEMPORARY TABLE tmp_approval_route_grants (
    role_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB;

DROP TEMPORARY TABLE IF EXISTS tmp_approval_route_frontier;
CREATE TEMPORARY TABLE tmp_approval_route_frontier LIKE tmp_approval_route_grants;

DROP TEMPORARY TABLE IF EXISTS tmp_approval_route_next;
CREATE TEMPORARY TABLE tmp_approval_route_next LIKE tmp_approval_route_grants;

INSERT IGNORE INTO tmp_approval_route_grants (role_id, menu_id)
SELECT role_id, page_menu_id FROM tmp_approval_route_roles;
INSERT IGNORE INTO tmp_approval_route_frontier (role_id, menu_id)
SELECT role_id, page_menu_id FROM tmp_approval_route_roles;

DROP PROCEDURE IF EXISTS expand_approval_route_ancestors;
DELIMITER $$
CREATE PROCEDURE expand_approval_route_ancestors()
BEGIN
    DECLARE v_frontier_count int DEFAULT 1;
    WHILE v_frontier_count > 0 DO
        DELETE FROM tmp_approval_route_next;
        INSERT IGNORE INTO tmp_approval_route_next (role_id, menu_id)
        SELECT DISTINCT frontier.role_id, parent_menu.menu_id
        FROM tmp_approval_route_frontier frontier
        JOIN sys_menu child_menu ON child_menu.menu_id = frontier.menu_id
        JOIN sys_menu parent_menu ON parent_menu.menu_id = child_menu.parent_id
        LEFT JOIN tmp_approval_route_grants existing_grant
          ON existing_grant.role_id = frontier.role_id
         AND existing_grant.menu_id = parent_menu.menu_id
        WHERE child_menu.parent_id <> 0
          AND parent_menu.menu_type IN ('M', 'C')
          AND parent_menu.status = '0'
          AND existing_grant.menu_id IS NULL;

        INSERT IGNORE INTO tmp_approval_route_grants (role_id, menu_id)
        SELECT role_id, menu_id FROM tmp_approval_route_next;
        DELETE FROM tmp_approval_route_frontier;
        INSERT IGNORE INTO tmp_approval_route_frontier (role_id, menu_id)
        SELECT role_id, menu_id FROM tmp_approval_route_next;
        SELECT COUNT(*) INTO v_frontier_count
        FROM tmp_approval_route_frontier;
    END WHILE;
END$$
DELIMITER ;

CALL expand_approval_route_ancestors();
DROP PROCEDURE expand_approval_route_ancestors;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, menu_id FROM tmp_approval_route_grants;

-- Approval-start outbox operations use dedicated permissions. They are not
-- inferred from business approve/review permissions and default only to the
-- active system administrator role.
DROP PROCEDURE IF EXISTS ensure_approval_outbox_ops_permission;
DELIMITER $$
CREATE PROCEDURE ensure_approval_outbox_ops_permission(
    IN p_preferred_menu_id bigint,
    IN p_menu_name varchar(64),
    IN p_parent_component varchar(255),
    IN p_order_num int,
    IN p_perms varchar(100)
)
BEGIN
    DECLARE v_menu_id bigint DEFAULT NULL;
    DECLARE v_parent_id bigint DEFAULT NULL;

    SET v_parent_id := (
        SELECT menu_id
        FROM sys_menu
        WHERE BINARY component = BINARY p_parent_component
          AND menu_type = 'C'
        ORDER BY status, menu_id
        LIMIT 1
    );
    SET v_menu_id := (
        SELECT menu_id FROM sys_menu
        WHERE BINARY perms = BINARY p_perms
        ORDER BY menu_id
        LIMIT 1
    );

    IF v_menu_id IS NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM sys_menu
            WHERE menu_id = p_preferred_menu_id
        ) THEN
            SET v_menu_id := p_preferred_menu_id;
        ELSE
            SELECT COALESCE(MAX(menu_id), 0) + 1
            INTO v_menu_id
            FROM sys_menu;
        END IF;

        INSERT INTO sys_menu
            (menu_id, menu_name, parent_id, order_num, path, component,
             query, route_name, is_frame, is_cache, menu_type, visible,
             status, perms, icon, create_by, create_time, remark)
        VALUES
            (v_menu_id, p_menu_name, COALESCE(v_parent_id, 0), p_order_num,
             '', NULL, NULL, '', 1, 0, 'F', '1', '0', p_perms, '#',
             'system', NOW(),
             '审批发起发件箱运维专用权限；默认仅授系统管理员');
    END IF;

    UPDATE sys_menu
    SET parent_id = COALESCE(v_parent_id, parent_id),
        menu_name = p_menu_name, menu_type = 'F', visible = '1',
        status = '0', update_by = 'system', update_time = NOW(),
        remark = '审批发起发件箱运维专用权限；默认仅授系统管理员'
    WHERE BINARY perms = BINARY p_perms;
END$$
DELIMITER ;

CALL ensure_approval_outbox_ops_permission(
    9720, '调拨审批发起发件箱查看', 'inventory/transfer/index', 91,
    'inv:transfer:approvalStartOutbox:list');
CALL ensure_approval_outbox_ops_permission(
    9721, '调拨审批发起发件箱重放', 'inventory/transfer/index', 92,
    'inv:transfer:approvalStartOutbox:replay');
CALL ensure_approval_outbox_ops_permission(
    9722, '盘点审批发起发件箱查看', 'inventory/stockCheck/index', 91,
    'inv:stockCheck:approvalStartOutbox:list');
CALL ensure_approval_outbox_ops_permission(
    9723, '盘点审批发起发件箱重放', 'inventory/stockCheck/index', 92,
    'inv:stockCheck:approvalStartOutbox:replay');
CALL ensure_approval_outbox_ops_permission(
    9724, '采购审批发起发件箱查看', 'oa/purchase/index', 91,
    'oa:purchase:approvalStartOutbox:list');
CALL ensure_approval_outbox_ops_permission(
    9725, '采购审批发起发件箱重放', 'oa/purchase/index', 92,
    'oa:purchase:approvalStartOutbox:replay');
CALL ensure_approval_outbox_ops_permission(
    9726, '健康证审批发起发件箱查看', 'hr/healthCertificate/index', 91,
    'hr:healthCertificate:approvalStartOutbox:list');
CALL ensure_approval_outbox_ops_permission(
    9727, '健康证审批发起发件箱重放', 'hr/healthCertificate/index', 92,
    'hr:healthCertificate:approvalStartOutbox:replay');

DROP PROCEDURE IF EXISTS ensure_approval_outbox_ops_permission;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_info.role_id, permission_menu.menu_id
FROM sys_role role_info
JOIN sys_menu permission_menu
  ON permission_menu.perms IN (
      'inv:transfer:approvalStartOutbox:list',
      'inv:transfer:approvalStartOutbox:replay',
      'inv:stockCheck:approvalStartOutbox:list',
      'inv:stockCheck:approvalStartOutbox:replay',
      'oa:purchase:approvalStartOutbox:list',
      'oa:purchase:approvalStartOutbox:replay',
      'hr:healthCertificate:approvalStartOutbox:list',
      'hr:healthCertificate:approvalStartOutbox:replay'
  )
WHERE role_info.role_key = 'admin'
  AND role_info.status = '0'
  AND role_info.del_flag = '0';
-- End approval-start outbox operations permissions.

DROP TEMPORARY TABLE IF EXISTS tmp_approval_route_next;
DROP TEMPORARY TABLE IF EXISTS tmp_approval_route_frontier;
DROP TEMPORARY TABLE IF EXISTS tmp_approval_route_grants;
DROP TEMPORARY TABLE IF EXISTS tmp_approval_route_roles;
DROP TEMPORARY TABLE IF EXISTS tmp_approval_required_route;

DROP TEMPORARY TABLE IF EXISTS tmp_retired_oa_task_menu;
DROP TEMPORARY TABLE IF EXISTS tmp_native_oa_approve_roles;

-- 只读执行结果，供发布记录核对。
SELECT business_code, template_name, business_source, engine_mode,
       definition_mode, template_status
FROM approval_template
WHERE business_code IN (
    'OA_PURCHASE', 'INV_TRANSFER', 'INV_STOCK_CHECK',
    'HR_HEALTH_CERTIFICATE'
)
ORDER BY template_id;

SELECT menu_id, menu_name, parent_id, component, perms, visible, status
FROM sys_menu
WHERE component = 'approval/manage/index' OR perms LIKE 'approval:%'
ORDER BY menu_id;
