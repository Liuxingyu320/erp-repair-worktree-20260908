package com.erp.inventory.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.erp.inventory.domain.dto.*;
import com.erp.inventory.domain.vo.InventoryItemSnapshot;
import com.erp.inventory.mapper.*;
import com.erp.inventory.service.impl.*;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import org.testcontainers.mysql.MySQLContainer;

/** Task-owned MySQL, real production mappers and Spring transactions; no production data. */
class InvSalesWarehouseMutationMySqlIT
{
    static MySQLContainer mysql;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    SqlSessionTemplate session;
    InvSalesServiceImpl sales;
    InvDeliveryNoticeServiceImpl notices;
    final Fault fault = new Fault();

    @BeforeAll static void start() throws Exception
    {
        String version = System.getProperty("inventory.mysql.version", "5.7.44");
        assertThat(version).isIn("5.7.44", "8.0.36");
        mysql = new MySQLContainer("mysql:" + version).withDatabaseName("joint_inventory_sales")
                .withUsername("inventory_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
                .withLabels(Map.of("erp.task", "joint-inventory-sales-20260913"))
                .withTmpFs(Map.of("/var/lib/mysql", "rw,size=1g"))
                .withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
        try {
            mysql.start();
            assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            jdbc = new JdbcTemplate(source); manager = new DataSourceTransactionManager(source);
            execute(resource("purchase-receive-b04a-baseline.sql"));
            execute(resource("purchase-return-b04c-baseline.sql"));
            execute(resource("stock-cost-b04d-baseline.sql"));
            // Exercise the actual additive migration with an old schema, then its re-run path.
            if (jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name='inv_sales_order' and column_name='version'", Integer.class) > 0)
                jdbc.execute("alter table inv_sales_order drop column version");
            execute(migration()); execute(migration());
            jdbc.update("insert into sys_dept(dept_id,dept_name) values(40,'warehouse 40'),(99,'foreign store')");
            jdbc.update("insert into inv_customer(customer_id,customer_name,shop_dept_id,status) values(1,'Customer',20,'0')");
            System.out.println("Inventory sales isolated MySQL " + version + " container=" + mysql.getContainerId());
        } catch (Throwable error) { mysql.stop(); throw error; }
    }
    @AfterAll static void stop() { if (mysql != null) { mysql.stop(); assertThat(mysql.isRunning()).isFalse(); } }
    @BeforeEach void fixture() throws Exception
    {
        for (String table : List.of("inv_sales_warehouse_repair_audit", "inv_outbound_record", "inv_stock_log", "inv_delivery_notice_detail", "inv_delivery_notice", "inv_sales_detail", "inv_sales_order", "inv_stock")) jdbc.update("delete from " + table);
        Configuration config = new Configuration(new Environment("inventory-sales", new SpringManagedTransactionFactory(), manager.getDataSource()));
        config.getTypeAliasRegistry().registerAliases("com.erp.inventory.domain"); config.addInterceptor(fault);
        for (String mapper : List.of("InvSalesOrderMapper", "InvSalesDetailMapper", "InvSalesWarehouseRepairMapper", "InvDeliveryNoticeMapper", "InvDeliveryNoticeDetailMapper", "InvCustomerMapper", "InvNumberSequenceMapper", "InvStockMapper", "InvStockLogMapper", "InvOutboundRecordMapper")) {
            String name = "mapper/inventory/" + mapper + ".xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) { new XMLMapperBuilder(input, config, name, config.getSqlFragments()).parse(); }
        }
        session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        var salesTarget = new InvSalesServiceImpl(); var noticeTarget = new InvDeliveryNoticeServiceImpl();
        InvDeptScopeMapper scope = mock(InvDeptScopeMapper.class);
        when(scope.selectDeptTypeById(anyLong())).thenAnswer(call -> ((Long) call.getArgument(0)) == 20L ? "STORE" : "WAREHOUSE");
        when(scope.countDeptInScope(anyLong(), anyLong())).thenAnswer(call -> {
            Long selected = call.getArgument(0), target = call.getArgument(1);
            return Objects.equals(selected, target) || (selected == 20L && Set.of(30L, 40L).contains(target)) ? 1 : 0;
        });
        for (Object target : List.of(salesTarget, noticeTarget)) {
            for (var field : target.getClass().getDeclaredFields()) if (config.hasMapper(field.getType())) ReflectionTestUtils.setField(target, field.getName(), session.getMapper(field.getType()));
            ReflectionTestUtils.setField(target, "deptScopeMapper", scope);
        }
        InventoryItemResolver resolver = mock(InventoryItemResolver.class);
        when(resolver.resolve(anyString(), anyLong(), nullable(Long.class))).thenAnswer(call -> {
            InventoryItemSnapshot item = new InventoryItemSnapshot(); item.setItemType(call.getArgument(0)); item.setItemId(call.getArgument(1));
            item.setProductId("product".equals(call.getArgument(0)) ? call.getArgument(1) : null);
            item.setItemName("Catalog item"); item.setStatus("0"); item.setSalesPrice(new BigDecimal("10")); return item;
        });
        ReflectionTestUtils.setField(salesTarget, "itemResolver", resolver);
        sales = proxy(salesTarget); notices = proxy(noticeTarget); login();
    }
    @AfterEach void clear() { SecurityContextHolder.remove(); }

    @Test void newSalesAllowsEmptyWarehouseDraftButRequiresItForSubmit()
    {
        InvSalesOrder draft = sales.saveDraft(order(), List.of(detail(1, null)), 20L);
        assertThat(draft.getStatus()).isEqualTo("draft");
        InvSalesOrder request = order(); request.setOrderId(draft.getOrderId()); request.setVersion(draft.getVersion());
        assertThatThrownBy(() -> sales.submitSales(request, List.of(detail(1, null)), 20L)).isInstanceOf(ServiceException.class).hasMessageContaining("出库仓库");
        assertThat(jdbc.queryForObject("select status from inv_sales_order", String.class)).isEqualTo("draft");
    }
    @Test void mixedWarehousesRemainOneNoticeWithFrozenLineAssignments()
    {
        InvSalesOrder submitted = sales.submitSales(order(), List.of(detail(1, 30L), detail(2, 40L)), 20L);
        InvDeliveryNotice notice = notices.createNotice(submitted.getOrderId(), 20L, submitted.getVersion());
        assertThat(count("inv_delivery_notice")).isEqualTo(1);
        assertThat(notice.getWarehouseId()).isNull();
        assertThat(jdbc.queryForList("select warehouse_id from inv_delivery_notice_detail order by detail_id", Long.class)).containsExactly(30L, 40L);
        assertThat(jdbc.queryForObject("select version from inv_sales_order", Long.class)).isEqualTo(2L);
    }
    @Test void twoDraftEditsFromSameVersionAllowOnlyOneReplacement() throws Exception
    {
        seed("draft", 30L); var outcomes = concurrent(() -> sales.saveDraft(edit("first"), List.of(detail(1, 30L)), 20L), () -> sales.saveDraft(edit("second"), List.of(detail(2, 40L)), 20L));
        assertThat(outcomes.stream().filter(InvSalesOrder.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(ServiceException.class::isInstance).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select version from inv_sales_order", Long.class)).isEqualTo(1L);
        assertThat(count("inv_sales_detail")).isEqualTo(1);
    }
    @Test void cancelVersusCreateCannotLeaveCancelledSalesWithAnActiveNotice() throws Exception
    {
        seed("submitted", 30L); var outcomes = concurrent(() -> notices.createNotice(1L, 20L, 0L), () -> { sales.cancelSales(1L, 20L, 0L); return "cancelled"; });
        assertThat(outcomes.stream().filter(ServiceException.class::isInstance).count()).isEqualTo(1);
        String status = jdbc.queryForObject("select status from inv_sales_order", String.class);
        assertThat(status).isIn("noticed", "cancelled"); assertThat(count("inv_delivery_notice")).isEqualTo("noticed".equals(status) ? 1 : 0);
    }
    @Test void oldRepeatableReadSnapshotCannotRestoreDraftAfterNoticeCreation() throws Exception
    {
        seed("submitted", 30L);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            assertThat(session.getMapper(InvSalesOrderMapper.class).selectInvSalesOrderById(1L).getStatus()).isEqualTo("submitted");
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try { pool.submit(() -> { login(); try { notices.createNotice(1L, 20L, 0L); } finally { SecurityContextHolder.remove(); } }).get(10, TimeUnit.SECONDS); }
            catch (Exception error) { throw new AssertionError(error); } finally { pool.shutdownNow(); }
            assertThatThrownBy(() -> sales.cancelSales(1L, 20L, 0L)).isInstanceOf(ServiceException.class);
            // The rejected REQUIRED mutation marks this read-snapshot transaction rollback-only.
            tx.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select status from inv_sales_order", String.class)).isEqualTo("noticed");
    }
    @Test void controlledHistoricalConcurrentRepairCreatesOneNoticeAndOneAudit() throws Exception
    {
        seed("submitted", null);
        var outcomes = concurrent(() -> notices.repairMissingWarehousesAndCreateNotice(1L, repair(30L), 20L), () -> notices.repairMissingWarehousesAndCreateNotice(1L, repair(40L), 20L));
        assertThat(outcomes.stream().filter(InvDeliveryNotice.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(ServiceException.class::isInstance).count()).isEqualTo(1);
        assertThat(count("inv_delivery_notice")).isEqualTo(1); assertThat(count("inv_sales_warehouse_repair_audit")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select warehouse_id from inv_sales_detail", Long.class)).isEqualTo(jdbc.queryForObject("select warehouse_id from inv_delivery_notice_detail", Long.class));
        assertThat(jdbc.queryForObject("select unit_price from inv_sales_detail", BigDecimal.class)).isEqualByComparingTo("10");
    }
    @ParameterizedTest @ValueSource(strings={"fillMissingWarehouse", "insertInvDeliveryNotice", "insertAudit"})
    void controlledHistoricalFailureRollsBackWarehouseNoticeVersionAndAudit(String statement)
    {
        seed("submitted", null); fault.failAfter = statement;
        assertThatThrownBy(() -> notices.repairMissingWarehousesAndCreateNotice(1L, repair(30L), 20L)).isInstanceOf(Exception.class);
        assertThat(jdbc.queryForObject("select warehouse_id from inv_sales_detail", Long.class)).isNull();
        assertThat(jdbc.queryForObject("select version from inv_sales_order", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select status from inv_sales_order", String.class)).isEqualTo("submitted");
        assertThat(count("inv_delivery_notice")).isZero(); assertThat(count("inv_delivery_notice_detail")).isZero(); assertThat(count("inv_sales_warehouse_repair_audit")).isZero();
    }
    @Test void controlledHistoricalRepairRejectsExistingWarehouseForeignMappingAndOutboundFacts()
    {
        seed("submitted", 30L);
        assertThatThrownBy(() -> notices.repairMissingWarehousesAndCreateNotice(1L, repair(40L), 20L)).isInstanceOf(ServiceException.class);
        jdbc.update("update inv_sales_detail set warehouse_id=null");
        assertThatThrownBy(() -> notices.repairMissingWarehousesAndCreateNotice(1L, repair(99L), 20L)).isInstanceOf(ServiceException.class).hasMessageContaining("无权");
        jdbc.update("insert into inv_outbound_record(sales_order_id,quantity) values(1,1)");
        assertThatThrownBy(() -> notices.repairMissingWarehousesAndCreateNotice(1L, repair(30L), 20L)).isInstanceOf(ServiceException.class).hasMessageContaining("出库事实");
        assertThat(count("inv_sales_warehouse_repair_audit")).isZero();
    }
    @Test void cancelledNoticeAndNewCreateUseSameRootWithoutDeadlock() throws Exception
    {
        seed("submitted", 30L); InvDeliveryNotice notice = notices.createNotice(1L, 20L, 0L);
        var outcomes = concurrent(() -> { notices.cancelNotice(notice.getNoticeId(), 20L); return "cancelled"; }, () -> notices.createNotice(1L, 20L, 1L));
        assertThat(outcomes).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from inv_delivery_notice where status in ('pending','delivering')", Integer.class)).isLessThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select status from inv_sales_order", String.class)).isIn("submitted", "noticed");
    }

    @Test void partialDeliveryOfOneWarehousePreservesOtherGroupAndCompletesOnlyAfterBoth()
    {
        InvDeliveryNotice notice=multiWarehouseNotice();
        Long first=lineId(notice.getNoticeId(),30L),second=lineId(notice.getNoticeId(),40L);
        notices.deliverNotice(notice.getNoticeId(),delivery(30L,first,"1"),30L);
        assertThat(jdbc.queryForObject("select status from inv_delivery_notice",String.class)).isEqualTo("delivering");
        assertThat(jdbc.queryForObject("select status from inv_sales_order",String.class)).isEqualTo("noticed");
        assertThat(jdbc.queryForObject("select delivered_qty from inv_delivery_notice_detail where detail_id=?",BigDecimal.class,second)).isZero();
        assertStock(30L,"9","45");assertStock(40L,"10","50");
        notices.deliverNotice(notice.getNoticeId(),delivery(40L,second,"2"),40L);
        assertThat(jdbc.queryForObject("select status from inv_delivery_notice",String.class)).isEqualTo("delivering");
        notices.deliverNotice(notice.getNoticeId(),delivery(30L,first,"1"),30L);
        assertThat(jdbc.queryForObject("select status from inv_delivery_notice",String.class)).isEqualTo("completed");
        assertThat(jdbc.queryForObject("select status from inv_sales_order",String.class)).isEqualTo("delivered");
        assertThat(jdbc.queryForObject("select warehouse_id from inv_delivery_notice",Long.class)).isNull();
        assertThat(jdbc.queryForList("select delivered_qty from inv_delivery_notice_detail order by warehouse_id",BigDecimal.class)).allSatisfy(q->assertThat(q).isEqualByComparingTo("2"));
        assertThat(jdbc.queryForObject("select sum(cost_amount) from inv_outbound_record",BigDecimal.class)).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("select sum(delivered_cost_amount) from inv_delivery_notice_detail",BigDecimal.class)).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("select sum(cost_amount) from inv_stock_log",BigDecimal.class)).isEqualByComparingTo("20");
        assertStock(30L,"8","40");assertStock(40L,"8","40");assertThat(count("inv_delivery_notice")).isEqualTo(1);
    }
    @Test void sendingOtherWarehouseLineIsRejectedWithoutAnyStockMovement()
    {
        InvDeliveryNotice notice=multiWarehouseNotice();
        assertThatThrownBy(()->notices.deliverNotice(notice.getNoticeId(),delivery(30L,lineId(notice.getNoticeId(),40L),"1"),30L)).isInstanceOf(ServiceException.class);
        assertStock(30L,"10","50");assertStock(40L,"10","50");assertThat(count("inv_outbound_record")).isZero();assertThat(count("inv_stock_log")).isZero();
        assertThat(jdbc.queryForObject("select status from inv_delivery_notice",String.class)).isEqualTo("pending");
    }
    @Test void failureAfterRealStockDeductionRollsBackOutboundCostAndEveryDocument()
    {
        InvDeliveryNotice notice=multiWarehouseNotice();Long line=lineId(notice.getNoticeId(),30L);
        fault.failAfter="deductInvStockWithCost";
        assertThatThrownBy(()->notices.deliverNotice(notice.getNoticeId(),delivery(30L,line,"1"),30L)).isInstanceOf(Exception.class);
        assertStock(30L,"10","50");assertThat(count("inv_outbound_record")).isZero();assertThat(count("inv_stock_log")).isZero();
        assertThat(jdbc.queryForObject("select sum(delivered_qty) from inv_delivery_notice_detail",BigDecimal.class)).isZero();
        assertThat(jdbc.queryForObject("select version from inv_sales_order",Long.class)).isEqualTo(2L);
        fault.failAfter=null;notices.deliverNotice(notice.getNoticeId(),delivery(30L,line,"1"),30L);assertStock(30L,"9","45");
    }
    @Test void twoConcurrentFullWarehouseDeliveriesCannotOverdeliverTheSameLine() throws Exception
    {
        InvDeliveryNotice notice=multiWarehouseNotice();Long line=lineId(notice.getNoticeId(),30L);
        var outcomes=concurrent(()->notices.deliverNotice(notice.getNoticeId(),delivery(30L,line,"2"),30L),()->notices.deliverNotice(notice.getNoticeId(),delivery(30L,line,"2"),30L));
        assertThat(outcomes.stream().filter(ServiceException.class::isInstance).count()).isEqualTo(1);assertStock(30L,"8","40");assertStock(40L,"10","50");
        assertThat(count("inv_outbound_record")).isEqualTo(1);assertThat(jdbc.queryForObject("select delivered_qty from inv_delivery_notice_detail where detail_id=?",BigDecimal.class,line)).isEqualByComparingTo("2");
    }
    InvDeliveryNotice multiWarehouseNotice()
    {
        InvSalesOrder submitted=sales.submitSales(order(),List.of(detail(1,30L),detail(2,40L)),20L);
        InvDeliveryNotice notice=notices.createNotice(submitted.getOrderId(),20L,submitted.getVersion());
        jdbc.update("insert into inv_stock(item_type,item_id,product_id,shop_dept_id,warehouse_id,current_quantity,available_quantity,locked_quantity,cost_price,total_cost,version) values('product',1,1,30,30,10,10,0,5,50,0),('product',2,2,40,40,10,10,0,5,50,0)");return notice;
    }
    static Long lineId(Long notice,Long warehouse){return jdbc.queryForObject("select detail_id from inv_delivery_notice_detail where notice_id=? and warehouse_id=?",Long.class,notice,warehouse);}
    static InvDeliverRequest delivery(Long warehouse,Long line,String quantity){var request=new InvDeliverRequest();request.setWarehouseId(warehouse);var item=new InvDeliverItem();item.setDetailId(line);item.setDeliverQuantity(new BigDecimal(quantity));request.setItems(List.of(item));return request;}
    static void assertStock(Long warehouse,String quantity,String cost){assertThat(jdbc.queryForObject("select current_quantity from inv_stock where warehouse_id=?",BigDecimal.class,warehouse)).isEqualByComparingTo(quantity);assertThat(jdbc.queryForObject("select available_quantity from inv_stock where warehouse_id=?",BigDecimal.class,warehouse)).isEqualByComparingTo(quantity);assertThat(jdbc.queryForObject("select total_cost from inv_stock where warehouse_id=?",BigDecimal.class,warehouse)).isEqualByComparingTo(cost);}

    static InvSalesOrder order() { InvSalesOrder row = new InvSalesOrder(); row.setOrderTitle("Sales"); row.setCustomerId(1L); return row; }
    static InvSalesOrder edit(String title) { InvSalesOrder row=order(); row.setOrderId(1L); row.setOrderTitle(title); row.setVersion(0L); return row; }
    static InvSalesDetail detail(long item, Long warehouse) { InvSalesDetail row=new InvSalesDetail(); row.setItemType("product"); row.setItemId(item); row.setProductId(item); row.setQuantity(new BigDecimal("2")); row.setUnitPrice(new BigDecimal("10")); row.setWarehouseId(warehouse); return row; }
    static InvSalesWarehouseRepairRequest repair(Long warehouse) { var body=new InvSalesWarehouseRepairRequest(); body.setVersion(0L); var a=new InvSalesWarehouseRepairRequest.Assignment(); a.setDetailId(11L); a.setWarehouseId(warehouse); body.setAssignments(List.of(a)); return body; }
    void seed(String status, Long warehouse) { jdbc.update("insert into inv_sales_order(order_id,order_no,order_title,customer_id,customer_name,status,shop_dept_id,applicant_id,version) values(1,'SO-1','Original',1,'Customer',?,20,1,0)",status); jdbc.update("insert into inv_sales_detail(detail_id,order_id,item_type,item_id,product_id,product_name,quantity,unit_price,amount,delivered_quantity,warehouse_id) values(11,1,'product',1,1,'Item',2,10,20,0,?)",warehouse); }
    static int count(String table) { return jdbc.queryForObject("select count(*) from " + table,Integer.class); }
    static void login() { SecurityContextHolder.setUserId("1"); SecurityContextHolder.setUserName("inventory-it"); SysUser user=new SysUser(); user.setDeptId(20L); LoginUser login=new LoginUser(); login.setSysUser(user); SecurityContextHolder.set(SecurityConstants.LOGIN_USER,login); }
    @SuppressWarnings("unchecked") static <T> T proxy(T target) { ProxyFactory proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true); proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource())); return (T)proxy.getProxy(); }
    static String resource(String name) throws Exception { try(InputStream input=InvSalesWarehouseMutationMySqlIT.class.getClassLoader().getResourceAsStream(name)) { return new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8); } }
    static String migration() throws Exception { Path root=Path.of("").toAbsolutePath(); while(root!=null&&!Files.exists(root.resolve("sql/erp_inventory_sales_revision_20260913.sql"))) root=root.getParent(); return Files.readString(Objects.requireNonNull(root).resolve("sql/erp_inventory_sales_revision_20260913.sql")); }
    static void execute(String sql) { jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> { try(var statement = connection.createStatement()) { for(String fragment:sql.replaceAll("(?m)^\\s*--.*$", "").split(";")) if(!fragment.isBlank()) statement.execute(fragment); } return null; }); }
    List<Object> concurrent(Callable<Object> a,Callable<Object> b) throws Exception { var pool=Executors.newFixedThreadPool(2); var barrier=new CountDownLatch(2); try { List<Future<Object>> futures=new ArrayList<>(); for(var work:List.of(a,b)) futures.add(pool.submit(()->{login(); try {barrier.countDown(); if(!barrier.await(5,TimeUnit.SECONDS)) throw new AssertionError("barrier"); return work.call();}catch(ServiceException error){return error;}finally{SecurityContextHolder.remove();}})); return List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS));}finally{pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();} }
    @Intercepts(@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class})) public static class Fault implements Interceptor { String failAfter; @Override public Object intercept(Invocation call) throws Throwable { Object result=call.proceed(); if(failAfter!=null&&((MappedStatement)call.getArgs()[0]).getId().endsWith("."+failAfter)) throw new ServiceException("isolated injected failure"); return result; } }
}
