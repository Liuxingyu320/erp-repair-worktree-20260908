package com.erp.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = false)
@DisplayName("新增业务及报销迁移 MySQL 5.7/8.0 发布门禁")
class NewBusinessMigrationsMySqlIT
{
    private static final String REIMBURSEMENT_MIGRATION =
            "erp_oa_reimbursement_20260730.sql";

    private static final String SALES_DELIVERY_MIGRATION =
            "erp_inventory_sales_delivery_transfer_consistency_20260805.sql";

    private static final String DISPOSITION_MIGRATION =
            "erp_inventory_transfer_discrepancy_disposition_20260805.sql";

    private static final List<String> REIMBURSEMENT_PREREQUISITES = List.of(
            "erp_hr_dept_leader_identity_20260713.sql");

    private static final List<String> FEATURE_FLAGS = List.of(
            "feature.hr.health-certificate.enabled",
            "feature.inventory.store-return.enabled",
            "feature.inventory.transfer-discrepancy.enabled",
            "feature.inventory.customer-service-card.enabled",
            "feature.oa.purchase.enabled",
            "feature.inventory.stock-check-native-approval.enabled",
            "feature.inventory.transfer-native-approval.enabled");

    private static final List<String> APPROVAL_TABLES = List.of(
            "approval_template",
            "approval_rule",
            "approval_rule_version",
            "approval_rule_condition",
            "approval_version_node",
            "approval_instance",
            "approval_task",
            "approval_task_candidate",
            "approval_action_log",
            "approval_callback_outbox",
            "approval_validation_run",
            "approval_validation_issue");

    private static final List<String> APPROVAL_START_OUTBOX_TABLES = List.of(
            "inv_transfer_approval_start_outbox",
            "inv_stock_check_approval_start_outbox",
            "oa_purchase_approval_start_outbox",
            "hr_health_certificate_approval_start_outbox",
            "oa_reimbursement_approval_start_outbox");

    private static final List<String> REIMBURSEMENT_TABLES = List.of(
            "oa_reimbursement",
            "oa_reimbursement_approval_start_outbox",
            "oa_reimbursement_item",
            "oa_reimbursement_invoice",
            "oa_reimbursement_invoice_recognition",
            "oa_reimbursement_export_batch",
            "oa_reimbursement_export_batch_item");

    @Container
    static final MySQLContainer MYSQL57 = mysql("mysql:5.7.44",
            "new_business_57");

    @Container
    static final MySQLContainer MYSQL80 = mysql("mysql:8.0.36",
            "new_business_80");

    private static final List<MySQLContainer> DATABASES =
            List.of(MYSQL57, MYSQL80);

    @BeforeAll
    static void applyEveryMigrationTwice() throws Exception
    {
        String baseline = resource("new-business-it-baseline.sql");
        List<String> migrations = migrationNames();
        String reimbursementMigration = Files.readString(
                migration(REIMBURSEMENT_MIGRATION), StandardCharsets.UTF_8);
        String salesDeliveryMigration = Files.readString(
                migration(SALES_DELIVERY_MIGRATION), StandardCharsets.UTF_8);
        String dispositionMigration = Files.readString(
                migration(DISPOSITION_MIGRATION), StandardCharsets.UTF_8);
        assertThat(migrations)
                .hasSize(15)
                .doesNotContain(
                        "erp_unified_approval_center_20260714.sql",
                        "erp_inventory_unified_approval_cutover_20260714.sql");

        for (MySQLContainer database : DATABASES)
        {
            try (Connection connection = connection(database))
            {
                executeScript(connection, baseline);
                applyMigrations(connection, migrations);
                applyMigrations(connection, REIMBURSEMENT_PREREQUISITES);
                executeScript(connection, reimbursementMigration);
                prepareSalesDeliveryMigrationBaseline(connection);
                executeScript(connection, salesDeliveryMigration);
                executeScript(connection, dispositionMigration);
                SchemaFingerprint first = fingerprint(connection);

                applyMigrations(connection, migrations);
                applyMigrations(connection, REIMBURSEMENT_PREREQUISITES);
                executeScript(connection, reimbursementMigration);
                executeScript(connection, salesDeliveryMigration);
                executeScript(connection, dispositionMigration);
                SchemaFingerprint second = fingerprint(connection);

                assertThat(second).isEqualTo(first);
            }
        }
    }

    @Test
    void bothSupportedDatabaseVersionsAreReal()
            throws Exception
    {
        try (Connection mysql57 = connection(MYSQL57);
                Connection mysql80 = connection(MYSQL80))
        {
            assertThat(value(mysql57, "select version()"))
                    .startsWith("5.7.44");
            assertThat(value(mysql80, "select version()"))
                    .startsWith("8.0.36");
        }
    }

    @Test
    void allFeatureFlagsExistExactlyOnceAndDefaultOff() throws Exception
    {
        String keys = quotedFlags();
        for (MySQLContainer database : DATABASES)
        {
            try (Connection connection = connection(database))
            {
                assertThat(count(connection,
                        "select count(*) from sys_config where config_key in ("
                                + keys + ")"))
                        .isEqualTo(FEATURE_FLAGS.size());
                assertThat(count(connection,
                        "select count(*) from sys_config where config_key in ("
                                + keys + ") and config_value='false'"))
                        .isEqualTo(FEATURE_FLAGS.size());
                assertThat(count(connection,
                        "select count(*) from sys_config where config_key in ("
                                + keys + ") group by config_key having count(*)>1"))
                        .isZero();
            }
        }
    }

    @Test
    void expectedTablesColumnsAndIndexesExist() throws Exception
    {
        for (MySQLContainer database : DATABASES)
        {
            try (Connection connection = connection(database))
            {
                assertThat(count(connection,
                        "select count(*) from information_schema.tables "
                                + "where table_schema=database() and table_name in ("
                                + "'hr_employee_health_certificate',"
                                + "'inv_transfer_discrepancy',"
                                + "'inv_transfer_discrepancy_detail',"
                                + "'inv_transfer_discrepancy_disposition',"
                                + "'inv_customer_service_profile',"
                                + "'inv_customer_service_record',"
                                + "'inv_customer_service_change_log',"
                                + "'inv_transfer_approval_start_outbox',"
                                + "'inv_stock_check_approval_start_outbox',"
                                + "'oa_purchase_approval_start_outbox',"
                                + "'hr_health_certificate_approval_start_outbox')"))
                        .isEqualTo(11);
                assertThat(count(connection,
                        "select count(*) from information_schema.columns "
                                + "where table_schema=database() and ((table_name="
                                + "'inv_oe_item' and column_name in ("
                                + "'purchase_reference_url',"
                                + "'purchase_reference_note')) or (table_name="
                                + "'inv_transfer_order' and column_name in ("
                                + "'return_reason_code','attachment_node_ids',"
                                + "'source_business_type','source_business_id',"
                                + "'version')))"))
                        .isEqualTo(7);
                assertThat(count(connection,
                        "select count(distinct concat(table_name, ':', index_name)) "
                                + "from information_schema.statistics "
                                + "where table_schema=database() and ((table_name="
                                + "'hr_employee_health_certificate' and index_name="
                                + "'uk_hr_health_user_current') or (table_name="
                                + "'inv_customer_service_change_log' and index_name="
                                + "'uk_inv_customer_service_log_shop_request_type'))"))
                        .isEqualTo(2);
                assertThat(value(connection,
                        indexColumns("hr_employee_health_certificate",
                                "uk_hr_health_user_current")))
                        .isEqualTo("user_id,current_flag");
                assertThat(value(connection,
                        indexColumns("inv_customer_service_change_log",
                                "uk_inv_customer_service_log_shop_request_type")))
                        .isEqualTo("shop_dept_id,request_key,change_type");
                assertThat(count(connection,
                        "select count(*) from inv_customer_service_profile "
                                + "where customer_id=1"))
                        .isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("差异处置台账列、索引和 requestId 幂等门禁存在")
    void discrepancyDispositionLedgerSchemaExists() throws Exception
    {
        for (MySQLContainer database : DATABASES)
        {
            try (Connection connection = connection(database);
                    Statement statement = connection.createStatement())
            {
                assertThat(count(connection,
                        "select count(*) from information_schema.columns "
                                + "where table_schema=database() and "
                                + "table_name='inv_transfer_discrepancy_disposition' "
                                + "and column_name in ("
                                + "'discrepancy_id','discrepancy_detail_id',"
                                + "'shipment_detail_id','category','decision',"
                                + "'quantity','cost_price','amount','inventory_impact',"
                                + "'source_location_dept_id','target_location_dept_id',"
                                + "'responsible_party','note','attachment_refs',"
                                + "'request_id','handled_by_user_id','handled_by_name',"
                                + "'handled_time','version')"))
                        .isEqualTo(19);
                assertThat(value(connection,
                        indexColumns("inv_transfer_discrepancy_disposition",
                                "uk_inv_transfer_disposition_request_category")))
                        .isEqualTo("discrepancy_id,request_id,discrepancy_detail_id,category");

                statement.executeUpdate("insert into "
                        + "inv_transfer_discrepancy_disposition "
                        + "(discrepancy_id, discrepancy_detail_id, "
                        + "shipment_detail_id, category, decision, quantity, "
                        + "cost_price, amount, inventory_impact, "
                        + "responsible_party, request_id, handled_time) "
                        + "values (99001,990011,990011,'SHORTAGE','WRITE_OFF',"
                        + "2.00,5.00,10.00,'NO_STOCK_CHANGE','LOGISTICS',"
                        + "'migration-idempotency-test',now())");
                assertThatThrownBy(() -> statement.executeUpdate("insert into "
                        + "inv_transfer_discrepancy_disposition "
                        + "(discrepancy_id, discrepancy_detail_id, "
                        + "shipment_detail_id, category, decision, quantity, "
                        + "cost_price, amount, inventory_impact, "
                        + "responsible_party, request_id, handled_time) "
                        + "values (99001,990011,990011,'SHORTAGE','WRITE_OFF',"
                        + "2.00,5.00,10.00,'NO_STOCK_CHANGE','LOGISTICS',"
                        + "'migration-idempotency-test',now())"))
                        .isInstanceOf(SQLException.class);
                statement.executeUpdate("delete from "
                        + "inv_transfer_discrepancy_disposition "
                        + "where discrepancy_id=99001");
            }
        }
    }

    @Test
    void salesDeliveryMigrationFreezesHistoryAndUsesSelectiveDedupe()
            throws Exception
    {
        for (MySQLContainer database : DATABASES)
        {
            try (Connection connection = connection(database);
                    Statement statement = connection.createStatement())
            {
                assertThat(count(connection,
                        "select count(*) from information_schema.columns "
                                + "where table_schema=database() and ((table_name="
                                + "'inv_delivery_notice_detail' and column_name in ("
                                + "'warehouse_id','delivered_cost_amount')) or (table_name="
                                + "'inv_outbound_record' and column_name in ("
                                + "'notice_id','notice_detail_id','cost_price','cost_amount')) or (table_name="
                                + "'inv_transfer_order' and column_name="
                                + "'p0b_sales_delivery_notice_source_id'))"))
                        .isEqualTo(7);
                assertThat(value(connection,
                        indexColumns("inv_transfer_order",
                                "uk_inv_transfer_sales_delivery_notice")))
                        .isEqualTo("p0b_sales_delivery_notice_source_id,from_warehouse_id");
                assertThat(value(connection,
                        "select concat(warehouse_id,'|',delivered_cost_amount) "
                                + "from inv_delivery_notice_detail where detail_id=70001"))
                        .isEqualTo("301|0.00");
                assertThat(count(connection,
                        "select count(*) from inv_transfer_order "
                                + "where order_no='TF-P0B-LEGACY' "
                                + "and purchase_id is null "
                                + "and source_business_type='sales_delivery' "
                                + "and source_business_id=70001"))
                        .isEqualTo(1);

                statement.executeUpdate("insert into inv_transfer_order "
                        + "(order_no, from_dept_id, from_warehouse_id, "
                        + "to_dept_id, to_warehouse_id, status, total_quantity, "
                        + "transfer_type, source_business_type, source_business_id) "
                        + "values ('TF-P0B-OTHER-1',201,301,202,202,'draft',1,"
                        + "'cross_store','manual_test',80001)");
                statement.executeUpdate("insert into inv_transfer_order "
                        + "(order_no, from_dept_id, from_warehouse_id, "
                        + "to_dept_id, to_warehouse_id, status, total_quantity, "
                        + "transfer_type, source_business_type, source_business_id) "
                        + "values ('TF-P0B-OTHER-2',201,301,202,202,'draft',1,"
                        + "'cross_store','manual_test',80001)");
                statement.executeUpdate("insert into inv_transfer_order "
                        + "(order_no, from_dept_id, from_warehouse_id, "
                        + "to_dept_id, to_warehouse_id, status, total_quantity, "
                        + "transfer_type, source_business_type, source_business_id) "
                        + "values ('TF-P0B-NOTICE-1',201,301,202,202,'delivered',1,"
                        + "'cross_store','sales_delivery_notice',80001)");
                assertThatThrownBy(() -> statement.executeUpdate(
                        "insert into inv_transfer_order "
                                + "(order_no, from_dept_id, from_warehouse_id, "
                                + "to_dept_id, to_warehouse_id, status, total_quantity, "
                                + "transfer_type, source_business_type, source_business_id) "
                                + "values ('TF-P0B-NOTICE-2',201,301,202,202,'delivered',1,"
                                + "'cross_store','sales_delivery_notice',80001)"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    void salesDeliveryMigrationBlocksUnknownShippedHistoricalCost()
            throws Exception
    {
        String migrationSql = Files.readString(
                migration(SALES_DELIVERY_MIGRATION), StandardCharsets.UTF_8);
        for (MySQLContainer database : DATABASES)
        {
            try (Connection connection = connection(database);
                    Statement statement = connection.createStatement())
            {
                statement.executeUpdate("insert into inv_sales_order "
                        + "(order_id, shop_dept_id, target_dept_id) "
                        + "values (71001,201,202)");
                statement.executeUpdate("insert into inv_sales_detail "
                        + "(detail_id, order_id, warehouse_id) "
                        + "values (71001,71001,301)");
                statement.executeUpdate("insert into inv_delivery_notice "
                        + "(notice_id, sales_order_id, status, shop_dept_id) "
                        + "values (71001,71001,'delivering',201)");
                statement.executeUpdate("insert into inv_delivery_notice_detail "
                        + "(detail_id, notice_id, sales_detail_id, delivered_qty, "
                        + "warehouse_id, delivered_cost_amount) "
                        + "values (71001,71001,71001,1,301,null)");

                assertThatThrownBy(() -> executeScript(connection,
                        migrationSql))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("shipped delivery notice cost is unknown");

                statement.executeUpdate("delete from inv_delivery_notice_detail "
                        + "where detail_id=71001");
                statement.executeUpdate("delete from inv_delivery_notice "
                        + "where notice_id=71001");
                statement.executeUpdate("delete from inv_sales_detail "
                        + "where detail_id=71001");
                statement.executeUpdate("delete from inv_sales_order "
                        + "where order_id=71001");
            }
        }
    }

    @Test
    void salesDeliveryMigrationBlocksInvalidSourceKey() throws Exception
    {
        String migrationSql = Files.readString(
                migration(SALES_DELIVERY_MIGRATION), StandardCharsets.UTF_8);
        for (MySQLContainer database : DATABASES)
        {
            try (Connection connection = connection(database);
                    Statement statement = connection.createStatement())
            {
                statement.executeUpdate("insert into inv_transfer_order "
                        + "(order_no, from_dept_id, from_warehouse_id, "
                        + "to_dept_id, to_warehouse_id, status, total_quantity, "
                        + "transfer_type, source_business_type, source_business_id) "
                        + "values ('TF-P0B-INVALID-SOURCE',201,301,202,202,"
                        + "'delivered',1,'cross_store','sales_delivery_notice',0)");
                try
                {
                    assertThatThrownBy(() -> executeScript(connection,
                            migrationSql))
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining(
                                    "invalid sales delivery notice source");
                }
                finally
                {
                    statement.executeUpdate("delete from inv_transfer_order "
                            + "where order_no='TF-P0B-INVALID-SOURCE'");
                }
            }
        }
    }

    @Test
    void unifiedApprovalSchemaBusinessColumnsAndOutboxesExist()
            throws Exception
    {
        for (MySQLContainer database : DATABASES)
        {
            try (Connection connection = connection(database))
            {
                assertThat(count(connection,
                        "select count(*) from information_schema.tables "
                                + "where table_schema=database() and table_name in ("
                                + quotedValues(APPROVAL_TABLES) + ")"))
                        .isEqualTo(APPROVAL_TABLES.size());
                assertThat(count(connection,
                        "select count(*) from information_schema.tables "
                                + "where table_schema=database() and table_name in ("
                                + quotedValues(APPROVAL_START_OUTBOX_TABLES) + ")"))
                        .isEqualTo(APPROVAL_START_OUTBOX_TABLES.size());
                assertThat(count(connection,
                        "select count(*) from information_schema.columns "
                                + "where table_schema=database() and ("
                                + "(table_name='hr_employee_health_certificate' "
                                + "and column_name in ('approval_instance_id',"
                                + "'approval_round','last_approval_event_key')) or "
                                + "(table_name='inv_stock_check' and column_name in ("
                                + "'approval_engine','last_approval_event_key',"
                                + "'row_version')) or "
                                + "(table_name='inv_transfer_order' and column_name in ("
                                + "'approval_round','approval_engine',"
                                + "'last_approval_event_key')) or "
                                + "(table_name='oa_purchase' and column_name in ("
                                + "'approval_instance_id','approval_round',"
                                + "'row_version','last_approval_event_key')))"))
                        .isEqualTo(13);
                assertThat(count(connection,
                        "select count(*) from information_schema.columns "
                                + "where table_schema=database() "
                                + "and table_name in ("
                                + quotedValues(APPROVAL_START_OUTBOX_TABLES)
                                + ") and column_name='remote_business_round'"))
                        .isEqualTo(APPROVAL_START_OUTBOX_TABLES.size());
            }
        }
    }

    @Test
    void reimbursementMigrationIsRepeatSafeAndOperationsStayAdminOnly()
            throws Exception
    {
        for (MySQLContainer database : DATABASES)
        {
            try (Connection connection = connection(database))
            {
                assertThat(count(connection,
                        "select count(*) from information_schema.tables "
                                + "where table_schema=database() and table_name in ("
                                + quotedValues(REIMBURSEMENT_TABLES) + ")"))
                        .isEqualTo(REIMBURSEMENT_TABLES.size());
                assertThat(value(connection,
                        indexColumns("oa_reimbursement_approval_start_outbox",
                                "idx_oa_reimbursement_approval_dispatch")))
                        .isEqualTo("status,next_retry_time,update_time");
                assertThat(count(connection,
                        "select count(*) from sys_config where config_key="
                                + "'feature.oa.reimbursement.enabled' "
                                + "and config_value='true'"))
                        .isEqualTo(1);
                assertThat(count(connection,
                        "select count(*) from approval_template "
                                + "where business_code='OA_REIMBURSEMENT' "
                                + "and template_status='ACTIVE' "
                                + "and engine_mode='NATIVE'"))
                        .isEqualTo(1);
                assertThat(count(connection,
                        "select count(*) from approval_version_node node_info "
                                + "join approval_rule rule_info "
                                + "on rule_info.current_version_id=node_info.version_id "
                                + "where rule_info.rule_code="
                                + "'OA_REIMBURSEMENT_DEFAULT' "
                                + "and rule_info.rule_status='ACTIVE'"))
                        .isEqualTo(2);
                assertThat(count(connection,
                        "select count(*) from sys_menu where perms in ("
                                + "'oa:reimbursement:approvalStartOutbox:list',"
                                + "'oa:reimbursement:approvalStartOutbox:replay')"))
                        .isEqualTo(2);
                assertThat(count(connection,
                        "select count(*) from sys_role_menu role_menu "
                                + "join sys_menu menu_info "
                                + "on menu_info.menu_id=role_menu.menu_id "
                                + "join sys_role role_info "
                                + "on role_info.role_id=role_menu.role_id "
                                + "where menu_info.perms in ("
                                + "'oa:reimbursement:approvalStartOutbox:list',"
                                + "'oa:reimbursement:approvalStartOutbox:replay') "
                                + "and not (role_info.role_id=1 "
                                + "or role_info.role_key='admin')"))
                        .isZero();
            }
        }
    }

    @Test
    void automaticApprovalSeedRemainsFailClosed() throws Exception
    {
        for (MySQLContainer database : DATABASES)
        {
            try (Connection connection = connection(database))
            {
                assertThat(count(connection,
                        "select count(*) from approval_template "
                                + "where business_code in "
                                + "('INV_TRANSFER','INV_STOCK_CHECK') "
                                + "and engine_mode='LEGACY'"))
                        .isEqualTo(2);
                assertThat(count(connection,
                        "select count(*) from approval_rule rule_info "
                                + "join approval_template template_info "
                                + "on template_info.template_id=rule_info.template_id "
                                + "where template_info.business_code in "
                                + "('INV_TRANSFER','INV_STOCK_CHECK') "
                                + "and rule_info.rule_status='ACTIVE'"))
                        .isZero();
                assertThat(count(connection,
                        "select count(*) from approval_instance"))
                        .isZero();
            }
        }
    }

    @Test
    void healthApprovalAdministratorDisableSurvivesSeedReplay()
            throws Exception
    {
        String seed = Files.readString(migration(
                "erp_unified_approval_seed_20260716.sql"),
                StandardCharsets.UTF_8);
        for (MySQLContainer database : DATABASES)
        {
            try (Connection connection = connection(database);
                    Statement statement = connection.createStatement())
            {
                statement.executeUpdate(
                        "update approval_rule rule_info "
                                + "join approval_template template_info "
                                + "on template_info.template_id=rule_info.template_id "
                                + "set rule_info.rule_status='DISABLED', "
                                + "rule_info.update_by='migration-it-admin' "
                                + "where template_info.business_code="
                                + "'HR_HEALTH_CERTIFICATE'");
                statement.executeUpdate(
                        "update approval_template set template_status="
                                + "'DISABLED', update_by='migration-it-admin' "
                                + "where business_code='HR_HEALTH_CERTIFICATE'");

                executeScript(connection, seed);

                assertThat(value(connection,
                        "select concat(rule_info.rule_status,'|',"
                                + "coalesce(rule_info.update_by,'')) "
                                + "from approval_rule rule_info "
                                + "join approval_template template_info "
                                + "on template_info.template_id=rule_info.template_id "
                                + "where template_info.business_code="
                                + "'HR_HEALTH_CERTIFICATE' limit 1"))
                        .isEqualTo("DISABLED|migration-it-admin");
                assertThat(value(connection,
                        "select concat(template_status,'|',"
                                + "coalesce(update_by,'')) from approval_template "
                                + "where business_code="
                                + "'HR_HEALTH_CERTIFICATE' limit 1"))
                        .isEqualTo("DISABLED|migration-it-admin");

                statement.executeUpdate(
                        "update approval_rule rule_info "
                                + "join approval_template template_info "
                                + "on template_info.template_id=rule_info.template_id "
                                + "set rule_info.rule_status='ACTIVE', "
                                + "rule_info.update_by='system' "
                                + "where template_info.business_code="
                                + "'HR_HEALTH_CERTIFICATE'");
                statement.executeUpdate(
                        "update approval_template set template_status="
                                + "'ACTIVE', update_by='system' "
                                + "where business_code='HR_HEALTH_CERTIFICATE'");
            }
        }
    }

    private static void prepareSalesDeliveryMigrationBaseline(
            Connection connection) throws SQLException
    {
        executeScript(connection, """
                CREATE TABLE inv_sales_order (
                    order_id bigint NOT NULL,
                    shop_dept_id bigint NOT NULL,
                    PRIMARY KEY (order_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

                CREATE TABLE inv_sales_detail (
                    detail_id bigint NOT NULL,
                    order_id bigint NOT NULL,
                    warehouse_id bigint DEFAULT NULL,
                    PRIMARY KEY (detail_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

                CREATE TABLE inv_delivery_notice (
                    notice_id bigint NOT NULL,
                    sales_order_id bigint NOT NULL,
                    status varchar(20) NOT NULL,
                    shop_dept_id bigint NOT NULL,
                    PRIMARY KEY (notice_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

                CREATE TABLE inv_delivery_notice_detail (
                    detail_id bigint NOT NULL,
                    notice_id bigint NOT NULL,
                    sales_detail_id bigint DEFAULT NULL,
                    delivered_qty decimal(16,2) DEFAULT 0.00,
                    PRIMARY KEY (detail_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

                CREATE TABLE inv_outbound_record (
                    outbound_id bigint NOT NULL AUTO_INCREMENT,
                    PRIMARY KEY (outbound_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

                INSERT INTO inv_sales_order
                    (order_id, shop_dept_id)
                VALUES (70001, 201);
                INSERT INTO inv_sales_detail
                    (detail_id, order_id, warehouse_id)
                VALUES (70001, 70001, 301);
                INSERT INTO inv_delivery_notice
                    (notice_id, sales_order_id, status, shop_dept_id)
                VALUES (70001, 70001, 'pending', 201);
                INSERT INTO inv_delivery_notice_detail
                    (detail_id, notice_id, sales_detail_id, delivered_qty)
                VALUES (70001, 70001, 70001, 0.00);
                INSERT INTO inv_transfer_order
                    (order_no, purchase_id, from_dept_id, from_warehouse_id,
                     to_dept_id, to_warehouse_id, status, total_quantity,
                     transfer_type, source_business_type, source_business_id)
                VALUES ('TF-P0B-LEGACY', 70001, 201, 301, 202, 202,
                        'delivered', 1.00, 'cross_store',
                        'sales_delivery', 70001);
                """);
    }

    private static MySQLContainer mysql(String image, String databaseName)
    {
        return new MySQLContainer(image)
                .withDatabaseName(databaseName)
                .withUsername("migration_it")
                .withPassword("migration_it_password")
                .withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
                .withCommand("--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_unicode_ci",
                        "--log-bin-trust-function-creators=1");
    }

    private static Connection connection(MySQLContainer database)
            throws SQLException
    {
        return DriverManager.getConnection(database.getJdbcUrl(),
                database.getUsername(), database.getPassword());
    }

    private static void applyMigrations(Connection connection,
            List<String> migrations) throws Exception
    {
        for (String name : migrations)
        {
            try
            {
                executeScript(connection, Files.readString(migration(name),
                        StandardCharsets.UTF_8));
            }
            catch (SQLException failure)
            {
                throw new SQLException("migration failed: " + name
                        + ": " + failure.getMessage(), failure);
            }
        }
    }

    private static List<String> migrationNames() throws IOException
    {
        return Files.readAllLines(projectFile(
                        "scripts/new-business-migrations-20260713.list"),
                StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    private static SchemaFingerprint fingerprint(Connection connection)
            throws SQLException
    {
        return new SchemaFingerprint(
                count(connection, "select count(*) from sys_config"),
                count(connection, "select count(*) from sys_menu"),
                count(connection, "select count(*) from sys_role_menu"),
                count(connection,
                        "select count(*) from inv_customer_service_profile"),
                count(connection,
                        "select count(*) from information_schema.tables "
                                + "where table_schema=database()"),
                count(connection,
                        "select count(*) from information_schema.columns "
                                + "where table_schema=database()"),
                count(connection,
                        "select count(*) from information_schema.statistics "
                                + "where table_schema=database()"),
                count(connection, "select count(*) from approval_template"),
                count(connection, "select count(*) from approval_rule"),
                count(connection, "select count(*) from approval_rule_version"),
                count(connection, "select count(*) from approval_version_node"));
    }

    private static String quotedFlags()
    {
        return quotedValues(FEATURE_FLAGS);
    }

    private static String indexColumns(String tableName, String indexName)
    {
        return "select group_concat(column_name order by seq_in_index) "
                + "from information_schema.statistics "
                + "where table_schema=database() and table_name='"
                + tableName + "' and index_name='" + indexName + "'";
    }

    private static String quotedValues(List<String> values)
    {
        return values.stream()
                .map(flag -> "'" + flag + "'")
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
    }

    private static int count(Connection connection, String sql)
            throws SQLException
    {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql))
        {
            if (!result.next())
            {
                return 0;
            }
            int rows = result.getInt(1);
            if (result.next())
            {
                return 1;
            }
            return rows;
        }
    }

    private static String value(Connection connection, String sql)
            throws SQLException
    {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql))
        {
            result.next();
            return result.getString(1);
        }
    }

    private static String resource(String name) throws IOException
    {
        try (var input = NewBusinessMigrationsMySqlIT.class.getClassLoader()
                .getResourceAsStream(name))
        {
            if (input == null)
            {
                throw new IOException("missing test resource " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path migration(String name)
    {
        return projectFile("sql/" + name);
    }

    private static Path projectFile(String relativePath)
    {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path path = root.resolve(relativePath);
        if (!Files.exists(path))
        {
            path = root.resolve("../../").resolve(relativePath).normalize();
        }
        if (!Files.exists(path))
        {
            throw new IllegalStateException("missing project file: " + path);
        }
        return path;
    }

    private static void executeScript(Connection connection, String sql)
            throws SQLException
    {
        String delimiter = ";";
        StringBuilder statement = new StringBuilder();
        try (Statement executor = connection.createStatement())
        {
            for (String line : sql.split("\\R", -1))
            {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.startsWith("#"))
                {
                    continue;
                }
                if (trimmed.toUpperCase().startsWith("DELIMITER "))
                {
                    delimiter = trimmed.substring("DELIMITER ".length())
                            .trim();
                    continue;
                }
                statement.append(line).append('\n');
                int end;
                while ((end = statement.indexOf(delimiter)) >= 0)
                {
                    String command = statement.substring(0, end).trim();
                    statement.delete(0, end + delimiter.length());
                    if (!command.isEmpty())
                    {
                        executor.execute(command);
                    }
                }
            }
            if (!statement.toString().trim().isEmpty())
            {
                executor.execute(statement.toString());
            }
        }
    }

    private record SchemaFingerprint(int configRows, int menuRows,
            int roleMenuRows, int profileRows, int tableRows, int columnRows,
            int indexRows, int approvalTemplateRows, int approvalRuleRows,
            int approvalVersionRows, int approvalNodeRows)
    {
    }
}
