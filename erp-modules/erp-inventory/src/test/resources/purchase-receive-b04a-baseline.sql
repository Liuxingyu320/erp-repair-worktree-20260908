-- Synthetic empty business schema for real production Mapper SQL.
-- Receipt/inspection and persistent command tables are installed by the unmodified migrations.
CREATE TABLE sys_dept (dept_id bigint PRIMARY KEY, dept_name varchar(80)) ENGINE=InnoDB;
CREATE TABLE sys_user (user_id bigint PRIMARY KEY, nick_name varchar(80)) ENGINE=InnoDB;
CREATE TABLE inv_purchase_order (
 order_id bigint PRIMARY KEY AUTO_INCREMENT, order_no varchar(64), order_title varchar(200),
 supplier_id bigint, supplier_name varchar(100), total_amount decimal(18,4), order_date date,
 status varchar(20), qc_status varchar(20), shop_dept_id bigint, applicant_id bigint,
 applicant_name varchar(80), applicant_dept_id bigint, create_by varchar(64), create_time datetime,
 update_by varchar(64), update_time datetime, remark varchar(500)
) ENGINE=InnoDB;
CREATE TABLE inv_purchase_detail (
 detail_id bigint PRIMARY KEY AUTO_INCREMENT, order_id bigint NOT NULL, item_type varchar(20), item_id bigint,
 item_code varchar(100), item_name varchar(200), product_id bigint, warehouse_id bigint,
 product_name varchar(200), sku varchar(100), spec varchar(100), unit varchar(30),
 quantity decimal(18,4) NOT NULL, unit_price decimal(18,4), amount decimal(18,4),
 received_quantity decimal(18,4) NOT NULL DEFAULT 0, KEY idx_purchase_detail_order(order_id)
) ENGINE=InnoDB;
CREATE TABLE inv_inbound_record (
 inbound_id bigint PRIMARY KEY AUTO_INCREMENT, purchase_order_id bigint NOT NULL, purchase_detail_id bigint,
 order_no varchar(64), item_type varchar(20), item_id bigint, product_id bigint,
 shop_dept_id bigint, warehouse_id bigint, quantity decimal(18,4) NOT NULL,
 qc_result varchar(20), qc_user varchar(64), qc_time datetime, qc_remark varchar(500),
 create_by varchar(64), create_time datetime, remark varchar(500), KEY idx_inbound_order(purchase_order_id)
) ENGINE=InnoDB;
CREATE TABLE inv_number_sequence (
 seq_name varchar(40) NOT NULL, seq_date varchar(20) NOT NULL, current_seq bigint NOT NULL,
 prefix varchar(30), PRIMARY KEY(seq_name,seq_date)
) ENGINE=InnoDB;
CREATE TABLE inv_stock (
 stock_id bigint PRIMARY KEY AUTO_INCREMENT, item_type varchar(20), item_id bigint, product_id bigint,
 shop_dept_id bigint, warehouse_id bigint, current_quantity decimal(18,4), locked_quantity decimal(18,4),
 available_quantity decimal(18,4), cost_price decimal(18,4), total_cost decimal(18,4), version bigint DEFAULT 0,
 batch_no varchar(100), expiry_date date, serial_no varchar(100), location_code varchar(100), location_name varchar(100),
 last_in_time datetime, last_out_time datetime, create_by varchar(64), create_time datetime,
 update_by varchar(64), update_time datetime, remark varchar(500),
 UNIQUE KEY uk_stock_item_scope(item_type,item_id,shop_dept_id,warehouse_id)
) ENGINE=InnoDB;
CREATE TABLE inv_stock_log (
 log_id bigint PRIMARY KEY AUTO_INCREMENT, item_type varchar(20), item_id bigint, product_id bigint,
 shop_dept_id bigint, warehouse_id bigint, movement_type varchar(40), business_type varchar(40),
 batch_no varchar(100), expiry_date date, serial_no varchar(100), location_code varchar(100), location_name varchar(100),
 business_id bigint, business_no varchar(64), change_quantity decimal(18,4), before_quantity decimal(18,4),
 after_quantity decimal(18,4), cost_price decimal(18,4), cost_amount decimal(18,4),
 create_by varchar(64), create_time datetime, remark varchar(500)
) ENGINE=InnoDB;
CREATE TABLE inv_product_category (
 category_id bigint PRIMARY KEY, category_name varchar(80), ancestors varchar(200), del_flag char(1) DEFAULT '0'
) ENGINE=InnoDB;
CREATE TABLE inv_oe_category LIKE inv_product_category;
CREATE TABLE inv_gift_category LIKE inv_product_category;
CREATE TABLE inv_product (
 product_id bigint PRIMARY KEY, product_code varchar(100), product_name varchar(200), category_id bigint,
 grade varchar(50), spec varchar(100), unit varchar(30), safety_stock_min decimal(18,4), cost_price decimal(18,4),
 image_url varchar(1000), package_image_url varchar(1000), dry_tea_image_url varchar(1000),
 tea_soup_image_url varchar(1000), leaf_bottom_image_url varchar(1000), extra_image_url varchar(1000),
 del_flag char(1) DEFAULT '0'
) ENGINE=InnoDB;
CREATE TABLE inv_oe_item (
 oe_item_id bigint PRIMARY KEY, oe_item_code varchar(100), oe_item_name varchar(200), category_id bigint,
 cost_price decimal(18,4), image_url varchar(1000), order_unit varchar(30), item_description varchar(500),
 del_flag char(1) DEFAULT '0'
) ENGINE=InnoDB;
CREATE TABLE inv_gift_box (
 gift_id bigint PRIMARY KEY, gift_code varchar(100), gift_name varchar(200), category_id bigint,
 cost_price decimal(18,4), image_url varchar(1000), replenishment_unit varchar(30), spec varchar(100), grade varchar(50),
 del_flag char(1) DEFAULT '0'
) ENGINE=InnoDB;
INSERT INTO sys_dept VALUES (20,'B04A warehouse'),(30,'Other warehouse');
INSERT INTO sys_user VALUES (77,'B04A tester');
INSERT INTO inv_product(product_id,product_code,product_name) VALUES (1,'B04A-1','Synthetic tea'),(2,'B04A-2','Synthetic tea 2');
