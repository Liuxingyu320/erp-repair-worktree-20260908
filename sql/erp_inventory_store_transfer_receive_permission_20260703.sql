-- Restore store-side transfer receiving after the store role hardening pass.
-- Store managers still need to confirm inbound transfer shipment batches for their selected store.

SET @transfer_receive_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE perms = 'inv:transfer:receive'
      AND status = '0'
    ORDER BY menu_id
    LIMIT 1
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, @transfer_receive_menu_id
FROM sys_role r
WHERE @transfer_receive_menu_id IS NOT NULL
  AND r.del_flag = '0'
  AND r.role_key = 'dz';
