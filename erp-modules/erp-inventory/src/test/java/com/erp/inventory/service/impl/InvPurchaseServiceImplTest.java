package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvInboundRecord;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvPurchaseDetail;
import com.erp.inventory.domain.InvPurchaseOrder;
import com.erp.inventory.domain.InvSupplier;
import com.erp.inventory.domain.dto.InvReceiveRequest;
import com.erp.inventory.domain.dto.InvQualityCheckItem;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvInboundRecordMapper;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvProductMapper;
import com.erp.inventory.mapper.InvPurchaseDetailMapper;
import com.erp.inventory.mapper.InvPurchaseOrderMapper;
import com.erp.inventory.mapper.InvReceiptBatchDetailMapper;
import com.erp.inventory.mapper.InvReceiptBatchMapper;
import com.erp.inventory.mapper.InvSupplierMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("采购服务")
class InvPurchaseServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("门店上下文不能保存采购单")
    void shouldRejectStoreContextWhenSavingPurchase()
    {
        SecurityContextHolder.setUserId("200");
        InvPurchaseServiceImpl service = purchaseService(Map.of(10L, "STORE"));

        assertThatThrownBy(() -> service.saveDraft(new InvPurchaseOrder(), Collections.emptyList(), 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择仓库");
    }

    @Test
    @DisplayName("采购业务阶段区分待收货、部分收货和待质检")
    void shouldResolvePurchaseBusinessStage()
    {
        InvPurchaseOrder pendingReceive = purchaseStageOrder(
                InvStatusConstants.SUBMITTED, null, "0", "10");
        InvPurchaseOrder partialReceived = purchaseStageOrder(
                InvStatusConstants.SUBMITTED, InvStatusConstants.QC_PASSED, "3", "7");
        InvPurchaseOrder pendingQc = purchaseStageOrder(
                InvStatusConstants.SUBMITTED, InvStatusConstants.QC_PENDING, "3", "7");
        InvPurchaseOrder rejected = purchaseStageOrder(
                InvStatusConstants.SUBMITTED, InvStatusConstants.QC_REJECTED, "0", "10");
        InvPurchaseOrder completed = purchaseStageOrder(
                InvStatusConstants.RECEIVED, InvStatusConstants.QC_PASSED, "10", "0");

        assertThat(InvPurchaseServiceImpl.resolveBusinessStage(pendingReceive)).isEqualTo("pending_receive");
        assertThat(InvPurchaseServiceImpl.resolveBusinessStage(partialReceived)).isEqualTo("partial_received");
        assertThat(InvPurchaseServiceImpl.resolveBusinessStage(pendingQc)).isEqualTo("pending_qc");
        assertThat(InvPurchaseServiceImpl.resolveBusinessStage(rejected)).isEqualTo("qc_rejected");
        assertThat(InvPurchaseServiceImpl.resolveBusinessStage(completed)).isEqualTo("completed");
    }

    private static InvPurchaseOrder purchaseStageOrder(String status, String qcStatus,
            String receivedQuantity, String remainingQuantity)
    {
        InvPurchaseOrder order = new InvPurchaseOrder();
        order.setStatus(status);
        order.setQcStatus(qcStatus);
        order.setReceivedQuantity(new BigDecimal(receivedQuantity));
        order.setRemainingQuantity(new BigDecimal(remainingQuantity));
        return order;
    }

    @Test
    @DisplayName("门店上下文不能采购收货")
    void shouldRejectStoreContextWhenReceivingPurchase()
    {
        SecurityContextHolder.setUserId("200");
        InvPurchaseServiceImpl service = purchaseService(Map.of(10L, "STORE"));
        InvReceiveRequest request = receiveRequest(10L);

        assertThatThrownBy(() -> service.receivePurchase(1L, request, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择仓库");
    }

    @Test
    @DisplayName("采购收货不能写入门店库存")
    void shouldRejectPurchaseReceiveIntoStoreDept()
    {
        SecurityContextHolder.setUserId("1");
        InvPurchaseServiceImpl service = purchaseService(Map.of(10L, "STORE", 20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", purchaseOrderMapper(20L));
        ReflectionTestUtils.setField(service, "purchaseDetailMapper", new EmptyPurchaseDetailMapper());
        InvReceiveRequest request = receiveRequest(10L);

        assertThatThrownBy(() -> service.receivePurchase(1L, request, 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货仓库必须为当前仓库");
    }

    @Test
    @DisplayName("采购收货不能写入其它仓库")
    void shouldRejectPurchaseReceiveIntoDifferentWarehouse()
    {
        SecurityContextHolder.setUserId("1");
        InvPurchaseServiceImpl service = purchaseService(Map.of(20L, "WAREHOUSE", 30L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", purchaseOrderMapper(20L));
        ReflectionTestUtils.setField(service, "purchaseDetailMapper", new EmptyPurchaseDetailMapper());
        InvReceiveRequest request = receiveRequest(30L);

        assertThatThrownBy(() -> service.receivePurchase(1L, request, 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货仓库必须为当前仓库");
    }

    @Test
    @DisplayName("绕过 Controller 直接调用收货服务也必须提供实际到货时间")
    void shouldFailClosedWhenReceiveArrivalTimeIsMissing()
    {
        SecurityContextHolder.setUserId("1");
        InvPurchaseServiceImpl service = purchaseService(
                Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "purchaseOrderMapper",
                purchaseOrderMapper(20L));
        InvPurchaseDetailMapper detailMapper = mock(
                InvPurchaseDetailMapper.class);
        ReflectionTestUtils.setField(service, "purchaseDetailMapper",
                detailMapper);
        InvReceiveRequest request = receiveRequest(20L);
        request.setArrivedTime(null);

        assertThatThrownBy(() -> service.receivePurchase(1L, request, 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("实际到货时间不能为空");
        verifyNoInteractions(detailMapper);
    }

    @Test
    @DisplayName("门店上下文不能采购质检")
    void shouldRejectStoreContextWhenQualityCheckingPurchase()
    {
        SecurityContextHolder.setUserId("200");
        InvPurchaseServiceImpl service = purchaseService(Map.of(10L, "STORE"));

        assertThatThrownBy(() -> service.qualityCheck(1L, InvStatusConstants.QC_PASSED, "", 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择仓库");
    }

    @Test
    @DisplayName("采购保存按商品档案自动同步供应商和进价")
    void shouldApplyProductCatalogWhenSavingPurchaseDraft()
    {
        InvPurchaseOrder order = new InvPurchaseOrder();
        InvPurchaseDetail detail = new InvPurchaseDetail();
        detail.setProductId(101L);
        detail.setQuantity(new BigDecimal("2.00"));
        detail.setReceivedQuantity(new BigDecimal("99.00"));
        detail.setWarehouseId(999L);

        InvProduct product = product(101L, "西湖龙井", "SP-101", "罐装", "盒", "杭州茶厂", "18.50");

        InvPurchaseServiceImpl.applyCatalogProductsForDraft(order, List.of(detail), Map.of(101L, product));

        assertThat(order.getSupplierName()).isEqualTo("杭州茶厂");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("37.00");
        assertThat(order.getOrderDate()).isNotNull();
        assertThat(detail.getProductName()).isEqualTo("西湖龙井");
        assertThat(detail.getSku()).isEqualTo("SP-101");
        assertThat(detail.getSpec()).isEqualTo("罐装");
        assertThat(detail.getUnit()).isEqualTo("盒");
        assertThat(detail.getUnitPrice()).isEqualByComparingTo("18.50");
        assertThat(detail.getAmount()).isEqualByComparingTo("37.00");
        assertThat(detail.getReceivedQuantity()).isZero();
        assertThat(detail.getWarehouseId()).isNull();
    }

    @Test
    @DisplayName("采购列表只能查询当前精确仓库")
    void shouldQueryOnlySelectedWarehouse()
    {
        SecurityContextHolder.setUserId("1");
        InvPurchaseServiceImpl service = purchaseService(Map.of(20L, "WAREHOUSE"));
        InvPurchaseOrderMapper mapper = mock(InvPurchaseOrderMapper.class);
        when(mapper.selectInvPurchaseOrderList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", mapper);
        InvPurchaseOrder query = new InvPurchaseOrder();

        service.selectPurchaseList(query, 20L);

        assertThat(query.getShopDeptId()).isEqualTo(20L);
        assertThat(query.getParams()).doesNotContainKeys("scopeDeptIds", "dataScope");
    }

    @Test
    @DisplayName("已保存采购草稿只能按ID提交")
    void shouldSubmitSavedDraftByIdentifier()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        InvPurchaseOrder draft = new InvPurchaseOrder();
        draft.setOrderId(1L);
        draft.setShopDeptId(20L);
        draft.setStatus(InvStatusConstants.DRAFT);
        FakePurchaseOrderMapper mapper = new FakePurchaseOrderMapper(draft);
        InvPurchaseServiceImpl service = purchaseService(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", mapper);

        service.submitSavedPurchase(1L, 20L);

        assertThat(mapper.updatedStatus).isEqualTo(InvStatusConstants.SUBMITTED);
    }

    @Test
    @DisplayName("采购保存强制把明细入库仓库绑定为当前仓库")
    void shouldOverwriteForgedPurchaseDetailWarehouse()
    {
        InvPurchaseServiceImpl service = purchaseService(Map.of(20L, "WAREHOUSE"));
        InvProduct product = product(101L, "西湖龙井", "SP-101", "罐装", "盒", "杭州茶厂", "18.50");
        FakeProductMapper productMapper = new FakeProductMapper(product);
        InventoryItemResolver itemResolver = new InventoryItemResolver();
        ReflectionTestUtils.setField(itemResolver, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "itemResolver", itemResolver);
        InvPurchaseDetail detail = purchaseDetail(101L, "2.00", "18.50");
        detail.setWarehouseId(99L);

        ReflectionTestUtils.invokeMethod(service, "applyCatalogItemsForDraft",
                new InvPurchaseOrder(), List.of(detail), 20L);

        assertThat(detail.getWarehouseId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("采购单按供应商档案重建名称快照")
    void shouldApplySupplierMasterSnapshot()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        InvPurchaseServiceImpl service = purchaseService(Map.of(20L, "WAREHOUSE"));
        InvSupplierMapper supplierMapper = mock(InvSupplierMapper.class);
        InvSupplier supplier = new InvSupplier();
        supplier.setSupplierId(701L);
        supplier.setSupplierName("档案供应商");
        supplier.setShopDeptId(20L);
        supplier.setStatus("0");
        supplier.setCooperationStatus("0");
        when(supplierMapper.selectInvSupplierById(701L)).thenReturn(supplier);
        ReflectionTestUtils.setField(service, "supplierMapper", supplierMapper);
        InvPurchaseOrder order = new InvPurchaseOrder();
        order.setSupplierId(701L);
        order.setSupplierName(null);

        ReflectionTestUtils.invokeMethod(service, "applySupplierSnapshot", order, 20L);

        assertThat(order.getSupplierName()).isEqualTo("档案供应商");
    }

    @Test
    @DisplayName("采购保存不能选择当前仓库关联范围外的商品")
    void shouldRejectPurchaseProductOutsideSelectedWarehouseScope()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        InvPurchaseOrder existing = new InvPurchaseOrder();
        existing.setOrderId(1L);
        existing.setShopDeptId(20L);
        existing.setStatus(InvStatusConstants.DRAFT);
        InvPurchaseOrder update = new InvPurchaseOrder();
        update.setOrderId(1L);
        update.setOrderTitle("越权采购测试");
        InvPurchaseDetail detail = purchaseDetail(101L, "1.00", "5.00");
        InvProduct outsideProduct = product(101L, "越权商品", "SP-101", "散装", "斤", "外部供应商", "5.00");
        outsideProduct.setShopDeptId(30L);
        InvPurchaseServiceImpl service = purchaseService(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", new FakePurchaseOrderMapper(existing));
        ReflectionTestUtils.setField(service, "purchaseDetailMapper", new EmptyPurchaseDetailMapper());
        FakeProductMapper productMapper = new FakeProductMapper(outsideProduct);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        InventoryItemResolver itemResolver = new InventoryItemResolver();
        ReflectionTestUtils.setField(itemResolver, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "itemResolver", itemResolver);

        assertThatThrownBy(() -> service.saveDraft(update, List.of(detail), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权采购该商品");
    }

    @Test
    @DisplayName("采购保存汇总多商品绑定供应商")
    void shouldSummarizeDistinctSuppliersFromSelectedProducts()
    {
        InvPurchaseOrder order = new InvPurchaseOrder();
        InvPurchaseDetail first = purchaseDetail(101L, "1.00", "5.00");
        InvPurchaseDetail second = purchaseDetail(102L, "1.00", "6.00");
        InvProduct firstProduct = product(101L, "白牡丹", "SP-101", "散装", "斤", "福鼎茶厂", "4.50");
        InvProduct secondProduct = product(102L, "正山小种", "SP-102", "罐装", "盒", "武夷茶厂", "5.50");

        InvPurchaseServiceImpl.applyCatalogProductsForDraft(order, List.of(first, second),
                Map.of(101L, firstProduct, 102L, secondProduct));

        assertThat(order.getSupplierName()).isEqualTo("福鼎茶厂、武夷茶厂");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("11.00");
    }

    @Test
    @DisplayName("采购商品未绑定供应商时不能保存")
    void shouldRejectPurchaseProductWithoutSupplier()
    {
        InvPurchaseOrder order = new InvPurchaseOrder();
        InvPurchaseDetail detail = purchaseDetail(101L, "1.00", "5.00");
        InvProduct product = product(101L, "未绑定商品", "SP-101", "散装", "斤", " ", "5.00");

        assertThatThrownBy(() -> InvPurchaseServiceImpl.applyCatalogProductsForDraft(order, List.of(detail), Map.of(101L, product)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未绑定供应商");
    }

    @Test
    @DisplayName("只有全部明细收满后采购单才进入已收货")
    void shouldResolvePurchaseStatusAfterQualityCheckByReceivedQuantities()
    {
        InvPurchaseDetail partial = new InvPurchaseDetail();
        partial.setQuantity(new BigDecimal("10.00"));
        partial.setReceivedQuantity(new BigDecimal("4.00"));

        InvPurchaseDetail full = new InvPurchaseDetail();
        full.setQuantity(new BigDecimal("2.00"));
        full.setReceivedQuantity(new BigDecimal("2.00"));

        assertThat(InvPurchaseServiceImpl.resolveStatusAfterAcceptedQualityCheck(List.of(partial, full)))
                .isEqualTo(InvStatusConstants.SUBMITTED);

        partial.setReceivedQuantity(new BigDecimal("10.00"));

        assertThat(InvPurchaseServiceImpl.resolveStatusAfterAcceptedQualityCheck(List.of(partial, full)))
                .isEqualTo(InvStatusConstants.RECEIVED);
    }

    @Test
    @DisplayName("采购收货仓库不能为空")
    void shouldRequireWarehouseForPurchaseReceive()
    {
        assertThatThrownBy(() -> InvPurchaseServiceImpl.requireReceiveWarehouseId(null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货仓库不能为空");

        assertThatThrownBy(() -> InvPurchaseServiceImpl.requireReceiveWarehouseId(0L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货仓库不能为空");

        assertThat(InvPurchaseServiceImpl.requireReceiveWarehouseId(901L)).isEqualTo(901L);
    }

    @Test
    @DisplayName("采购收货数量必须大于零且符合十八位四位小数精度")
    void shouldRejectInvalidPurchaseReceiveQuantity()
    {
        InvPurchaseDetail detail = purchaseDetail(101L, "10.00", "5.00");
        detail.setDetailId(501L);
        detail.setReceivedQuantity(BigDecimal.ZERO);
        InvReceiveRequest request = receiveRequest(20L, 501L, "0");

        assertThatThrownBy(() -> InvPurchaseServiceImpl.validateReceiveItems(
                request, Map.of(501L, detail)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货数量必须大于0");

        request.getItems().get(0).setReceiveQuantity(new BigDecimal("1.00001"));
        assertThatThrownBy(() -> InvPurchaseServiceImpl.validateReceiveItems(
                request, Map.of(501L, detail)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货数量精度无效");

        request.getItems().get(0).setReceiveQuantity(new BigDecimal("123456789012345"));
        assertThatThrownBy(() -> InvPurchaseServiceImpl.validateReceiveItems(
                request, Map.of(501L, detail)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货数量精度无效");
    }

    @Test
    @DisplayName("采购质检只处理本批待检入库记录")
    void shouldFilterPendingInboundRecordsForQualityCheck()
    {
        InvInboundRecord acceptedHistory = inboundRecord(101L, "6.00", InvStatusConstants.QC_PASSED);
        InvInboundRecord rejectedHistory = inboundRecord(101L, "1.00", InvStatusConstants.QC_REJECTED);
        InvInboundRecord legacyPending = inboundRecord(101L, "2.00", null);
        InvInboundRecord currentPending = inboundRecord(102L, "3.00", InvStatusConstants.QC_PENDING);

        List<InvInboundRecord> pendingRecords = InvPurchaseServiceImpl.pendingQualityCheckRecords(
                List.of(acceptedHistory, rejectedHistory, legacyPending, currentPending));

        assertThat(pendingRecords).containsExactly(legacyPending, currentPending);
    }

    @Test
    @DisplayName("质检拒绝只回退本批待检入库数量")
    void shouldRollbackOnlyPendingRejectedInboundQuantities()
    {
        InvPurchaseDetail firstBatchAccepted = purchaseDetail(101L, "10.00");
        InvPurchaseDetail untouchedProduct = purchaseDetail(102L, "5.00");

        InvInboundRecord acceptedHistory = inboundRecord(101L, "6.00", InvStatusConstants.QC_PASSED);
        InvInboundRecord currentRejected = inboundRecord(101L, "4.00", InvStatusConstants.QC_PENDING);

        InvPurchaseServiceImpl.rollbackRejectedPendingReceipts(
                List.of(firstBatchAccepted, untouchedProduct),
                InvPurchaseServiceImpl.pendingQualityCheckRecords(List.of(acceptedHistory, currentRejected)));

        assertThat(firstBatchAccepted.getReceivedQuantity()).isEqualByComparingTo("6.00");
        assertThat(untouchedProduct.getReceivedQuantity()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("采购入库记录必须绑定采购明细行")
    void shouldBindInboundRecordToPurchaseDetailWhenReceiving()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        InvPurchaseOrder order = new InvPurchaseOrder();
        order.setOrderId(1L);
        order.setShopDeptId(20L);
        order.setOrderNo("PO1");
        order.setStatus(InvStatusConstants.SUBMITTED);
        InvPurchaseDetail detail = purchaseDetail(101L, "10.00", "5.00");
        detail.setDetailId(501L);
        detail.setReceivedQuantity(BigDecimal.ZERO);
        FakeInboundRecordMapper inboundRecordMapper = new FakeInboundRecordMapper();
        InvPurchaseServiceImpl service = purchaseService(Map.of(20L, "WAREHOUSE"));
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", new FakePurchaseOrderMapper(order));
        ReflectionTestUtils.setField(service, "purchaseDetailMapper", new ListPurchaseDetailMapper(List.of(detail)));
        ReflectionTestUtils.setField(service, "inboundRecordMapper", inboundRecordMapper);
        InvNumberSequenceMapper numberSequenceMapper = mock(InvNumberSequenceMapper.class);
        when(numberSequenceMapper.selectLastInsertId()).thenReturn(1L);
        ReflectionTestUtils.setField(service, "numberSequenceMapper", numberSequenceMapper);
        InvReceiptBatchMapper receiptBatchMapper = mock(InvReceiptBatchMapper.class);
        doAnswer(invocation -> {
            invocation.<com.erp.inventory.domain.InvReceiptBatch>getArgument(0).setBatchId(601L);
            return 1;
        }).when(receiptBatchMapper).insertInvReceiptBatch(org.mockito.ArgumentMatchers.any());
        ReflectionTestUtils.setField(service, "receiptBatchMapper", receiptBatchMapper);
        InvReceiptBatchDetailMapper receiptBatchDetailMapper = mock(InvReceiptBatchDetailMapper.class);
        doAnswer(invocation -> {
            invocation.<com.erp.inventory.domain.InvReceiptBatchDetail>getArgument(0).setBatchDetailId(701L);
            return 1;
        }).when(receiptBatchDetailMapper).insertInvReceiptBatchDetail(org.mockito.ArgumentMatchers.any());
        ReflectionTestUtils.setField(service, "receiptBatchDetailMapper", receiptBatchDetailMapper);

        Date actualArrival = new Date(1_752_637_800_000L);
        InvReceiveRequest receiveRequest = receiveRequest(20L, 501L,
                "4.00");
        receiveRequest.setArrivedTime(actualArrival);
        service.receivePurchase(1L, receiveRequest, 20L);

        assertThat(inboundRecordMapper.insertedRecords).hasSize(1);
        assertThat(ReflectionTestUtils.getField(inboundRecordMapper.insertedRecords.get(0), "purchaseDetailId"))
                .isEqualTo(501L);
        assertThat(inboundRecordMapper.insertedRecords.get(0).getReceiptBatchId()).isEqualTo(601L);
        assertThat(inboundRecordMapper.insertedRecords.get(0).getReceiptBatchDetailId()).isEqualTo(701L);
        org.mockito.ArgumentCaptor<com.erp.inventory.domain.InvReceiptBatch> batch =
                org.mockito.ArgumentCaptor.forClass(
                        com.erp.inventory.domain.InvReceiptBatch.class);
        org.mockito.Mockito.verify(receiptBatchMapper)
                .insertInvReceiptBatch(batch.capture());
        assertThat(batch.getValue().getArrivedTime()).isSameAs(actualArrival);
    }

    @Test
    @DisplayName("逐行质检必须数量守恒且拒收需原因")
    void shouldValidateLineQualityCheckQuantities()
    {
        InvQualityCheckItem unbalanced = qualityCheckItem("10", "8", "1", "0");
        assertThatThrownBy(() -> InvPurchaseServiceImpl.validateQualityCheckQuantities(unbalanced, new BigDecimal("10")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须等于");

        InvQualityCheckItem exceedsPending = qualityCheckItem("10", "8", "2", "0");
        assertThatThrownBy(() -> InvPurchaseServiceImpl.validateQualityCheckQuantities(exceedsPending, new BigDecimal("9")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能超过");

        InvQualityCheckItem missingReason = qualityCheckItem("10", "8", "2", "0");
        assertThatThrownBy(() -> InvPurchaseServiceImpl.validateQualityCheckQuantities(missingReason, new BigDecimal("10")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须填写原因");

        missingReason.setDefectReason("外包装破损");
        InvPurchaseServiceImpl.validateQualityCheckQuantities(missingReason, new BigDecimal("10"));
    }

    @Test
    @DisplayName("逐行质检数量最多四位小数")
    void shouldRejectOverPrecisionQualityCheckQuantity()
    {
        InvQualityCheckItem overPrecision = qualityCheckItem("1.00001", "1.00001", "0", "0");

        assertThatThrownBy(() -> InvPurchaseServiceImpl.validateQualityCheckQuantities(
                overPrecision, new BigDecimal("10")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("精度无效");
    }

    @Test
    @DisplayName("采购质检附件只接受受信任的本地上传路径")
    void shouldAcceptOnlyTrustedLocalPurchaseQualityAttachments()
    {
        assertThat(InvPurchaseServiceImpl.isSafeAttachmentUrl(
                "/file/public/2026/08/qc.pdf")).isTrue();
        assertThat(InvPurchaseServiceImpl.isSafeAttachmentUrl(
                "/profile/upload/qc.png")).isTrue();
        assertThat(InvPurchaseServiceImpl.isSafeAttachmentUrl(
                "/uploads/erp-new-2/purchase/qc.png")).isTrue();
        assertThat(InvPurchaseServiceImpl.isSafeAttachmentUrl(
                "https://files.example.test/qc.png")).isFalse();
        assertThat(InvPurchaseServiceImpl.isSafeAttachmentUrl(
                "/shared/qc.png")).isFalse();
        assertThat(InvPurchaseServiceImpl.isSafeAttachmentUrl(
                "/profile/%2e%2e/secret.txt")).isFalse();
        assertThat(InvPurchaseServiceImpl.isSafeAttachmentUrl(
                "/file/public/")).isFalse();
    }

    private static InvQualityCheckItem qualityCheckItem(String inspected, String accepted,
            String rejected, String concession)
    {
        InvQualityCheckItem item = new InvQualityCheckItem();
        item.setInspectedQuantity(new BigDecimal(inspected));
        item.setAcceptedQuantity(new BigDecimal(accepted));
        item.setRejectedQuantity(new BigDecimal(rejected));
        item.setConcessionQuantity(new BigDecimal(concession));
        return item;
    }

    @Test
    @DisplayName("重复商品行拒检时只回退对应明细行")
    void shouldRollbackRejectedReceiptByPurchaseDetailWhenProductRepeated()
    {
        InvPurchaseDetail firstLine = purchaseDetail(101L, "10.00", "5.00");
        firstLine.setDetailId(501L);
        firstLine.setReceivedQuantity(new BigDecimal("5.00"));
        InvPurchaseDetail secondLine = purchaseDetail(101L, "10.00", "7.00");
        secondLine.setDetailId(502L);
        secondLine.setReceivedQuantity(new BigDecimal("7.00"));
        InvInboundRecord rejectedSecondLine = inboundRecord(101L, "3.00", InvStatusConstants.QC_PENDING);
        ReflectionTestUtils.setField(rejectedSecondLine, "purchaseDetailId", 502L);

        InvPurchaseServiceImpl.rollbackRejectedPendingReceipts(
                List.of(firstLine, secondLine),
                InvPurchaseServiceImpl.pendingQualityCheckRecords(List.of(rejectedSecondLine)));

        assertThat(firstLine.getReceivedQuantity()).isEqualByComparingTo("5.00");
        assertThat(secondLine.getReceivedQuantity()).isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("已提交采购单不能物理删除")
    void shouldRejectDeletingSubmittedPurchase()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        InvPurchaseServiceImpl service = purchaseService(Map.of(20L, "WAREHOUSE"));
        InvPurchaseOrder order = new InvPurchaseOrder();
        order.setOrderId(1L);
        order.setShopDeptId(20L);
        order.setOrderNo("PO1");
        order.setStatus(InvStatusConstants.SUBMITTED);
        FakePurchaseOrderMapper purchaseOrderMapper = new FakePurchaseOrderMapper(order);
        InvInboundRecordMapper inboundRecordMapper = mock(InvInboundRecordMapper.class);
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", purchaseOrderMapper);
        ReflectionTestUtils.setField(service, "purchaseDetailMapper", new EmptyPurchaseDetailMapper());
        ReflectionTestUtils.setField(service, "inboundRecordMapper", inboundRecordMapper);

        assertThatThrownBy(() -> service.deletePurchase(1L, 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前状态不允许删除");
        assertThat(purchaseOrderMapper.deleteCalls).isZero();
        verifyNoInteractions(inboundRecordMapper);
    }

    @Test
    @DisplayName("已收货待质检采购单不能取消")
    void shouldRejectCancellingPurchasePendingQualityCheck()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        InvPurchaseServiceImpl service = purchaseService(Map.of(20L, "WAREHOUSE"));
        InvPurchaseOrder order = new InvPurchaseOrder();
        order.setOrderId(1L);
        order.setShopDeptId(20L);
        order.setOrderNo("PO1");
        order.setStatus(InvStatusConstants.SUBMITTED);
        order.setQcStatus(InvStatusConstants.QC_PENDING);
        FakePurchaseOrderMapper purchaseOrderMapper = new FakePurchaseOrderMapper(order);
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", purchaseOrderMapper);

        assertThatThrownBy(() -> service.cancelPurchase(1L, 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已发生收货");
        assertThat(purchaseOrderMapper.updateCalls).isZero();
    }

    @Test
    @DisplayName("未收货的草稿和已提交采购单仍可取消")
    void shouldAllowCancellingUnreceivedDraftAndSubmittedPurchase()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");

        FakePurchaseOrderMapper draftMapper = cancelPurchaseWithStatus(InvStatusConstants.DRAFT, null);
        assertThat(draftMapper.updateCalls).isEqualTo(1);
        assertThat(draftMapper.updatedStatus).isEqualTo(InvStatusConstants.CANCELLED);

        FakePurchaseOrderMapper submittedMapper = cancelPurchaseWithStatus(InvStatusConstants.SUBMITTED, null);
        assertThat(submittedMapper.updateCalls).isEqualTo(1);
        assertThat(submittedMapper.updatedStatus).isEqualTo(InvStatusConstants.CANCELLED);
    }

    private static InvPurchaseDetail purchaseDetail(Long productId, String receivedQuantity)
    {
        InvPurchaseDetail detail = new InvPurchaseDetail();
        detail.setProductId(productId);
        detail.setReceivedQuantity(new BigDecimal(receivedQuantity));
        return detail;
    }

    private static InvPurchaseDetail purchaseDetail(Long productId, String quantity, String unitPrice)
    {
        InvPurchaseDetail detail = new InvPurchaseDetail();
        detail.setProductId(productId);
        detail.setQuantity(new BigDecimal(quantity));
        detail.setUnitPrice(new BigDecimal(unitPrice));
        return detail;
    }

    private static InvInboundRecord inboundRecord(Long productId, String quantity, String qcResult)
    {
        InvInboundRecord record = new InvInboundRecord();
        record.setProductId(productId);
        record.setQuantity(new BigDecimal(quantity));
        record.setQcResult(qcResult);
        return record;
    }

    private static InvProduct product(Long productId, String productName, String sku, String spec, String unit,
            String supplierName, String purchasePrice)
    {
        InvProduct product = new InvProduct();
        product.setProductId(productId);
        product.setProductName(productName);
        product.setSku(sku);
        product.setSpec(spec);
        product.setUnit(unit);
        product.setSupplierName(supplierName);
        product.setPurchasePrice(new BigDecimal(purchasePrice));
        product.setStatus("0");
        return product;
    }

    private static InvPurchaseServiceImpl purchaseService(Map<Long, String> deptTypes)
    {
        InvPurchaseServiceImpl service = new InvPurchaseServiceImpl();
        service.deptScopeMapper = new FakeDeptScopeMapper(deptTypes);
        return service;
    }

    private static InvReceiveRequest receiveRequest(Long warehouseId)
    {
        InvReceiveRequest request = new InvReceiveRequest();
        request.setWarehouseId(warehouseId);
        request.setItems(Collections.emptyList());
        request.setArrivedTime(new Date(1_752_637_800_000L));
        return request;
    }

    private static InvReceiveRequest receiveRequest(Long warehouseId, Long detailId, String quantity)
    {
        InvReceiveRequest request = new InvReceiveRequest();
        request.setWarehouseId(warehouseId);
        request.setArrivedTime(new Date(1_752_637_800_000L));
        com.erp.inventory.domain.dto.InvReceiveItem item = new com.erp.inventory.domain.dto.InvReceiveItem();
        item.setDetailId(detailId);
        item.setReceiveQuantity(new BigDecimal(quantity));
        request.setItems(List.of(item));
        return request;
    }

    private static InvPurchaseOrderMapper purchaseOrderMapper(Long shopDeptId)
    {
        InvPurchaseOrder order = new InvPurchaseOrder();
        order.setOrderId(1L);
        order.setShopDeptId(shopDeptId);
        order.setOrderNo("PO1");
        order.setStatus(InvStatusConstants.SUBMITTED);
        return new FakePurchaseOrderMapper(order);
    }

    private static FakePurchaseOrderMapper cancelPurchaseWithStatus(String status, String qcStatus)
    {
        InvPurchaseServiceImpl service = purchaseService(Map.of(20L, "WAREHOUSE"));
        InvPurchaseOrder order = new InvPurchaseOrder();
        order.setOrderId(1L);
        order.setShopDeptId(20L);
        order.setOrderNo("PO1");
        order.setStatus(status);
        order.setQcStatus(qcStatus);
        FakePurchaseOrderMapper purchaseOrderMapper = new FakePurchaseOrderMapper(order);
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", purchaseOrderMapper);

        service.cancelPurchase(1L, 20L);

        return purchaseOrderMapper;
    }

    private static class FakeDeptScopeMapper implements InvDeptScopeMapper
    {
        @Override public List<Long> selectUserAuthorizedInventoryDeptIds(Long userId) { return java.util.Collections.emptyList(); }
        @Override public List<Long> selectAllActiveInventoryDeptIds() { return java.util.Collections.emptyList(); }
        private final Map<Long, String> deptTypes = new HashMap<>();

        FakeDeptScopeMapper(Map<Long, String> deptTypes)
        {
            this.deptTypes.putAll(deptTypes);
        }

        @Override
        public List<Long> selectSubDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectRelatedDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectActiveRelatedDeptIdsForReplenishment(
                Long deptId)
        {
            return selectRelatedDeptIds(deptId);
        }

        @Override
        public List<Long> selectAncestorDeptIds(Long deptId)
        {
            return Collections.emptyList();
        }

        @Override
        public Long selectRawBusinessRootDeptId(Long deptId)
        {
            return deptId;
        }

        @Override
        public List<Long> selectUserStoreScopeDeptIds(Long userId)
        {
            return Collections.emptyList();
        }

        @Override
        public List<Long> selectAllStoreDeptIds()
        {
            return Collections.emptyList();
        }

        @Override
        public int countDeptInScope(Long scopeDeptId, Long targetDeptId)
        {
            return 1;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            return 1;
        }

        @Override
        public String selectDeptNameById(Long deptId)
        {
            return "测试组织";
        }

        @Override
        public String selectDeptTypeById(Long deptId)
        {
            return deptTypes.get(deptId);
        }
    }

    private static class FakePurchaseOrderMapper implements InvPurchaseOrderMapper
    {
        private final InvPurchaseOrder order;
        private int deleteCalls;
        private int updateCalls;
        private String updatedStatus;

        FakePurchaseOrderMapper(InvPurchaseOrder order)
        {
            this.order = order;
        }

        @Override
        public InvPurchaseOrder selectInvPurchaseOrderById(Long orderId)
        {
            return order;
        }

        @Override
        public InvPurchaseOrder selectInvPurchaseOrderByIdForUpdate(Long orderId)
        {
            return order;
        }

        @Override
        public List<InvPurchaseOrder> selectInvPurchaseOrderList(InvPurchaseOrder order)
        {
            return Collections.emptyList();
        }

        @Override
        public List<InvPurchaseOrder> selectReturnablePurchaseOrderList(InvPurchaseOrder order)
        {
            return Collections.emptyList();
        }

        @Override
        public List<InvPurchaseOrder> selectMyInvPurchaseOrderList(InvPurchaseOrder order)
        {
            return Collections.emptyList();
        }

        @Override
        public int countBySupplierNameAndShop(String supplierName, Long shopDeptId)
        {
            return 0;
        }

        @Override
        public int insertInvPurchaseOrder(InvPurchaseOrder order)
        {
            return 1;
        }

        @Override
        public int updateInvPurchaseOrder(InvPurchaseOrder order)
        {
            updateCalls++;
            updatedStatus = order.getStatus();
            return 1;
        }

        @Override
        public int updatePurchaseContent(InvPurchaseOrder order)
        {
            updateCalls++;
            return 1;
        }

        @Override
        public int transitionPurchaseStatus(Long orderId, String expectedStatus,
                String status, String qcStatus, String updateBy)
        {
            updateCalls++;
            updatedStatus = status;
            return 1;
        }

        @Override
        public int deleteInvPurchaseOrderByIds(Long[] orderIds)
        {
            deleteCalls++;
            return 1;
        }
    }

    private static class EmptyPurchaseDetailMapper implements InvPurchaseDetailMapper
    {
        @Override
        public List<InvPurchaseDetail> selectInvPurchaseDetailByOrderId(Long orderId)
        {
            return Collections.emptyList();
        }

        @Override
        public List<InvPurchaseDetail> selectInvPurchaseDetailByOrderIdForUpdate(Long orderId)
        {
            return Collections.emptyList();
        }

        @Override
        public int insertInvPurchaseDetail(InvPurchaseDetail detail)
        {
            return 1;
        }

        @Override
        public int updateInvPurchaseDetail(InvPurchaseDetail detail)
        {
            return 1;
        }

        @Override
        public int deleteInvPurchaseDetailByOrderId(Long orderId)
        {
            return 1;
        }

        @Override
        public int batchInsertInvPurchaseDetail(List<InvPurchaseDetail> details)
        {
            return 1;
        }
    }

    private static class FakeProductMapper implements InvProductMapper
    {
        private final InvProduct product;

        private FakeProductMapper(InvProduct product)
        {
            this.product = product;
        }

        @Override
        public List<InvProduct> selectInvProductList(InvProduct product)
        {
            return Collections.emptyList();
        }

        @Override
        public List<InvProduct> selectInvProductListBySupplier(String supplierName, List<Long> scopeDeptIds)
        {
            return Collections.emptyList();
        }

        @Override
        public InvProduct selectInvProductById(Long productId)
        {
            return product != null && productId.equals(product.getProductId()) ? product : null;
        }

        @Override
        public InvProduct selectInvProductByCodeAndShop(String productCode, Long shopDeptId)
        {
            return null;
        }

        @Override
        public InvProduct selectInvProductByNaturalKey(Long categoryId, String productName, String spec, Long shopDeptId)
        {
            return null;
        }

        @Override
        public int countBusinessReferenceByProductId(Long productId)
        {
            return 0;
        }

        @Override
        public int insertInvProduct(InvProduct product)
        {
            return 0;
        }

        @Override
        public int updateInvProduct(InvProduct product)
        {
            return 0;
        }

        @Override
        public int deleteInvProductByIds(Long[] productIds)
        {
            return 0;
        }
    }

    private static class ListPurchaseDetailMapper extends EmptyPurchaseDetailMapper
    {
        private final List<InvPurchaseDetail> details;

        private ListPurchaseDetailMapper(List<InvPurchaseDetail> details)
        {
            this.details = details;
        }

        @Override
        public List<InvPurchaseDetail> selectInvPurchaseDetailByOrderIdForUpdate(Long orderId)
        {
            return details;
        }
    }

    private static class FakeInboundRecordMapper implements InvInboundRecordMapper
    {
        private final List<InvInboundRecord> insertedRecords = new java.util.ArrayList<>();

        @Override
        public List<InvInboundRecord> selectInvInboundRecordByOrderId(Long orderId)
        {
            return Collections.emptyList();
        }

        @Override
        public List<InvInboundRecord> selectPendingInvInboundRecordByOrderId(Long orderId)
        {
            return Collections.emptyList();
        }

        @Override
        public InvInboundRecord selectByBatchDetailIdForUpdate(Long batchDetailId)
        {
            return null;
        }

        @Override
        public int insertInvInboundRecord(InvInboundRecord record)
        {
            insertedRecords.add(record);
            return 1;
        }

        @Override
        public int updateInvInboundRecord(InvInboundRecord record)
        {
            return 1;
        }

        @Override
        public int deleteByOrderId(Long orderId)
        {
            return 1;
        }

        @Override
        public int deleteRejectedByOrderId(Long orderId)
        {
            return 1;
        }
    }
}
