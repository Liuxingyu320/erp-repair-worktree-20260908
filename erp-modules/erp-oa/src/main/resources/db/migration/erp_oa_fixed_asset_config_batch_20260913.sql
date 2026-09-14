-- Additive: legacy configurations remain untouched. Keep scope/command rows when rolling back the UI.
CREATE TABLE IF NOT EXISTS oa_fixed_asset_config_scope (
  shop_dept_id BIGINT NOT NULL,
  scope_version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (shop_dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS oa_fixed_asset_config_command (
  shop_dept_id BIGINT NOT NULL,
  actor_id BIGINT NOT NULL,
  request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  result_json MEDIUMTEXT NULL,
  create_time DATETIME NOT NULL,
  complete_time DATETIME NULL,
  PRIMARY KEY (shop_dept_id,actor_id,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
