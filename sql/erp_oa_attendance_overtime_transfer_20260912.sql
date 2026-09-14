-- B08-B: migrate after B08-A, before deploying the source-allocation read paths.
-- No existing role is automatically granted conversion authority.
CREATE TABLE IF NOT EXISTS oa_attendance_overtime_transfer (
 transfer_id bigint NOT NULL AUTO_INCREMENT,
 original_transfer_id bigint DEFAULT NULL,
 bucket_id bigint NOT NULL,
 action varchar(16) NOT NULL COMMENT 'APPLY/REVERSE, append only',
 client_request_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_fingerprint char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 user_id bigint NOT NULL, shop_id bigint NOT NULL, leave_type_id bigint NOT NULL,
 legal_entity_id bigint NOT NULL, owner_dept_id bigint NOT NULL,
 source_day_result_id bigint NOT NULL, source_schedule_id bigint NOT NULL,
 source_business_date date NOT NULL, salary_month varchar(7) NOT NULL,
 source_version bigint NOT NULL, source_settled_at datetime NOT NULL,
 source_worked_minutes int NOT NULL, source_scheduled_minutes int NOT NULL,
 transfer_minutes int NOT NULL,
 rule_id bigint NOT NULL, mapping_id bigint NOT NULL, mapping_version bigint NOT NULL,
 operator_user_id bigint NOT NULL, reason varchar(500) NOT NULL,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(transfer_id),
 UNIQUE KEY uk_ot_request(shop_id,operator_user_id,client_request_id),
 UNIQUE KEY uk_ot_reverse(original_transfer_id,action),
 KEY idx_ot_source(source_day_result_id,action),
 KEY idx_ot_bucket(bucket_id,action),
 KEY idx_ot_period(shop_id,salary_month,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='主管已日结加班转调休核定和审计撤销';
SET @b08b_parent=(SELECT menu_id FROM sys_menu WHERE perms='oa:attendance:center:list' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '核定加班转调休',@b08b_parent,84,'',NULL,NULL,'',1,0,'F','0','0','oa:attendance:leave:balance:convert','#','system',NOW(),'主管按已日结来源核定；与早退抵扣和计薪用途互斥'
WHERE @b08b_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='oa:attendance:leave:balance:convert');
