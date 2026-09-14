-- R07: install before the backend that uses the explicit purchase-return lock indexes.
-- Reuses the names in scripts/fixtures/repair-purchase-facts-20260909.sql.
-- Non-unique indexes only; no business rows are rewritten. MySQL 5.7 / 8.0 InnoDB.
DROP PROCEDURE IF EXISTS inv_ensure_purchase_return_lock_indexes;
DELIMITER $$
CREATE PROCEDURE inv_ensure_purchase_return_lock_indexes()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'inv_purchase_return'
                 AND index_name = 'idx_ipr_purchase') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                       WHERE table_schema = DATABASE() AND table_name = 'inv_purchase_return'
                         AND index_name = 'idx_ipr_purchase' AND seq_in_index = 1
                         AND column_name = 'purchase_order_id' AND index_type = 'BTREE') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'idx_ipr_purchase has incompatible columns; stop R07 release';
        END IF;
    ELSE
        ALTER TABLE inv_purchase_return ADD INDEX idx_ipr_purchase (purchase_order_id), ALGORITHM=INPLACE, LOCK=NONE;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'inv_purchase_return_detail'
                 AND index_name = 'idx_iprd_return') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                       WHERE table_schema = DATABASE() AND table_name = 'inv_purchase_return_detail'
                         AND index_name = 'idx_iprd_return' AND seq_in_index = 1
                         AND column_name = 'return_id' AND index_type = 'BTREE') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'idx_iprd_return has incompatible columns; stop R07 release';
        END IF;
    ELSE
        ALTER TABLE inv_purchase_return_detail ADD INDEX idx_iprd_return (return_id), ALGORITHM=INPLACE, LOCK=NONE;
    END IF;
END$$
DELIMITER ;
CALL inv_ensure_purchase_return_lock_indexes();
DROP PROCEDURE inv_ensure_purchase_return_lock_indexes;
