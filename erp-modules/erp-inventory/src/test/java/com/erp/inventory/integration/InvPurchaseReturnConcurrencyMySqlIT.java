package com.erp.inventory.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.erp.inventory.mapper.*;
import com.erp.inventory.service.impl.InvPurchaseReturnServiceImpl;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

/** Real return service, business tables and mappers in fresh local MySQL; scope directory is synthetic. */
class InvPurchaseReturnConcurrencyMySqlIT
{
    static MySQLContainer mysql;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    static final String MIGRATION = "erp_inventory_purchase_return_lock_indexes_20260912.sql";
    InvPurchaseReturnServiceImpl service;
    InvPurchaseReturnDetailMapper details;
    final Faults faults = new Faults();

    @BeforeAll static void start() throws Exception
    {
        String version = System.getProperty("b04c.mysql.version", "5.7.44");
        assertThat(version).isIn("5.7.44", "8.0.36");
        mysql = new MySQLContainer("mysql:" + version).withDatabaseName("b04c_returns")
                .withUsername("b04c_it").withPassword(UUID.randomUUID().toString())
                .withReuse(false).withLabels(Map.of("erp.task", "b04c"))
                .withTmpFs(Map.of("/var/lib/mysql", "rw,size=1g"))
                .withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
        try
        {
            mysql.start(); assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            jdbc = new JdbcTemplate(source); manager = new DataSourceTransactionManager(source);
            assertThat(jdbc.queryForObject("select version()", String.class)).startsWith(version);
            execute(resource("purchase-receive-b04a-baseline.sql")); execute(resource("purchase-return-b04c-baseline.sql"));
            assertThat(indexCount()).isZero(); migrate(); migrate(); assertThat(indexCount()).isEqualTo(2);
            System.out.printf("B04C isolated image=%s container=%s host=%s port=%s%n", version, mysql.getContainerId(), mysql.getHost(), mysql.getMappedPort(3306));
        }
        catch (Throwable failure) { mysql.stop(); throw failure; }
    }

    @AfterAll static void stop()
    {
        if (mysql != null)
        {
            String id = mysql.getContainerId(); mysql.stop(); assertThat(mysql.isRunning()).isFalse();
            System.out.println("B04C cleaned container=" + id);
        }
    }
    @AfterEach void clearIdentity() { SecurityContextHolder.remove(); }

    @BeforeEach void fixture() throws Exception
    {
        for (String table : List.of("inv_stock_log", "inv_purchase_return_detail", "inv_purchase_return", "inv_inbound_record", "inv_purchase_detail", "inv_purchase_order", "inv_stock", "inv_number_sequence"))
            jdbc.update("delete from " + table);
        seedSource(10L, 201L, 1L); seedReturn(101L, 10L, 201L, 1L, "7"); seedReturn(102L, 10L, 201L, 1L, "7");
        Configuration config = new Configuration(new Environment("b04c", new SpringManagedTransactionFactory(), manager.getDataSource()));
        config.getTypeAliasRegistry().registerAliases("com.erp.inventory.domain"); config.addInterceptor(faults);
        for (String mapper : List.of("InvPurchaseOrderMapper", "InvPurchaseDetailMapper", "InvPurchaseReturnMapper", "InvPurchaseReturnDetailMapper", "InvInboundRecordMapper", "InvStockMapper", "InvStockLogMapper", "InvNumberSequenceMapper"))
        {
            String resource = "mapper/inventory/" + mapper + ".xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
            { new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse(); }
        }
        SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        InvPurchaseReturnServiceImpl target = new InvPurchaseReturnServiceImpl();
        for (Class<?> mapper : List.of(InvPurchaseOrderMapper.class, InvPurchaseDetailMapper.class, InvPurchaseReturnMapper.class,
                InvPurchaseReturnDetailMapper.class, InvInboundRecordMapper.class, InvStockMapper.class, InvStockLogMapper.class, InvNumberSequenceMapper.class))
        {
            String name = mapper.getSimpleName().substring(3);
            ReflectionTestUtils.setField(target, Character.toLowerCase(name.charAt(0)) + name.substring(1), session.getMapper(mapper));
        }
        details = session.getMapper(InvPurchaseReturnDetailMapper.class);
        InvDeptScopeMapper scopes = mock(InvDeptScopeMapper.class);
        when(scopes.selectDeptTypeById(anyLong())).thenReturn("WAREHOUSE");
        when(scopes.countDeptInScope(anyLong(), anyLong())).thenAnswer(c -> Objects.equals(c.getArgument(0), c.getArgument(1)) ? 1 : 0);
        ReflectionTestUtils.setField(target, "deptScopeMapper", scopes);
        ProxyFactory proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (InvPurchaseReturnServiceImpl) proxy.getProxy(); login();
    }

    @Test void twoSevensOnlyOneCanReserveAndWinnerCanConfirm() throws Exception
    {
        List<Object> result = concurrent(() -> submit(101L), () -> submit(102L));
        oneSuccess(result); long winner = submittedId(); confirm(winner);
        assertThat(reserved(10L)).isEqualByComparingTo("7"); stock(1L, "3"); logs(1);
    }

    @Test void sevenAndThreeCanBothReserveAndConfirm() throws Exception
    {
        jdbc.update("update inv_purchase_return_detail set quantity=3,amount=3 where return_id=102");
        assertThat(concurrent(() -> submit(101L), () -> submit(102L))).containsOnly("success");
        assertThat(concurrent(() -> confirm(101L), () -> confirm(102L))).containsOnly("success");
        assertThat(reserved(10L)).isEqualByComparingTo("10"); stock(1L, "0"); logs(2);
        assertThat(jdbc.queryForObject("select sum(returned_quantity) from inv_purchase_return_detail", BigDecimal.class)).isEqualByComparingTo("10");
    }

    @Test void cancellingReleasesOnlyTheCancelledReservation()
    {
        submit(101L); assertThatThrownBy(() -> submit(102L)).isInstanceOf(ServiceException.class);
        service.cancelReturn(101L, 20L); submit(102L); confirm(102L);
        assertThat(status(101L)).isEqualTo("cancelled"); assertThat(status(102L)).isEqualTo("returned");
        assertThat(reserved(10L)).isEqualByComparingTo("7"); stock(1L, "3"); logs(1);
    }

    @Test void confirmAndCancelHaveExactlyOneTerminalWinner() throws Exception
    {
        submit(101L); oneSuccess(concurrent(() -> confirm(101L), () -> service.cancelReturn(101L, 20L)));
        if ("returned".equals(status(101L))) { stock(1L, "3"); logs(1); }
        else { assertThat(status(101L)).isEqualTo("cancelled"); stock(1L, "10"); logs(0); }
    }

    @Test void concurrentConfirmDoesNotDeductTwice() throws Exception
    {
        submit(101L); oneSuccess(concurrent(() -> confirm(101L), () -> confirm(101L)));
        stock(1L, "3"); logs(1); assertThat(status(101L)).isEqualTo("returned");
    }

    @Test void oldRepeatableReadSnapshotCannotHideNewReservation() throws Exception
    {
        Object outcome = afterOldSnapshot(() -> submit(101L), () -> submit(102L));
        assertThat(outcome).isInstanceOf(ServiceException.class);
        assertThat(status(101L)).isEqualTo("submitted"); assertThat(status(102L)).isEqualTo("draft");
        confirm(101L); stock(1L, "3"); logs(1);
    }

    @Test void currentInboundFactsOverrideOldNestedAggregateSnapshot() throws Exception
    {
        jdbc.update("update inv_inbound_record set accepted_quantity=3,inspected_quantity=3,qc_result='pending'");
        jdbc.update("update inv_stock set current_quantity=3,available_quantity=3,total_cost=3");
        Object outcome = afterOldSnapshot(() -> tx(() -> {
            jdbc.queryForObject("select order_id from inv_purchase_order where order_id=10 for update", Long.class);
            jdbc.update("update inv_inbound_record set accepted_quantity=10,inspected_quantity=10,qc_result='passed'");
            jdbc.update("update inv_stock set current_quantity=10,available_quantity=10,total_cost=10,version=version+1");
        }), () -> submit(102L));
        assertThat(outcome).isEqualTo("success"); confirm(102L); stock(1L, "3"); logs(1);
    }

    @Test void pendingAndRejectedReceiptsCannotBackReturnReservations()
    {
        jdbc.update("update inv_inbound_record set accepted_quantity=0,rejected_quantity=3,inspected_quantity=3,qc_result='pending'");
        assertThatThrownBy(() -> submit(101L)).isInstanceOf(ServiceException.class);
        assertThat(status(101L)).isEqualTo("draft"); assertThat(reserved(10L)).isEqualByComparingTo("0"); logs(0);
    }

    @Test void changedSourceAfterVisibleSnapshotIsRejectedBeforeReservation() throws Exception
    {
        seedSource(20L, 202L, 2L);
        Object outcome = afterOldSnapshot(() -> jdbc.update("update inv_purchase_return set purchase_order_id=20 where return_id=102"), () -> submit(102L));
        assertThat(outcome).isInstanceOf(ServiceException.class);
        assertThat(((ServiceException) outcome).getMessage()).contains("原采购单已变化");
        assertThat(status(102L)).isEqualTo("draft"); logs(0);
    }

    @Test void createAndExistingDraftCompeteForSameRemainingQuota() throws Exception
    {
        oneSuccess(concurrent(() -> submit(101L), () -> service.submitReturn(header(), List.of(item("7")), 20L)));
        assertThat(reserved(10L)).isEqualByComparingTo("7"); confirm(submittedId()); stock(1L, "3"); logs(1);
    }

    @Test void lateSubmitFailureDoesNotLeaveReservedStatus()
    {
        faults.after.set("updateInvPurchaseReturnStatus");
        assertThatThrownBy(() -> submit(101L)).hasRootCauseInstanceOf(ServiceException.class)
                .hasStackTraceContaining("B04C injected after updateInvPurchaseReturnStatus");
        assertThat(status(101L)).isEqualTo("draft"); assertThat(reserved(10L)).isEqualByComparingTo("0");
        submit(102L); confirm(102L); stock(1L, "3"); logs(1);
    }

    @Test void lateConfirmFailureRollsBackStockLogQuantityAndStatus()
    {
        submit(101L); faults.after.set("insertInvStockLog");
        assertThatThrownBy(() -> confirm(101L)).hasRootCauseInstanceOf(ServiceException.class)
                .hasStackTraceContaining("B04C injected after insertInvStockLog");
        stock(1L, "10"); logs(0); assertThat(status(101L)).isEqualTo("submitted");
        assertThat(jdbc.queryForObject("select returned_quantity from inv_purchase_return_detail where return_id=101", BigDecimal.class)).isEqualByComparingTo("0");
        confirm(101L); stock(1L, "3"); logs(1);
    }

    @Test void insufficientAvailableStockKeepsSubmittedReservationAndNoMovement()
    {
        submit(101L); jdbc.update("update inv_stock set available_quantity=6,locked_quantity=4");
        assertThatThrownBy(() -> confirm(101L)).isInstanceOf(ServiceException.class);
        assertThat(status(101L)).isEqualTo("submitted"); logs(0);
        assertThat(jdbc.queryForObject("select current_quantity from inv_stock", BigDecimal.class)).isEqualByComparingTo("10");
    }

    @Test void migrationRerunPreservesLegacyRowsAndRejectsIncompatibleNamedIndex() throws Exception
    {
        List<Map<String,Object>> before = jdbc.queryForList("select * from inv_purchase_return order by return_id");
        migrate(); migrate(); assertThat(indexCount()).isEqualTo(2);
        assertThat(jdbc.queryForList("select * from inv_purchase_return order by return_id")).isEqualTo(before);
        jdbc.execute("alter table inv_purchase_return drop index idx_ipr_purchase, add index idx_ipr_purchase(status)");
        try { assertThatThrownBy(InvPurchaseReturnConcurrencyMySqlIT::migrate).hasMessageContaining("incompatible"); }
        finally { jdbc.execute("alter table inv_purchase_return drop index idx_ipr_purchase"); migrate(); }
    }

    @Test void lockIndexesPreventUnrelatedOrderBlocking() throws Exception
    {
        seedSource(20L, 202L, 2L); seedReturn(202L, 20L, 202L, 2L, "1");
        jdbc.update("update inv_purchase_return set status='submitted'");
        jdbc.execute("alter table inv_purchase_return drop index idx_ipr_purchase");
        jdbc.execute("alter table inv_purchase_return_detail drop index idx_iprd_return");
        try
        {
            heldRead(() -> jdbc.queryForList("select d.detail_id from inv_purchase_return r straight_join inv_purchase_return_detail d on d.return_id=r.return_id where r.purchase_order_id=10 and r.status in ('submitted','returned') for update"), true);
            migrate();
            heldRead(() -> details.selectReservedDetailsForUpdate(10L, null), false);
        }
        finally { migrate(); }
    }

    private void heldRead(Runnable read, boolean expectedBlocked) throws Exception
    {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        try
        {
            Future<?> first = pool.submit(() -> tx(() -> { read.run(); held.countDown(); await(release); }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Integer> other = pool.submit(() -> jdbc.update("update inv_purchase_return set remark=? where return_id=202", "other-order-" + expectedBlocked));
            if (expectedBlocked) assertThatThrownBy(() -> other.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            else assertThat(other.get(3, TimeUnit.SECONDS)).isEqualTo(1);
            release.countDown(); first.get(10, TimeUnit.SECONDS); assertThat(other.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            System.out.println("B04C unrelated order blocked without lock indexes=" + expectedBlocked);
        }
        finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    private Object afterOldSnapshot(Runnable intervening, Runnable action) throws Exception
    {
        ExecutorService pool = Executors.newSingleThreadExecutor(); CountDownLatch snapshot = new CountDownLatch(1), resume = new CountDownLatch(1);
        try
        {
            Future<Object> result = pool.submit(() -> {
                login();
                try { tx(() -> {
                    String v = jdbc.queryForObject("select version()", String.class);
                    String isolation = jdbc.queryForObject("select " + (v.startsWith("5.7") ? "@@tx_isolation" : "@@transaction_isolation"), String.class);
                    assertThat(isolation).isEqualTo("REPEATABLE-READ");
                    jdbc.queryForList("select * from inv_purchase_return"); jdbc.queryForList("select * from inv_inbound_record");
                    snapshot.countDown(); await(resume); action.run();
                }); return "success"; }
                catch (Exception failure) { return failure; }
                finally { SecurityContextHolder.remove(); }
            });
            assertThat(snapshot.await(10, TimeUnit.SECONDS)).isTrue(); intervening.run(); resume.countDown();
            return result.get(15, TimeUnit.SECONDS);
        }
        finally { resume.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    private List<Object> concurrent(Runnable first, Runnable second) throws Exception
    {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try
        {
            List<Future<Object>> futures = new ArrayList<>();
            for (Runnable action : List.of(first, second)) futures.add(pool.submit(() -> {
                login(); ready.countDown(); await(start);
                try { action.run(); return "success"; } catch (Exception failure) { return failure; }
                finally { SecurityContextHolder.remove(); }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
        }
        finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    private void submit(Long id) { service.submitSavedReturn(id, 0L, 20L); }
    private void confirm(Long id) { service.confirmReturn(id, 20L); }
    private String status(Long id) { return jdbc.queryForObject("select status from inv_purchase_return where return_id=?", String.class, id); }
    private long submittedId() { return jdbc.queryForObject("select return_id from inv_purchase_return where status='submitted'", Long.class); }
    private BigDecimal reserved(Long order) { return jdbc.queryForObject("select coalesce(sum(d.quantity),0) from inv_purchase_return r join inv_purchase_return_detail d on d.return_id=r.return_id where r.purchase_order_id=? and r.status in ('submitted','returned')", BigDecimal.class, order); }
    private void stock(Long item, String quantity) { assertThat(jdbc.queryForObject("select current_quantity from inv_stock where item_id=?", BigDecimal.class, item)).isEqualByComparingTo(quantity); }
    private void logs(int n) { assertThat(jdbc.queryForObject("select count(*) from inv_stock_log", Integer.class)).isEqualTo(n); }
    private void oneSuccess(List<Object> results)
    {
        assertThat(results.stream().filter("success"::equals)).hasSize(1);
        assertThat(results.stream().filter(result -> result instanceof ServiceException)).hasSize(1);
    }
    private static void tx(Runnable action) { new TransactionTemplate(manager).execute(status -> { action.run(); return null; }); }
    private static void await(CountDownLatch latch)
    { try { if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("barrier timed out"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } }
    private static void login()
    { SecurityContextHolder.setUserId("1"); SecurityContextHolder.setUserName("b04c"); LoginUser login = new LoginUser(); SysUser user = new SysUser(1L); user.setDeptId(20L); login.setSysUser(user); SecurityContextHolder.set(SecurityConstants.LOGIN_USER, login); }
    private static void seedSource(Long order, Long detail, Long product)
    {
        jdbc.update("insert into inv_purchase_order(order_id,order_no,supplier_name,shop_dept_id,status,qc_status) values(?,?, 'supplier',20,'received','passed')", order, "PO-" + order);
        jdbc.update("insert into inv_purchase_detail(detail_id,order_id,item_type,item_id,product_id,product_name,warehouse_id,quantity,unit_price,amount,received_quantity) values(?,?,'product',?,?,'tea',20,10,1,10,10)", detail, order, product, product);
        jdbc.update("insert into inv_inbound_record(purchase_order_id,purchase_detail_id,product_id,quantity,qc_result,receipt_batch_detail_id,accepted_quantity,concession_quantity,rejected_quantity,inspected_quantity) values(?,?,?,10,'passed',?,10,0,0,10)", order, detail, product, detail);
        jdbc.update("insert into inv_stock(item_type,item_id,product_id,shop_dept_id,warehouse_id,current_quantity,available_quantity,locked_quantity,cost_price,total_cost,version) values('product',?,?,20,20,10,10,0,1,10,0)", product, product);
    }
    private static void seedReturn(Long id, Long order, Long detail, Long product, String quantity)
    {
        jdbc.update("insert into inv_purchase_return(return_id,return_no,purchase_order_id,purchase_order_no,return_title,supplier_name,return_date,return_reason,responsibility,shop_dept_id,status) values(?,?,?,?, 'return','supplier','2026-09-12','quality','supplier',20,'draft')", id, "PR-" + id, order, "PO-" + order);
        jdbc.update("insert into inv_purchase_return_detail(return_id,purchase_detail_id,item_type,item_id,product_id,product_name,quantity,unit_price,amount,returned_quantity) values(?,?,'product',?,?,'tea',?,1,?,0)", id, detail, product, product, new BigDecimal(quantity), new BigDecimal(quantity));
    }
    private static InvPurchaseReturn header()
    { InvPurchaseReturn r = new InvPurchaseReturn();r.setVersion(0L); r.setPurchaseOrderId(10L); r.setReturnTitle("new return"); r.setReturnDate(new Date()); r.setReturnReason("quality"); r.setResponsibility("supplier"); return r; }
    private static InvPurchaseReturnDetail item(String quantity)
    { InvPurchaseReturnDetail d = new InvPurchaseReturnDetail(); d.setPurchaseDetailId(201L); d.setItemType("product"); d.setItemId(1L); d.setProductId(1L); d.setQuantity(new BigDecimal(quantity)); return d; }
    private static int indexCount() { return jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema=database() and index_name in ('idx_ipr_purchase','idx_iprd_return')", Integer.class); }
    private static void migrate() throws Exception
    {
        Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("sql/" + MIGRATION))) root = root.getParent();
        if (root == null) throw new IllegalStateException("Missing R07 migration"); execute(Files.readString(root.resolve("sql/" + MIGRATION)));
    }
    private static String resource(String name) throws Exception
    { try (InputStream input = InvPurchaseReturnConcurrencyMySqlIT.class.getClassLoader().getResourceAsStream(name)) { return new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8); } }
    private static void execute(String sql) throws Exception
    {
        try (Connection connection = Objects.requireNonNull(manager.getDataSource()).getConnection(); Statement statement = connection.createStatement())
        {
            String delimiter = ";"; StringBuilder pending = new StringBuilder();
            for (String raw : sql.split("\\R"))
            {
                String line = raw.trim(); if (line.isEmpty() || line.startsWith("--")) continue;
                if (line.toUpperCase(Locale.ROOT).startsWith("DELIMITER ")) { delimiter = line.substring(10).trim(); continue; }
                pending.append(raw).append('\n');
                if (line.endsWith(delimiter))
                {
                    String command = pending.toString().trim(); command = command.substring(0, command.length() - delimiter.length());
                    statement.execute(command); pending.setLength(0);
                }
            }
            assertThat(pending.toString().trim()).isEmpty();
        }
    }
    @Intercepts(@Signature(type = Executor.class, method = "update", args = { MappedStatement.class, Object.class }))
    static class Faults implements Interceptor
    {
        final AtomicReference<String> after = new AtomicReference<>();
        @Override public Object intercept(Invocation invocation) throws Throwable
        {
            Object result = invocation.proceed(); String requested = after.get();
            if (requested != null && ((MappedStatement) invocation.getArgs()[0]).getId().endsWith("." + requested) && after.compareAndSet(requested, null))
                throw new ServiceException("B04C injected after " + requested);
            return result;
        }
    }
}
