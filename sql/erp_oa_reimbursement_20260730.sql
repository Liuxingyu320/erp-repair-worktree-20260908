-- OA reimbursement management: private invoices, two-step approval and
-- traceable accounting export batches.
-- MySQL 5.7 / 8.0 compatible and repeat-safe.

CREATE TABLE IF NOT EXISTS oa_reimbursement (
    reimbursement_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '报销单ID',
    reimbursement_no varchar(40) NOT NULL COMMENT '报销单号',
    title varchar(120) NOT NULL COMMENT '报销标题',
    purpose varchar(500) NOT NULL COMMENT '报销事由',
    applicant_id bigint(20) NOT NULL COMMENT '申请人用户ID',
    applicant_name varchar(64) NOT NULL COMMENT '申请人账号快照',
    applicant_dept_id bigint(20) DEFAULT NULL COMMENT '申请部门ID快照',
    shop_dept_id bigint(20) NOT NULL COMMENT '归属店铺组织ID',
    total_amount decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '报销总金额',
    status varchar(24) NOT NULL DEFAULT 'draft' COMMENT '业务审批状态',
    export_status varchar(24) NOT NULL DEFAULT 'not_exported' COMMENT '财务导出状态',
    submitted_time datetime DEFAULT NULL COMMENT '提交时间',
    approved_time datetime DEFAULT NULL COMMENT '审批通过时间',
    last_export_time datetime DEFAULT NULL COMMENT '最近导出时间',
    approval_instance_id bigint(20) DEFAULT NULL COMMENT '当前审批实例ID',
    approval_round int NOT NULL DEFAULT 0 COMMENT '审批轮次',
    row_version bigint(20) NOT NULL DEFAULT 0 COMMENT '业务行乐观锁',
    last_approval_event_key varchar(128) DEFAULT NULL COMMENT '最近审批回调事件键',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (reimbursement_id),
    UNIQUE KEY uk_oa_reimbursement_no (reimbursement_no),
    KEY idx_oa_reimbursement_applicant (applicant_id, status, create_time),
    KEY idx_oa_reimbursement_finance (shop_dept_id, status, approved_time),
    KEY idx_oa_reimbursement_export (export_status, status, approved_time),
    KEY idx_oa_reimbursement_approval (approval_instance_id, approval_round)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA报销申请';

CREATE TABLE IF NOT EXISTS oa_reimbursement_approval_start_outbox (
    outbox_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '发件箱ID',
    reimbursement_id bigint(20) NOT NULL COMMENT '报销单ID',
    business_round int NOT NULL COMMENT '审批轮次',
    idempotency_key varchar(128) NOT NULL COMMENT '审批中心幂等键',
    request_json mediumtext NOT NULL COMMENT '发起请求快照',
    status varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态',
    retry_count int NOT NULL DEFAULT 0 COMMENT '自动重试次数',
    next_retry_time datetime DEFAULT NULL COMMENT '下次重试时间',
    last_http_status int DEFAULT NULL COMMENT '最后远端状态码',
    last_error_code varchar(64) DEFAULT NULL COMMENT '最后错误码',
    last_error_message varchar(255) DEFAULT NULL COMMENT '错误摘要',
    remote_instance_id bigint(20) DEFAULT NULL COMMENT '审批中心实例ID',
    remote_status varchar(32) DEFAULT NULL COMMENT '审批中心初始状态',
    remote_business_round int DEFAULT NULL COMMENT '远端实际业务轮次',
    reimbursement_version bigint(20) NOT NULL COMMENT '入队时报销单版本',
    version bigint(20) NOT NULL DEFAULT 0 COMMENT '发件箱CAS版本',
    remote_succeeded_time datetime DEFAULT NULL COMMENT '远端成功固化时间',
    completed_time datetime DEFAULT NULL COMMENT '本地关联完成时间',
    manual_replay_count int NOT NULL DEFAULT 0 COMMENT '人工重放次数',
    manual_replay_by varchar(64) DEFAULT NULL COMMENT '最后人工重放人',
    manual_replay_time datetime DEFAULT NULL COMMENT '最后人工重放时间',
    create_by varchar(64) DEFAULT NULL COMMENT '创建人',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT NULL COMMENT '更新人',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_oa_reimbursement_approval_round
        (reimbursement_id, business_round),
    UNIQUE KEY uk_oa_reimbursement_approval_idempotency (idempotency_key),
    KEY idx_oa_reimbursement_approval_dispatch
        (status, next_retry_time, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA报销统一审批发起发件箱';

CREATE TABLE IF NOT EXISTS oa_reimbursement_item (
    item_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '费用明细ID',
    reimbursement_id bigint(20) NOT NULL COMMENT '报销单ID',
    expense_type varchar(32) DEFAULT NULL COMMENT '费用类型，草稿可为空',
    expense_date date DEFAULT NULL COMMENT '费用发生日期，草稿可为空',
    merchant_name varchar(120) DEFAULT NULL COMMENT '商户名称',
    description varchar(300) DEFAULT NULL COMMENT '费用说明，草稿可为空',
    claimed_amount decimal(12,2) DEFAULT NULL COMMENT '报销金额，草稿可为空',
    sort_no int NOT NULL DEFAULT 0 COMMENT '排序',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (item_id),
    KEY idx_oa_reimbursement_item_claim (reimbursement_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA报销费用明细';

-- Existing installations may already have the first version of the table.
-- Draft rows must be able to retain incomplete mobile input without inventing
-- placeholder values; submission still performs strict service validation.
ALTER TABLE oa_reimbursement_item
    MODIFY expense_type varchar(32) DEFAULT NULL
        COMMENT '费用类型，草稿可为空',
    MODIFY expense_date date DEFAULT NULL
        COMMENT '费用发生日期，草稿可为空',
    MODIFY description varchar(300) DEFAULT NULL
        COMMENT '费用说明，草稿可为空',
    MODIFY claimed_amount decimal(12,2) DEFAULT NULL
        COMMENT '报销金额，草稿可为空';

CREATE TABLE IF NOT EXISTS oa_reimbursement_invoice (
    invoice_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '发票附件ID',
    reimbursement_id bigint(20) NOT NULL COMMENT '报销单ID',
    item_id bigint(20) DEFAULT NULL COMMENT '可选关联费用明细ID',
    original_name varchar(180) NOT NULL COMMENT '原始文件名',
    stored_name varchar(80) NOT NULL COMMENT '私有存储文件名',
    storage_path varchar(500) NOT NULL COMMENT '私有相对路径',
    content_type varchar(100) NOT NULL COMMENT '内容类型',
    file_extension varchar(12) NOT NULL COMMENT '文件扩展名',
    file_size bigint(20) NOT NULL COMMENT '文件字节数',
    sha256 char(64) NOT NULL COMMENT '文件SHA-256',
    duplicate_status varchar(16) NOT NULL DEFAULT 'none' COMMENT '重复提示状态',
    duplicate_reimbursement_id bigint(20) DEFAULT NULL COMMENT '历史重复报销单ID',
    sort_no int NOT NULL DEFAULT 0 COMMENT '排序',
    uploaded_by bigint(20) NOT NULL COMMENT '上传人ID',
    uploaded_by_name varchar(64) NOT NULL COMMENT '上传人账号快照',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (invoice_id),
    UNIQUE KEY uk_oa_reimbursement_invoice_path (storage_path),
    KEY idx_oa_reimbursement_invoice_claim (reimbursement_id, sort_no),
    KEY idx_oa_reimbursement_invoice_hash (sha256, reimbursement_id),
    UNIQUE KEY uk_oa_reimbursement_invoice_claim_hash
        (reimbursement_id, sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA报销私有发票附件';

-- Same-claim replay is idempotent.  Existing installations are checked for
-- financial-data conflicts and fail closed before the unique key is added.
SET @erp_oa_reimbursement_schema := DATABASE();
DROP PROCEDURE IF EXISTS erp_oa_reimbursement_invoice_hash_guard;
DELIMITER $$
CREATE PROCEDURE erp_oa_reimbursement_invoice_hash_guard()
BEGIN
    DECLARE v_duplicate_count bigint DEFAULT 0;
    SELECT COUNT(*) INTO v_duplicate_count
    FROM (
        SELECT reimbursement_id, sha256
        FROM oa_reimbursement_invoice
        GROUP BY reimbursement_id, sha256
        HAVING COUNT(*) > 1
    ) duplicate_rows;
    IF v_duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'oa_reimbursement_invoice has duplicate reimbursement_id + sha256 rows';
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics s
        WHERE s.table_schema = @erp_oa_reimbursement_schema
          AND s.table_name = 'oa_reimbursement_invoice'
          AND s.index_name = 'uk_oa_reimbursement_invoice_claim_hash'
        GROUP BY s.table_schema, s.table_name, s.index_name, s.non_unique
        HAVING MAX(s.non_unique) = 0
           AND COUNT(*) = 2
           AND SUM(s.seq_in_index = 1
               AND s.column_name = 'reimbursement_id') = 1
           AND SUM(s.seq_in_index = 2
               AND s.column_name = 'sha256') = 1
    ) THEN
        SET @erp_oa_reimbursement_invoice_hash_ddl :=
            'ALTER TABLE oa_reimbursement_invoice ADD UNIQUE KEY uk_oa_reimbursement_invoice_claim_hash (reimbursement_id, sha256)';
        PREPARE erp_oa_reimbursement_invoice_hash_stmt
            FROM @erp_oa_reimbursement_invoice_hash_ddl;
        EXECUTE erp_oa_reimbursement_invoice_hash_stmt;
        DEALLOCATE PREPARE erp_oa_reimbursement_invoice_hash_stmt;
    END IF;
END$$
DELIMITER ;
CALL erp_oa_reimbursement_invoice_hash_guard();
DROP PROCEDURE IF EXISTS erp_oa_reimbursement_invoice_hash_guard;

CREATE TABLE IF NOT EXISTS oa_reimbursement_invoice_recognition (
    invoice_id bigint(20) NOT NULL COMMENT '发票附件ID',
    reimbursement_id bigint(20) NOT NULL COMMENT '报销单ID',
    recognition_status varchar(20) NOT NULL DEFAULT 'pending'
        COMMENT 'pending/succeeded/partial/failed/corrected/unconfigured',
    requested_engine varchar(16) NOT NULL DEFAULT 'auto'
        COMMENT 'auto/cloud/local',
    recognition_engine varchar(16) DEFAULT NULL COMMENT '实际识别引擎',
    recognition_provider varchar(32) DEFAULT NULL COMMENT '识别服务提供方',
    recognition_message varchar(500) DEFAULT NULL COMMENT '识别说明',
    invoice_type varchar(80) DEFAULT NULL COMMENT '发票类型',
    invoice_code varchar(32) DEFAULT NULL COMMENT '发票代码',
    invoice_number varchar(40) DEFAULT NULL COMMENT '发票号码',
    invoice_date date DEFAULT NULL COMMENT '开票日期',
    seller_name varchar(200) DEFAULT NULL COMMENT '销售方名称',
    seller_tax_no varchar(40) DEFAULT NULL COMMENT '销售方纳税人识别号',
    purchaser_name varchar(200) DEFAULT NULL COMMENT '购买方名称',
    purchaser_tax_no varchar(40) DEFAULT NULL COMMENT '购买方纳税人识别号',
    amount_without_tax decimal(12,2) DEFAULT NULL COMMENT '不含税金额',
    tax_amount decimal(12,2) DEFAULT NULL COMMENT '税额',
    invoice_total_amount decimal(12,2) DEFAULT NULL COMMENT '价税合计',
    check_code varchar(40) DEFAULT NULL COMMENT '校验码',
    service_type varchar(80) DEFAULT NULL COMMENT '消费类型',
    commodity_summary varchar(500) DEFAULT NULL COMMENT '商品或服务摘要',
    recognition_confidence decimal(5,4) DEFAULT NULL COMMENT '识别置信度',
    recognition_raw_text mediumtext COMMENT '本地识别原文',
    recognition_raw_payload longtext COMMENT '云端原始响应',
    recognized_time datetime DEFAULT NULL COMMENT '最近识别时间',
    corrected_by bigint(20) DEFAULT NULL COMMENT '人工修正人ID',
    corrected_time datetime DEFAULT NULL COMMENT '人工修正时间',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (invoice_id),
    KEY idx_oa_invoice_recognition_claim (reimbursement_id, invoice_id),
    KEY idx_oa_invoice_recognition_number
        (invoice_number, invoice_code, reimbursement_id),
    KEY idx_oa_invoice_recognition_status
        (recognition_status, recognition_engine)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA报销发票双引擎识别结果';

CREATE TABLE IF NOT EXISTS oa_reimbursement_export_batch (
    batch_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '导出批次ID',
    batch_no varchar(40) NOT NULL COMMENT '导出批次号',
    reimbursement_count int NOT NULL COMMENT '报销单数量',
    item_count int NOT NULL COMMENT '费用明细数量',
    invoice_count int NOT NULL COMMENT '发票数量',
    total_amount decimal(14,2) NOT NULL COMMENT '导出总金额',
    archive_name varchar(180) NOT NULL COMMENT '资料包文件名',
    archive_path varchar(500) NOT NULL COMMENT '私有归档相对路径',
    archive_size bigint(20) NOT NULL COMMENT '资料包字节数',
    archive_sha256 char(64) NOT NULL COMMENT '资料包SHA-256',
    created_by_user_id bigint(20) NOT NULL COMMENT '导出人ID',
    created_by_name varchar(64) NOT NULL COMMENT '导出人账号快照',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (batch_id),
    UNIQUE KEY uk_oa_reimbursement_export_no (batch_no),
    UNIQUE KEY uk_oa_reimbursement_export_path (archive_path),
    KEY idx_oa_reimbursement_export_creator (created_by_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA报销会计导出批次';

CREATE TABLE IF NOT EXISTS oa_reimbursement_export_batch_item (
    batch_item_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '批次明细ID',
    batch_id bigint(20) NOT NULL COMMENT '导出批次ID',
    reimbursement_id bigint(20) NOT NULL COMMENT '报销单ID',
    reimbursement_no varchar(40) NOT NULL COMMENT '报销单号快照',
    applicant_id bigint(20) NOT NULL COMMENT '申请人ID快照',
    applicant_name varchar(64) NOT NULL COMMENT '申请人姓名快照',
    shop_dept_id bigint(20) NOT NULL COMMENT '店铺组织ID快照',
    total_amount decimal(12,2) NOT NULL COMMENT '报销金额快照',
    approved_time datetime DEFAULT NULL COMMENT '审批通过时间快照',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (batch_item_id),
    UNIQUE KEY uk_oa_reimbursement_batch_claim (batch_id, reimbursement_id),
    KEY idx_oa_reimbursement_batch_item_claim (reimbursement_id, batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA报销会计导出批次明细';

-- Enable the approved feature without overwriting a later administrator choice.
INSERT INTO sys_config
    (config_name, config_key, config_value, config_type,
     create_by, create_time, remark)
SELECT 'OA报销功能开关', 'feature.oa.reimbursement.enabled',
       'true', 'Y', 'system', NOW(),
       '报销表、私有发票、两级审批与会计资料包已一并交付'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key = 'feature.oa.reimbursement.enabled'
);

-- Native approval definition: department leader, then scoped finance lead.
INSERT INTO approval_template
    (business_code, template_name, business_source, engine_mode,
     definition_mode, legacy_adapter_code, callback_service,
     template_status, create_by, create_time, remark)
SELECT 'OA_REIMBURSEMENT', '费用报销审批', 'oa', 'NATIVE', 'LIMITED',
       NULL, 'erp-oa', 'ACTIVE', 'system', NOW(),
       '部门负责人审批后转财务负责人；任一节点无人时阻断提交'
WHERE NOT EXISTS (
    SELECT 1 FROM approval_template
    WHERE business_code = 'OA_REIMBURSEMENT'
);

SET @reimbursement_template_id := (
    SELECT template_id FROM approval_template
    WHERE business_code = 'OA_REIMBURSEMENT' LIMIT 1
);

INSERT INTO approval_rule
    (template_id, rule_code, rule_name, scope_type, scope_id, scope_name,
     business_subtype, rule_status, current_version_id, latest_version_no,
     lock_version, create_by, create_time, remark)
SELECT @reimbursement_template_id, 'OA_REIMBURSEMENT_DEFAULT',
       '费用报销默认审批', 'ALL', NULL, NULL, 'EXPENSE', 'DRAFT',
       NULL, 1, 0, 'system', NOW(),
       '部门负责人、财务负责人两级审批'
WHERE @reimbursement_template_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule
      WHERE rule_code = 'OA_REIMBURSEMENT_DEFAULT'
  );

SET @reimbursement_rule_id := (
    SELECT rule_id FROM approval_rule
    WHERE template_id = @reimbursement_template_id
      AND rule_code = 'OA_REIMBURSEMENT_DEFAULT'
    LIMIT 1
);

SET @reimbursement_definition_snapshot :=
    '{"schemaVersion":1,"versionNo":1,"selector":{"ruleCode":"OA_REIMBURSEMENT_DEFAULT","ruleName":"费用报销默认审批","scopeType":"ALL","scopeId":null,"scopeName":null,"businessSubtype":"EXPENSE"},"conditions":[],"nodes":[{"nodeOrder":1,"nodeCode":"DEPARTMENT_LEADER","nodeName":"部门负责人","strategyType":"ORG_LEADER","strategyCode":"ORG_LEADER","strategyConfig":{},"approvalMode":"UNIQUE_BEST","requiredCount":1,"missingPolicy":"BLOCK","selfPolicy":"SKIP","returnAllowed":"1","rejectAllowed":"1"},{"nodeOrder":2,"nodeCode":"FINANCE_LEADER","nodeName":"财务负责人","strategyType":"BUSINESS_STRATEGY","strategyCode":"PERMISSION_HOLDER","strategyConfig":{"permissionKey":"oa:reimbursement:finance:approve"},"approvalMode":"ANY_ONE","requiredCount":1,"missingPolicy":"BLOCK","selfPolicy":"BLOCK","returnAllowed":"1","rejectAllowed":"1"}]}';

INSERT INTO approval_rule_version
    (rule_id, version_no, version_status, definition_snapshot,
     definition_checksum, published_by_user_id, published_by_name,
     published_time, lock_version, create_by, create_time, remark)
SELECT @reimbursement_rule_id, 1, 'PUBLISHED',
       @reimbursement_definition_snapshot,
       SHA2(@reimbursement_definition_snapshot, 256),
       NULL, 'system', NOW(), 0, 'system', NOW(),
       '费用报销内置两级审批版本'
WHERE @reimbursement_rule_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule_version
      WHERE rule_id = @reimbursement_rule_id AND version_no = 1
  );

SET @reimbursement_version_id := (
    SELECT version_id FROM approval_rule_version
    WHERE rule_id = @reimbursement_rule_id AND version_no = 1
    LIMIT 1
);

INSERT INTO approval_version_node
    (version_id, node_order, node_code, node_name, strategy_type,
     strategy_code, strategy_config, approval_mode, required_count,
     missing_policy, self_policy, return_allowed, reject_allowed,
     create_by, create_time, remark)
SELECT @reimbursement_version_id, 1, 'DEPARTMENT_LEADER', '部门负责人',
       'ORG_LEADER', 'ORG_LEADER', '{}', 'UNIQUE_BEST', 1,
       'BLOCK', 'SKIP', '1', '1', 'system', NOW(),
       '取申请部门向上最近的实名组织负责人'
WHERE @reimbursement_version_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_version_node
      WHERE version_id = @reimbursement_version_id
        AND node_code = 'DEPARTMENT_LEADER'
  );

INSERT INTO approval_version_node
    (version_id, node_order, node_code, node_name, strategy_type,
     strategy_code, strategy_config, approval_mode, required_count,
     missing_policy, self_policy, return_allowed, reject_allowed,
     create_by, create_time, remark)
SELECT @reimbursement_version_id, 2, 'FINANCE_LEADER', '财务负责人',
       'BUSINESS_STRATEGY', 'PERMISSION_HOLDER',
       '{"permissionKey":"oa:reimbursement:finance:approve"}',
       'ANY_ONE', 1, 'BLOCK', 'BLOCK', '1', '1', 'system', NOW(),
       '按申请部门所在组织范围解析财务审批人'
WHERE @reimbursement_version_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_version_node
      WHERE version_id = @reimbursement_version_id
        AND node_code = 'FINANCE_LEADER'
  );

UPDATE approval_rule
SET rule_status = 'ACTIVE',
    current_version_id = @reimbursement_version_id,
    latest_version_no = 1,
    lock_version = lock_version + 1,
    update_by = 'system', update_time = NOW()
WHERE rule_id = @reimbursement_rule_id
  AND rule_status = 'DRAFT'
  AND current_version_id IS NULL
  AND @reimbursement_version_id IS NOT NULL
  AND 2 = (
      SELECT COUNT(*) FROM approval_version_node
      WHERE version_id = @reimbursement_version_id
  );

-- Desktop menus. IDs are allocated from the current database to avoid
-- colliding with independently delivered feature migrations.
SET @oa_parent_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE menu_type = 'M'
      AND (path = 'oa' OR menu_name = 'OA管理')
    ORDER BY menu_id LIMIT 1
);
SET @oa_parent_menu_id := COALESCE(@oa_parent_menu_id, 3000);

SET @reimbursement_self_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'oa:reimbursement:self'
    ORDER BY menu_id LIMIT 1
);
SET @reimbursement_self_menu_id := COALESCE(
    @reimbursement_self_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu menu_seed)
);
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query,
     route_name, is_frame, is_cache, menu_type, visible, status, perms,
     icon, create_by, create_time, remark)
SELECT @reimbursement_self_menu_id, '我的报销', @oa_parent_menu_id, 11,
       'reimbursement', 'oa/reimbursement/index', NULL,
       'OaReimbursement', 1, 0, 'C', '0', '0',
       'oa:reimbursement:self', 'money', 'system', NOW(),
       '员工创建、提交和跟踪自己的报销申请'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'oa:reimbursement:self'
);

SET @reimbursement_approve_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'oa:reimbursement:approve'
    ORDER BY menu_id LIMIT 1
);
SET @reimbursement_approve_menu_id := COALESCE(
    @reimbursement_approve_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu menu_seed)
);
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query,
     route_name, is_frame, is_cache, menu_type, visible, status, perms,
     icon, create_by, create_time, remark)
SELECT @reimbursement_approve_menu_id, '报销审批',
       @reimbursement_self_menu_id, 1, '', NULL, NULL, '',
       1, 0, 'F', '0', '0', 'oa:reimbursement:approve', '#',
       'system', NOW(), '统一审批的报销基础动作权限'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'oa:reimbursement:approve'
);

SET @reimbursement_finance_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'oa:reimbursement:finance:list'
    ORDER BY menu_id LIMIT 1
);
SET @reimbursement_finance_menu_id := COALESCE(
    @reimbursement_finance_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu menu_seed)
);
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query,
     route_name, is_frame, is_cache, menu_type, visible, status, perms,
     icon, create_by, create_time, remark)
SELECT @reimbursement_finance_menu_id, '报销财务台账',
       @oa_parent_menu_id, 12, 'reimbursement-finance',
       'oa/reimbursement/index', '{"mode":"finance"}',
       'OaReimbursementFinance', 1, 0, 'C', '0', '0',
       'oa:reimbursement:finance:list', 'excel', 'system', NOW(),
       '财务查看已审批报销并制作会计资料包'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE perms = 'oa:reimbursement:finance:list'
);

UPDATE sys_menu
SET query = '{"mode":"finance"}'
WHERE perms = 'oa:reimbursement:finance:list'
  AND (query IS NULL OR query <> '{"mode":"finance"}');

SET @reimbursement_finance_approve_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'oa:reimbursement:finance:approve'
    ORDER BY menu_id LIMIT 1
);
SET @reimbursement_finance_approve_menu_id := COALESCE(
    @reimbursement_finance_approve_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu menu_seed)
);
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query,
     route_name, is_frame, is_cache, menu_type, visible, status, perms,
     icon, create_by, create_time, remark)
SELECT @reimbursement_finance_approve_menu_id, '财务报销审批',
       @reimbursement_finance_menu_id, 1, '', NULL, NULL, '',
       1, 0, 'F', '0', '0', 'oa:reimbursement:finance:approve', '#',
       'system', NOW(), '第二级财务负责人候选权限'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE perms = 'oa:reimbursement:finance:approve'
);

SET @reimbursement_export_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'oa:reimbursement:finance:export'
    ORDER BY menu_id LIMIT 1
);
SET @reimbursement_export_menu_id := COALESCE(
    @reimbursement_export_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu menu_seed)
);
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query,
     route_name, is_frame, is_cache, menu_type, visible, status, perms,
     icon, create_by, create_time, remark)
SELECT @reimbursement_export_menu_id, '导出会计资料包',
       @reimbursement_finance_menu_id, 2, '', NULL, NULL, '',
       1, 0, 'F', '0', '0', 'oa:reimbursement:finance:export', '#',
       'system', NOW(), '生成Excel及原始发票附件ZIP'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE perms = 'oa:reimbursement:finance:export'
);

-- Reimbursement approval-start outbox operations use dedicated permissions.
SET @reimbursement_outbox_list_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'oa:reimbursement:approvalStartOutbox:list'
    ORDER BY menu_id LIMIT 1
);
SET @reimbursement_outbox_list_menu_id := COALESCE(
    @reimbursement_outbox_list_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu menu_seed)
);
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query,
     route_name, is_frame, is_cache, menu_type, visible, status, perms,
     icon, create_by, create_time, remark)
SELECT @reimbursement_outbox_list_menu_id, '报销审批发起运维查看',
       @reimbursement_self_menu_id, 90, '', NULL, NULL, '',
       1, 0, 'F', '1', '0',
       'oa:reimbursement:approvalStartOutbox:list', '#', 'system', NOW(),
       '仅管理员查看报销审批发起发件箱'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE perms = 'oa:reimbursement:approvalStartOutbox:list'
);

SET @reimbursement_outbox_replay_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'oa:reimbursement:approvalStartOutbox:replay'
    ORDER BY menu_id LIMIT 1
);
SET @reimbursement_outbox_replay_menu_id := COALESCE(
    @reimbursement_outbox_replay_menu_id,
    (SELECT COALESCE(MAX(menu_id), 0) + 1 FROM sys_menu menu_seed)
);
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query,
     route_name, is_frame, is_cache, menu_type, visible, status, perms,
     icon, create_by, create_time, remark)
SELECT @reimbursement_outbox_replay_menu_id, '报销审批发起失败重放',
       @reimbursement_self_menu_id, 91, '', NULL, NULL, '',
       1, 0, 'F', '1', '0',
       'oa:reimbursement:approvalStartOutbox:replay', '#', 'system', NOW(),
       '仅管理员人工重放可恢复的报销审批发起失败记录'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE perms = 'oa:reimbursement:approvalStartOutbox:replay'
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, @reimbursement_outbox_list_menu_id
FROM sys_role
WHERE status = '0' AND del_flag = '0'
  AND (role_id = 1 OR role_key = 'admin');
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, @reimbursement_outbox_replay_menu_id
FROM sys_role
WHERE status = '0' AND del_flag = '0'
  AND (role_id = 1 OR role_key = 'admin');
-- End reimbursement approval-start outbox operations permissions.

-- Every active role can submit its own reimbursement.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, @reimbursement_self_menu_id
FROM sys_role
WHERE status = '0' AND del_flag = '0';

-- Existing OA approver roles become eligible department approvers.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_menu.role_id, @reimbursement_approve_menu_id
FROM sys_role_menu role_menu
JOIN sys_menu permission_menu
  ON permission_menu.menu_id = role_menu.menu_id
JOIN sys_role role_row ON role_row.role_id = role_menu.role_id
WHERE permission_menu.perms = 'oa:todo:approve'
  AND role_row.status = '0' AND role_row.del_flag = '0';

-- Ensure every configured organization leader can execute the first node.
-- Granting the permission to their active roles also covers later leaders
-- assigned through the same managerial roles.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT user_role.role_id, @reimbursement_approve_menu_id
FROM sys_dept leader_dept
JOIN sys_user leader_user
  ON leader_user.user_id = leader_dept.leader_user_id
JOIN sys_user_role user_role
  ON user_role.user_id = leader_user.user_id
JOIN sys_role leader_role
  ON leader_role.role_id = user_role.role_id
WHERE leader_dept.del_flag = '0' AND leader_dept.status = '0'
  AND leader_user.del_flag = '0' AND leader_user.status = '0'
  AND leader_role.del_flag = '0' AND leader_role.status = '0';

-- Existing salary-export roles are the safest available finance-role signal.
-- They receive both the node-specific permission and the base action permission.
DROP TEMPORARY TABLE IF EXISTS tmp_reimbursement_finance_role;
CREATE TEMPORARY TABLE tmp_reimbursement_finance_role (
    role_id bigint(20) NOT NULL PRIMARY KEY
) ENGINE=Memory;

INSERT IGNORE INTO tmp_reimbursement_finance_role (role_id)
SELECT DISTINCT role_menu.role_id
FROM sys_role_menu role_menu
JOIN sys_menu permission_menu
  ON permission_menu.menu_id = role_menu.menu_id
JOIN sys_role role_row ON role_row.role_id = role_menu.role_id
WHERE permission_menu.perms IN ('oa:salary:export', 'system:salary:export')
  AND role_row.status = '0' AND role_row.del_flag = '0';

INSERT IGNORE INTO tmp_reimbursement_finance_role (role_id)
SELECT role_id FROM sys_role
WHERE status = '0' AND del_flag = '0'
  AND (role_id = 1 OR role_key = 'admin');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, @reimbursement_finance_menu_id
FROM tmp_reimbursement_finance_role;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, @reimbursement_finance_approve_menu_id
FROM tmp_reimbursement_finance_role;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, @reimbursement_export_menu_id
FROM tmp_reimbursement_finance_role;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, @reimbursement_approve_menu_id
FROM tmp_reimbursement_finance_role;

DROP TEMPORARY TABLE IF EXISTS tmp_reimbursement_finance_role;

SELECT business_code, template_status, engine_mode
FROM approval_template
WHERE business_code = 'OA_REIMBURSEMENT';
SELECT rule_code, rule_status, current_version_id
FROM approval_rule
WHERE rule_code = 'OA_REIMBURSEMENT_DEFAULT';
