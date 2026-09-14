-- Additive gallery storage. Existing image_url remains the cover for old readers.
-- Apply before upgrading inventory and OA services. Rerunnable on MySQL 8.
-- Preserve historical raw references; application readers fall back to image_url when image_urls is NULL.
SET @gallery_schema = DATABASE();
SET @gallery_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @gallery_schema AND TABLE_NAME = 'inv_oe_item' AND COLUMN_NAME = 'image_urls') = 0,
    'ALTER TABLE inv_oe_item ADD COLUMN image_urls TEXT NULL COMMENT ''Ordered image URL JSON array; NULL uses legacy cover'' AFTER image_url',
    'SELECT 1');
PREPARE gallery_stmt FROM @gallery_ddl;
EXECUTE gallery_stmt;
DEALLOCATE PREPARE gallery_stmt;
SET @gallery_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @gallery_schema AND TABLE_NAME = 'inv_gift_box' AND COLUMN_NAME = 'image_urls') = 0,
    'ALTER TABLE inv_gift_box ADD COLUMN image_urls TEXT NULL COMMENT ''Ordered image URL JSON array; NULL uses legacy cover'' AFTER image_url',
    'SELECT 1');
PREPARE gallery_stmt FROM @gallery_ddl;
EXECUTE gallery_stmt;
DEALLOCATE PREPARE gallery_stmt;
