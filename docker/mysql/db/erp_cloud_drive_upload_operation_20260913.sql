-- P03: durable upload receipts. Apply before upgrading every file-service instance and PC/H5 client.
-- No historical upload is inferred to have this receipt. Retain receipts across deployments/retries.
CREATE TABLE IF NOT EXISTS drive_upload_operation (
  operation_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  actor_id bigint NOT NULL,
  space_id bigint NOT NULL,
  parent_id bigint NOT NULL,
  file_name varchar(255) NOT NULL,
  size_bytes bigint NOT NULL,
  sha256 char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  node_id bigint DEFAULT NULL,
  create_time datetime(3) NOT NULL,
  update_time datetime(3) NOT NULL,
  PRIMARY KEY (operation_id),
  KEY idx_drive_upload_actor (actor_id,create_time),
  KEY idx_drive_upload_status (status,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
