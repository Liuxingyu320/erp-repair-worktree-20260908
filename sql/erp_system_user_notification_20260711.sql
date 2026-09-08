-- Targeted user notifications and mobile device push tokens.
-- These tables intentionally remain separate from the all-user sys_notice feed.

CREATE TABLE IF NOT EXISTS sys_user_notification (
    notification_id bigint NOT NULL AUTO_INCREMENT COMMENT '定向消息ID',
    user_id bigint NOT NULL COMMENT '接收用户ID',
    channel varchar(20) NOT NULL COMMENT '消息渠道',
    business_key varchar(180) NOT NULL COMMENT '业务幂等键',
    title varchar(120) NOT NULL COMMENT '消息标题',
    body varchar(1000) NOT NULL COMMENT '消息正文',
    route_type varchar(64) NOT NULL COMMENT '客户端白名单路由类型',
    route_params varchar(2000) DEFAULT NULL COMMENT '非敏感路由参数JSON',
    read_status char(1) NOT NULL DEFAULT '0' COMMENT '阅读状态（0未读 1已读）',
    read_time datetime DEFAULT NULL COMMENT '阅读时间',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (notification_id),
    UNIQUE KEY uk_sys_user_notification_business (user_id, business_key),
    KEY idx_sys_user_notification_unread (user_id, read_status, notification_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指定用户可见的站内消息';

CREATE TABLE IF NOT EXISTS sys_user_device_token (
    token_id bigint NOT NULL AUTO_INCREMENT COMMENT '设备令牌ID',
    user_id bigint NOT NULL COMMENT '当前绑定用户ID',
    platform varchar(20) NOT NULL COMMENT '设备平台（ANDROID IOS）',
    token text NOT NULL COMMENT '推送服务原始令牌，仅供发送使用',
    token_hash char(64) NOT NULL COMMENT '原始令牌SHA-256',
    app_id varchar(100) NOT NULL COMMENT '移动应用ID',
    device_name varchar(200) DEFAULT NULL COMMENT '设备名称',
    enabled char(1) NOT NULL DEFAULT '1' COMMENT '是否有效（1有效 0禁用）',
    last_registered_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近注册时间',
    disabled_time datetime DEFAULT NULL COMMENT '禁用时间',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (token_id),
    UNIQUE KEY uk_sys_user_device_token (platform, token_hash),
    KEY idx_sys_user_device_token_user (user_id, enabled, platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户移动设备推送令牌';
