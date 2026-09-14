-- S10 personal message snapshot/page access. Non-unique index, no data rewrite.
-- MySQL 5.7 / 8.0 InnoDB; safe to repeat. Publish through the normal migration sequence.
DROP PROCEDURE IF EXISTS sys_ensure_user_notification_page_index;
DELIMITER $$
CREATE PROCEDURE sys_ensure_user_notification_page_index()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema=DATABASE() AND table_name='sys_user_notification'
                 AND index_name='idx_sys_user_notification_user_id') THEN
        IF (SELECT COUNT(*) FROM information_schema.statistics
            WHERE table_schema=DATABASE() AND table_name='sys_user_notification'
              AND index_name='idx_sys_user_notification_user_id' AND index_type='BTREE' AND non_unique=1
              AND ((seq_in_index=1 AND column_name='user_id')
                OR (seq_in_index=2 AND column_name='notification_id'))) <> 2 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Notification page index has incompatible columns; stop release';
        END IF;
    ELSE
        ALTER TABLE sys_user_notification
            ADD INDEX idx_sys_user_notification_user_id(user_id,notification_id), ALGORITHM=INPLACE, LOCK=NONE;
    END IF;
END$$
DELIMITER ;
CALL sys_ensure_user_notification_page_index();
DROP PROCEDURE sys_ensure_user_notification_page_index;
