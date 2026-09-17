package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.erp.inventory.mapper.*;
import com.erp.inventory.service.*;

/** Real MySQL and production mapper/receipt SQL in a disposable database. */
@EnabledIfEnvironmentVariable(named = "ERP_REPAIR_NATIVE_MYSQL", matches = "1")
class InvDraftRevisionNativeMysqlTest
{
    static String database;
    static JdbcTemplate admin, jdbc;
    static TransactionTemplate tx;
    static SqlSessionTemplate sql;
    static InvQualityCommandExecutor executor;
    @BeforeAll static void setup() throws Exception
    {
        String base = "jdbc:mysql://127.0.0.1:3306/", suffix = "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai";
        admin = new JdbcTemplate(new UnpooledDataSource("com.mysql.cj.jdbc.Driver", base + "mysql" + suffix, "root", ""));
        database = "erp_draft_repair_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create database " + database);
        var ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", base + database + suffix, "root", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table inv_purchase_order (order_id bigint primary key, version bigint not null default 0, status varchar(20), order_title varchar(128), supplier_id bigint, supplier_name varchar(128), total_amount decimal(18,2), order_date date, remark varchar(500), update_by varchar(64), update_time datetime) engine=InnoDB");
        jdbc.execute("create table inv_purchase_return (return_id bigint primary key, version bigint not null default 0, status varchar(20), return_title varchar(128), supplier_name varchar(128), total_amount decimal(18,2), return_date date, return_reason varchar(500), responsibility varchar(128), attachment_urls varchar(500), remark varchar(500), update_by varchar(64), update_time datetime) engine=InnoDB");
        jdbc.execute("create table inv_sales_return (return_id bigint primary key, version bigint not null default 0, status varchar(20), return_title varchar(128), customer_name varchar(128), sales_order_id bigint, sales_order_no varchar(64), total_amount decimal(18,2), return_date date, remark varchar(500), update_by varchar(64), update_time datetime) engine=InnoDB");
        jdbc.execute("create table draft_line (kind int, quantity int) engine=InnoDB");
        jdbc.execute(Files.readString(Path.of("../../sql/erp_inventory_quality_command_20260909.sql")));
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var config = new Configuration(new Environment("native", new SpringManagedTransactionFactory(), ds));
        config.getTypeAliasRegistry().registerAliases("com.erp.inventory.domain");
        for (String mapper : new String[]{"InvPurchaseDetailMapper", "InvPurchaseOrderMapper", "InvPurchaseReturnMapper", "InvSalesReturnMapper", "InvQualityCommandMapper"}) {
            String resource = "mapper/inventory/" + mapper + ".xml";
            try (InputStream in = InvDraftRevisionNativeMysqlTest.class.getClassLoader().getResourceAsStream(resource)) { new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse(); }
        }
        sql = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        executor = new InvQualityCommandExecutor(sql.getMapper(InvQualityCommandMapper.class), new ObjectMapper());
    }
    @AfterAll static void cleanup() { if (database != null && database.matches("erp_draft_repair_[0-9a-f]{32}")) admin.execute("drop database " + database); }
    @BeforeEach void reset() {
        for (String table : new String[]{"inv_purchase_order", "inv_purchase_return", "inv_sales_return", "draft_line", "inv_quality_command"}) jdbc.update("delete from " + table);
    }
    int update(int kind, long version, int quantity) {
        if (kind == 0) { var row = new InvPurchaseOrder(); row.setOrderId(1L); row.setVersion(version); row.setOrderTitle("editor-" + quantity); row.setTotalAmount(BigDecimal.valueOf(quantity)); return sql.getMapper(InvPurchaseOrderMapper.class).updatePurchaseContent(row); }
        if (kind == 1) { var row = new InvPurchaseReturn(); row.setReturnId(1L); row.setVersion(version); row.setReturnTitle("editor-" + quantity); row.setTotalAmount(BigDecimal.valueOf(quantity)); return sql.getMapper(InvPurchaseReturnMapper.class).updateInvPurchaseReturn(row); }
        var row = new InvSalesReturn(); row.setReturnId(1L); row.setVersion(version); row.setReturnTitle("editor-" + quantity); row.setTotalAmount(BigDecimal.valueOf(quantity)); row.getParams().put("updateContent", true); row.getParams().put("expectedStatus", "draft"); return sql.getMapper(InvSalesReturnMapper.class).updateInvSalesReturn(row);
    }
    @ParameterizedTest @ValueSource(ints={0,1,2}) void concurrentEditorsCannotOverwriteHeaderOrDetails(int kind) throws Exception {
        String table = new String[]{"inv_purchase_order","inv_purchase_return","inv_sales_return"}[kind], id = kind == 0 ? "order_id" : "return_id";
        jdbc.update("insert into " + table + " (" + id + ",status,total_amount) values (1,'draft',10)");
        jdbc.update("insert into draft_line values (?,10)",kind);
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<Integer> action = () -> { start.await(); return tx.execute(status -> {
                int rows = update(kind,0,20);
                if (rows == 1) jdbc.update("update draft_line set quantity=20 where kind=?",kind);
                return rows;
            }); };
            var a=pool.submit(action);var b=pool.submit(action);start.countDown();
            assertThat(a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select version from " + table,Long.class)).isEqualTo(1);
            assertThat(update(kind,0,10)).isZero();
            assertThat(jdbc.queryForObject("select total_amount from " + table,BigDecimal.class)).isEqualByComparingTo("20");
            assertThat(jdbc.queryForObject("select quantity from draft_line",Integer.class)).isEqualTo(20);
        } finally { pool.shutdownNow(); }
    }
    private InvDraftCommandService facade(boolean fail) {
        var purchase = mock(IInvPurchaseService.class);
        when(purchase.saveDraft(any(),any(),any())).thenAnswer(call -> {
            jdbc.update("insert into inv_purchase_order(order_id,status,total_amount) values(1,'draft',20)");
            jdbc.update("insert into draft_line values(0,20)");
            if (fail) throw new ServiceException("injected failure");
            var result=new InvPurchaseOrder();result.setOrderId(1L);result.setVersion(0L);result.setStatus("draft");return result;
        });
        var service = new InvDraftCommandService(executor,purchase,mock(IInvSalesReturnService.class),mock(IInvPurchaseReturnService.class));
        var scope = mock(InvDeptScopeMapper.class);when(scope.countUserShopScope(77L,20L)).thenReturn(1);when(scope.selectDeptTypeById(20L)).thenReturn("WAREHOUSE");
        org.springframework.test.util.ReflectionTestUtils.setField(service,"deptScopeMapper",scope);return service;
    }
    private InvPurchaseOrder command(InvDraftCommandService service,String requestId) {
        SecurityContextHolder.setUserId("77");SecurityContextHolder.setUserName("draft-test");
        try { return tx.execute(status -> service.purchase(requestId,new InvPurchaseOrder(),20L,false)); }
        finally { SecurityContextHolder.remove(); }
    }
    @Test void lostResponseReplaysSameDraftAfterRestartAndLockExpiry() {
        assertThat(command(facade(false),"draft-lost-response").getOrderId()).isEqualTo(1);
        jdbc.update("update inv_quality_command set completed_time=date_sub(now(),interval 2 day)");
        assertThat(command(facade(false),"draft-lost-response").getOrderId()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inv_purchase_order",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from draft_line",Integer.class)).isEqualTo(1);
    }
    @Test void revokedOrganizationCannotReplayAnExistingReceipt() {
        command(facade(false),"draft-revoked-scope");
        var service=facade(false);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"deptScopeMapper",mock(InvDeptScopeMapper.class));
        assertThatThrownBy(() -> command(service,"draft-revoked-scope")).isInstanceOf(ServiceException.class);
        assertThat(jdbc.queryForObject("select count(*) from inv_purchase_order",Integer.class)).isEqualTo(1);
    }
    @Test void rejectionRollsBackReceiptHeaderAndDetails() {
        assertThatThrownBy(() -> command(facade(true),"draft-rollback-test")).isInstanceOf(ServiceException.class);
        for (String table : new String[]{"inv_quality_command","inv_purchase_order","draft_line"}) assertThat(jdbc.queryForObject("select count(*) from " + table,Integer.class)).isZero();
        assertThat(command(facade(false),"draft-rollback-test").getOrderId()).isEqualTo(1);
    }
}
