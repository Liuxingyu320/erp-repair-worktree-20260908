-- ERP 统一审批中心核心结构与内置模板目录。
-- MySQL 5.7 / 8.0 兼容，可重复执行。
-- 本迁移只新增结构、内置配置和审批管理菜单，不删除存量运行/历史审批数据。
-- 审批管理前端已交付，本迁移会幂等启用管理菜单；不改动旧调拨配置菜单 4410。

CREATE TABLE IF NOT EXISTS approval_template (
    template_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审批模板ID',
    business_code varchar(64) NOT NULL COMMENT '稳定业务编码',
    template_name varchar(100) NOT NULL COMMENT '模板名称',
    business_source varchar(32) NOT NULL COMMENT '业务来源 oa/inventory/system',
    engine_mode varchar(16) NOT NULL COMMENT 'NATIVE/LEGACY',
    definition_mode varchar(16) NOT NULL DEFAULT 'LIMITED' COMMENT 'FIXED/LIMITED',
    legacy_adapter_code varchar(64) DEFAULT NULL COMMENT '旧引擎适配器编码',
    callback_service varchar(64) DEFAULT NULL COMMENT '业务回调服务名',
    template_status varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/DISABLED',
    lock_version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (template_id),
    UNIQUE KEY uk_approval_template_business (business_code),
    KEY idx_approval_template_mode (engine_mode, template_status, business_source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一审批业务模板';

CREATE TABLE IF NOT EXISTS approval_rule (
    rule_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审批规则ID',
    template_id bigint(20) NOT NULL COMMENT '审批模板ID',
    rule_code varchar(64) NOT NULL COMMENT '稳定规则编码',
    rule_name varchar(128) NOT NULL COMMENT '规则名称',
    scope_type varchar(16) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/AREA/STORE',
    scope_id bigint(20) DEFAULT NULL COMMENT '区域或门店组织ID',
    scope_name varchar(100) DEFAULT NULL COMMENT '范围名称快照',
    business_subtype varchar(64) NOT NULL DEFAULT 'ALL' COMMENT '业务子类型或ALL',
    rule_status varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/DISABLED',
    current_version_id bigint(20) DEFAULT NULL COMMENT '当前发布版本ID',
    latest_version_no int NOT NULL DEFAULT 0 COMMENT '最新版本号',
    lock_version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (rule_id),
    UNIQUE KEY uk_approval_rule_code (rule_code),
    KEY idx_approval_rule_match (template_id, rule_status, scope_type, scope_id, business_subtype)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一审批适用规则';

CREATE TABLE IF NOT EXISTS approval_rule_version (
    version_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '规则版本ID',
    rule_id bigint(20) NOT NULL COMMENT '审批规则ID',
    version_no int NOT NULL COMMENT '规则内版本号',
    version_status varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/RETIRED',
    definition_snapshot longtext COMMENT '完整不可变定义快照JSON',
    definition_checksum char(64) DEFAULT NULL COMMENT '定义SHA-256',
    published_by_user_id bigint(20) DEFAULT NULL COMMENT '发布人用户ID',
    published_by_name varchar(64) DEFAULT NULL COMMENT '发布人姓名快照',
    published_time datetime DEFAULT NULL COMMENT '发布时间',
    lock_version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (version_id),
    UNIQUE KEY uk_approval_rule_version (rule_id, version_no),
    KEY idx_approval_rule_version_status (rule_id, version_status, published_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一审批不可变规则版本';

CREATE TABLE IF NOT EXISTS approval_rule_condition (
    condition_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '规则条件ID',
    version_id bigint(20) NOT NULL COMMENT '规则版本ID',
    condition_order int NOT NULL COMMENT '条件顺序',
    field_code varchar(64) NOT NULL COMMENT '白名单业务字段',
    operator_code varchar(16) NOT NULL COMMENT 'EQ/IN/GTE/GT/LTE/LT',
    value_type varchar(16) NOT NULL DEFAULT 'STRING' COMMENT 'STRING/NUMBER/BOOLEAN',
    value_text varchar(1000) NOT NULL COMMENT '结构化条件值',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (condition_id),
    UNIQUE KEY uk_approval_condition_order (version_id, condition_order),
    KEY idx_approval_condition_field (version_id, field_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一审批版本条件';

CREATE TABLE IF NOT EXISTS approval_version_node (
    node_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审批版本节点ID',
    version_id bigint(20) NOT NULL COMMENT '规则版本ID',
    node_order int NOT NULL COMMENT '节点顺序',
    node_code varchar(64) NOT NULL COMMENT '版本内稳定节点编码',
    node_name varchar(100) NOT NULL COMMENT '节点名称',
    strategy_type varchar(32) NOT NULL COMMENT 'BUSINESS_STRATEGY/RESPONSIBILITY_POST/ORG_LEADER/FIXED_USERS',
    strategy_code varchar(64) NOT NULL COMMENT '受控解析策略编码',
    strategy_config longtext COMMENT '策略配置JSON',
    approval_mode varchar(16) NOT NULL DEFAULT 'UNIQUE_BEST' COMMENT 'UNIQUE_BEST/ANY_ONE/ALL',
    required_count int NOT NULL DEFAULT 1 COMMENT '所需通过人数',
    missing_policy varchar(16) NOT NULL DEFAULT 'BLOCK' COMMENT 'BLOCK/SKIP_WARN',
    self_policy varchar(32) NOT NULL DEFAULT 'BLOCK' COMMENT 'BLOCK/SKIP/ALLOW/SKIP_THROUGH',
    return_allowed char(1) NOT NULL DEFAULT '1' COMMENT '是否允许退回修改',
    reject_allowed char(1) NOT NULL DEFAULT '1' COMMENT '是否允许拒绝',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (node_id),
    UNIQUE KEY uk_approval_node_order (version_id, node_order),
    UNIQUE KEY uk_approval_node_code (version_id, node_code),
    KEY idx_approval_node_strategy (strategy_type, strategy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一审批不可变版本节点';

CREATE TABLE IF NOT EXISTS approval_instance (
    instance_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审批实例ID',
    root_instance_id bigint(20) DEFAULT NULL COMMENT '首轮实例ID',
    previous_instance_id bigint(20) DEFAULT NULL COMMENT '上一轮实例ID',
    template_id bigint(20) NOT NULL COMMENT '审批模板ID',
    business_code varchar(64) NOT NULL COMMENT '业务编码',
    business_source varchar(32) NOT NULL COMMENT '业务来源',
    business_id varchar(128) NOT NULL COMMENT '业务主键字符串',
    business_subtype varchar(64) NOT NULL DEFAULT 'ALL' COMMENT '业务子类型',
    business_round int NOT NULL DEFAULT 1 COMMENT '业务审批轮次',
    idempotency_key varchar(128) NOT NULL COMMENT '发起幂等键',
    applicant_user_id bigint(20) NOT NULL COMMENT '申请人用户ID',
    applicant_name varchar(64) DEFAULT NULL COMMENT '申请人姓名快照',
    applicant_dept_id bigint(20) DEFAULT NULL COMMENT '申请人组织快照',
    applicant_dept_name varchar(100) DEFAULT NULL COMMENT '申请人组织名称快照',
    anchor_dept_id bigint(20) DEFAULT NULL COMMENT '审批责任锚点组织',
    anchor_dept_name varchar(100) DEFAULT NULL COMMENT '锚点组织名称快照',
    rule_id bigint(20) NOT NULL COMMENT '命中规则ID',
    rule_version_id bigint(20) NOT NULL COMMENT '命中规则版本ID',
    rule_version_no int NOT NULL COMMENT '命中规则版本号',
    business_snapshot longtext COMMENT '提交时业务快照JSON',
    business_digest char(64) DEFAULT NULL COMMENT '业务快照SHA-256',
    route_snapshot text COMMENT '业务跳转快照JSON',
    status varchar(24) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/各回调中间态/最终态',
    current_node_id bigint(20) DEFAULT NULL COMMENT '当前版本节点ID',
    current_node_order int DEFAULT NULL COMMENT '当前节点顺序',
    callback_status varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/PROCESSING/SUCCEEDED/FAILED/DEAD',
    approved_action_count int NOT NULL DEFAULT 0 COMMENT '已发生同意动作数',
    started_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
    finished_time datetime DEFAULT NULL COMMENT '结束时间',
    lock_version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (instance_id),
    UNIQUE KEY uk_approval_instance_idempotency (idempotency_key),
    UNIQUE KEY uk_approval_instance_business_round (business_code, business_id, business_round),
    KEY idx_approval_instance_business (business_code, business_id, create_time),
    KEY idx_approval_instance_status (status, current_node_order, create_time),
    KEY idx_approval_instance_applicant (applicant_user_id, status, create_time),
    KEY idx_approval_instance_callback (callback_status, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一审批实例';

CREATE TABLE IF NOT EXISTS approval_task (
    task_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审批任务ID',
    instance_id bigint(20) NOT NULL COMMENT '审批实例ID',
    node_id bigint(20) NOT NULL COMMENT '审批版本节点ID',
    node_order int NOT NULL COMMENT '节点顺序',
    node_code varchar(64) NOT NULL COMMENT '节点编码快照',
    node_name varchar(100) NOT NULL COMMENT '节点名称快照',
    task_status varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT 'WAITING/PENDING/APPROVED/RETURNED/REJECTED/SKIPPED/CANCELLED',
    approval_mode varchar(16) NOT NULL COMMENT 'UNIQUE_BEST/ANY_ONE/ALL',
    required_count int NOT NULL DEFAULT 1 COMMENT '所需通过人数',
    completed_count int NOT NULL DEFAULT 0 COMMENT '已通过人数',
    activated_time datetime DEFAULT NULL COMMENT '激活时间，WAITING节点为NULL',
    completed_time datetime DEFAULT NULL COMMENT '完成时间',
    lock_version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_approval_task_node (instance_id, node_order),
    KEY idx_approval_task_status (task_status, activated_time),
    KEY idx_approval_task_instance (instance_id, task_status, node_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一审批节点任务';

-- 早期草案会在创建 WAITING 任务时提前写入激活时间。
-- 改为可空后，只有真正进入 PENDING 时才由运行时写入。
ALTER TABLE approval_task
    MODIFY COLUMN activated_time datetime DEFAULT NULL
        COMMENT '激活时间，WAITING节点为NULL';

UPDATE approval_task
SET activated_time = NULL
WHERE task_status = 'WAITING'
  AND activated_time IS NOT NULL;

CREATE TABLE IF NOT EXISTS approval_task_candidate (
    candidate_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务候选人ID',
    task_id bigint(20) NOT NULL COMMENT '审批任务ID',
    instance_id bigint(20) NOT NULL COMMENT '审批实例ID',
    user_id bigint(20) NOT NULL COMMENT '候选用户ID',
    user_name varchar(64) DEFAULT NULL COMMENT '候选人姓名快照',
    dept_id bigint(20) DEFAULT NULL COMMENT '候选人组织快照',
    dept_name varchar(100) DEFAULT NULL COMMENT '候选人组织名称快照',
    candidate_source_type varchar(32) NOT NULL COMMENT '候选人来源类型',
    candidate_source_code varchar(64) DEFAULT NULL COMMENT '岗位或策略编码',
    candidate_order int NOT NULL DEFAULT 1 COMMENT '候选优先顺序',
    candidate_status varchar(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/RETURNED/REJECTED/CANCELLED/REASSIGNED',
    candidate_reason varchar(500) DEFAULT NULL COMMENT '候选或取消原因',
    acted_time datetime DEFAULT NULL COMMENT '处理时间',
    reassigned_from_candidate_id bigint(20) DEFAULT NULL COMMENT '改派来源候选记录',
    lock_version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (candidate_id),
    UNIQUE KEY uk_approval_candidate_user (task_id, user_id),
    KEY idx_approval_candidate_todo (user_id, candidate_status, create_time),
    KEY idx_approval_candidate_task (task_id, candidate_status, candidate_order),
    KEY idx_approval_candidate_instance (instance_id, candidate_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一审批任务候选人';

CREATE TABLE IF NOT EXISTS approval_action_log (
    action_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审批动作ID',
    action_key varchar(191) NOT NULL COMMENT '动作幂等键',
    instance_id bigint(20) NOT NULL COMMENT '审批实例ID',
    task_id bigint(20) DEFAULT NULL COMMENT '审批任务ID',
    candidate_id bigint(20) DEFAULT NULL COMMENT '候选人记录ID',
    action_type varchar(32) NOT NULL COMMENT 'START/APPROVE/RETURN/REJECT/WITHDRAW/TERMINATE/REASSIGN/SKIP/CALLBACK',
    operator_user_id bigint(20) DEFAULT NULL COMMENT '操作人用户ID',
    operator_name varchar(64) DEFAULT NULL COMMENT '操作人姓名快照',
    operator_source varchar(16) NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN/SYSTEM/CALLBACK',
    from_status varchar(24) DEFAULT NULL COMMENT '原状态',
    to_status varchar(24) DEFAULT NULL COMMENT '新状态',
    action_reason varchar(500) DEFAULT NULL COMMENT '操作原因',
    request_id varchar(191) DEFAULT NULL COMMENT '请求链路ID（含回调重放前缀）',
    action_snapshot longtext COMMENT '动作上下文快照JSON',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    remark varchar(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (action_id),
    UNIQUE KEY uk_approval_action_key (action_key),
    KEY idx_approval_action_instance (instance_id, create_time, action_id),
    KEY idx_approval_action_operator (operator_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一审批不可变动作日志';

-- 兼容早期建表后重跑，为实例前缀 + 128位 requestId 留足空间。
ALTER TABLE approval_action_log
    MODIFY COLUMN action_key varchar(191) NOT NULL COMMENT '动作幂等键',
    MODIFY COLUMN request_id varchar(191) DEFAULT NULL
        COMMENT '请求链路ID（含回调重放前缀）';

CREATE TABLE IF NOT EXISTS approval_callback_outbox (
    outbox_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '回调事件ID',
    event_key varchar(128) NOT NULL COMMENT '回调事件幂等键',
    instance_id bigint(20) NOT NULL COMMENT '审批实例ID',
    business_code varchar(64) NOT NULL COMMENT '业务编码',
    business_source varchar(32) NOT NULL COMMENT '业务来源',
    business_id varchar(128) NOT NULL COMMENT '业务主键字符串',
    business_round int NOT NULL COMMENT '业务审批轮次',
    callback_action varchar(32) NOT NULL COMMENT 'APPROVE/RETURN/REJECT/WITHDRAW/TERMINATE',
    callback_service varchar(64) NOT NULL COMMENT '回调服务名',
    callback_code varchar(64) NOT NULL COMMENT '受控回调适配器编码',
    payload longtext NOT NULL COMMENT '回调载荷JSON',
    callback_status varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RETRY/SUCCEEDED/DEAD',
    retry_count int NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry_count int NOT NULL DEFAULT 10 COMMENT '最大重试次数',
    next_retry_time datetime DEFAULT NULL COMMENT '下次重试时间',
    locked_by varchar(64) DEFAULT NULL COMMENT '分发锁持有者',
    lock_until datetime DEFAULT NULL COMMENT '分发锁到期时间',
    last_error_code varchar(64) DEFAULT NULL COMMENT '最近错误码',
    last_error_message varchar(500) DEFAULT NULL COMMENT '脱敏错误摘要',
    delivered_time datetime DEFAULT NULL COMMENT '成功送达时间',
    lock_version bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_approval_callback_event (event_key),
    KEY idx_approval_callback_dispatch (callback_status, next_retry_time, outbox_id),
    KEY idx_approval_callback_instance (instance_id, callback_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一审批业务回调Outbox';

CREATE TABLE IF NOT EXISTS approval_validation_run (
    run_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '配置检查批次ID',
    template_id bigint(20) DEFAULT NULL COMMENT '审批模板ID',
    rule_id bigint(20) DEFAULT NULL COMMENT '审批规则ID',
    rule_version_id bigint(20) DEFAULT NULL COMMENT '规则版本ID',
    validation_type varchar(16) NOT NULL COMMENT 'PUBLISH/FULL/MANUAL',
    run_status varchar(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/PASSED/FAILED',
    total_count int NOT NULL DEFAULT 0 COMMENT '问题总数',
    error_count int NOT NULL DEFAULT 0 COMMENT '错误数',
    warning_count int NOT NULL DEFAULT 0 COMMENT '警告数',
    started_by_user_id bigint(20) DEFAULT NULL COMMENT '发起人用户ID',
    started_by_name varchar(64) DEFAULT NULL COMMENT '发起人姓名快照',
    started_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    finished_time datetime DEFAULT NULL COMMENT '结束时间',
    scope_snapshot longtext COMMENT '检查范围快照JSON',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (run_id),
    KEY idx_approval_validation_status (run_status, started_time),
    KEY idx_approval_validation_target (template_id, rule_id, rule_version_id, started_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一审批配置检查批次';

CREATE TABLE IF NOT EXISTS approval_validation_issue (
    issue_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '配置问题ID',
    run_id bigint(20) NOT NULL COMMENT '配置检查批次ID',
    template_id bigint(20) DEFAULT NULL COMMENT '审批模板ID',
    rule_id bigint(20) DEFAULT NULL COMMENT '审批规则ID',
    rule_version_id bigint(20) DEFAULT NULL COMMENT '规则版本ID',
    severity varchar(16) NOT NULL COMMENT 'ERROR/WARNING',
    issue_code varchar(64) NOT NULL COMMENT '稳定问题编码',
    scope_type varchar(16) DEFAULT NULL COMMENT 'ALL/AREA/STORE',
    scope_id bigint(20) DEFAULT NULL COMMENT '范围组织ID',
    scope_name varchar(100) DEFAULT NULL COMMENT '范围名称快照',
    business_subtype varchar(64) DEFAULT NULL COMMENT '业务子类型',
    node_code varchar(64) DEFAULT NULL COMMENT '节点编码',
    node_name varchar(100) DEFAULT NULL COMMENT '节点名称',
    issue_message varchar(1000) NOT NULL COMMENT '问题说明',
    suggestion varchar(1000) DEFAULT NULL COMMENT '修复建议',
    issue_snapshot longtext COMMENT '问题上下文快照JSON',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    remark varchar(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (issue_id),
    KEY idx_approval_issue_run (run_id, severity, issue_id),
    KEY idx_approval_issue_target (template_id, issue_code, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一审批配置检查问题';

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
                AND custom_node.node_code <> 'HEALTH_CERT_REVIEWER'
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

-- Mandatory responsibility coverage: every active store must be covered by
-- active responsibility users holding the corresponding business permission.
SET @stock_check_coverage_missing := (
    SELECT COUNT(*)
    FROM sys_dept target_store
    WHERE target_store.del_flag = '0'
      AND target_store.status = '0'
      AND target_store.dept_type = 'STORE'
      AND NOT EXISTS (
          SELECT 1
          FROM sys_user candidate
          JOIN sys_user_post candidate_post_link
            ON candidate_post_link.user_id = candidate.user_id
          JOIN sys_post candidate_post
            ON candidate_post.post_id = candidate_post_link.post_id
           AND candidate_post.post_code = 'yyzj'
           AND candidate_post.status = '0'
          JOIN sys_user_shop candidate_scope
            ON candidate_scope.user_id = candidate.user_id
          JOIN sys_dept scope_dept
            ON scope_dept.dept_id = candidate_scope.dept_id
           AND scope_dept.del_flag = '0' AND scope_dept.status = '0'
          LEFT JOIN sys_user_profile candidate_profile
            ON candidate_profile.user_id = candidate.user_id
          WHERE candidate.del_flag = '0' AND candidate.status = '0'
            AND (candidate_profile.employee_status IS NULL
                 OR TRIM(candidate_profile.employee_status) = ''
                 OR TRIM(candidate_profile.employee_status) <> '离职')
            AND (scope_dept.dept_id = target_store.dept_id
                 OR FIND_IN_SET(scope_dept.dept_id, target_store.ancestors))
            AND EXISTS (
                SELECT 1 FROM sys_user_role ur
                JOIN sys_role role_info ON role_info.role_id = ur.role_id
                JOIN sys_role_menu rm ON rm.role_id = role_info.role_id
                JOIN sys_menu permission_menu ON permission_menu.menu_id = rm.menu_id
                WHERE ur.user_id = candidate.user_id
                  AND role_info.del_flag = '0' AND role_info.status = '0'
                  AND permission_menu.status = '0'
                  AND permission_menu.perms = 'inv:stockCheck:approve'
            )
      )
);

SET @transfer_coverage_missing := (
    SELECT COUNT(*)
    FROM sys_dept target_store
    WHERE target_store.del_flag = '0'
      AND target_store.status = '0'
      AND target_store.dept_type = 'STORE'
      AND (
          NOT EXISTS (
              SELECT 1
              FROM sys_user candidate
              JOIN sys_user_post candidate_post_link
                ON candidate_post_link.user_id = candidate.user_id
              JOIN sys_post candidate_post
                ON candidate_post.post_id = candidate_post_link.post_id
               AND candidate_post.post_code = 'yyzj'
               AND candidate_post.status = '0'
              JOIN sys_user_shop candidate_scope
                ON candidate_scope.user_id = candidate.user_id
              JOIN sys_dept scope_dept
                ON scope_dept.dept_id = candidate_scope.dept_id
               AND scope_dept.del_flag = '0' AND scope_dept.status = '0'
              LEFT JOIN sys_user_profile candidate_profile
                ON candidate_profile.user_id = candidate.user_id
              WHERE candidate.del_flag = '0' AND candidate.status = '0'
                AND (candidate_profile.employee_status IS NULL
                     OR TRIM(candidate_profile.employee_status) = ''
                     OR TRIM(candidate_profile.employee_status) <> '离职')
                AND (scope_dept.dept_id = target_store.dept_id
                     OR FIND_IN_SET(scope_dept.dept_id, target_store.ancestors))
                AND EXISTS (
                    SELECT 1 FROM sys_user_role ur
                    JOIN sys_role role_info ON role_info.role_id = ur.role_id
                    JOIN sys_role_menu rm ON rm.role_id = role_info.role_id
                    JOIN sys_menu permission_menu ON permission_menu.menu_id = rm.menu_id
                    WHERE ur.user_id = candidate.user_id
                      AND role_info.del_flag = '0' AND role_info.status = '0'
                      AND permission_menu.status = '0'
                      AND permission_menu.perms = 'inv:transfer:approve'
                )
          )
          OR NOT EXISTS (
              SELECT 1
              FROM sys_user candidate
              JOIN sys_user_post candidate_post_link
                ON candidate_post_link.user_id = candidate.user_id
              JOIN sys_post candidate_post
                ON candidate_post.post_id = candidate_post_link.post_id
               AND candidate_post.post_code = 'zjl'
               AND candidate_post.status = '0'
              JOIN sys_user_shop candidate_scope
                ON candidate_scope.user_id = candidate.user_id
              JOIN sys_dept scope_dept
                ON scope_dept.dept_id = candidate_scope.dept_id
               AND scope_dept.del_flag = '0' AND scope_dept.status = '0'
              LEFT JOIN sys_user_profile candidate_profile
                ON candidate_profile.user_id = candidate.user_id
              WHERE candidate.del_flag = '0' AND candidate.status = '0'
                AND (candidate_profile.employee_status IS NULL
                     OR TRIM(candidate_profile.employee_status) = ''
                     OR TRIM(candidate_profile.employee_status) <> '离职')
                AND (scope_dept.dept_id = target_store.dept_id
                     OR FIND_IN_SET(scope_dept.dept_id, target_store.ancestors))
                AND EXISTS (
                    SELECT 1 FROM sys_user_role ur
                    JOIN sys_role role_info ON role_info.role_id = ur.role_id
                    JOIN sys_role_menu rm ON rm.role_id = role_info.role_id
                    JOIN sys_menu permission_menu ON permission_menu.menu_id = rm.menu_id
                    WHERE ur.user_id = candidate.user_id
                      AND role_info.del_flag = '0' AND role_info.status = '0'
                      AND permission_menu.status = '0'
                      AND permission_menu.perms = 'inv:transfer:approve'
                )
          )
      )
);

UPDATE approval_rule
SET rule_status = 'ACTIVE', current_version_id = @stock_check_version_id,
    latest_version_no = GREATEST(latest_version_no, 1),
    update_by = 'system', update_time = NOW()
WHERE rule_id = @stock_check_rule_id
  AND @stock_check_coverage_missing = 0
  AND EXISTS (
      SELECT 1 FROM approval_rule_version published_version
      WHERE published_version.version_id = @stock_check_version_id
        AND published_version.version_status = 'PUBLISHED'
        AND published_version.definition_checksum =
            SHA2(@stock_check_snapshot, 256)
  );

UPDATE approval_rule
SET rule_status = 'ACTIVE', current_version_id = @transfer_version_id,
    latest_version_no = GREATEST(latest_version_no, 1),
    update_by = 'system', update_time = NOW()
WHERE rule_id = @transfer_rule_id
  AND @transfer_coverage_missing = 0
  AND EXISTS (
      SELECT 1 FROM approval_rule_version published_version
      WHERE published_version.version_id = @transfer_version_id
        AND published_version.version_status = 'PUBLISHED'
        AND published_version.definition_checksum =
            SHA2(@transfer_snapshot, 256)
  );

UPDATE approval_template
SET engine_mode = 'NATIVE', legacy_adapter_code = 'INV_STOCK_CHECK',
    template_status = 'ACTIVE', update_by = 'system', update_time = NOW(),
    remark = '新盘点走统一审批，旧实例由Legacy Bridge原位完成'
WHERE template_id = @stock_check_template_id
  AND EXISTS (
      SELECT 1 FROM approval_rule active_rule
      WHERE active_rule.rule_id = @stock_check_rule_id
        AND active_rule.rule_status = 'ACTIVE'
  );

UPDATE approval_template
SET engine_mode = 'NATIVE', legacy_adapter_code = 'INV_TRANSFER',
    template_status = 'ACTIVE', update_by = 'system', update_time = NOW(),
    remark = '新调拨走统一审批，旧实例由Legacy Bridge原位完成'
WHERE template_id = @transfer_template_id
  AND EXISTS (
      SELECT 1 FROM approval_rule active_rule
      WHERE active_rule.rule_id = @transfer_rule_id
        AND active_rule.rule_status = 'ACTIVE'
  );

-- The inventory association migration creates both keys disabled. Enable new
-- submissions only after the exact rules and mandatory coverage are active.
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

UPDATE sys_config
SET config_value = 'true', update_by = 'system', update_time = NOW(),
    remark = '统一审批固定规则已发布且高层责任覆盖校验通过'
WHERE config_key = 'feature.inventory.stock-check-native-approval.enabled'
  AND EXISTS (
      SELECT 1 FROM approval_template
      WHERE business_code = 'INV_STOCK_CHECK'
        AND engine_mode = 'NATIVE' AND template_status = 'ACTIVE'
  );

UPDATE sys_config
SET config_value = 'true', update_by = 'system', update_time = NOW(),
    remark = '统一审批固定四级规则已发布且高层责任覆盖校验通过'
WHERE config_key = 'feature.inventory.transfer-native-approval.enabled'
  AND EXISTS (
      SELECT 1 FROM approval_template
      WHERE business_code = 'INV_TRANSFER'
        AND engine_mode = 'NATIVE' AND template_status = 'ACTIVE'
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
     WHERE (p_component IS NOT NULL AND component = p_component)
        OR (p_perms IS NOT NULL AND perms = p_perms);

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
  ON route.required_permission = approval_permission.perms
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
