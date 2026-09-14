package com.erp.inventory.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.domain.*;
import com.erp.inventory.domain.dto.*;
import com.erp.inventory.mapper.*;
import com.erp.inventory.service.impl.InvPurchaseServiceImpl;
import com.erp.inventory.service.impl.InvQualityCommandExecutor;

/** Real business mappers and one Spring transaction; only organization services are synthetic. */
@Testcontainers(disabledWithoutDocker = false)
@Execution(ExecutionMode.SAME_THREAD)
class InvPurchaseReceiveMultiBatchMySqlIT
{
    private static final Map<String, MySQLContainer> DATABASES = new LinkedHashMap<>();
    private static final String RECEIPT_MIGRATION = "erp_inventory_receipt_quality_20260713.sql";
    private static final String COMMAND_MIGRATION = "erp_inventory_quality_command_20260909.sql";
    private static final List<String> FACT_TABLES = List.of("inv_quality_inspection_attachment",
            "inv_quality_inspection", "inv_stock_log", "inv_stock", "inv_inbound_record",
            "inv_receipt_batch_detail", "inv_receipt_batch", "inv_quality_command",
            "inv_number_sequence", "inv_purchase_detail", "inv_purchase_order");

    static java.util.stream.Stream<String> versions()
    {
        String version = System.getProperty("b04a.mysql.version");
        if (version == null) return java.util.stream.Stream.of("mysql:5.7.44", "mysql:8.0.36");
        if (!List.of("5.7.44", "8.0.36").contains(version))
            throw new IllegalArgumentException("Unsupported isolated MySQL test version");
        return java.util.stream.Stream.of("mysql:" + version);
    }

    @BeforeAll static void startFreshIsolatedDatabases() throws Exception
    {
        try
        {
            for (String image : versions().toList())
            {
                MySQLContainer mysql = new MySQLContainer(image).withDatabaseName("b04a_receipts")
                        .withUsername("b04a_it").withPassword(UUID.randomUUID().toString())
                        .withReuse(false).withLabels(Map.of("erp.task", "b04a"))
                        .withTmpFs(Map.of("/var/lib/mysql", "rw,size=1g"))
                        .withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
                        .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci",
                                "--log-bin-trust-function-creators=1");
                DATABASES.put(image, mysql); mysql.start();
                assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
                try (Connection c = connection(mysql))
                {
                    executeScript(c, resource("purchase-receive-b04a-baseline.sql"));
                    migrateTwice(c);
                    try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("select version()"))
                    {
                        assertThat(r.next()).isTrue();
                        assertThat(r.getString(1)).startsWith(image.substring("mysql:".length()));
                        System.out.printf("B04A DB image=%s container=%s host=%s port=%d version=%s%n",
                                image, mysql.getContainerId(), mysql.getHost(), mysql.getMappedPort(3306), r.getString(1));
                    }
                }
            }
        }
        catch (Throwable failure)
        {
            stopOwnedDatabases(); throw failure;
        }
    }

    @AfterAll static void stopOwnedDatabases()
    {
        for (var entry : DATABASES.entrySet())
        {
            String id = entry.getValue().getContainerId();
            entry.getValue().stop();
            assertThat(entry.getValue().isRunning()).isFalse();
            System.out.printf("B04A cleaned image=%s container=%s%n", entry.getKey(), id);
        }
    }

    @AfterEach void clearIdentity() { SecurityContextHolder.remove(); }

    @ParameterizedTest @MethodSource("versions")
    void sameIdConcurrentCreatesOneBatchAndNoAvailableStock(String image) throws Exception
    {
        Fixture f = fixture(image, "100");
        List<Object> results = concurrent(f, () -> f.receive("same-receipt-01", "20"),
                () -> f.receive("same-receipt-01", "20"));
        assertThat(results).allMatch(r -> r instanceof InvPurchaseReceiveResult);
        assertThat(((InvPurchaseReceiveResult) results.get(0)).getReceiptBatchId())
                .isEqualTo(((InvPurchaseReceiveResult) results.get(1)).getReceiptBatchId());
        f.count("inv_receipt_batch", 1); f.count("inv_receipt_batch_detail", 1);
        f.count("inv_inbound_record", 1); f.count("inv_quality_command", 1);
        f.received("20"); f.stock("0"); f.assertBalanced();
    }

    @ParameterizedTest @MethodSource("versions")
    void droppedResponseReplaysOriginalAfterRealQcAndRebuiltService(String image) throws Exception
    {
        Fixture f = fixture(image, "20");
        var original = f.receive("lost-response-01", "20");
        String saved = f.jdbc.queryForObject("select result_payload from inv_quality_command where request_id='lost-response-01'", String.class);
        f.qc(original, "qc-complete-01", "20", "0", "0");
        assertThat(f.status()).isEqualTo("received:passed");
        Fixture restarted = new Fixture(DATABASES.get(image));
        var replay = restarted.receive("lost-response-01", "20");
        assertThat(new ObjectMapper().writeValueAsString(replay))
                .isEqualTo(new ObjectMapper().writeValueAsString(original));
        assertThat(f.jdbc.queryForObject("select result_payload from inv_quality_command where request_id='lost-response-01'", String.class)).isEqualTo(saved);
        assertThatThrownBy(() -> restarted.receive("new-after-full-01", "20")).isInstanceOf(ServiceException.class);
        f.count("inv_receipt_batch", 1); f.count("inv_quality_inspection", 1); f.count("inv_stock_log", 1);
        f.received("20"); f.stock("20"); f.assertBalanced();
    }

    @ParameterizedTest @MethodSource("versions")
    void sameIdentityCannotChangePayloadActorOrderOrWarehouse(String image)
    {
        Fixture f = fixture(image, "100"); f.receive("identity-receipt-01", "20");
        List<java.util.function.Consumer<InvReceiveRequest>> changes = List.of(
                r -> r.getItems().get(0).setReceiveQuantity(new BigDecimal("21")),
                r -> r.setArrivedTime(new java.util.Date(1)), r -> r.setRemark("other"),
                r -> r.setSupplierBatchNo("other"), r -> r.setDeliveryNoteNo("other"),
                r -> r.setWarehouseId(30L));
        for (var change : changes)
        {
            var request = receiveRequest("20"); change.accept(request);
            assertThatThrownBy(() -> f.service.receivePurchase(10L, request, 20L, "identity-receipt-01"))
                    .isInstanceOf(ServiceException.class);
        }
        login(78);
        assertThatThrownBy(() -> f.receive("identity-receipt-01", "20")).isInstanceOf(ServiceException.class);
        login(77);
        f.seedOrder(11, 211, "100");
        assertThatThrownBy(() -> f.service.receivePurchase(11L, receiveRequest("20"), 20L, "identity-receipt-01"))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> f.service.receivePurchase(10L, receiveRequest("20"), 30L, "identity-receipt-01"))
                .isInstanceOf(ServiceException.class);
        for (String bad : Arrays.asList(null, "", "short", "invalid request id"))
            assertThatThrownBy(() -> f.receive(bad, "20")).isInstanceOf(ServiceException.class);
        f.count("inv_receipt_batch", 1); f.count("inv_quality_command", 1); f.received("20"); f.stock("0");
    }

    @ParameterizedTest @MethodSource("versions")
    void lateFailuresRollbackCommandSequenceAndAllReceiptFacts(String image)
    {
        for (String statement : List.of("insertInvReceiptBatch", "insertInvReceiptBatchDetail",
                "insertInvInboundRecord", "updateInvPurchaseDetail", "completeIfPending"))
        {
            Fixture f = fixture(image, "100"); f.faults.after.set(statement);
            assertThatThrownBy(() -> f.receive("rollback-receipt-01", "20"))
                    .isInstanceOf(org.mybatis.spring.MyBatisSystemException.class)
                    .hasRootCauseInstanceOf(ServiceException.class).rootCause().hasMessageContaining("B04A injected");
            for (String table : FACT_TABLES.subList(0, FACT_TABLES.size() - 2)) f.count(table, 0);
            f.received("0");
            f.receive("rollback-receipt-01", "20");
            f.count("inv_receipt_batch", 1); f.count("inv_quality_command", 1); f.received("20");
        }
    }

    @ParameterizedTest @MethodSource("versions")
    void multiplePendingBatchesAndPartialQcOnlyPostAcceptedDeltas(String image)
    {
        Fixture f = fixture(image, "50");
        var a = f.receive("multi-receipt-a", "20"); var b = f.receive("multi-receipt-b", "30");
        f.count("inv_receipt_batch", 2); f.received("50"); f.stock("0");
        var crossBatch = f.qcRequest(b, "cross-real-batch-detail", "1", "0", "0");
        crossBatch.setReceiptBatchId(a.getReceiptBatchId());
        assertThatThrownBy(() -> f.service.qualityCheckBatch(10L, crossBatch, 20L))
                .isInstanceOf(ServiceException.class).hasMessageContaining("不属于当前收货批次");
        f.count("inv_quality_command", 2); f.count("inv_quality_inspection", 0);
        assertThat(f.decimal("select sum(pending_quantity) from inv_receipt_batch")).isEqualByComparingTo("50");
        f.qc(a, "multi-qc-a", "20", "0", "0"); f.stock("20");
        assertThat(f.status()).isEqualTo("submitted:pending");
        var partial = f.qcRequest(b, "multi-qc-b1", "8", "2", "0");
        partial.getItems().get(0).setAttachmentUrls(List.of("/file/public/b04a.jpg"));
        f.service.qualityCheckBatch(10L, partial, 20L);
        f.service.qualityCheckBatch(10L, partial, 20L);
        f.stock("28"); f.received("48"); f.count("inv_quality_inspection", 2);
        f.count("inv_quality_inspection_attachment", 1); f.count("inv_stock_log", 2);
        assertThat(f.decimal("select pending_quantity from inv_receipt_batch where batch_id=" + b.getReceiptBatchId())).isEqualByComparingTo("20");
        f.qc(b, "multi-qc-b2", "10", "5", "5");
        f.stock("43"); f.received("43"); assertThat(f.status()).isEqualTo("submitted:concession");
        assertThat(f.decimal("select sum(total_cost) from inv_stock")).isEqualByComparingTo("107.5");
        f.count("inv_quality_inspection", 3); f.count("inv_stock_log", 3); f.assertBalanced();
    }

    @ParameterizedTest @MethodSource("versions")
    void unselectedLineStaysPendingAndInvalidBatchInputsWriteNothing(String image)
    {
        Fixture f = fixture(image, "10");
        f.jdbc.update("insert into inv_purchase_detail(detail_id,order_id,item_type,item_id,product_id,warehouse_id,quantity,unit_price) values (202,10,'product',2,2,20,20,2.5)");
        var r = receiveRequest("10");
        var second = new InvReceiveItem(); second.setDetailId(202L); second.setReceiveQuantity(new BigDecimal("20"));
        r.setItems(List.of(r.getItems().get(0), second));
        var batch = f.service.receivePurchase(10L, r, 20L, "two-lines-receipt");
        f.qc(batch, "line-one-partial", "5", "0", "0");
        f.stock("5");
        assertThat(f.decimal("select pending_quantity from inv_receipt_batch_detail where purchase_detail_id=202")).isEqualByComparingTo("20");
        var bad = f.qcRequest(batch, "bad-detail-id", "1", "0", "0"); bad.getItems().get(0).setBatchDetailId(999L);
        assertThatThrownBy(() -> f.service.qualityCheckBatch(10L, bad, 20L)).isInstanceOf(ServiceException.class);
        var over = f.qcRequest(batch, "over-pending-id", "6", "0", "0");
        assertThatThrownBy(() -> f.service.qualityCheckBatch(10L, over, 20L)).isInstanceOf(ServiceException.class);
        var wrongBatch = f.qcRequest(batch, "wrong-batch-id", "1", "0", "0"); wrongBatch.setReceiptBatchId(999L);
        assertThatThrownBy(() -> f.service.qualityCheckBatch(10L, wrongBatch, 20L)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> f.service.qualityCheckBatch(10L, over, 30L)).isInstanceOf(ServiceException.class);
        f.count("inv_quality_inspection", 1); f.count("inv_stock_log", 1); f.count("inv_quality_command", 2);
        f.stock("5"); f.assertBalanced();
    }

    @ParameterizedTest @MethodSource("versions")
    void completionOrderDoesNotChangeWholeOrderConclusion(String image)
    {
        String expected = null;
        for (boolean reverse : List.of(false, true))
        {
            Fixture f = fixture(image, "50");
            var a = f.receive("order-receipt-a", "20"); var b = f.receive("order-receipt-b", "30");
            if (reverse) f.qc(b, "order-qc-b", "15", "10", "5");
            f.qc(a, "order-qc-a", "20", "0", "0");
            if (!reverse) f.qc(b, "order-qc-b", "15", "10", "5");
            f.received("40"); f.stock("40"); assertThat(f.status()).isEqualTo("submitted:concession");
            String snapshot = f.status() + ":" + f.decimal("select sum(total_cost) from inv_stock");
            if (expected == null) expected = snapshot; else assertThat(snapshot).isEqualTo(expected);
            f.assertBalanced();
        }
    }

    @ParameterizedTest @MethodSource("versions")
    void oldRepeatableReadSnapshotCannotOverReceiveAfterWaitingForOrderLock(String image) throws Exception
    {
        Fixture f = fixture(image, "30");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicReference<Future<Object>> waiting = new AtomicReference<>();
        AtomicReference<BigDecimal> oldSnapshot = new AtomicReference<>();
        CountDownLatch snapshotRead = new CountDownLatch(1);
        Set<Long> connections = ConcurrentHashMap.newKeySet();
        try
        {
            f.tx.execute(status -> {
                connections.add(f.connectionId());
                f.jdbc.queryForObject("select order_id from inv_purchase_order where order_id=10 for update", Long.class);
                waiting.set(worker.submit(() -> {
                    login(77);
                    try
                    {
                        return f.tx.execute(other -> {
                            connections.add(f.connectionId());
                            oldSnapshot.set(f.decimal("select received_quantity from inv_purchase_detail where detail_id=201"));
                            snapshotRead.countDown();
                            return f.receive("remaining-second", "20");
                        });
                    }
                    catch (ServiceException rejected) { return rejected; }
                    finally { SecurityContextHolder.remove(); }
                }));
                await(snapshotRead); f.receive("remaining-first", "20"); return null;
            });
            assertThat(waiting.get().get(20, TimeUnit.SECONDS)).isInstanceOf(ServiceException.class);
            assertThat(oldSnapshot.get()).isEqualByComparingTo("0"); assertThat(connections).hasSize(2);
            f.count("inv_receipt_batch", 1); f.count("inv_quality_command", 1); f.received("20"); f.stock("0");
        }
        finally { worker.shutdownNow(); }
    }

    @ParameterizedTest @MethodSource("versions")
    void receivingAndInspectingConcurrentKeepOneRejectionDelta(String image) throws Exception
    {
        Fixture f = fixture(image, "50"); var a = f.receive("interleave-receipt-a", "20");
        var outcomes = concurrent(f, () -> f.receive("interleave-receipt-b", "30"), () -> {
            f.qc(a, "interleave-qc-a", "15", "5", "0"); return Boolean.TRUE;
        });
        assertThat(outcomes).noneMatch(Throwable.class::isInstance);
        f.qc(a, "interleave-qc-a", "15", "5", "0");
        f.received("45"); f.stock("15"); f.count("inv_quality_inspection", 1);
        assertThat(f.status()).isEqualTo("submitted:pending"); f.assertBalanced();
    }

    @ParameterizedTest @MethodSource("versions")
    void legacyWholeOrderNeverTakesOverNewBatchesAndStillReplaysHistory(String image)
    {
        Fixture f = fixture(image, "47");
        f.jdbc.update("update inv_purchase_detail set received_quantity=7 where detail_id=201");
        f.jdbc.update("insert into inv_inbound_record(purchase_order_id,purchase_detail_id,order_no,item_type,item_id,product_id,shop_dept_id,warehouse_id,quantity,qc_result) values (10,201,'PO-B04A','product',1,1,20,20,7,'pending')");
        f.jdbc.update("insert into inv_inbound_record(purchase_order_id,purchase_detail_id,order_no,item_type,item_id,product_id,shop_dept_id,warehouse_id,quantity,qc_result) values (10,201,'PO-B04A','product',1,1,20,20,3,'rejected')");
        var a = f.receive("legacy-mix-a", "20");
        f.service.qualityCheckWithRequest(10L, "passed", "legacy only", 20L, "legacy-historical-qc");
        f.stock("7"); f.count("inv_quality_inspection", 0); assertThat(f.status()).isEqualTo("submitted:pending");
        f.qc(a, "legacy-mix-qc-a", "20", "0", "0");
        var b = f.receive("legacy-mix-b", "20");
        assertThatThrownBy(() -> f.service.qualityCheckWithRequest(10L, "passed", "old confirm", 20L, "legacy-stale-confirm"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("请选择具体收货批次");
        f.service.qualityCheckWithRequest(10L, "passed", "legacy only", 20L, "legacy-historical-qc");
        f.stock("27"); f.count("inv_quality_inspection", 1);
        assertThat(f.decimal("select pending_quantity from inv_receipt_batch where batch_id=" + b.getReceiptBatchId())).isEqualByComparingTo("20");
        f.qc(b, "legacy-mix-qc-b", "20", "0", "0");
        f.stock("47"); assertThat(f.status()).isEqualTo("received:concession");
        assertThat(f.jdbc.queryForObject("select count(*) from inv_inbound_record where receipt_batch_id is null and qc_result='rejected' and quantity=3", Integer.class)).isEqualTo(1);
        f.assertBalanced();
    }

    @ParameterizedTest @MethodSource("versions")
    void qcLateFailureRollsBackStockEventsAttachmentsAndRejectedQuantity(String image)
    {
        Fixture f = fixture(image, "20"); var a = f.receive("qc-rollback-receipt", "20");
        var qc = f.qcRequest(a, "qc-rollback-command", "15", "5", "0");
        qc.getItems().get(0).setAttachmentUrls(List.of("/file/public/evidence.jpg"));
        f.faults.after.set("updateInspectionProgress");
        assertThatThrownBy(() -> f.service.qualityCheckBatch(10L, qc, 20L))
                .isInstanceOf(org.mybatis.spring.MyBatisSystemException.class)
                .hasRootCauseInstanceOf(ServiceException.class).rootCause().hasMessageContaining("B04A injected");
        f.count("inv_quality_inspection", 0); f.count("inv_quality_inspection_attachment", 0);
        f.count("inv_stock_log", 0); f.count("inv_quality_command", 1); f.received("20"); f.stock("0");
        f.service.qualityCheckBatch(10L, qc, 20L); f.received("15"); f.stock("15"); f.assertBalanced();
    }

    @ParameterizedTest @MethodSource("versions")
    void migrationsRemainIdempotentWithExistingBusinessFacts(String image) throws Exception
    {
        Fixture f = fixture(image, "20"); var a = f.receive("migration-receipt", "20");
        f.qc(a, "migration-qc", "20", "0", "0");
        List<Integer> before = FACT_TABLES.stream().map(f::count).toList();
        long indexes = f.jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema=database()", Long.class);
        try (Connection c = connection(DATABASES.get(image))) { migrateTwice(c); }
        assertThat(FACT_TABLES.stream().map(f::count).toList()).isEqualTo(before);
        assertThat(f.jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema=database()", Long.class)).isEqualTo(indexes);
        assertThat(Files.readAllBytes(projectFile("docker/mysql/db/" + RECEIPT_MIGRATION)))
                .isEqualTo(Files.readAllBytes(projectFile("sql/" + RECEIPT_MIGRATION)));
        System.out.println("B04A migration sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(projectFile("sql/" + RECEIPT_MIGRATION)))));
        f.received("20"); f.stock("20");
    }

    private static Fixture fixture(String image, String quantity)
    {
        login(77); Fixture f = new Fixture(DATABASES.get(image));
        FACT_TABLES.forEach(t -> f.jdbc.update("delete from " + t)); f.seedOrder(10, 201, quantity); return f;
    }

    private static void login(long id)
    {
        SecurityContextHolder.setUserId(Long.toString(id)); SecurityContextHolder.setUserName("b04a-" + id);
    }

    private static List<Object> concurrent(Fixture f, Supplier<Object> first, Supplier<Object> second) throws Exception
    {
        ExecutorService workers = Executors.newFixedThreadPool(2); CyclicBarrier ready = new CyclicBarrier(2);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        try
        {
            List<Future<Object>> futures = new ArrayList<>();
            for (Supplier<Object> action : List.of(first, second)) futures.add(workers.submit(() -> {
                login(77);
                try { return f.tx.execute(s -> { ids.add(f.connectionId()); barrier(ready); return action.get(); }); }
                catch (Throwable failure) { return failure; }
                finally { SecurityContextHolder.remove(); }
            }));
            List<Object> values = List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
            assertThat(ids).hasSize(2); return values;
        }
        finally { workers.shutdownNow(); }
    }

    private static void await(CountDownLatch latch)
    {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
    private static void barrier(CyclicBarrier barrier)
    {
        try { barrier.await(10, TimeUnit.SECONDS); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static InvReceiveRequest receiveRequest(String quantity)
    {
        var item = new InvReceiveItem(); item.setDetailId(201L); item.setReceiveQuantity(new BigDecimal(quantity));
        var request = new InvReceiveRequest(); request.setWarehouseId(20L); request.setItems(List.of(item));
        request.setArrivedTime(new java.util.Date(1752637800000L)); return request;
    }

    private static class Fixture
    {
        final JdbcTemplate jdbc;
        final TransactionTemplate tx;
        final Faults faults = new Faults();
        final InvPurchaseServiceImpl service;
        Fixture(MySQLContainer mysql)
        {
            DataSource source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            jdbc = new JdbcTemplate(source);
            DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
            tx = new TransactionTemplate(manager); tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            Configuration config = new Configuration(new Environment("b04a", new SpringManagedTransactionFactory(), source));
            config.getTypeAliasRegistry().registerAliases("com.erp.inventory.domain"); config.addInterceptor(faults);
            for (String name : List.of("InvPurchaseDetail", "InvPurchaseOrder", "InvReceiptBatch", "InvReceiptBatchDetail",
                    "InvInboundRecord", "InvQualityInspection", "InvQualityInspectionAttachment", "InvStock", "InvStockLog",
                    "InvNumberSequence", "InvQualityCommand"))
            {
                String resource = "mapper/inventory/" + name + "Mapper.xml";
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource))
                { new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse(); }
                catch (Exception failure) { throw new IllegalStateException(resource, failure); }
            }
            SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
            InvPurchaseServiceImpl target = new InvPurchaseServiceImpl();
            for (Class<?> mapper : List.of(InvPurchaseOrderMapper.class, InvPurchaseDetailMapper.class,
                    InvReceiptBatchMapper.class, InvReceiptBatchDetailMapper.class, InvInboundRecordMapper.class,
                    InvQualityInspectionMapper.class, InvQualityInspectionAttachmentMapper.class,
                    InvStockMapper.class, InvStockLogMapper.class, InvNumberSequenceMapper.class))
            {
                String name = mapper.getSimpleName().substring(3);
                ReflectionTestUtils.setField(target, Character.toLowerCase(name.charAt(0)) + name.substring(1), session.getMapper(mapper));
            }
            var departments = mock(InvDeptScopeMapper.class);
            when(departments.selectDeptTypeById(anyLong())).thenReturn("WAREHOUSE");
            when(departments.countDeptInScope(anyLong(), anyLong())).thenAnswer(c -> Objects.equals(c.getArgument(0), c.getArgument(1)) ? 1 : 0);
            var scope = mock(ShopScopeService.class);
            when(scope.resolveRequiredShopDept(anyLong())).thenAnswer(c -> c.getArgument(0));
            when(scope.hasUserShopScope(anyLong(), anyLong())).thenReturn(true);
            ReflectionTestUtils.setField(target, "deptScopeMapper", departments);
            ReflectionTestUtils.setField(target, "shopScopeService", scope);
            ReflectionTestUtils.setField(target, "qualityCommandExecutor", proxy(new InvQualityCommandExecutor(
                    session.getMapper(InvQualityCommandMapper.class), new ObjectMapper()), manager));
            service = proxy(target, manager);
        }
        void seedOrder(long order, long detail, String quantity)
        {
            jdbc.update("insert into inv_purchase_order(order_id,order_no,shop_dept_id,status) values (?, ?,20,'submitted')", order, "PO-B04A-" + order);
            jdbc.update("insert into inv_purchase_detail(detail_id,order_id,item_type,item_id,product_id,warehouse_id,quantity,unit_price,received_quantity) values (?,?,'product',1,1,20,?,2.5,0)", detail, order, new BigDecimal(quantity));
        }
        InvPurchaseReceiveResult receive(String id, String quantity) { return service.receivePurchase(10L, receiveRequest(quantity), 20L, id); }
        InvQualityCheckRequest qcRequest(InvPurchaseReceiveResult batch, String id, String accepted, String rejected, String concession)
        {
            var item = new InvQualityCheckItem();
            item.setBatchDetailId(jdbc.queryForObject("select min(batch_detail_id) from inv_receipt_batch_detail where batch_id=?", Long.class, batch.getReceiptBatchId()));
            item.setAcceptedQuantity(new BigDecimal(accepted)); item.setRejectedQuantity(new BigDecimal(rejected));
            item.setConcessionQuantity(new BigDecimal(concession));
            item.setInspectedQuantity(item.getAcceptedQuantity().add(item.getRejectedQuantity()).add(item.getConcessionQuantity()));
            item.setDefectReason("synthetic inspection reason");
            var request = new InvQualityCheckRequest(); request.setReceiptBatchId(batch.getReceiptBatchId());
            request.setRequestId(id); request.setItems(List.of(item)); return request;
        }
        void qc(InvPurchaseReceiveResult batch, String id, String accepted, String rejected, String concession)
        { service.qualityCheckBatch(10L, qcRequest(batch, id, accepted, rejected, concession), 20L); }
        int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }
        void count(String table, int expected) { assertThat(count(table)).as(table).isEqualTo(expected); }
        BigDecimal decimal(String sql) { return jdbc.queryForObject(sql, BigDecimal.class); }
        void received(String quantity) { assertThat(decimal("select received_quantity from inv_purchase_detail where detail_id=201")).isEqualByComparingTo(quantity); }
        void stock(String quantity) { assertThat(decimal("select coalesce(sum(available_quantity),0) from inv_stock")).isEqualByComparingTo(quantity); }
        String status() { return jdbc.queryForObject("select concat(status,':',qc_status) from inv_purchase_order where order_id=10", String.class); }
        long connectionId()
        {
            String version = jdbc.queryForObject("select version()", String.class);
            String variable = version.startsWith("5.7") ? "@@tx_isolation" : "@@transaction_isolation";
            assertThat(jdbc.queryForObject("select " + variable, String.class)).isEqualTo("REPEATABLE-READ");
            long id = jdbc.queryForObject("select connection_id()", Long.class);
            System.out.printf("B04A transaction connection=%d isolation=REPEATABLE-READ%n", id); return id;
        }
        void assertBalanced()
        {
            assertThat(jdbc.queryForObject("select count(*) from inv_receipt_batch_detail where received_quantity <> pending_quantity+inspected_quantity or inspected_quantity <> accepted_quantity+rejected_quantity+concession_quantity", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from inv_receipt_batch b where pending_quantity <> (select coalesce(sum(d.pending_quantity),0) from inv_receipt_batch_detail d where d.batch_id=b.batch_id)", Integer.class)).isZero();
            System.out.printf("B04A facts batches=%d details=%d inbound=%d inspections=%d commands=%d stock=%s status=%s%n",
                    count("inv_receipt_batch"), count("inv_receipt_batch_detail"), count("inv_inbound_record"),
                    count("inv_quality_inspection"), count("inv_quality_command"), decimal("select coalesce(sum(available_quantity),0) from inv_stock"), status());
        }
    }

    @Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
    private static class Faults implements Interceptor
    {
        final AtomicReference<String> after = new AtomicReference<>();
        @Override public Object intercept(Invocation invocation) throws Throwable
        {
            Object result = invocation.proceed(); String requested = after.get();
            if (requested != null && ((MappedStatement) invocation.getArgs()[0]).getId().endsWith("." + requested)
                    && after.compareAndSet(requested, null)) throw new ServiceException("B04A injected after " + requested);
            return result;
        }
    }

    @SuppressWarnings("unchecked") private static <T> T proxy(T target, DataSourceTransactionManager manager)
    {
        ProxyFactory proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }
    private static Connection connection(MySQLContainer mysql) throws SQLException
    { return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()); }
    private static Path projectFile(String relative)
    {
        Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("sql/" + RECEIPT_MIGRATION))) root = root.getParent();
        if (root == null) throw new IllegalStateException("Missing project SQL root"); return root.resolve(relative);
    }
    private static String resource(String name) throws Exception
    {
        try (InputStream in = InvPurchaseReceiveMultiBatchMySqlIT.class.getClassLoader().getResourceAsStream(name))
        { return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8); }
    }
    private static void migrateTwice(Connection c) throws Exception
    {
        for (int pass = 0; pass < 2; pass++) for (String name : List.of(RECEIPT_MIGRATION, COMMAND_MIGRATION))
            executeScript(c, Files.readString(projectFile("sql/" + name)));
    }
    private static void executeScript(Connection c, String sql) throws SQLException
    {
        String delimiter = ";"; StringBuilder pending = new StringBuilder();
        try (Statement executor = c.createStatement())
        {
            for (String line : sql.split("\\R", -1))
            {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.startsWith("#")) continue;
                if (trimmed.toUpperCase(Locale.ROOT).startsWith("DELIMITER "))
                { delimiter = trimmed.substring(10).trim(); continue; }
                pending.append(line).append('\n'); int end;
                while ((end = pending.indexOf(delimiter)) >= 0)
                {
                    String statement = pending.substring(0, end).trim(); pending.delete(0, end + delimiter.length());
                    if (!statement.isEmpty()) executor.execute(statement);
                }
            }
            if (!pending.toString().isBlank()) executor.execute(pending.toString());
        }
    }
}
