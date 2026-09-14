-- INV-N03: reference probes use (item_type,item_id); additive indexes only.
-- Apply after the existing inventory schema migrations. Missing optional tables are left untouched.

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_stock' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_stock' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_stock ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_stock_log' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_stock_log' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_stock_log ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_purchase_detail' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_purchase_detail' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_purchase_detail ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_sales_detail' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_sales_detail' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_sales_detail ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_purchase_return_detail' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_purchase_return_detail' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_purchase_return_detail ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_sales_return_detail' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_sales_return_detail' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_sales_return_detail ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_delivery_notice_detail' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_delivery_notice_detail' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_delivery_notice_detail ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_inbound_record' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_inbound_record' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_inbound_record ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_outbound_record' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_outbound_record' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_outbound_record ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_transfer_detail' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_transfer_detail' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_transfer_detail ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_transfer_shipment_detail' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_transfer_shipment_detail' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_transfer_shipment_detail ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_stock_check_detail' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_stock_check_detail' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_stock_check_detail ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_receipt_batch_detail' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_receipt_batch_detail' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_receipt_batch_detail ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_transfer_reservation' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_transfer_reservation' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_transfer_reservation ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_transfer_discrepancy_detail' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_transfer_discrepancy_detail' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_transfer_discrepancy_detail ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_inventory_lot' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_inventory_lot' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_inventory_lot ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_inventory_serial' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_inventory_serial' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_inventory_serial ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_item_fulfillment_policy' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_item_fulfillment_policy' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_item_fulfillment_policy ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_stock_balance_detail' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_stock_balance_detail' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_stock_balance_detail ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_stock_ledger_detail' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_stock_ledger_detail' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_stock_ledger_detail ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_transfer_quarantine_reservation' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_transfer_quarantine_reservation' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_transfer_quarantine_reservation ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_transfer_receipt_discrepancy_adjudication_workflow_link' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_transfer_receipt_discrepancy_adjudication_workflow_link' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_transfer_receipt_discrepancy_adjudication_workflow_link ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_transfer_receipt_discrepancy_damage_loss_ledger' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_transfer_receipt_discrepancy_damage_loss_ledger' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_transfer_receipt_discrepancy_damage_loss_ledger ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_transfer_receipt_discrepancy_responsibility_ledger' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_transfer_receipt_discrepancy_responsibility_ledger' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_transfer_receipt_discrepancy_responsibility_ledger ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_transfer_receipt_discrepancy_shortage_loss_ledger' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_transfer_receipt_discrepancy_shortage_loss_ledger' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_transfer_receipt_discrepancy_shortage_loss_ledger ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_transfer_shipment_receipt_allocation' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_transfer_shipment_receipt_allocation' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_transfer_shipment_receipt_allocation ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_transfer_shipment_serial' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_transfer_shipment_serial' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_transfer_shipment_serial ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;

SET @catalog_ref_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_warehouse_task_detail' AND column_name IN ('item_type','item_id'));
SET @catalog_ref_index = (SELECT COUNT(*) FROM information_schema.statistics a JOIN information_schema.statistics b ON a.table_schema=b.table_schema AND a.table_name=b.table_name AND a.index_name=b.index_name WHERE a.table_schema=DATABASE() AND a.table_name='inv_warehouse_task_detail' AND a.seq_in_index=1 AND a.column_name='item_type' AND b.seq_in_index=2 AND b.column_name='item_id');
SET @catalog_ref_sql = IF(@catalog_ref_exists=2 AND @catalog_ref_index=0, 'ALTER TABLE inv_warehouse_task_detail ADD INDEX idx_catalog_ref_20260913 (item_type,item_id)', 'SELECT 1');
PREPARE catalog_ref_stmt FROM @catalog_ref_sql;
EXECUTE catalog_ref_stmt;
DEALLOCATE PREPARE catalog_ref_stmt;
