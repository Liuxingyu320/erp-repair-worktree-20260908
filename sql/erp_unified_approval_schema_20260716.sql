-- ERP unified approval center: schema-only expand migration.
-- MySQL 5.7 / 8.0 兼容，可重复执行。
-- This automatic migration creates approval_* tables only. It does not seed,
-- publish, enable, or cut over any business workflow.

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
