-- Immutable HR lifecycle actions and reliable OA signing-event outbox.
-- Employee/profile changes and both inserts are committed by one later lifecycle-service transaction.

CREATE TABLE IF NOT EXISTS sys_hr_lifecycle_action (
    action_id bigint NOT NULL AUTO_INCREMENT COMMENT '人事生命周期动作ID',
    action_type varchar(32) NOT NULL COMMENT '动作类型',
    employee_id bigint NOT NULL COMMENT '员工用户ID',
    source_type varchar(40) NOT NULL COMMENT '来源业务类型',
    source_business_id varchar(64) NOT NULL COMMENT '来源业务ID',
    before_snapshot_json json DEFAULT NULL COMMENT '动作前显式员工快照',
    after_snapshot_json json NOT NULL COMMENT '动作后显式员工快照',
    effective_date date DEFAULT NULL COMMENT '业务生效日期',
    business_status varchar(32) NOT NULL COMMENT '业务状态',
    risk_level varchar(16) NOT NULL COMMENT '风险等级',
    risk_codes_json json DEFAULT NULL COMMENT '风险代码列表',
    risk_detail varchar(1000) DEFAULT NULL COMMENT 'HR可读风险说明',
    request_id varchar(64) NOT NULL COMMENT '业务幂等请求ID',
    version bigint NOT NULL DEFAULT 1 COMMENT '业务事件版本',
    operator_type varchar(16) NOT NULL COMMENT '操作方类型：HUMAN或SYSTEM',
    operator_user_id bigint DEFAULT NULL COMMENT '人工操作用户ID；系统动作为空',
    operator_name varchar(64) DEFAULT NULL COMMENT '操作用户姓名',
    operator_ip varchar(64) DEFAULT NULL COMMENT '操作IP',
    operator_user_agent varchar(500) DEFAULT NULL COMMENT '操作终端',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (action_id),
    UNIQUE KEY uk_sys_hr_lifecycle_action_request (request_id),
    KEY idx_sys_hr_lifecycle_employee (employee_id, action_type, business_status, create_time),
    KEY idx_sys_hr_lifecycle_source (source_type, source_business_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变人事生命周期动作';

CREATE TABLE IF NOT EXISTS sys_hr_sign_event_outbox (
    outbox_id bigint NOT NULL AUTO_INCREMENT COMMENT '签约事件发件箱ID',
    action_id bigint NOT NULL COMMENT '人事动作ID',
    event_version bigint NOT NULL COMMENT '动作事件版本',
    payload_json json NOT NULL COMMENT '固定人事签约事件载荷',
    status varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
    retry_count int NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time datetime DEFAULT NULL COMMENT '下次重试时间',
    last_http_status int DEFAULT NULL COMMENT '最后HTTP状态码',
    last_error varchar(1000) DEFAULT NULL COMMENT '最后错误摘要',
    remote_task_id bigint DEFAULT NULL COMMENT 'OA签约任务ID',
    version bigint NOT NULL DEFAULT 0 COMMENT '投递乐观锁版本',
    sent_time datetime DEFAULT NULL COMMENT '投递成功时间',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_sys_hr_sign_event_action_version (action_id, event_version),
    KEY idx_sys_hr_sign_event_due (status, next_retry_time, outbox_id),
    KEY idx_sys_hr_sign_event_action (action_id, outbox_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人事签约事件可靠发件箱';
