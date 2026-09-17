package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.controller.InvPurchaseController;
import com.erp.inventory.domain.*;
import com.erp.inventory.domain.dto.InvQualityCheckItem;
import com.erp.inventory.domain.dto.InvQualityCheckRequest;
import com.erp.inventory.mapper.*;

/**
 * Real purchase service, command executor, mapper and Spring transaction proxies.
 * Purchase metadata is isolated in mocks; their inspection/stock writes use the
 * same native MySQL transaction as the command. No existing business tables are used.
 */
@EnabledIfEnvironmentVariable(named = "ERP_REPAIR_NATIVE_MYSQL", matches = "1")
class InvPurchaseQualityCommandWiringMysqlTest
{
    private static JdbcTemplate admin, jdbc;
    private static String database;
    private static DataSourceTransactionManager transactions;
    private static InvQualityCommandMapper commandMapper;

    @BeforeAll static void createScratchDatabase() throws Exception
    {
        String base = "jdbc:mysql://127.0.0.1:3306/";
        String options = "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai";
        admin = new JdbcTemplate(new UnpooledDataSource("com.mysql.cj.jdbc.Driver", base + "mysql" + options, "root", ""));
        database = "erp_qc_wiring_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create database " + database);
        UnpooledDataSource ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", base + database + options, "root", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute(Files.readString(Path.of("../../sql/erp_inventory_quality_command_20260909.sql")));
        jdbc.execute("create table wiring_fact (id bigint primary key auto_increment, kind varchar(32), quantity decimal(16,4)) engine=InnoDB");
        transactions = new DataSourceTransactionManager(ds);
        Configuration config = new Configuration(new Environment("wiring", new SpringManagedTransactionFactory(), ds));
        config.getTypeAliasRegistry().registerAlias("InvQualityCommand", InvQualityCommand.class);
        String resource = "mapper/inventory/InvQualityCommandMapper.xml";
        try (InputStream in = InvPurchaseQualityCommandWiringMysqlTest.class.getClassLoader().getResourceAsStream(resource))
        {
            new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
        }
        commandMapper = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(InvQualityCommandMapper.class);
    }

    @AfterAll static void dropScratchDatabase()
    {
        if (database != null && database.matches("erp_qc_wiring_[0-9a-f]{32}")) admin.execute("drop database " + database);
    }

    @BeforeEach void reset()
    {
        jdbc.update("delete from inv_quality_command");
        jdbc.update("delete from wiring_fact");
        SecurityContextHolder.setUserId("77");
        SecurityContextHolder.setUserName("qc-wiring-test");
    }

    @AfterEach void clearLogin() { SecurityContextHolder.remove(); }

    @Test void legacyHistoricalReceiptsPersistOneCommandAndReplayAfterCompletion()
    {
        Fixture f = new Fixture(2, true);
        f.service.qualityCheckWithRequest(10L, InvStatusConstants.QC_PASSED, "整单验收", 20L, "legacy-two-batches");
        assertThat(countCommands()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select command_type from inv_quality_command", String.class)).isEqualTo("PURCHASE_QUALITY_CHECK_LEGACY");
        assertThat(jdbc.queryForObject("select request_id from inv_quality_command", String.class)).isEqualTo("legacy-two-batches");
        assertThat(countFacts("inspection")).isZero();
        assertThat(stockQuantity()).isEqualByComparingTo("20");
        assertThat(f.order.getStatus()).isEqualTo(InvStatusConstants.RECEIVED);
        // A new executor instance must replay persisted results even after the order left pending QC.
        f.replaceExecutor();
        f.service.qualityCheckWithRequest(10L, InvStatusConstants.QC_PASSED, "整单验收", 20L, "legacy-two-batches");
        assertThat(countCommands()).isEqualTo(1);
        assertThat(countFacts("inspection")).isZero();
        assertThat(stockQuantity()).isEqualByComparingTo("20");
        verify(f.batches, never()).selectPendingByOrderId(10L);
    }

    @Test void legacyEntryRejectsBatchBackedReceiptsWithoutPersistingCommandOrStock()
    {
        Fixture f = new Fixture(2);
        assertThatThrownBy(() -> f.service.qualityCheckWithRequest(10L, InvStatusConstants.QC_PASSED,
                "", 20L, "legacy-cannot-consume-batches"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("批次");
        assertThat(countCommands()).isZero();
        assertThat(stockQuantity()).isEqualByComparingTo("0");
        assertThat(countFacts("inspection")).isZero();
    }

    @Test void batchEntryPersistsRequestAndPartialReplayDoesNotRepeatStockOrInspection()
    {
        Fixture f = new Fixture(1);
        InvQualityCheckRequest request = request("batch-partial-five", "5");
        f.service.qualityCheckBatch(10L, request, 20L);
        f.replaceExecutor();
        f.service.qualityCheckBatch(10L, request, 20L);
        assertThat(countCommands()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select resource_key from inv_quality_command", String.class)).isEqualTo("purchase:10:batch:100");
        assertThat(countFacts("inspection")).isEqualTo(1);
        assertThat(stockQuantity()).isEqualByComparingTo("5");
        assertThat(f.rows.get(0).getPendingQuantity()).isEqualByComparingTo("5");
        assertThatThrownBy(() -> f.service.qualityCheckBatch(10L, request("batch-partial-five", "6"), 20L))
                .isInstanceOf(ServiceException.class).hasMessageContaining("拒绝覆盖");
        assertThat(stockQuantity()).isEqualByComparingTo("5");
    }

    @Test void bothServiceTransactionsRollBackCommandAndAlreadyWrittenFactsOnLateFailure()
    {
        for (boolean legacy : List.of(false, true))
        {
            Fixture failing = new Fixture(1, legacy);
            failing.failAfterStock = true;
            String id = legacy ? "legacy-late-failure" : "batch-late-failure";
            assertThatThrownBy(() -> invoke(failing, legacy, id)).isInstanceOf(ServiceException.class).hasMessageContaining("forced late failure");
            assertThat(countCommands()).isZero();
            assertThat(jdbc.queryForObject("select count(*) from wiring_fact", Integer.class)).isZero();
            // Fresh metadata emulates the database rollback, then the exact failed request ID is reusable.
            invoke(new Fixture(1, legacy), legacy, id);
            assertThat(countCommands()).isEqualTo(1);
            assertThat(stockQuantity()).isEqualByComparingTo("10");
            jdbc.update("delete from inv_quality_command");
            jdbc.update("delete from wiring_fact");
        }
    }

    @Test void bothEntriesKeepScopeAndWarehouseChecksBeforeReplayAndBatchWarehouseInsideTransaction()
    {
        for (boolean legacy : List.of(false, true))
        {
            Fixture f = new Fixture(1, legacy);
            String id = legacy ? "legacy-scope-check" : "batch-scope-check";
            invoke(f, legacy, id);
            when(f.scope.resolveRequiredShopDept(20L)).thenThrow(new ServiceException("无权选择当前仓库"));
            assertThatThrownBy(() -> invoke(f, legacy, id)).hasMessageContaining("无权选择当前仓库");
            assertThat(countCommands()).isEqualTo(1);
            assertThat(countFacts("inspection")).isEqualTo(legacy ? 0 : 1);
            jdbc.update("delete from inv_quality_command");
            jdbc.update("delete from wiring_fact");
            Fixture otherWarehouse = new Fixture(1, legacy);
            assertThatThrownBy(() -> invokeAt(otherWarehouse, legacy, id, 30L)).hasMessageContaining("只能操作当前仓库");
            assertThatThrownBy(() -> invokeAt(otherWarehouse, legacy, id, 40L)).hasMessageContaining("请选择仓库");
            assertThat(countCommands()).isZero();
            if (!legacy)
            {
                otherWarehouse.batchRows.get(0).setWarehouseId(30L);
                assertThatThrownBy(() -> invoke(otherWarehouse, false, id)).hasMessageContaining("只能质检当前仓库");
                assertThat(countCommands()).isZero();
            }
        }
    }

    @Test void bothControllerEntrypointsStillRequirePurchaseQualityPermission()
    {
        int checked = 0;
        for (java.lang.reflect.Method method : InvPurchaseController.class.getDeclaredMethods())
        {
            if (method.getName().equals("qualityCheck") || method.getName().equals("qualityCheckBatch"))
            {
                checked++;
                assertThat(method.getAnnotation(RequiresPermissions.class)).isNotNull();
                assertThat(method.getAnnotation(RequiresPermissions.class).value()).containsExactly("inv:purchase:qc");
            }
        }
        assertThat(checked).isEqualTo(2);
    }

    private static void invoke(Fixture f, boolean legacy, String id) { invokeAt(f, legacy, id, 20L); }
    private static void invokeAt(Fixture f, boolean legacy, String id, Long warehouse)
    {
        if (legacy) f.service.qualityCheckWithRequest(10L, InvStatusConstants.QC_PASSED, "", warehouse, id);
        else f.service.qualityCheckBatch(10L, request(id, "10"), warehouse);
    }
    private static InvQualityCheckRequest request(String id, String amount)
    {
        InvQualityCheckItem item = new InvQualityCheckItem();
        item.setBatchDetailId(1000L);
        item.setInspectedQuantity(new BigDecimal(amount));
        item.setAcceptedQuantity(new BigDecimal(amount));
        item.setRejectedQuantity(BigDecimal.ZERO);
        item.setConcessionQuantity(BigDecimal.ZERO);
        InvQualityCheckRequest request = new InvQualityCheckRequest();
        request.setRequestId(id); request.setReceiptBatchId(100L); request.setItems(List.of(item));
        return request;
    }
    private static int countCommands() { return jdbc.queryForObject("select count(*) from inv_quality_command", Integer.class); }
    private static int countFacts(String kind) { return jdbc.queryForObject("select count(*) from wiring_fact where kind=?", Integer.class, kind); }
    private static BigDecimal stockQuantity() { return jdbc.queryForObject("select coalesce(sum(quantity),0) from wiring_fact where kind='stock'", BigDecimal.class); }
    @SuppressWarnings("unchecked") private static <T> T transactional(T target)
    {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }

    private static class Fixture
    {
        final InvPurchaseServiceImpl target = new InvPurchaseServiceImpl();
        final InvPurchaseServiceImpl service;
        final ShopScopeService scope = mock(ShopScopeService.class);
        final InvPurchaseOrder order = new InvPurchaseOrder();
        final InvReceiptBatchMapper batches = mock(InvReceiptBatchMapper.class);
        final List<InvReceiptBatch> batchRows = new ArrayList<>();
        final List<InvReceiptBatchDetail> rows = new ArrayList<>();
        boolean failAfterStock;
        Fixture(int batchCount) { this(batchCount, false); }
        Fixture(int batchCount, boolean historical)
        {
            InvDeptScopeMapper departments = mock(InvDeptScopeMapper.class);
            when(departments.selectDeptTypeById(anyLong())).thenAnswer(call -> ((Long) call.getArgument(0)) == 40L ? "STORE" : "WAREHOUSE");
            when(departments.countDeptInScope(20L, 20L)).thenReturn(1);
            when(scope.resolveRequiredShopDept(anyLong())).thenAnswer(call -> call.getArgument(0));
            when(scope.hasUserShopScope(77L, 20L)).thenReturn(true);
            target.deptScopeMapper = departments; target.shopScopeService = scope;
            order.setOrderId(10L); order.setShopDeptId(20L); order.setOrderNo("PO-fixture");
            order.setStatus(InvStatusConstants.SUBMITTED); order.setQcStatus(InvStatusConstants.QC_PENDING);
            InvPurchaseOrderMapper orders = mock(InvPurchaseOrderMapper.class);
            when(orders.selectInvPurchaseOrderById(10L)).thenReturn(order);
            when(orders.selectInvPurchaseOrderByIdForUpdate(10L)).thenReturn(order);
            when(orders.transitionPurchaseStatus(eq(10L), anyString(), anyString(), anyString(), anyString())).thenAnswer(call -> {
                order.setStatus(call.getArgument(2)); order.setQcStatus(call.getArgument(3)); return 1;
            });
            InvPurchaseDetail purchase = new InvPurchaseDetail();
            purchase.setDetailId(201L); purchase.setProductId(1L); purchase.setItemId(1L); purchase.setItemType("product");
            purchase.setQuantity(BigDecimal.TEN.multiply(BigDecimal.valueOf(batchCount)));
            purchase.setReceivedQuantity(purchase.getQuantity()); purchase.setUnitPrice(BigDecimal.ONE);
            InvPurchaseDetailMapper details = mock(InvPurchaseDetailMapper.class);
            when(details.selectInvPurchaseDetailByOrderIdForUpdate(10L)).thenReturn(List.of(purchase));
            InvReceiptBatchDetailMapper batchDetails = mock(InvReceiptBatchDetailMapper.class);
            InvInboundRecordMapper inboundMapper = mock(InvInboundRecordMapper.class);
            List<InvInboundRecord> inboundRows = new ArrayList<>();
            for (int index = 0; index < batchCount; index++)
            {
                InvReceiptBatch batch = new InvReceiptBatch();
                batch.setBatchId(100L + index); batch.setPurchaseOrderId(10L); batch.setWarehouseId(20L);
                batch.setPendingQuantity(BigDecimal.TEN); batch.setStatus("pending"); batchRows.add(batch);
                InvReceiptBatchDetail row = new InvReceiptBatchDetail();
                row.setBatchDetailId(1000L + index); row.setBatchId(batch.getBatchId()); row.setPurchaseDetailId(201L);
                row.setReceivedQuantity(BigDecimal.TEN); row.setPendingQuantity(BigDecimal.TEN); rows.add(row);
                when(batches.selectByIdForUpdate(batch.getBatchId())).thenReturn(batch);
                when(batchDetails.selectByBatchId(batch.getBatchId())).thenReturn(List.of(row));
                when(batchDetails.selectByBatchIdForUpdate(batch.getBatchId())).thenReturn(List.of(row));
                InvInboundRecord inbound = new InvInboundRecord();
                inbound.setInboundId(2000L + index);
                inbound.setQuantity(BigDecimal.TEN);
                inbound.setQcResult(InvStatusConstants.QC_PENDING);
                if (!historical)
                {
                    inbound.setReceiptBatchId(batch.getBatchId());
                    inbound.setReceiptBatchDetailId(row.getBatchDetailId());
                }
                inboundRows.add(inbound);
                inbound.setPurchaseOrderId(10L); inbound.setPurchaseDetailId(201L); inbound.setProductId(1L);
                inbound.setItemType("product"); inbound.setItemId(1L); inbound.setShopDeptId(20L); inbound.setWarehouseId(20L);
                when(inboundMapper.selectByBatchDetailIdForUpdate(row.getBatchDetailId())).thenReturn(inbound);
            }
            when(inboundMapper.selectByOrderIdForUpdate(10L)).thenReturn(inboundRows);
            when(batches.selectByOrderIdForUpdate(10L)).thenReturn(historical ? List.of() : batchRows);
            when(batchDetails.selectByOrderIdForUpdate(10L)).thenReturn(historical ? List.of() : rows);
            when(batches.selectPendingByOrderId(10L)).thenAnswer(call -> new ArrayList<>(batchRows));
            when(batches.countPendingByOrderId(10L)).thenAnswer(call -> (int) batchRows.stream().filter(b -> b.getPendingQuantity().signum() > 0).count());
            when(batches.updateProgress(anyLong(), any(), anyString(), anyString())).thenAnswer(call -> {
                if (failAfterStock) throw new ServiceException("forced late failure");
                batchRows.stream().filter(b -> b.getBatchId().equals(call.getArgument(0))).findFirst().orElseThrow().setPendingQuantity(call.getArgument(1));
                return 1;
            });
            when(batchDetails.updateInspectionProgress(any())).thenReturn(1);
            when(inboundMapper.updateInvInboundRecord(any())).thenReturn(1);
            AtomicLong sequence = new AtomicLong();
            InvNumberSequenceMapper numbers = mock(InvNumberSequenceMapper.class);
            when(numbers.selectLastInsertId()).thenAnswer(call -> sequence.incrementAndGet());
            InvQualityInspectionMapper inspections = mock(InvQualityInspectionMapper.class);
            when(inspections.insertInvQualityInspection(any())).thenAnswer(call -> {
                InvQualityInspection inspection = call.getArgument(0); inspection.setInspectionId(sequence.incrementAndGet());
                return jdbc.update("insert into wiring_fact(kind,quantity) values('inspection',?)", inspection.getInspectedQuantity());
            });
            InvStockMapper stocks = mock(InvStockMapper.class);
            when(stocks.insertInvStock(any())).thenAnswer(call -> {
                InvStock stock = call.getArgument(0);
                return jdbc.update("insert into wiring_fact(kind,quantity) values('stock',?)", stock.getCurrentQuantity());
            });
            InvStockLogMapper logs = mock(InvStockLogMapper.class);
            when(logs.insertInvStockLog(any())).thenAnswer(call -> {
                if (failAfterStock) throw new ServiceException("forced late failure");
                return 1;
            });
            ReflectionTestUtils.setField(target, "purchaseOrderMapper", orders);
            ReflectionTestUtils.setField(target, "purchaseDetailMapper", details);
            ReflectionTestUtils.setField(target, "receiptBatchMapper", batches);
            ReflectionTestUtils.setField(target, "receiptBatchDetailMapper", batchDetails);
            ReflectionTestUtils.setField(target, "inboundRecordMapper", inboundMapper);
            ReflectionTestUtils.setField(target, "numberSequenceMapper", numbers);
            ReflectionTestUtils.setField(target, "qualityInspectionMapper", inspections);
            ReflectionTestUtils.setField(target, "stockMapper", stocks);
            ReflectionTestUtils.setField(target, "stockLogMapper", logs);
            replaceExecutor();
            service = transactional(target);
        }
        void replaceExecutor()
        {
            ReflectionTestUtils.setField(target, "qualityCommandExecutor", transactional(new InvQualityCommandExecutor(commandMapper, new ObjectMapper())));
        }
    }
}
