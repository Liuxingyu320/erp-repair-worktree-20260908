-- OA attendance V2 rebuild: shift, required active site, schedule,
-- photo/location/geofence punch,
-- daily settlement, leave and correction approval.
-- MySQL 5.7 / 8.0 compatible and repeat-safe.
--
-- This is an expand migration.  It intentionally does not drop or mutate
-- oa_attendance_record; the old table remains available for an explicit,
-- separately approved payroll cutover and rollback window.
-- Existing attendance sites and published schedule snapshots remain intact.
-- Site/snapshot columns stay nullable only for expand compatibility with old
-- drafts and historical rows.  New or updated schedules must bind an enabled
-- site, publishing must freeze the complete geofence snapshot, and punching
-- fails closed when any required snapshot value is absent.

CREATE TABLE IF NOT EXISTS oa_attendance_shift (
    shift_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '班次ID',
    shift_code varchar(32) NOT NULL COMMENT '稳定班次编码',
    shift_name varchar(64) NOT NULL COMMENT '班次名称',
    punch_mode varchar(24) NOT NULL DEFAULT 'SHIFT_BOUNDARY'
        COMMENT 'SHIFT_BOUNDARY/PER_WORK_SEGMENT',
    start_time time NOT NULL COMMENT '业务日内上班时间',
    end_time time NOT NULL COMMENT '业务日内下班时间',
    cross_day tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否跨次日',
    standard_minutes int NOT NULL COMMENT '应出勤分钟数',
    grace_in_minutes int NOT NULL DEFAULT 0 COMMENT '上班迟到宽限分钟',
    grace_out_minutes int NOT NULL DEFAULT 0 COMMENT '下班早退宽限分钟',
    check_in_open_minutes int NOT NULL DEFAULT 120 COMMENT '上班前可打卡分钟',
    check_in_close_minutes int NOT NULL DEFAULT 240 COMMENT '上班后可打卡分钟',
    check_out_open_minutes int NOT NULL DEFAULT 240 COMMENT '下班前可打卡分钟',
    check_out_close_minutes int NOT NULL DEFAULT 240 COMMENT '下班后可打卡分钟',
    photo_required tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否强制现场照片',
    location_required tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否强制定位',
    effective_from date DEFAULT NULL COMMENT '生效起始日',
    effective_to date DEFAULT NULL COMMENT '生效截止日',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    version int NOT NULL DEFAULT 1 COMMENT '班次规则版本',
    row_version bigint(20) NOT NULL DEFAULT 0 COMMENT '业务行乐观锁',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (shift_id),
    UNIQUE KEY uk_oa_attendance_shift_code (shift_code),
    KEY idx_oa_attendance_shift_status (status, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤班次模板';

CREATE TABLE IF NOT EXISTS oa_attendance_shift_segment (
    segment_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '班次时段ID',
    shift_id bigint(20) NOT NULL COMMENT '班次ID',
    segment_type varchar(16) NOT NULL COMMENT 'WORK/BREAK',
    segment_order int NOT NULL COMMENT '时段顺序',
    start_minute_offset int NOT NULL COMMENT '相对业务日零点开始分钟，可大于1440',
    end_minute_offset int NOT NULL COMMENT '相对业务日零点结束分钟，可大于1440',
    paid tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否计入应出勤分钟',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (segment_id),
    UNIQUE KEY uk_oa_attendance_shift_segment_order (shift_id, segment_order),
    KEY idx_oa_attendance_shift_segment_range
        (shift_id, start_minute_offset, end_minute_offset)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤班次工作及休息时段';

CREATE TABLE IF NOT EXISTS oa_attendance_site (
    site_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '考勤地点ID',
    site_code varchar(32) NOT NULL COMMENT '稳定地点编码',
    site_name varchar(64) NOT NULL COMMENT '地点名称',
    shop_id bigint(20) NOT NULL COMMENT '归属门店组织ID',
    address varchar(255) NOT NULL COMMENT '地点地址',
    longitude decimal(10,7) NOT NULL COMMENT '经度',
    latitude decimal(10,7) NOT NULL COMMENT '纬度',
    coordinate_system varchar(16) NOT NULL DEFAULT 'GCJ02'
        COMMENT 'WGS84/GCJ02/BD09',
    radius_meters int NOT NULL DEFAULT 200 COMMENT '允许打卡半径米',
    max_accuracy_meters int NOT NULL DEFAULT 100 COMMENT '允许最大定位误差米',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    row_version bigint(20) NOT NULL DEFAULT 0 COMMENT '业务行乐观锁',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (site_id),
    UNIQUE KEY uk_oa_attendance_site_code (site_code),
    KEY idx_oa_attendance_site_shop (shop_id, status, site_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤定位围栏';

CREATE TABLE IF NOT EXISTS oa_attendance_schedule (
    schedule_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '排班ID',
    schedule_no varchar(40) NOT NULL COMMENT '排班编号',
    user_id bigint(20) NOT NULL COMMENT '员工用户ID',
    user_name varchar(64) NOT NULL COMMENT '员工姓名快照',
    shop_id bigint(20) NOT NULL COMMENT '排班门店组织ID',
    business_date date NOT NULL COMMENT '排班业务日',
    shift_id bigint(20) NOT NULL COMMENT '班次ID',
    site_id bigint(20) DEFAULT NULL COMMENT '考勤地点ID；兼容历史草稿可空，新建/更新/发布必须绑定启用地点',
    status varchar(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/PUBLISHED/CHANGED/CANCELLED',
    shift_version int NOT NULL COMMENT '发布时班次版本',
    shift_code_snapshot varchar(32) NOT NULL COMMENT '班次编码快照',
    shift_name_snapshot varchar(64) NOT NULL COMMENT '班次名称快照',
    punch_mode_snapshot varchar(24) NOT NULL DEFAULT 'SHIFT_BOUNDARY'
        COMMENT '发布时打卡模式快照',
    start_time_snapshot time NOT NULL COMMENT '上班时间快照',
    end_time_snapshot time NOT NULL COMMENT '下班时间快照',
    cross_day_snapshot tinyint(1) NOT NULL DEFAULT 0 COMMENT '跨日快照',
    standard_minutes_snapshot int NOT NULL COMMENT '应出勤分钟快照',
    grace_in_minutes_snapshot int NOT NULL DEFAULT 0 COMMENT '上班宽限快照',
    grace_out_minutes_snapshot int NOT NULL DEFAULT 0 COMMENT '下班宽限快照',
    check_in_open_minutes_snapshot int NOT NULL COMMENT '上班前窗口快照',
    check_in_close_minutes_snapshot int NOT NULL COMMENT '上班后窗口快照',
    check_out_open_minutes_snapshot int NOT NULL COMMENT '下班前窗口快照',
    check_out_close_minutes_snapshot int NOT NULL COMMENT '下班后窗口快照',
    photo_required_snapshot tinyint(1) NOT NULL DEFAULT 1 COMMENT '拍照规则快照',
    location_required_snapshot tinyint(1) NOT NULL DEFAULT 1 COMMENT '定位规则快照',
    site_name_snapshot varchar(64) DEFAULT NULL COMMENT '地点名称快照；兼容历史数据可空，发布必须冻结',
    address_snapshot varchar(255) DEFAULT NULL COMMENT '地点地址快照；兼容历史数据可空，发布必须冻结',
    longitude_snapshot decimal(10,7) DEFAULT NULL COMMENT '围栏经度快照；兼容历史数据可空，发布必须冻结',
    latitude_snapshot decimal(10,7) DEFAULT NULL COMMENT '围栏纬度快照；兼容历史数据可空，发布必须冻结',
    coordinate_system_snapshot varchar(16) DEFAULT NULL COMMENT '围栏坐标系快照；兼容历史数据可空，发布必须冻结',
    radius_meters_snapshot int DEFAULT NULL COMMENT '围栏半径快照；兼容历史数据可空，发布必须冻结',
    max_accuracy_meters_snapshot int DEFAULT NULL COMMENT '定位精度快照；兼容历史数据可空，发布必须冻结',
    published_by bigint(20) DEFAULT NULL COMMENT '发布人用户ID',
    published_at datetime DEFAULT NULL COMMENT '发布时间',
    cancelled_by bigint(20) DEFAULT NULL COMMENT '取消人用户ID',
    cancelled_at datetime DEFAULT NULL COMMENT '取消时间',
    change_reason varchar(500) DEFAULT NULL COMMENT '变更或取消原因',
    row_version bigint(20) NOT NULL DEFAULT 0 COMMENT '业务行乐观锁',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (schedule_id),
    UNIQUE KEY uk_oa_attendance_schedule_no (schedule_no),
    UNIQUE KEY uk_oa_attendance_schedule_user_day (user_id, business_date),
    KEY idx_oa_attendance_schedule_shop_day (shop_id, business_date, status),
    KEY idx_oa_attendance_schedule_shift (shift_id, business_date),
    KEY idx_oa_attendance_schedule_site (site_id, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤员工排班及发布快照';

CREATE TABLE IF NOT EXISTS oa_attendance_schedule_segment_snapshot (
    schedule_segment_snapshot_id bigint(20) NOT NULL AUTO_INCREMENT
        COMMENT '排班班次段快照ID',
    schedule_id bigint(20) NOT NULL COMMENT '已发布排班ID',
    segment_order int NOT NULL COMMENT '班次段顺序快照',
    segment_type varchar(16) NOT NULL COMMENT 'WORK/BREAK',
    start_minute_offset int NOT NULL COMMENT '相对业务日零点开始分钟',
    end_minute_offset int NOT NULL COMMENT '相对业务日零点结束分钟',
    paid tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否计入核定工作分钟',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布快照时间',
    PRIMARY KEY (schedule_segment_snapshot_id),
    UNIQUE KEY uk_oa_attendance_schedule_segment_order
        (schedule_id, segment_order),
    KEY idx_oa_attendance_schedule_segment_schedule (schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤已发布排班班次段不可变快照';

CREATE TABLE IF NOT EXISTS oa_attendance_punch_challenge (
    challenge_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '一次性打卡挑战ID',
    challenge_token char(64) NOT NULL COMMENT '不可预测挑战令牌SHA-256文本',
    schedule_id bigint(20) NOT NULL COMMENT '已发布排班ID',
    user_id bigint(20) NOT NULL COMMENT '员工用户ID',
    shop_id bigint(20) NOT NULL COMMENT '排班门店ID',
    business_date date NOT NULL COMMENT '排班业务日',
    punch_type varchar(8) NOT NULL COMMENT 'IN/OUT',
    schedule_segment_snapshot_id bigint(20) DEFAULT NULL
        COMMENT '分段模式目标WORK段快照ID；首尾模式为空',
    punch_slot_key varchar(64) DEFAULT NULL
        COMMENT '不可变打卡槽位键；旧数据可为空',
    status varchar(16) NOT NULL DEFAULT 'ISSUED'
        COMMENT 'ISSUED/CONSUMED/EXPIRED/REVOKED',
    issued_at datetime NOT NULL COMMENT '服务端签发时间',
    expires_at datetime NOT NULL COMMENT '服务端过期时间',
    consumed_at datetime DEFAULT NULL COMMENT '原子消费时间',
    consumed_event_id bigint(20) DEFAULT NULL COMMENT '消费产生的打卡事件ID',
    row_version bigint(20) NOT NULL DEFAULT 0 COMMENT 'CAS版本',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (challenge_id),
    UNIQUE KEY uk_oa_attendance_challenge_token (challenge_token),
    KEY idx_oa_attendance_challenge_consume
        (user_id, schedule_id, punch_type, status, expires_at),
    KEY idx_oa_attendance_challenge_slot
        (schedule_id, punch_slot_key, status, expires_at),
    KEY idx_oa_attendance_challenge_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤短时一次性打卡挑战';

CREATE TABLE IF NOT EXISTS oa_attendance_punch_event (
    punch_event_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '不可变打卡事件ID',
    event_no varchar(40) NOT NULL COMMENT '打卡事件编号',
    schedule_id bigint(20) NOT NULL COMMENT '排班ID',
    challenge_id bigint(20) NOT NULL COMMENT '一次性挑战ID',
    user_id bigint(20) NOT NULL COMMENT '员工用户ID',
    user_name varchar(64) NOT NULL COMMENT '员工姓名快照',
    shop_id bigint(20) NOT NULL COMMENT '门店ID',
    business_date date NOT NULL COMMENT '排班业务日',
    punch_type varchar(8) NOT NULL COMMENT 'IN/OUT',
    schedule_segment_snapshot_id bigint(20) DEFAULT NULL
        COMMENT '分段模式目标WORK段快照ID；首尾模式为空',
    punch_slot_key varchar(64) DEFAULT NULL
        COMMENT '不可变打卡槽位键；旧数据可为空',
    server_punch_time datetime NOT NULL COMMENT '权威服务端打卡时间',
    client_capture_time datetime DEFAULT NULL COMMENT '客户端照片拍摄时间',
    longitude decimal(10,7) NOT NULL COMMENT '提交经度',
    latitude decimal(10,7) NOT NULL COMMENT '提交纬度',
    accuracy_meters decimal(8,2) NOT NULL COMMENT '定位精度米',
    distance_meters decimal(10,2) DEFAULT NULL COMMENT '距围栏中心米；兼容历史拒绝事件可空，成功打卡必须记录',
    coordinate_system varchar(16) NOT NULL COMMENT '坐标系',
    resolved_address varchar(255) DEFAULT NULL COMMENT '逆地理地址',
    geofence_status varchar(16) NOT NULL
        COMMENT 'INSIDE/OUTSIDE/INACCURATE；历史NOT_APPLICABLE仅兼容保留',
    verification_status varchar(16) NOT NULL COMMENT 'ACCEPTED/REJECTED',
    rejection_code varchar(32) DEFAULT NULL COMMENT '拒绝原因码',
    rejection_message varchar(255) DEFAULT NULL COMMENT '拒绝原因摘要',
    client_request_id varchar(64) NOT NULL COMMENT '客户端提交幂等键',
    client_ip varchar(64) DEFAULT NULL COMMENT '客户端IP',
    user_agent varchar(500) DEFAULT NULL COMMENT '客户端UA',
    device_id varchar(128) DEFAULT NULL COMMENT '受控设备标识',
    app_version varchar(32) DEFAULT NULL COMMENT 'App版本',
    risk_flags varchar(500) DEFAULT NULL COMMENT '风险标记集合',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (punch_event_id),
    UNIQUE KEY uk_oa_attendance_punch_event_no (event_no),
    UNIQUE KEY uk_oa_attendance_punch_challenge (challenge_id),
    UNIQUE KEY uk_oa_attendance_punch_request (user_id, client_request_id),
    KEY idx_oa_attendance_punch_schedule
        (schedule_id, punch_type, verification_status, server_punch_time),
    KEY idx_oa_attendance_punch_slot
        (schedule_id, punch_slot_key, verification_status, server_punch_time),
    KEY idx_oa_attendance_punch_user_day
        (user_id, business_date, server_punch_time),
    KEY idx_oa_attendance_punch_shop_day
        (shop_id, business_date, verification_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤不可覆盖的打卡事件';

-- Compatibility upgrade for databases where an earlier copy of this migration
-- was already applied.  MODIFY COLUMN only preserves expand-compatible
-- nullability; runtime publishing and punching still fail closed on missing
-- site/geofence snapshots.  No historical audit row is rewritten.
-- MySQL 5.7 has no MODIFY COLUMN IF EXISTS, so each change is guarded.
SET @attendance_v2_schema := DATABASE();

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE oa_attendance_schedule MODIFY COLUMN site_id bigint(20) DEFAULT NULL COMMENT ''考勤地点ID；兼容历史草稿可空，新建/更新/发布必须绑定启用地点''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_schedule'
      AND column_name = 'site_id'
      AND is_nullable = 'NO'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE oa_attendance_schedule MODIFY COLUMN site_name_snapshot varchar(64) DEFAULT NULL COMMENT ''地点名称快照；兼容历史数据可空，发布必须冻结''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_schedule'
      AND column_name = 'site_name_snapshot'
      AND is_nullable = 'NO'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE oa_attendance_schedule MODIFY COLUMN address_snapshot varchar(255) DEFAULT NULL COMMENT ''地点地址快照；兼容历史数据可空，发布必须冻结''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_schedule'
      AND column_name = 'address_snapshot'
      AND is_nullable = 'NO'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE oa_attendance_schedule MODIFY COLUMN longitude_snapshot decimal(10,7) DEFAULT NULL COMMENT ''围栏经度快照；兼容历史数据可空，发布必须冻结''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_schedule'
      AND column_name = 'longitude_snapshot'
      AND is_nullable = 'NO'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE oa_attendance_schedule MODIFY COLUMN latitude_snapshot decimal(10,7) DEFAULT NULL COMMENT ''围栏纬度快照；兼容历史数据可空，发布必须冻结''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_schedule'
      AND column_name = 'latitude_snapshot'
      AND is_nullable = 'NO'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE oa_attendance_schedule MODIFY COLUMN coordinate_system_snapshot varchar(16) DEFAULT NULL COMMENT ''围栏坐标系快照；兼容历史数据可空，发布必须冻结''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_schedule'
      AND column_name = 'coordinate_system_snapshot'
      AND is_nullable = 'NO'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE oa_attendance_schedule MODIFY COLUMN radius_meters_snapshot int DEFAULT NULL COMMENT ''围栏半径快照；兼容历史数据可空，发布必须冻结''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_schedule'
      AND column_name = 'radius_meters_snapshot'
      AND is_nullable = 'NO'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE oa_attendance_schedule MODIFY COLUMN max_accuracy_meters_snapshot int DEFAULT NULL COMMENT ''定位精度快照；兼容历史数据可空，发布必须冻结''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_schedule'
      AND column_name = 'max_accuracy_meters_snapshot'
      AND is_nullable = 'NO'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE oa_attendance_punch_event MODIFY COLUMN distance_meters decimal(10,2) DEFAULT NULL COMMENT ''距围栏中心米；兼容历史拒绝事件可空，成功打卡必须记录''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_punch_event'
      AND column_name = 'distance_meters'
      AND is_nullable = 'NO'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE oa_attendance_punch_event MODIFY COLUMN geofence_status varchar(16) NOT NULL COMMENT ''INSIDE/OUTSIDE/INACCURATE；历史NOT_APPLICABLE仅兼容保留''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_punch_event'
      AND column_name = 'geofence_status'
      AND column_comment NOT LIKE '%NOT_APPLICABLE%'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

CREATE TABLE IF NOT EXISTS oa_attendance_evidence (
    evidence_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '打卡证据ID',
    punch_event_id bigint(20) NOT NULL COMMENT '打卡事件ID',
    original_name varchar(180) NOT NULL COMMENT '原始文件名',
    original_storage_path varchar(500) NOT NULL COMMENT '原图私有相对路径',
    watermarked_storage_path varchar(500) NOT NULL COMMENT '水印图私有相对路径',
    content_type varchar(100) NOT NULL COMMENT '验证后的媒体类型',
    file_extension varchar(12) NOT NULL COMMENT '验证后的扩展名',
    original_size bigint(20) NOT NULL COMMENT '原图字节数',
    watermarked_size bigint(20) NOT NULL COMMENT '水印图字节数',
    original_sha256 char(64) NOT NULL COMMENT '原图SHA-256',
    watermarked_sha256 char(64) NOT NULL COMMENT '水印图SHA-256',
    image_width int NOT NULL COMMENT '原图像素宽度',
    image_height int NOT NULL COMMENT '原图像素高度',
    watermark_payload mediumtext NOT NULL COMMENT '服务端实际绘制水印内容快照',
    captured_at datetime DEFAULT NULL COMMENT '客户端拍摄时间',
    uploaded_by bigint(20) NOT NULL COMMENT '上传用户ID',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '证据固化时间',
    PRIMARY KEY (evidence_id),
    UNIQUE KEY uk_oa_attendance_evidence_event (punch_event_id),
    UNIQUE KEY uk_oa_attendance_evidence_original_path (original_storage_path),
    UNIQUE KEY uk_oa_attendance_evidence_watermark_path (watermarked_storage_path),
    KEY idx_oa_attendance_evidence_original_hash (original_sha256),
    KEY idx_oa_attendance_evidence_watermark_hash (watermarked_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤原图与服务器水印图私有证据';

CREATE TABLE IF NOT EXISTS oa_attendance_day_result (
    day_result_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '每日考勤结果ID',
    schedule_id bigint(20) NOT NULL COMMENT '排班ID',
    user_id bigint(20) NOT NULL COMMENT '员工用户ID',
    user_name varchar(64) NOT NULL COMMENT '员工姓名快照',
    shop_id bigint(20) NOT NULL COMMENT '门店ID',
    business_date date NOT NULL COMMENT '业务日',
    shift_id bigint(20) NOT NULL COMMENT '班次ID',
    scheduled_minutes int NOT NULL DEFAULT 0 COMMENT '计划出勤分钟',
    worked_minutes int NOT NULL DEFAULT 0 COMMENT '核定工作分钟',
    paid_leave_minutes int NOT NULL DEFAULT 0 COMMENT '带薪请假分钟',
    unpaid_leave_minutes int NOT NULL DEFAULT 0 COMMENT '无薪请假分钟',
    absence_minutes int NOT NULL DEFAULT 0 COMMENT '缺勤分钟',
    late_minutes int NOT NULL DEFAULT 0 COMMENT '迟到分钟',
    early_leave_minutes int NOT NULL DEFAULT 0 COMMENT '早退分钟',
    result_status varchar(24) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/NORMAL/LATE/EARLY/LATE_EARLY/MISSED_IN/MISSED_OUT/ABSENT/LEAVE_FULL/LEAVE_PARTIAL/EXCEPTION',
    exception_codes varchar(500) DEFAULT NULL COMMENT '异常原因码集合',
    first_in_event_id bigint(20) DEFAULT NULL COMMENT '采用的首次上班事件',
    first_in_correction_request_id bigint(20) DEFAULT NULL
        COMMENT '采用的上班补卡更正申请',
    last_out_event_id bigint(20) DEFAULT NULL COMMENT '采用的末次下班事件',
    last_out_correction_request_id bigint(20) DEFAULT NULL
        COMMENT '采用的下班补卡更正申请',
    calculation_version int NOT NULL DEFAULT 1 COMMENT '结算算法版本',
    calculated_at datetime DEFAULT NULL COMMENT '最近计算时间',
    settled_at datetime DEFAULT NULL COMMENT '最终结算时间',
    row_version bigint(20) NOT NULL DEFAULT 0 COMMENT '业务行乐观锁',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (day_result_id),
    UNIQUE KEY uk_oa_attendance_day_result_schedule (schedule_id),
    UNIQUE KEY uk_oa_attendance_day_result_user_day (user_id, business_date),
    KEY idx_oa_attendance_day_result_shop
        (shop_id, business_date, result_status),
    KEY idx_oa_attendance_day_result_settlement
        (result_status, settled_at, business_date),
    KEY idx_oa_attendance_day_result_in_correction
        (first_in_correction_request_id),
    KEY idx_oa_attendance_day_result_out_correction
        (last_out_correction_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤分钟级每日核定结果';

-- A correction is an auditable virtual punch source.  Never fabricate or
-- overwrite oa_attendance_punch_event because its challenge/location/evidence
-- contract is immutable.  These guarded upgrades also cover a database where
-- an earlier copy of this repeat-safe migration already created day_result.
SET @attendance_v2_schema := DATABASE();
SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_day_result ADD COLUMN first_in_correction_request_id bigint(20) DEFAULT NULL COMMENT ''采用的上班补卡更正申请'' AFTER first_in_event_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_day_result'
      AND column_name = 'first_in_correction_request_id'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_day_result ADD COLUMN last_out_correction_request_id bigint(20) DEFAULT NULL COMMENT ''采用的下班补卡更正申请'' AFTER last_out_event_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_day_result'
      AND column_name = 'last_out_correction_request_id'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_day_result ADD KEY idx_oa_attendance_day_result_in_correction (first_in_correction_request_id)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_day_result'
      AND index_name = 'idx_oa_attendance_day_result_in_correction'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_day_result ADD KEY idx_oa_attendance_day_result_out_correction (last_out_correction_request_id)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_day_result'
      AND index_name = 'idx_oa_attendance_day_result_out_correction'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

-- Payroll stores the exact attendance V2 minute inputs used for each salary
-- calculation.  MySQL 5.7 has no ADD COLUMN IF NOT EXISTS, so every addition
-- is guarded independently and is safe when this migration is replayed.
SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_salary_record ADD COLUMN scheduled_minutes int NOT NULL DEFAULT 0 COMMENT ''考勤V2计划出勤分钟''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_salary_record'
      AND column_name = 'scheduled_minutes'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_salary_record ADD COLUMN worked_minutes int NOT NULL DEFAULT 0 COMMENT ''考勤V2核定工作分钟''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_salary_record'
      AND column_name = 'worked_minutes'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_salary_record ADD COLUMN paid_leave_minutes int NOT NULL DEFAULT 0 COMMENT ''考勤V2带薪请假分钟''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_salary_record'
      AND column_name = 'paid_leave_minutes'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_salary_record ADD COLUMN unpaid_leave_minutes int NOT NULL DEFAULT 0 COMMENT ''考勤V2无薪请假分钟''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_salary_record'
      AND column_name = 'unpaid_leave_minutes'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_salary_record ADD COLUMN absence_minutes int NOT NULL DEFAULT 0 COMMENT ''考勤V2缺勤分钟''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_salary_record'
      AND column_name = 'absence_minutes'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_salary_record ADD COLUMN attendance_source_version varchar(40) NOT NULL DEFAULT ''LEGACY_UNVERIFIED'' COMMENT ''工资考勤来源版本''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_salary_record'
      AND column_name = 'attendance_source_version'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

CREATE TABLE IF NOT EXISTS oa_attendance_leave_type (
    leave_type_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '请假类型ID',
    type_code varchar(32) NOT NULL COMMENT '稳定类型编码',
    type_name varchar(64) NOT NULL COMMENT '类型名称',
    unit_mode varchar(16) NOT NULL DEFAULT 'MINUTE'
        COMMENT 'MINUTE/HALF_DAY/DAY/MIXED',
    pay_policy varchar(16) NOT NULL DEFAULT 'UNPAID'
        COMMENT 'PAID/UNPAID/POLICY',
    paid_ratio decimal(5,4) NOT NULL DEFAULT 0.0000 COMMENT '固定带薪比例',
    balance_required tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否校验假期余额',
    attachment_required tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否始终要求附件',
    attachment_threshold_minutes int DEFAULT NULL COMMENT '超过该分钟数要求附件',
    min_minutes int NOT NULL DEFAULT 30 COMMENT '单次最小分钟数',
    step_minutes int NOT NULL DEFAULT 30 COMMENT '申请分钟步长',
    max_minutes_per_request int DEFAULT NULL COMMENT '单次最大分钟数',
    allow_cross_day tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否允许跨业务日',
    approval_required tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否需要审批',
    sort_no int NOT NULL DEFAULT 0 COMMENT '排序',
    status varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    row_version bigint(20) NOT NULL DEFAULT 0 COMMENT '业务行乐观锁',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '政策说明',
    PRIMARY KEY (leave_type_id),
    UNIQUE KEY uk_oa_attendance_leave_type_code (type_code),
    KEY idx_oa_attendance_leave_type_status (status, sort_no, type_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤可配置请假类型';

CREATE TABLE IF NOT EXISTS oa_attendance_leave_request (
    leave_request_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '请假申请ID',
    leave_request_no varchar(40) NOT NULL COMMENT '请假单号',
    user_id bigint(20) NOT NULL COMMENT '申请人用户ID',
    user_name varchar(64) NOT NULL COMMENT '申请人姓名快照',
    shop_id bigint(20) NOT NULL COMMENT '归属门店组织ID',
    leave_type_id bigint(20) NOT NULL COMMENT '请假类型ID',
    start_time datetime NOT NULL COMMENT '请假开始时间',
    end_time datetime NOT NULL COMMENT '请假结束时间',
    total_minutes int NOT NULL COMMENT '核定请假总分钟',
    reason varchar(1000) NOT NULL COMMENT '请假原因',
    status varchar(24) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/SUBMITTING/PENDING/APPROVED/REJECTED/RETURNED/CANCELLED',
    business_round int NOT NULL DEFAULT 0 COMMENT '统一审批业务轮次',
    approval_instance_id bigint(20) DEFAULT NULL COMMENT '当前审批实例ID',
    row_version bigint(20) NOT NULL DEFAULT 0 COMMENT '业务行乐观锁',
    last_approval_event_key varchar(128) DEFAULT NULL COMMENT '最近审批回调幂等键',
    submitted_at datetime DEFAULT NULL COMMENT '提交时间',
    approved_at datetime DEFAULT NULL COMMENT '审批通过时间',
    rejected_at datetime DEFAULT NULL COMMENT '审批拒绝时间',
    cancelled_at datetime DEFAULT NULL COMMENT '撤回或销假时间',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (leave_request_id),
    UNIQUE KEY uk_oa_attendance_leave_request_no (leave_request_no),
    KEY idx_oa_attendance_leave_user
        (user_id, status, start_time),
    KEY idx_oa_attendance_leave_shop
        (shop_id, status, start_time),
    KEY idx_oa_attendance_leave_overlap
        (user_id, start_time, end_time, status),
    KEY idx_oa_attendance_leave_approval
        (approval_instance_id, business_round)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤请假申请';

CREATE TABLE IF NOT EXISTS oa_attendance_leave_segment (
    leave_segment_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '请假业务日分段ID',
    leave_request_id bigint(20) NOT NULL COMMENT '请假申请ID',
    business_date date NOT NULL COMMENT '排班业务日',
    start_time datetime NOT NULL COMMENT '分段开始时间',
    end_time datetime NOT NULL COMMENT '分段结束时间',
    total_minutes int NOT NULL COMMENT '分段总分钟',
    paid_minutes int NOT NULL DEFAULT 0 COMMENT '带薪分钟',
    unpaid_minutes int NOT NULL DEFAULT 0 COMMENT '无薪分钟',
    schedule_id bigint(20) DEFAULT NULL COMMENT '关联排班ID',
    segment_status varchar(16) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE/CANCELLED',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (leave_segment_id),
    UNIQUE KEY uk_oa_attendance_leave_segment_range
        (leave_request_id, business_date, start_time, end_time),
    KEY idx_oa_attendance_leave_segment_schedule
        (schedule_id, segment_status),
    KEY idx_oa_attendance_leave_segment_day
        (business_date, segment_status, leave_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤请假按排班业务日拆分明细';

CREATE TABLE IF NOT EXISTS oa_attendance_leave_attachment (
    attachment_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '请假附件ID',
    leave_request_id bigint(20) NOT NULL COMMENT '请假申请ID',
    original_name varchar(180) NOT NULL COMMENT '原始文件名',
    storage_path varchar(500) NOT NULL COMMENT '私有相对路径',
    content_type varchar(100) NOT NULL COMMENT '验证后的媒体类型',
    file_extension varchar(12) NOT NULL COMMENT '验证后的扩展名',
    file_size bigint(20) NOT NULL COMMENT '文件字节数',
    sha256 char(64) NOT NULL COMMENT '文件SHA-256',
    uploaded_by bigint(20) NOT NULL COMMENT '上传用户ID',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (attachment_id),
    UNIQUE KEY uk_oa_attendance_leave_attachment_path (storage_path),
    UNIQUE KEY uk_oa_attendance_leave_attachment_hash
        (leave_request_id, sha256),
    KEY idx_oa_attendance_leave_attachment_request (leave_request_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤请假私有附件';

CREATE TABLE IF NOT EXISTS oa_attendance_leave_approval_start_outbox (
    outbox_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审批发起发件箱ID',
    leave_request_id bigint(20) NOT NULL COMMENT '请假申请ID',
    business_round int NOT NULL COMMENT '审批轮次',
    idempotency_key varchar(128) NOT NULL COMMENT '统一审批幂等键',
    request_json mediumtext NOT NULL COMMENT '审批发起请求快照',
    status varchar(32) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/PROCESSING/RETRY/REMOTE_SUCCEEDED/COMPLETED/FAILED',
    attempt_count int NOT NULL DEFAULT 0 COMMENT '自动尝试次数',
    next_retry_at datetime DEFAULT NULL COMMENT '下次重试时间',
    claim_token varchar(64) DEFAULT NULL COMMENT '处理器认领令牌',
    claimed_at datetime DEFAULT NULL COMMENT '认领时间',
    last_http_status int DEFAULT NULL COMMENT '最后远端状态码',
    last_error_code varchar(64) DEFAULT NULL COMMENT '最后错误码',
    last_error varchar(500) DEFAULT NULL COMMENT '脱敏错误摘要',
    remote_instance_id bigint(20) DEFAULT NULL COMMENT '审批中心实例ID',
    remote_status varchar(32) DEFAULT NULL COMMENT '审批中心初始状态',
    remote_business_round int DEFAULT NULL COMMENT '远端实际业务轮次',
    request_row_version bigint(20) NOT NULL COMMENT '入队时请假单版本',
    row_version bigint(20) NOT NULL DEFAULT 0 COMMENT '发件箱CAS版本',
    remote_succeeded_at datetime DEFAULT NULL COMMENT '远端成功固化时间',
    completed_at datetime DEFAULT NULL COMMENT '本地关联完成时间',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_oa_attendance_leave_outbox_round
        (leave_request_id, business_round),
    UNIQUE KEY uk_oa_attendance_leave_outbox_key (idempotency_key),
    KEY idx_oa_attendance_leave_outbox_dispatch
        (status, next_retry_at, claimed_at, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤请假统一审批发起发件箱';

CREATE TABLE IF NOT EXISTS oa_attendance_correction_request (
    correction_request_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '补卡更正申请ID',
    correction_request_no varchar(40) NOT NULL COMMENT '补卡更正单号',
    user_id bigint(20) NOT NULL COMMENT '申请人用户ID',
    user_name varchar(64) NOT NULL COMMENT '申请人姓名快照',
    shop_id bigint(20) NOT NULL COMMENT '归属门店组织ID',
    schedule_id bigint(20) NOT NULL COMMENT '目标排班ID',
    business_date date NOT NULL COMMENT '目标业务日',
    correction_type varchar(24) NOT NULL
        COMMENT 'MISSING_PUNCH/WRONG_TIME/WRONG_TYPE/OTHER',
    target_punch_type varchar(8) NOT NULL COMMENT 'IN/OUT',
    target_schedule_segment_snapshot_id bigint(20) DEFAULT NULL
        COMMENT '分段模式目标WORK段快照ID；首尾模式为空',
    target_punch_slot_key varchar(64) DEFAULT NULL
        COMMENT '补卡目标槽位键；旧数据可为空',
    original_punch_event_id bigint(20) DEFAULT NULL COMMENT '原始打卡事件ID',
    requested_punch_time datetime NOT NULL COMMENT '申请核定打卡时间',
    reason varchar(1000) NOT NULL COMMENT '补卡或更正原因',
    attachment_refs mediumtext COMMENT '私有附件引用JSON，仅作快照',
    status varchar(24) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/SUBMITTING/PENDING/APPROVED/REJECTED/RETURNED/CANCELLED',
    business_round int NOT NULL DEFAULT 0 COMMENT '统一审批业务轮次',
    approval_instance_id bigint(20) DEFAULT NULL COMMENT '当前审批实例ID',
    row_version bigint(20) NOT NULL DEFAULT 0 COMMENT '业务行乐观锁',
    last_approval_event_key varchar(128) DEFAULT NULL COMMENT '最近审批回调幂等键',
    submitted_at datetime DEFAULT NULL COMMENT '提交时间',
    approved_at datetime DEFAULT NULL COMMENT '审批通过时间',
    rejected_at datetime DEFAULT NULL COMMENT '审批拒绝时间',
    cancelled_at datetime DEFAULT NULL COMMENT '撤回时间',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (correction_request_id),
    UNIQUE KEY uk_oa_attendance_correction_request_no (correction_request_no),
    KEY idx_oa_attendance_correction_user
        (user_id, status, business_date),
    KEY idx_oa_attendance_correction_shop
        (shop_id, status, business_date),
    KEY idx_oa_attendance_correction_schedule
        (schedule_id, target_punch_type, status),
    KEY idx_oa_attendance_correction_slot
        (schedule_id, target_punch_slot_key, status),
    KEY idx_oa_attendance_correction_approval
        (approval_instance_id, business_round)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤补卡与更正申请，原打卡事件不可覆盖';

CREATE TABLE IF NOT EXISTS oa_attendance_correction_approval_start_outbox (
    outbox_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审批发起发件箱ID',
    correction_request_id bigint(20) NOT NULL COMMENT '补卡更正申请ID',
    business_round int NOT NULL COMMENT '审批轮次',
    idempotency_key varchar(128) NOT NULL COMMENT '统一审批幂等键',
    request_json mediumtext NOT NULL COMMENT '审批发起请求快照',
    status varchar(32) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/PROCESSING/RETRY/REMOTE_SUCCEEDED/COMPLETED/FAILED',
    attempt_count int NOT NULL DEFAULT 0 COMMENT '自动尝试次数',
    next_retry_at datetime DEFAULT NULL COMMENT '下次重试时间',
    claim_token varchar(64) DEFAULT NULL COMMENT '处理器认领令牌',
    claimed_at datetime DEFAULT NULL COMMENT '认领时间',
    last_http_status int DEFAULT NULL COMMENT '最后远端状态码',
    last_error_code varchar(64) DEFAULT NULL COMMENT '最后错误码',
    last_error varchar(500) DEFAULT NULL COMMENT '脱敏错误摘要',
    remote_instance_id bigint(20) DEFAULT NULL COMMENT '审批中心实例ID',
    remote_status varchar(32) DEFAULT NULL COMMENT '审批中心初始状态',
    remote_business_round int DEFAULT NULL COMMENT '远端实际业务轮次',
    request_row_version bigint(20) NOT NULL COMMENT '入队时更正单版本',
    row_version bigint(20) NOT NULL DEFAULT 0 COMMENT '发件箱CAS版本',
    remote_succeeded_at datetime DEFAULT NULL COMMENT '远端成功固化时间',
    completed_at datetime DEFAULT NULL COMMENT '本地关联完成时间',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_oa_attendance_correction_outbox_round
        (correction_request_id, business_round),
    UNIQUE KEY uk_oa_attendance_correction_outbox_key (idempotency_key),
    KEY idx_oa_attendance_correction_outbox_dispatch
        (status, next_retry_at, claimed_at, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OA考勤补卡统一审批发起发件箱';

-- Conservative defaults: attendance V2 is deployed disabled and must be
-- enabled only after shift/schedule configuration and a store pilot.
INSERT INTO sys_config
    (config_name, config_key, config_value, config_type,
     create_by, create_time, remark)
SELECT 'OA考勤V2总开关', 'feature.oa.attendance.v2.enabled',
       'false', 'Y', 'system', NOW(),
       '默认关闭；完成地点围栏、班次排班和真机定位拍照验收后按发布清单开启'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key = 'feature.oa.attendance.v2.enabled'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type,
     create_by, create_time, remark)
SELECT 'OA考勤挑战有效秒数', 'oa.attendance.challenge.ttl.seconds',
       '180', 'Y', 'system', NOW(), '一次性打卡挑战默认3分钟失效'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key = 'oa.attendance.challenge.ttl.seconds'
);

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type,
     create_by, create_time, remark)
SELECT 'OA考勤照片最大字节数', 'oa.attendance.photo.max.bytes',
       '5242880', 'Y', 'system', NOW(), '现场照片默认最大5MB'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key = 'oa.attendance.photo.max.bytes'
);

-- Policy-neutral leave types.  Company/legal paid-leave policy is deliberately
-- not invented here; administrators may configure additional types and ratios.
INSERT INTO oa_attendance_leave_type
    (type_code, type_name, unit_mode, pay_policy, paid_ratio,
     balance_required, attachment_required, attachment_threshold_minutes,
     min_minutes, step_minutes, allow_cross_day, approval_required,
     sort_no, status, create_by, create_time, remark)
SELECT 'PERSONAL', '事假', 'MINUTE', 'UNPAID', 0.0000,
       0, 0, NULL, 30, 30, 1, 1, 10, 'ENABLED',
       'system', NOW(), '默认无薪；实际公司制度可另行调整'
WHERE NOT EXISTS (
    SELECT 1 FROM oa_attendance_leave_type WHERE type_code = 'PERSONAL'
);

INSERT INTO oa_attendance_leave_type
    (type_code, type_name, unit_mode, pay_policy, paid_ratio,
     balance_required, attachment_required, attachment_threshold_minutes,
     min_minutes, step_minutes, allow_cross_day, approval_required,
     sort_no, status, create_by, create_time, remark)
SELECT 'SICK', '病假', 'MINUTE', 'POLICY', 0.0000,
       0, 0, 480, 30, 30, 1, 1, 20, 'ENABLED',
       'system', NOW(), '超过一个标准工作日要求证明；工资比例由公司政策配置'
WHERE NOT EXISTS (
    SELECT 1 FROM oa_attendance_leave_type WHERE type_code = 'SICK'
);

-- Unified approval: leave.  A missing organization leader blocks submission;
-- if the applicant is the resolved leader, resolution skips that person and
-- continues upward instead of self-approving.
INSERT INTO approval_template
    (business_code, template_name, business_source, engine_mode,
     definition_mode, legacy_adapter_code, callback_service,
     template_status, create_by, create_time, remark)
SELECT 'OA_ATTENDANCE_LEAVE', '考勤请假审批', 'oa', 'NATIVE', 'FIXED',
       NULL, 'erp-oa', 'DRAFT', 'system', NOW(),
       '组织负责人审批；缺失阻断，本人节点向上跳过'
WHERE NOT EXISTS (
    SELECT 1 FROM approval_template
    WHERE business_code = 'OA_ATTENDANCE_LEAVE'
);

SET @attendance_leave_template_id := (
    SELECT template_id FROM approval_template
    WHERE business_code = 'OA_ATTENDANCE_LEAVE' LIMIT 1
);

INSERT INTO approval_rule
    (template_id, rule_code, rule_name, scope_type, scope_id, scope_name,
     business_subtype, rule_status, current_version_id, latest_version_no,
     lock_version, create_by, create_time, remark)
SELECT @attendance_leave_template_id, 'OA_ATTENDANCE_LEAVE_DEFAULT',
       '考勤请假默认审批', 'ALL', NULL, NULL, 'ALL', 'DRAFT',
       NULL, 1, 0, 'system', NOW(),
       '从申请人归属组织向上解析负责人'
WHERE @attendance_leave_template_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule
      WHERE rule_code = 'OA_ATTENDANCE_LEAVE_DEFAULT'
  );

SET @attendance_leave_rule_id := (
    SELECT rule_id FROM approval_rule
    WHERE template_id = @attendance_leave_template_id
      AND rule_code = 'OA_ATTENDANCE_LEAVE_DEFAULT'
    LIMIT 1
);
SET @attendance_leave_definition :=
    '{"schemaVersion":1,"versionNo":1,"selector":{"ruleCode":"OA_ATTENDANCE_LEAVE_DEFAULT","ruleName":"考勤请假默认审批","scopeType":"ALL","scopeId":null,"scopeName":null,"businessSubtype":"ALL"},"conditions":[],"nodes":[{"nodeOrder":1,"nodeCode":"ORG_LEADER","nodeName":"组织负责人","strategyType":"ORG_LEADER","strategyCode":"ORG_LEADER","strategyConfig":{},"approvalMode":"UNIQUE_BEST","requiredCount":1,"missingPolicy":"BLOCK","selfPolicy":"SKIP_THROUGH","returnAllowed":"1","rejectAllowed":"1"}]}';

INSERT INTO approval_rule_version
    (rule_id, version_no, version_status, definition_snapshot,
     definition_checksum, published_by_user_id, published_by_name,
     published_time, lock_version, create_by, create_time, remark)
SELECT @attendance_leave_rule_id, 1, 'PUBLISHED',
       @attendance_leave_definition,
       SHA2(@attendance_leave_definition, 256),
       NULL, 'system', NOW(), 0, 'system', NOW(),
       '考勤请假内置组织负责人审批版本'
WHERE @attendance_leave_rule_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule_version
      WHERE rule_id = @attendance_leave_rule_id AND version_no = 1
  );

SET @attendance_leave_version_id := (
    SELECT version_id FROM approval_rule_version
    WHERE rule_id = @attendance_leave_rule_id AND version_no = 1 LIMIT 1
);

INSERT INTO approval_version_node
    (version_id, node_order, node_code, node_name, strategy_type,
     strategy_code, strategy_config, approval_mode, required_count,
     missing_policy, self_policy, return_allowed, reject_allowed,
     create_by, create_time, remark)
SELECT @attendance_leave_version_id, 1, 'ORG_LEADER', '组织负责人',
       'ORG_LEADER', 'ORG_LEADER', '{}', 'UNIQUE_BEST', 1,
       'BLOCK', 'SKIP_THROUGH', '1', '1', 'system', NOW(),
       '缺失负责人阻断；申请人本人则继续向上查找'
WHERE @attendance_leave_version_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_version_node
      WHERE version_id = @attendance_leave_version_id
        AND node_code = 'ORG_LEADER'
  );

UPDATE approval_rule
SET rule_status = 'ACTIVE',
    current_version_id = @attendance_leave_version_id,
    latest_version_no = 1,
    lock_version = lock_version + 1,
    update_by = 'system', update_time = NOW()
WHERE rule_id = @attendance_leave_rule_id
  AND rule_status = 'DRAFT'
  AND current_version_id IS NULL
  AND @attendance_leave_version_id IS NOT NULL
  AND 1 = (
      SELECT COUNT(*) FROM approval_version_node
      WHERE version_id = @attendance_leave_version_id
  );

UPDATE approval_template
SET template_status = 'ACTIVE', lock_version = lock_version + 1,
    update_by = 'system', update_time = NOW()
WHERE template_id = @attendance_leave_template_id
  AND template_status = 'DRAFT'
  AND EXISTS (
      SELECT 1 FROM approval_rule
      WHERE rule_id = @attendance_leave_rule_id
        AND rule_status = 'ACTIVE'
        AND current_version_id = @attendance_leave_version_id
  );

-- Unified approval: correction, with the same fail-closed/self-skip policy.
INSERT INTO approval_template
    (business_code, template_name, business_source, engine_mode,
     definition_mode, legacy_adapter_code, callback_service,
     template_status, create_by, create_time, remark)
SELECT 'OA_ATTENDANCE_CORRECTION', '考勤补卡审批', 'oa', 'NATIVE', 'FIXED',
       NULL, 'erp-oa', 'DRAFT', 'system', NOW(),
       '组织负责人审批；缺失阻断，本人节点向上跳过'
WHERE NOT EXISTS (
    SELECT 1 FROM approval_template
    WHERE business_code = 'OA_ATTENDANCE_CORRECTION'
);

SET @attendance_correction_template_id := (
    SELECT template_id FROM approval_template
    WHERE business_code = 'OA_ATTENDANCE_CORRECTION' LIMIT 1
);

INSERT INTO approval_rule
    (template_id, rule_code, rule_name, scope_type, scope_id, scope_name,
     business_subtype, rule_status, current_version_id, latest_version_no,
     lock_version, create_by, create_time, remark)
SELECT @attendance_correction_template_id, 'OA_ATTENDANCE_CORRECTION_DEFAULT',
       '考勤补卡默认审批', 'ALL', NULL, NULL, 'ALL', 'DRAFT',
       NULL, 1, 0, 'system', NOW(),
       '从申请人归属组织向上解析负责人'
WHERE @attendance_correction_template_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule
      WHERE rule_code = 'OA_ATTENDANCE_CORRECTION_DEFAULT'
  );

SET @attendance_correction_rule_id := (
    SELECT rule_id FROM approval_rule
    WHERE template_id = @attendance_correction_template_id
      AND rule_code = 'OA_ATTENDANCE_CORRECTION_DEFAULT'
    LIMIT 1
);
SET @attendance_correction_definition :=
    '{"schemaVersion":1,"versionNo":1,"selector":{"ruleCode":"OA_ATTENDANCE_CORRECTION_DEFAULT","ruleName":"考勤补卡默认审批","scopeType":"ALL","scopeId":null,"scopeName":null,"businessSubtype":"ALL"},"conditions":[],"nodes":[{"nodeOrder":1,"nodeCode":"ORG_LEADER","nodeName":"组织负责人","strategyType":"ORG_LEADER","strategyCode":"ORG_LEADER","strategyConfig":{},"approvalMode":"UNIQUE_BEST","requiredCount":1,"missingPolicy":"BLOCK","selfPolicy":"SKIP_THROUGH","returnAllowed":"1","rejectAllowed":"1"}]}';

INSERT INTO approval_rule_version
    (rule_id, version_no, version_status, definition_snapshot,
     definition_checksum, published_by_user_id, published_by_name,
     published_time, lock_version, create_by, create_time, remark)
SELECT @attendance_correction_rule_id, 1, 'PUBLISHED',
       @attendance_correction_definition,
       SHA2(@attendance_correction_definition, 256),
       NULL, 'system', NOW(), 0, 'system', NOW(),
       '考勤补卡内置组织负责人审批版本'
WHERE @attendance_correction_rule_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_rule_version
      WHERE rule_id = @attendance_correction_rule_id AND version_no = 1
  );

SET @attendance_correction_version_id := (
    SELECT version_id FROM approval_rule_version
    WHERE rule_id = @attendance_correction_rule_id AND version_no = 1 LIMIT 1
);

INSERT INTO approval_version_node
    (version_id, node_order, node_code, node_name, strategy_type,
     strategy_code, strategy_config, approval_mode, required_count,
     missing_policy, self_policy, return_allowed, reject_allowed,
     create_by, create_time, remark)
SELECT @attendance_correction_version_id, 1, 'ORG_LEADER', '组织负责人',
       'ORG_LEADER', 'ORG_LEADER', '{}', 'UNIQUE_BEST', 1,
       'BLOCK', 'SKIP_THROUGH', '1', '1', 'system', NOW(),
       '缺失负责人阻断；申请人本人则继续向上查找'
WHERE @attendance_correction_version_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM approval_version_node
      WHERE version_id = @attendance_correction_version_id
        AND node_code = 'ORG_LEADER'
  );

UPDATE approval_rule
SET rule_status = 'ACTIVE',
    current_version_id = @attendance_correction_version_id,
    latest_version_no = 1,
    lock_version = lock_version + 1,
    update_by = 'system', update_time = NOW()
WHERE rule_id = @attendance_correction_rule_id
  AND rule_status = 'DRAFT'
  AND current_version_id IS NULL
  AND @attendance_correction_version_id IS NOT NULL
  AND 1 = (
      SELECT COUNT(*) FROM approval_version_node
      WHERE version_id = @attendance_correction_version_id
  );

UPDATE approval_template
SET template_status = 'ACTIVE', lock_version = lock_version + 1,
    update_by = 'system', update_time = NOW()
WHERE template_id = @attendance_correction_template_id
  AND template_status = 'DRAFT'
  AND EXISTS (
      SELECT 1 FROM approval_rule
      WHERE rule_id = @attendance_correction_rule_id
        AND rule_status = 'ACTIVE'
        AND current_version_id = @attendance_correction_version_id
  );

-- Replace the old visible attendance entry with the V2 center.  The old API
-- permission buttons are disabled, not deleted, so rollback evidence remains.
SET @attendance_center_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms IN ('oa:attendance:center:list', 'oa:attendance:list')
    ORDER BY (perms = 'oa:attendance:center:list') DESC, menu_id
    LIMIT 1
);

UPDATE sys_menu
SET menu_name = '考勤管理', path = 'attendance-v2',
    component = 'oa/attendance/index', route_name = 'OaAttendanceV2',
    visible = '0', status = '0', perms = 'oa:attendance:center:list',
    icon = 'time-range', update_by = 'system', update_time = NOW(),
    remark = '考勤V2班次、排班、打卡定位与照片证据、请假和补卡中心'
WHERE menu_id = @attendance_center_menu_id;

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT '考勤管理',
       COALESCE((SELECT menu_id FROM sys_menu
                 WHERE menu_type = 'M'
                   AND (path = 'oa' OR menu_name = 'OA管理')
                 ORDER BY menu_id LIMIT 1), 3000),
       5, 'attendance-v2', 'oa/attendance/index', NULL, 'OaAttendanceV2',
       1, 0, 'C', '0', '0', 'oa:attendance:center:list', 'time-range',
       'system', NOW(),
       '考勤V2班次、排班、打卡定位与照片证据、请假和补卡中心'
WHERE @attendance_center_menu_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'oa:attendance:center:list'
  );

SET @attendance_center_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE perms = 'oa:attendance:center:list'
    ORDER BY menu_id LIMIT 1
);

UPDATE sys_menu
SET visible = '1', status = '1', update_by = 'system', update_time = NOW(),
    remark = '已停用：旧考勤权限由考勤V2细分权限替代'
WHERE perms IN (
    'oa:attendance:list', 'oa:attendance:query', 'oa:attendance:export'
);

DROP TEMPORARY TABLE IF EXISTS tmp_oa_attendance_v2_permission;

-- Expand-only compatibility for per-work-segment punch slots.  Existing
-- shifts and schedules retain the legacy two-boundary behavior, while old
-- challenge/event/correction rows remain valid with NULL slot identity.
SET @attendance_v2_schema := DATABASE();

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_shift ADD COLUMN punch_mode varchar(24) NOT NULL DEFAULT ''SHIFT_BOUNDARY'' COMMENT ''SHIFT_BOUNDARY/PER_WORK_SEGMENT'' AFTER shift_name',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_shift'
      AND column_name = 'punch_mode'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_schedule ADD COLUMN punch_mode_snapshot varchar(24) NOT NULL DEFAULT ''SHIFT_BOUNDARY'' COMMENT ''发布时打卡模式快照'' AFTER shift_name_snapshot',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_schedule'
      AND column_name = 'punch_mode_snapshot'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_punch_challenge ADD COLUMN schedule_segment_snapshot_id bigint(20) DEFAULT NULL COMMENT ''分段模式目标WORK段快照ID；首尾模式为空'' AFTER punch_type',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_punch_challenge'
      AND column_name = 'schedule_segment_snapshot_id'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_punch_challenge ADD COLUMN punch_slot_key varchar(64) DEFAULT NULL COMMENT ''不可变打卡槽位键；旧数据可为空'' AFTER schedule_segment_snapshot_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_punch_challenge'
      AND column_name = 'punch_slot_key'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_punch_event ADD COLUMN schedule_segment_snapshot_id bigint(20) DEFAULT NULL COMMENT ''分段模式目标WORK段快照ID；首尾模式为空'' AFTER punch_type',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_punch_event'
      AND column_name = 'schedule_segment_snapshot_id'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_punch_event ADD COLUMN punch_slot_key varchar(64) DEFAULT NULL COMMENT ''不可变打卡槽位键；旧数据可为空'' AFTER schedule_segment_snapshot_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_punch_event'
      AND column_name = 'punch_slot_key'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_correction_request ADD COLUMN target_schedule_segment_snapshot_id bigint(20) DEFAULT NULL COMMENT ''分段模式目标WORK段快照ID；首尾模式为空'' AFTER target_punch_type',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_correction_request'
      AND column_name = 'target_schedule_segment_snapshot_id'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_correction_request ADD COLUMN target_punch_slot_key varchar(64) DEFAULT NULL COMMENT ''补卡目标槽位键；旧数据可为空'' AFTER target_schedule_segment_snapshot_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_correction_request'
      AND column_name = 'target_punch_slot_key'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_punch_challenge ADD KEY idx_oa_attendance_challenge_slot (schedule_id, punch_slot_key, status, expires_at)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_punch_challenge'
      AND index_name = 'idx_oa_attendance_challenge_slot'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_punch_event ADD KEY idx_oa_attendance_punch_slot (schedule_id, punch_slot_key, verification_status, server_punch_time)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_punch_event'
      AND index_name = 'idx_oa_attendance_punch_slot'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;

SET @attendance_v2_sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE oa_attendance_correction_request ADD KEY idx_oa_attendance_correction_slot (schedule_id, target_punch_slot_key, status)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = @attendance_v2_schema
      AND table_name = 'oa_attendance_correction_request'
      AND index_name = 'idx_oa_attendance_correction_slot'
);
PREPARE attendance_v2_stmt FROM @attendance_v2_sql;
EXECUTE attendance_v2_stmt;
DEALLOCATE PREPARE attendance_v2_stmt;
-- Append-only facts used to close work intervals left between approved leave.
CREATE TABLE IF NOT EXISTS oa_attendance_remaining_work_confirmation (
    confirmation_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '剩余工作确认ID',
    confirmation_no varchar(32) NOT NULL COMMENT '不可变确认编号',
    schedule_id bigint(20) NOT NULL COMMENT '已发布排班ID',
    user_id bigint(20) NOT NULL,
    user_name varchar(64) NOT NULL,
    shop_id bigint(20) NOT NULL,
    business_date date NOT NULL,
    remaining_start datetime NOT NULL COMMENT '请假差集区间开始',
    remaining_end datetime NOT NULL COMMENT '请假差集区间结束',
    decision varchar(32) NOT NULL COMMENT 'ATTENDED/ABSENT/RETURN_FOR_EVIDENCE',
    actual_arrival_time datetime DEFAULT NULL,
    actual_departure_time datetime DEFAULT NULL,
    reason varchar(500) NOT NULL,
    supersedes_confirmation_id bigint(20) DEFAULT NULL
        COMMENT '追加更正时指向上一事实，不更新或删除旧事实',
    decided_by bigint(20) NOT NULL,
    decided_by_name varchar(64) NOT NULL,
    decided_at datetime NOT NULL,
    create_by varchar(64) DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (confirmation_id),
    UNIQUE KEY uk_oa_attendance_remaining_work_no (confirmation_no),
    KEY idx_oa_attendance_remaining_work_interval
        (schedule_id, remaining_start, remaining_end, confirmation_id),
    KEY idx_oa_attendance_remaining_work_shop_day
        (shop_id, business_date, schedule_id),
    KEY idx_oa_attendance_remaining_work_supersedes
        (supersedes_confirmation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='部分请假剩余工作区间追加确认';

CREATE TABLE IF NOT EXISTS oa_attendance_remaining_work_attachment (
    attachment_id bigint(20) NOT NULL AUTO_INCREMENT,
    confirmation_id bigint(20) NOT NULL,
    original_name varchar(255) NOT NULL,
    storage_path varchar(500) NOT NULL,
    content_type varchar(100) NOT NULL,
    file_extension varchar(16) NOT NULL,
    file_size bigint(20) NOT NULL,
    sha256 char(64) NOT NULL,
    uploaded_by bigint(20) NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (attachment_id),
    UNIQUE KEY uk_oa_attendance_remaining_work_attachment_path (storage_path),
    KEY idx_oa_attendance_remaining_work_attachment_confirmation
        (confirmation_id, attachment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='剩余工作区间确认补充证据';


CREATE TEMPORARY TABLE tmp_oa_attendance_v2_permission (
    menu_name varchar(50) NOT NULL,
    order_num int NOT NULL,
    perms varchar(100) NOT NULL PRIMARY KEY,
    remark varchar(500) NOT NULL
) ENGINE=Memory;

INSERT INTO tmp_oa_attendance_v2_permission
    (menu_name, order_num, perms, remark)
VALUES
('班次列表', 10, 'oa:attendance:shift:list', '查看班次模板'),
('班次查询', 11, 'oa:attendance:shift:query', '查询班次详情'),
('班次新增', 12, 'oa:attendance:shift:add', '新增班次模板'),
('班次修改', 13, 'oa:attendance:shift:edit', '修改班次并递增版本'),
('班次删除', 14, 'oa:attendance:shift:remove', '停用未引用班次'),
('地点列表', 20, 'oa:attendance:site:list', '按授权门店查看考勤地点'),
('地点查询', 21, 'oa:attendance:site:query', '查询考勤地点详情'),
('地点新增', 22, 'oa:attendance:site:add', '新增门店考勤地点和围栏'),
('地点修改', 23, 'oa:attendance:site:edit', '修改未冻结到既有排班的地点配置'),
('地点删除', 24, 'oa:attendance:site:remove', '停用未被有效排班使用的地点'),
('排班列表', 30, 'oa:attendance:schedule:list', '按授权门店查看排班'),
('排班查询', 31, 'oa:attendance:schedule:query', '查询排班详情'),
('排班新增', 32, 'oa:attendance:schedule:add', '新增或批量安排排班'),
('排班修改', 33, 'oa:attendance:schedule:edit', '修改草稿排班'),
('排班删除', 34, 'oa:attendance:schedule:remove', '取消草稿或发布排班'),
('排班发布', 35, 'oa:attendance:schedule:publish', '发布并冻结班次规则快照'),
('本人打卡', 40, 'oa:attendance:punch:self', '本人获取上下文、挑战并拍照定位打卡'),
('本人考勤记录', 41, 'oa:attendance:record:self', '本人查看自己的考勤和证据'),
('团队考勤列表', 42, 'oa:attendance:record:list', '按授权门店查看团队考勤'),
('敏感考勤证据', 43, 'oa:attendance:record:evidence', '查看精确位置和私有照片证据'),
('考勤结果导出', 44, 'oa:attendance:record:export', '导出考勤结果，默认不含精确位置和照片'),
('请假类型列表', 50, 'oa:attendance:leave:type:list', '查看请假类型配置'),
('请假类型新增', 51, 'oa:attendance:leave:type:add', '新增请假类型'),
('请假类型修改', 52, 'oa:attendance:leave:type:edit', '修改请假类型'),
('请假类型删除', 53, 'oa:attendance:leave:type:remove', '停用请假类型'),
('本人请假', 54, 'oa:attendance:leave:self', '本人申请、撤回和查看请假'),
('团队请假列表', 55, 'oa:attendance:leave:list', '按授权门店查看请假'),
('请假审批能力', 56, 'oa:attendance:leave:approve', '统一审批组织负责人候选权限'),
('本人补卡', 60, 'oa:attendance:correction:self', '本人申请和查看补卡更正'),
('团队补卡列表', 61, 'oa:attendance:correction:list', '按授权门店查看补卡更正'),
('补卡审批能力', 62, 'oa:attendance:correction:approve', '统一审批组织负责人候选权限'),
('每日结果列表', 70, 'oa:attendance:day:list', '查看分钟级每日结果'),
('每日结果结算', 71, 'oa:attendance:day:settle', '执行或重算每日考勤结果'),
('剩余工作确认', 72, 'oa:attendance:remaining-work:manage', '对部分请假形成的剩余工作区间追加核验事实');

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
SELECT p.menu_name, @attendance_center_menu_id, p.order_num,
       '', NULL, NULL, '', 1, 0, 'F', '0', '0', p.perms, '#',
       'system', NOW(), p.remark
FROM tmp_oa_attendance_v2_permission p
WHERE @attendance_center_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu m WHERE m.perms = p.perms
  );

-- Every active role may use its own mobile attendance/leave/correction flow.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms IN (
    'oa:attendance:punch:self',
    'oa:attendance:record:self',
    'oa:attendance:leave:self',
    'oa:attendance:correction:self'
)
WHERE r.status = '0' AND r.del_flag = '0';

-- Head-office/admin roles manage global rules and receive the V2 center.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m
  ON m.perms = 'oa:attendance:center:list'
  OR m.perms LIKE 'oa:attendance:%'
WHERE r.status = '0' AND r.del_flag = '0'
  AND (r.role_id = 1 OR r.role_key IN ('admin', 'yyzj', 'zjl', 'qyyyzzj'));

-- Store-management roles receive scoped scheduling and team exception actions,
-- but never the high-sensitive evidence/export or global-rule edit permissions.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms IN (
    'oa:attendance:center:list',
    'oa:attendance:shift:list', 'oa:attendance:shift:query',
    'oa:attendance:site:list', 'oa:attendance:site:query',
    'oa:attendance:site:add', 'oa:attendance:site:edit',
    'oa:attendance:site:remove',
    'oa:attendance:schedule:list', 'oa:attendance:schedule:query',
    'oa:attendance:schedule:add', 'oa:attendance:schedule:edit',
    'oa:attendance:schedule:remove', 'oa:attendance:schedule:publish',
    'oa:attendance:record:list',
    'oa:attendance:leave:list', 'oa:attendance:leave:approve',
    'oa:attendance:correction:list', 'oa:attendance:correction:approve',
    'oa:attendance:day:list', 'oa:attendance:remaining-work:manage'
)
WHERE r.status = '0' AND r.del_flag = '0'
  AND r.role_key IN ('dz', 'yyjl', 'zdjl');

-- Existing unified-todo approvers and configured organization leaders can
-- execute the two approval actions without receiving management data access.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, attendance_permission.menu_id
FROM sys_role_menu rm
JOIN sys_menu existing_permission
  ON existing_permission.menu_id = rm.menu_id
JOIN sys_menu attendance_permission
  ON attendance_permission.perms IN (
      'oa:attendance:leave:approve',
      'oa:attendance:correction:approve'
  )
JOIN sys_role r ON r.role_id = rm.role_id
WHERE existing_permission.perms = 'oa:todo:approve'
  AND r.status = '0' AND r.del_flag = '0';

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT ur.role_id, attendance_permission.menu_id
FROM sys_dept d
JOIN sys_user u ON u.user_id = d.leader_user_id
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role r ON r.role_id = ur.role_id
JOIN sys_menu attendance_permission
  ON attendance_permission.perms IN (
      'oa:attendance:leave:approve',
      'oa:attendance:correction:approve'
  )
WHERE d.del_flag = '0' AND d.status = '0'
  AND u.del_flag = '0' AND u.status = '0'
  AND r.del_flag = '0' AND r.status = '0';

DROP TEMPORARY TABLE IF EXISTS tmp_oa_attendance_v2_permission;
