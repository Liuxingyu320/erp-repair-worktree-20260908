-- Run after all attendance V2 schema migrations as part of the new-only release.
-- This removes obsolete permission entries, never historical attendance records.
DROP PROCEDURE IF EXISTS assert_attendance_v2_cutover_ready;
DELIMITER $$
CREATE PROCEDURE assert_attendance_v2_cutover_ready()
BEGIN
    DECLARE ready_tables INT DEFAULT 0;
    SELECT COUNT(*) INTO ready_tables FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name IN (
        'oa_attendance_shift',
        'oa_attendance_shift_segment',
        'oa_attendance_site',
        'oa_attendance_schedule',
        'oa_attendance_schedule_segment_snapshot',
        'oa_attendance_punch_challenge',
        'oa_attendance_punch_event',
        'oa_attendance_evidence',
        'oa_attendance_day_result',
        'oa_attendance_leave_type',
        'oa_attendance_leave_request',
        'oa_attendance_leave_segment',
        'oa_attendance_leave_attachment',
        'oa_attendance_leave_approval_start_outbox',
        'oa_attendance_correction_request',
        'oa_attendance_correction_approval_start_outbox',
        'oa_attendance_remaining_work_confirmation',
        'oa_attendance_remaining_work_attachment',
        'oa_attendance_time_credit_period_lock',
        'oa_attendance_time_credit_adjustment');
    IF ready_tables <> 20 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Attendance V2 schema is incomplete; cutover aborted';
    END IF;
    IF (SELECT COUNT(*) FROM sys_config WHERE config_key = 'feature.oa.attendance.v2.enabled') <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Attendance V2 feature configuration is missing or duplicated';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'oa:attendance:center:list' AND status = '0') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Attendance V2 menu migration is missing';
    END IF;
END$$
DELIMITER ;
CALL assert_attendance_v2_cutover_ready();
DROP PROCEDURE assert_attendance_v2_cutover_ready;

START TRANSACTION;
DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.perms IN ('oa:attendance:list', 'oa:attendance:query', 'oa:attendance:export');
DELETE FROM sys_menu
WHERE perms IN ('oa:attendance:list', 'oa:attendance:query', 'oa:attendance:export');
UPDATE sys_config
SET config_value = 'true', update_by = 'system', update_time = NOW(),
    remark = '仅使用新打卡；地点围栏、班次、排班及定位拍照须配置完成'
WHERE config_key = 'feature.oa.attendance.v2.enabled';
COMMIT;
-- Refresh the system configuration cache / restart the services after application.
