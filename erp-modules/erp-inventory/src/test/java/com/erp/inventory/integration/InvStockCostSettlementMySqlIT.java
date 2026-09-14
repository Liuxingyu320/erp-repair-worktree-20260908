package com.erp.inventory.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
import com.erp.inventory.domain.dto.*;
import com.erp.inventory.domain.vo.InventoryItemSnapshot;
import com.erp.inventory.mapper.*;
import com.erp.inventory.service.impl.*;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

/** Fresh local MySQL, actual service transactions/business mappers/tables. Scope/catalog directory is synthetic. */
class InvStockCostSettlementMySqlIT
{
    enum Pathway { DELIVERY, PURCHASE_RETURN, STOCK_CHECK, EXISTING_ADJUST, ITEM_ADJUST }
    static MySQLContainer mysql;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    SqlSessionTemplate session;
    InvStockMapper stocks;
    InvDeliveryNoticeServiceImpl delivery;
    InvPurchaseReturnServiceImpl purchaseReturn;
    InvSalesReturnServiceImpl salesReturn;
    InvStockServiceImpl adjustment;
    InvStockCheckAdjustmentService stockCheck;
    final Faults faults = new Faults();

    @BeforeAll static void start() throws Exception
    {
        String version = System.getProperty("b04d.mysql.version", "5.7.44");
        assertThat(version).isIn("5.7.44", "8.0.36");
        mysql = new MySQLContainer("mysql:" + version).withDatabaseName("b04d_cost")
                .withUsername("b04d_it").withPassword(UUID.randomUUID().toString())
                .withReuse(false).withLabels(Map.of("erp.task", "b04d"))
                .withTmpFs(Map.of("/var/lib/mysql", "rw,size=1g"))
                .withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
        try
        {
            mysql.start(); assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            jdbc = new JdbcTemplate(source); manager = new DataSourceTransactionManager(source);
            assertThat(jdbc.queryForObject("select version()", String.class)).startsWith(version);
            execute(resource("purchase-receive-b04a-baseline.sql"));
            execute(resource("purchase-return-b04c-baseline.sql")); execute(resource("stock-cost-b04d-baseline.sql"));
            assertThat(jdbc.queryForObject("select numeric_scale from information_schema.columns where table_schema=database() and table_name='inv_stock' and column_name='total_cost'", Integer.class)).isEqualTo(2);
            System.out.printf("B04D isolated image=%s container=%s host=%s port=%s%n", version, mysql.getContainerId(), mysql.getHost(), mysql.getMappedPort(3306));
        }
        catch (Throwable failure) { mysql.stop(); throw failure; }
    }

    @AfterAll static void stop()
    {
        if (mysql != null)
        {
            String id = mysql.getContainerId(); mysql.stop(); assertThat(mysql.isRunning()).isFalse();
            System.out.println("B04D cleaned container=" + id);
        }
    }
    @AfterEach void clearIdentity() { SecurityContextHolder.remove(); }

    @BeforeEach void fixture() throws Exception
    {
        for (String table : List.of("inv_stock_log", "inv_outbound_record", "inv_delivery_notice_detail", "inv_delivery_notice",
                "inv_sales_return_detail", "inv_sales_return", "inv_sales_detail", "inv_sales_order", "inv_purchase_return_detail", "inv_purchase_return",
                "inv_inbound_record", "inv_purchase_detail", "inv_purchase_order", "inv_stock")) jdbc.update("delete from " + table);
        jdbc.update("insert ignore into sys_dept(dept_id,dept_name) values(20,'warehouse')");
        jdbc.update("insert ignore into inv_product(product_id,product_code,product_name,cost_price) values(1,'P1','tea',0.67)");
        Configuration config = new Configuration(new Environment("b04d", new SpringManagedTransactionFactory(), manager.getDataSource()));
        config.getTypeAliasRegistry().registerAliases("com.erp.inventory.domain"); config.addInterceptor(faults);
        for (String mapper : List.of("InvPurchaseOrderMapper", "InvPurchaseDetailMapper", "InvPurchaseReturnMapper", "InvPurchaseReturnDetailMapper", "InvInboundRecordMapper",
                "InvStockMapper", "InvStockLogMapper", "InvNumberSequenceMapper", "InvDeliveryNoticeMapper", "InvDeliveryNoticeDetailMapper",
                "InvOutboundRecordMapper", "InvSalesOrderMapper", "InvSalesDetailMapper", "InvSalesReturnMapper", "InvSalesReturnDetailMapper"))
        {
            String resource = "mapper/inventory/" + mapper + ".xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
            { new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse(); }
        }
        session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)); stocks = session.getMapper(InvStockMapper.class);
        delivery = wire(new InvDeliveryNoticeServiceImpl()); purchaseReturn = wire(new InvPurchaseReturnServiceImpl());
        salesReturn = wire(new InvSalesReturnServiceImpl()); adjustment = wire(new InvStockServiceImpl()); stockCheck = wire(new InvStockCheckAdjustmentService());
        seed("3", "2.00", "0.67"); login();
    }

    @ParameterizedTest
    @CsvSource({"DELIVERY,2.00,0.67,0.66", "PURCHASE_RETURN,2.00,0.67,0.66", "STOCK_CHECK,2.00,0.67,0.66", "EXISTING_ADJUST,2.00,0.67,0.66", "ITEM_ADJUST,2.00,0.67,0.66",
            "DELIVERY,1.00,0.33,0.34", "PURCHASE_RETURN,1.00,0.33,0.34", "STOCK_CHECK,1.00,0.33,0.34", "EXISTING_ADJUST,1.00,0.33,0.34", "ITEM_ADJUST,1.00,0.33,0.34"})
    void threeBatchesUseOneExactAmountEverywhere(Pathway path, String pool, String average, String last)
    {
        cost(pool, average);
        for (int i = 0; i < 3; i++) deduct(path, "1");
        balance("0", "0");
        assertThat(jdbc.queryForList("select cost_amount from inv_stock_log order by log_id", BigDecimal.class))
                .containsExactly(bd(average), bd(average), bd(last));
        evidence(path, pool, 3);
    }

    @ParameterizedTest
    @CsvSource({"DELIVERY,2.00,0.67", "PURCHASE_RETURN,2.00,0.67", "STOCK_CHECK,2.00,0.67", "EXISTING_ADJUST,2.00,0.67", "ITEM_ADJUST,2.00,0.67",
            "DELIVERY,1.00,0.33", "PURCHASE_RETURN,1.00,0.33", "STOCK_CHECK,1.00,0.33", "EXISTING_ADJUST,1.00,0.33", "ITEM_ADJUST,1.00,0.33"})
    void wholeBatchSettlesAndNewInboundHasCorrectAverage(Pathway path, String pool, String average)
    {
        cost(pool, average); deduct(path, "3"); balance("0", "0"); evidence(path, pool, 1);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            InvStock stock = stocks.selectInvStockByIdForUpdate(1L);
            assertThat(stocks.addInvStockWithCost(1L, stock.getVersion(), bd("3"), bd("6.00"), "b04d-inbound")).isEqualTo(1);
        });
        balance("3", "6.00"); assertThat(number("select cost_price from inv_stock")).isEqualByComparingTo("2.00");
    }

    @ParameterizedTest @EnumSource(Pathway.class)
    void availableExhaustionPreservesLockedQuantityCost(Pathway path)
    {
        jdbc.update("update inv_stock set available_quantity=2,locked_quantity=1");
        deduct(path, "2"); balance("1", "0.66"); evidence(path, "1.34", 1);
        assertThat(number("select available_quantity from inv_stock")).isZero();
        assertThat(number("select locked_quantity from inv_stock")).isEqualByComparingTo("1");
        assertThatThrownBy(() -> deduct(path, "1")).isInstanceOf(ServiceException.class);
        balance("1", "0.66"); evidence(path, "1.34", 1);
    }

    @ParameterizedTest @EnumSource(Pathway.class)
    void unknownOrNegativeCostCannotMutateStock(Pathway path)
    {
        for (String invalid : List.of("total_cost=NULL", "cost_price=NULL", "total_cost=-0.01", "cost_price=-0.01"))
        {
            cost("2.00", "0.67"); jdbc.update("update inv_stock set " + invalid);
            assertThatThrownBy(() -> deduct(path, "3")).isInstanceOf(ServiceException.class).hasMessageContaining("成本");
            assertThat(number("select current_quantity from inv_stock")).isEqualByComparingTo("3");
            assertThat(number("select version from inv_stock")).isZero();
            assertThat(count("inv_stock_log")).isZero(); assertThat(count("inv_outbound_record")).isZero();
        }
    }

    @ParameterizedTest @EnumSource(Pathway.class)
    void lateLogFailureRollsBackWholeLastDeduction(Pathway path)
    {
        for (boolean returnZero : List.of(true, false))
        {
            faults.statement = "InvStockLogMapper.insertInvStockLog"; faults.returnZero = returnZero;
            assertThatThrownBy(() -> deduct(path, "3")).isInstanceOf(RuntimeException.class);
            balance("3", "2.00"); assertThat(number("select version from inv_stock")).isZero();
            assertThat(count("inv_stock_log")).isZero(); assertThat(count("inv_outbound_record")).isZero();
            assertThat(number("select delivered_cost_amount from inv_delivery_notice_detail")).isZero();
            assertThat(number("select sum(returned_quantity) from inv_purchase_return_detail")).isZero();
        }
        faults.statement = null; deduct(path, "3"); balance("0", "0"); evidence(path, "2.00", 1);
    }

    @Test void outboundZeroRowsCannotCommitDelivery()
    {
        faults.statement = "InvOutboundRecordMapper.insertInvOutboundRecord"; faults.returnZero = true;
        assertThatThrownBy(() -> deduct(Pathway.DELIVERY, "3")).isInstanceOf(ServiceException.class).hasMessageContaining("出库记录");
        balance("3", "2.00"); assertThat(count("inv_stock_log")).isZero(); assertThat(count("inv_outbound_record")).isZero();
    }

    @ParameterizedTest @CsvSource({"2.00,0.67", "1.00,0.33"})
    void actualDeliveryThenFullReturnHasZeroNetCost(String pool, String average)
    {
        cost(pool, average); for (int i=0; i<3; i++) deduct(Pathway.DELIVERY, "1");
        balance("0", "0");
        jdbc.update("insert into inv_sales_return(return_id,return_no,sales_order_id,sales_order_no,return_title,customer_name,shop_dept_id,status) values(300,'SR-300',10,'SO-10','return','customer',20,'submitted')");
        jdbc.update("insert into inv_sales_return_detail(return_id,sales_detail_id,item_type,item_id,product_id,product_name,quantity,unit_price,amount,returned_quantity,returned_cost_amount) values(300,11,'product',1,1,'tea',3,9.99,29.97,0,0)");
        salesReturn.confirmReturn(300L, 20L); balance("3", pool);
        assertThat(number("select sum(case when change_quantity<0 then -cost_amount else cost_amount end) from inv_stock_log")).isZero();
        assertThat(number("select returned_cost_amount from inv_sales_return_detail")).isEqualByComparingTo(pool);
        assertThat(jdbc.queryForObject("select status from inv_sales_return", String.class)).isEqualTo("returned");
    }

    @Test void optimisticVersionAndCostPoolSqlGuardsRemainEffective()
    {
        assertThat(stocks.deductInvStockWithCost(1L, 9L, bd("1"), bd("0.67"), "test")).isZero();
        assertThat(stocks.deductInvStockWithCost(1L, 0L, bd("4"), bd("2.00"), "test")).isZero();
        assertThat(stocks.deductInvStockWithCost(1L, 0L, bd("3"), bd("2.01"), "test")).isZero();
        assertThat(stocks.deductInvStockWithCost(1L, 0L, bd("3"), bd("1.99"), "test")).isZero();
        assertThat(stocks.deductInvStockWithCost(1L, 0L, bd("1"), bd("-0.01"), "test")).isZero();
        assertThat(stocks.deductInvStockWithCost(1L, 0L, bd("0"), bd("0"), "test")).isZero();
        balance("3", "2.00"); assertThat(number("select version from inv_stock")).isZero();
    }

    @Test void concurrentSameExpectedVersionOnlyOneAdjustmentCommits() throws Exception
    {
        List<Object> results = concurrent(() -> existing("-3", 0L), () -> existing("-3", 0L));
        assertThat(results.stream().filter(x -> x == Boolean.TRUE).count()).isEqualTo(1);
        assertThat(results.stream().filter(ServiceException.class::isInstance).count()).isEqualTo(1);
        balance("0", "0"); evidence(Pathway.EXISTING_ADJUST, "2.00", 1);
    }

    @Test void concurrentPartialAdjustmentsSerializeRemainingCost() throws Exception
    {
        List<Object> results = concurrent(() -> deduct(Pathway.ITEM_ADJUST, "1"), () -> deduct(Pathway.ITEM_ADJUST, "2"));
        assertThat(results).containsExactly(Boolean.TRUE, Boolean.TRUE);
        balance("0", "0"); evidence(Pathway.ITEM_ADJUST, "2.00", 2);
    }

    private void deduct(Pathway path, String quantity)
    {
        switch (path)
        {
            case DELIVERY -> {
                InvDeliverItem item = new InvDeliverItem(); item.setDetailId(101L); item.setDeliverQuantity(bd(quantity));
                InvDeliverRequest request = new InvDeliverRequest(); request.setWarehouseId(20L); request.setItems(List.of(item));
                delivery.deliverNotice(100L, request, 20L);
            }
            case PURCHASE_RETURN -> {
                // Each batch is a separate, already-submitted document on the same locked source order.
                long id = number("select coalesce(max(return_id),200) from inv_purchase_return").longValue();
                if (number("select coalesce(sum(returned_quantity),0) from inv_purchase_return_detail where return_id=" + id).signum() > 0) id++;
                if (jdbc.queryForObject("select count(*) from inv_purchase_return where return_id=?", Integer.class, id) == 0) seedPurchaseReturn(id);
                jdbc.update("update inv_purchase_return_detail set quantity=?,amount=?*9.99 where return_id=?", bd(quantity), bd(quantity), id);
                purchaseReturn.confirmReturn(id, 20L);
            }
            case STOCK_CHECK -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
                InvStock stock = stocks.selectInvStockByIdForUpdate(1L);
                InvStockCheck check = new InvStockCheck(); check.setCheckId(400L); check.setCheckNo("SC-400"); check.setShopDeptId(20L); check.setWarehouseId(20L);
                InvStockCheckDetail detail = new InvStockCheckDetail(); detail.setDetailId(401L); detail.setItemType("product"); detail.setItemId(1L); detail.setProductId(1L); detail.setProductName("tea");
                detail.setBookQty(stock.getCurrentQuantity()); detail.setDiffQty(bd(quantity).negate()); detail.setCostPrice(bd("99.99"));
                assertThat(stockCheck.evaluate(check, List.of(detail), true, "b04d").getAdjustments()).hasSize(1);
            });
            case EXISTING_ADJUST -> existing(bd(quantity).negate().toPlainString(), number("select version from inv_stock").longValue());
            case ITEM_ADJUST -> {
                InvStockAdjustRequest request = new InvStockAdjustRequest(); request.setItemType("product"); request.setItemId(1L); request.setProductId(1L);
                request.setShopDeptId(20L); request.setWarehouseId(20L); request.setAdjustQuantity(bd(quantity).negate()); request.setReason("cost settlement");
                adjustment.adjustStock(request, 20L);
            }
        }
    }
    private void existing(String quantity, Long version)
    {
        InvExistingStockAdjustRequest request = new InvExistingStockAdjustRequest(); request.setExpectedVersion(version);
        request.setAdjustQuantity(bd(quantity)); request.setReason("cost settlement"); adjustment.adjustExistingStock(1L, request, 20L);
    }
    private void evidence(Pathway path, String amount, int movements)
    {
        assertThat(number("select sum(cost_amount) from inv_stock_log")).isEqualByComparingTo(amount);
        assertThat(count("inv_stock_log")).isEqualTo(movements);
        if (path == Pathway.DELIVERY)
        {
            assertThat(number("select sum(cost_amount) from inv_outbound_record")).isEqualByComparingTo(amount);
            assertThat(number("select delivered_cost_amount from inv_delivery_notice_detail")).isEqualByComparingTo(amount);
            assertThat(count("inv_outbound_record")).isEqualTo(movements);
        }
        if (path == Pathway.PURCHASE_RETURN)
            assertThat(number("select sum(amount) from inv_purchase_return_detail")).isEqualByComparingTo(number("select sum(quantity)*9.99 from inv_purchase_return_detail"));
    }
    private static void balance(String quantity, String cost)
    { assertThat(number("select current_quantity from inv_stock")).isEqualByComparingTo(quantity); assertThat(number("select total_cost from inv_stock")).isEqualByComparingTo(cost); }
    private static void cost(String total, String average) { jdbc.update("update inv_stock set total_cost=?,cost_price=?", bd(total), bd(average)); }
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
    private static BigDecimal number(String sql) { return jdbc.queryForObject(sql, BigDecimal.class); }
    private static int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }
    private static void login()
    { SecurityContextHolder.setUserId("1"); SecurityContextHolder.setUserName("b04d"); LoginUser login = new LoginUser(); SysUser user = new SysUser(1L); user.setDeptId(20L); login.setSysUser(user); SecurityContextHolder.set(SecurityConstants.LOGIN_USER, login); }
    private static void seed(String quantity, String total, String average)
    {
        jdbc.update("insert into inv_stock(stock_id,item_type,item_id,product_id,shop_dept_id,warehouse_id,current_quantity,available_quantity,locked_quantity,total_cost,cost_price,version) values(1,'product',1,1,20,20,?,?,0,?,?,0)", bd(quantity), bd(quantity), bd(total), bd(average));
        jdbc.update("insert into inv_purchase_order(order_id,order_no,supplier_name,shop_dept_id,status,qc_status) values(20,'PO-20','supplier',20,'received','passed')");
        jdbc.update("insert into inv_purchase_detail(detail_id,order_id,item_type,item_id,product_id,product_name,warehouse_id,quantity,unit_price,amount,received_quantity) values(21,20,'product',1,1,'tea',20,3,9.99,29.97,3)");
        jdbc.update("insert into inv_inbound_record(purchase_order_id,purchase_detail_id,product_id,quantity,qc_result,receipt_batch_detail_id,accepted_quantity,concession_quantity,rejected_quantity,inspected_quantity) values(20,21,1,3,'passed',21,3,0,0,3)");
        seedPurchaseReturn(200L);
        jdbc.update("insert into inv_sales_order(order_id,order_no,shop_dept_id,applicant_id,status) values(10,'SO-10',20,1,'noticed')");
        jdbc.update("insert into inv_sales_detail(detail_id,order_id,item_type,item_id,product_id,product_name,warehouse_id,quantity,delivered_quantity) values(11,10,'product',1,1,'tea',20,3,0)");
        jdbc.update("insert into inv_delivery_notice(notice_id,notice_no,sales_order_id,sales_order_no,status,shop_dept_id,warehouse_id) values(100,'DN-100',10,'SO-10','pending',20,20)");
        jdbc.update("insert into inv_delivery_notice_detail(detail_id,notice_id,sales_detail_id,warehouse_id,item_type,item_id,product_id,product_name,notice_qty,delivered_qty,delivered_cost_amount) values(101,100,11,20,'product',1,1,'tea',3,0,0)");
    }
    private static void seedPurchaseReturn(long id)
    {
        jdbc.update("insert into inv_purchase_return(return_id,return_no,purchase_order_id,purchase_order_no,return_title,supplier_name,return_date,return_reason,responsibility,shop_dept_id,status) values(?, ?,20,'PO-20','return','supplier','2026-09-12','quality','supplier',20,'submitted')", id, "PR-"+id);
        jdbc.update("insert into inv_purchase_return_detail(return_id,purchase_detail_id,item_type,item_id,product_id,product_name,quantity,unit_price,amount,returned_quantity) values(?,21,'product',1,1,'tea',3,9.99,29.97,0)", id);
    }
    private <T> T wire(T target)
    {
        for (Class<?> type=target.getClass(); type!=null; type=type.getSuperclass())
            for (java.lang.reflect.Field field : type.getDeclaredFields())
                if (session.getConfiguration().hasMapper(field.getType())) ReflectionTestUtils.setField(target, field.getName(), session.getMapper(field.getType()));
        if (!(target instanceof InvStockCheckAdjustmentService))
        {
            InvDeptScopeMapper scopes = mock(InvDeptScopeMapper.class);
            when(scopes.selectDeptTypeById(anyLong())).thenReturn("WAREHOUSE");
            when(scopes.countDeptInScope(anyLong(), anyLong())).thenAnswer(c -> Objects.equals(c.getArgument(0), c.getArgument(1)) ? 1 : 0);
            ReflectionTestUtils.setField(target, "deptScopeMapper", scopes);
        }
        if (target instanceof InvStockServiceImpl)
        {
            InventoryItemResolver catalog = mock(InventoryItemResolver.class);
            InventoryItemSnapshot item = new InventoryItemSnapshot(); item.setItemType("product"); item.setItemId(1L); item.setProductId(1L); item.setCostPrice(bd("99.99"));
            when(catalog.resolve("product", 1L, 1L)).thenReturn(item); ReflectionTestUtils.setField(target, "itemResolver", catalog);
        }
        ProxyFactory proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }
    private List<Object> concurrent(Runnable first, Runnable second) throws Exception
    {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch barrier = new CountDownLatch(2);
        try
        {
            List<Future<Object>> futures = new ArrayList<>();
            for (Runnable job : List.of(first, second)) futures.add(pool.submit(() -> {
                login(); barrier.countDown(); if (!barrier.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("barrier");
                try { job.run(); return Boolean.TRUE; } catch (RuntimeException failure) { return failure; } finally { SecurityContextHolder.remove(); }
            }));
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
        finally { pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }
    private static String resource(String name) throws Exception
    { try (InputStream input = InvStockCostSettlementMySqlIT.class.getClassLoader().getResourceAsStream(name)) { return new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8); } }
    private static void execute(String sql) throws Exception
    {
        try (Connection connection = Objects.requireNonNull(manager.getDataSource()).getConnection(); Statement statement = connection.createStatement())
        {
            StringBuilder pending = new StringBuilder();
            for (String raw : sql.split("\\R"))
            {
                String line = raw.trim(); if (line.isEmpty() || line.startsWith("--")) continue;
                pending.append(raw).append('\n');
                if (line.endsWith(";")) { String command = pending.toString().trim(); statement.execute(command.substring(0,command.length()-1)); pending.setLength(0); }
            }
            assertThat(pending.toString().trim()).isEmpty();
        }
    }
    @Intercepts(@Signature(type=Executor.class, method="update", args={MappedStatement.class,Object.class}))
    static class Faults implements Interceptor
    {
        String statement; boolean returnZero;
        @Override public Object intercept(Invocation call) throws Throwable
        {
            if (statement != null && ((MappedStatement)call.getArgs()[0]).getId().endsWith(statement))
            { if (returnZero) return 0; throw new ServiceException("B04D injected late write failure"); }
            return call.proceed();
        }
    }
}
