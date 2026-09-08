-- Schema-only fixture for the unified approval release gate.
--
-- This file is intentionally small, synthetic, and safe to commit. It models
-- the clean-install OA purchase/menu contract without copying a real database
-- dump or any user, employee, credential, or business records. It is not part
-- of Docker bootstrap and must never be used as a production data seed.

CREATE TABLE `oa_purchase` (
  `purchase_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'draft',
  `approval_instance_id` bigint DEFAULT NULL,
  `approval_round` int NOT NULL DEFAULT 0,
  `row_version` bigint NOT NULL DEFAULT 0,
  `last_approval_event_key` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`purchase_id`),
  KEY `idx_oa_purchase_approval_instance` (`approval_instance_id`, `approval_round`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_menu` (
  `menu_id` bigint NOT NULL,
  `menu_name` varchar(64) NOT NULL,
  `perms` varchar(128) DEFAULT NULL,
  `visible` char(1) NOT NULL DEFAULT '0',
  `status` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_role_menu` (
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `perms`, `visible`, `status`) VALUES
  (3003, '我的待办（已退役）', 'oa:todo:list', '1', '1'),
  (3004, '我的已办（已退役）', 'oa:done:list', '1', '1'),
  (3103, '待办审批', 'oa:todo:approve', '0', '0'),
  (3105, '待办导出（已退役）', 'oa:todo:export', '1', '1'),
  (3106, '已办导出（已退役）', 'oa:done:export', '1', '1');

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 3103);
