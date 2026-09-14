package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvPurchaseDetail;
import com.erp.inventory.domain.InvInboundRecord;
import com.erp.inventory.domain.InvPurchaseOrder;
import com.erp.inventory.domain.InvPurchaseReturn;
import com.erp.inventory.domain.InvPurchaseReturnDetail;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvInboundRecordMapper;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvPurchaseDetailMapper;
import com.erp.inventory.mapper.InvPurchaseOrderMapper;
import com.erp.inventory.mapper.InvPurchaseReturnDetailMapper;
import com.erp.inventory.mapper.InvPurchaseReturnMapper;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

@DisplayName("采购退货服务")
class InvPurchaseReturnServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("门店上下文不能保存采购退货单")
    void shouldRejectStoreContextWhenSavingPurchaseReturn()
    {
        SecurityContextHolder.setUserId("200");
        InvPurchaseReturnServiceImpl service = purchaseReturnService(Map.of(10L, "STORE"));

        assertThatThrownBy(() -> service.saveDraft(new InvPurchaseReturn(), Collections.emptyList(), 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择仓库");
    }

    @Test
    @DisplayName("门店上下文不能提交采购退货单")
    void shouldRejectStoreContextWhenSubmittingPurchaseReturn()
    {
        SecurityContextHolder.setUserId("200");
        InvPurchaseReturnServiceImpl service = purchaseReturnService(Map.of(10L, "STORE"));

        assertThatThrownBy(() -> service.submitReturn(new InvPurchaseReturn(), Collections.emptyList(), 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择仓库");
    }

    @Test
    @DisplayName("门店上下文不能确认采购退货")
    void shouldRejectStoreContextWhenConfirmingPurchaseReturn()
    {
        SecurityContextHolder.setUserId("200");
        InvPurchaseReturnServiceImpl service = purchaseReturnService(Map.of(10L, "STORE"));

        assertThatThrownBy(() -> service.confirmReturn(1L, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择仓库");
    }

    @Test
    @DisplayName("采购退货当前单据重复商品行需合并校验")
    void shouldAggregateDuplicateProductRowsWhenSubmittingPurchaseReturn() throws Exception
    {
        loginAsAdmin();
        InvPurchaseReturnServiceImpl service = purchasReturnServiceWithQuantityFixtures(
                purchaseReturnDetail(101L, "3.00"), purchaseReturnDetail(101L, "3.00"));

        assertThatThrownBy(() -> service.submitReturn(purchaseReturn(), List.of(
                purchaseReturnDetail(101L, "3.00"), purchaseReturnDetail(101L, "3.00")), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超过原采购已合格或让步入库数量");
    }

    @Test
    @DisplayName("采购退货数量必须大于0")
    void shouldRejectNonPositivePurchaseReturnQuantity() throws Exception
    {
        loginAsAdmin();
        InvPurchaseReturnServiceImpl service = purchasReturnServiceWithQuantityFixtures(
                purchaseReturnDetail(101L, "0.00"));

        assertThatThrownBy(() -> service.submitReturn(purchaseReturn(),
                List.of(purchaseReturnDetail(101L, "0.00")), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("退货数量必须大于0");
    }

    @Test
    @DisplayName("采购退货明细由原采购单重算商品信息和金额")
    void shouldNormalizePurchaseReturnDetailsFromOriginalPurchaseOrder() throws Exception
    {
        loginAsAdmin();
        PurchaseReturnFixture fixture = purchaseReturnServiceWithOriginalPurchaseDetail(originalPurchaseDetail());
        InvPurchaseReturnDetail input = purchaseReturnDetail(101L, "2.00");
        input.setProductName("前端伪造商品");
        input.setSku("BAD-SKU");
        input.setSpec("错误规格");
        input.setUnit("错误单位");
        input.setUnitPrice(new BigDecimal("1.00"));
        input.setAmount(new BigDecimal("1.00"));

        fixture.service.submitReturn(purchaseReturn(), List.of(input), 20L);

        InvPurchaseReturnDetail savedDetail = fixture.detailsRef.get().get(0);
        assertThat(savedDetail.getProductName()).isEqualTo("原采购商品");
        assertThat(savedDetail.getSku()).isEqualTo("SKU-ORIG");
        assertThat(savedDetail.getSpec()).isEqualTo("100g");
        assertThat(savedDetail.getUnit()).isEqualTo("盒");
        assertThat(savedDetail.getUnitPrice()).isEqualByComparingTo("9.50");
        assertThat(savedDetail.getAmount()).isEqualByComparingTo("19.00");
        assertThat(fixture.returnRef.get().getTotalAmount()).isEqualByComparingTo("19.00");
        assertThat(fixture.returnRef.get().getPurchaseOrderNo()).isEqualTo("PO-SOURCE");
        assertThat(fixture.returnRef.get().getSupplierName()).isEqualTo("来源供应商");
        assertThat(fixture.returnRef.get().getReturnDate()).isNotNull();
    }

    @Test
    @DisplayName("采购退货重复商品按原采购明细行重算金额")
    void shouldNormalizeDuplicatePurchaseReturnDetailsBySourceDetailId() throws Exception
    {
        loginAsAdmin();
        InvPurchaseDetail firstLine = originalPurchaseDetail(501L, "第一行商品", "9.50", "5.00");
        InvPurchaseDetail secondLine = originalPurchaseDetail(502L, "第二行商品", "18.00", "4.00");
        PurchaseReturnFixture fixture = purchaseReturnServiceWithOriginalPurchaseDetail(firstLine, secondLine);
        InvPurchaseReturnDetail input = purchaseReturnDetail(101L, "2.00");
        input.setPurchaseDetailId(502L);

        fixture.service.submitReturn(purchaseReturn(), List.of(input), 20L);

        InvPurchaseReturnDetail savedDetail = fixture.detailsRef.get().get(0);
        assertThat(savedDetail.getPurchaseDetailId()).isEqualTo(502L);
        assertThat(savedDetail.getProductName()).isEqualTo("第二行商品");
        assertThat(savedDetail.getUnitPrice()).isEqualByComparingTo("18.00");
        assertThat(savedDetail.getAmount()).isEqualByComparingTo("36.00");
        assertThat(fixture.returnRef.get().getTotalAmount()).isEqualByComparingTo("36.00");
    }

    @Test
    @DisplayName("OE采购明细可按物料类型和原明细退货")
    void shouldNormalizeOePurchaseReturnBySourceDetail() throws Exception
    {
        loginAsAdmin();
        InvPurchaseDetail original = new InvPurchaseDetail();
        original.setDetailId(701L);
        original.setItemType("oe");
        original.setItemId(801L);
        original.setItemCode("OE-801");
        original.setItemName("茶叶展架");
        original.setProductName("茶叶展架");
        original.setUnit("个");
        original.setUnitPrice(new BigDecimal("120.00"));
        original.setReceivedQuantity(new BigDecimal("2.00"));
        original.setStockedQuantity(new BigDecimal("2.00"));
        PurchaseReturnFixture fixture = purchaseReturnServiceWithOriginalPurchaseDetail(original);
        InvPurchaseReturnDetail input = new InvPurchaseReturnDetail();
        input.setPurchaseDetailId(701L);
        input.setItemType("oe");
        input.setItemId(801L);
        input.setQuantity(BigDecimal.ONE);

        fixture.service.submitReturn(purchaseReturn(), List.of(input), 20L);

        InvPurchaseReturnDetail saved = fixture.detailsRef.get().get(0);
        assertThat(saved.getItemType()).isEqualTo("oe");
        assertThat(saved.getItemId()).isEqualTo(801L);
        assertThat(saved.getItemCode()).isEqualTo("OE-801");
        assertThat(saved.getProductId()).isNull();
        assertThat(saved.getAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("采购退货重复商品缺少原明细行时拒绝保存")
    void shouldRejectAmbiguousPurchaseReturnDetailWithoutSourceDetailId() throws Exception
    {
        loginAsAdmin();
        InvPurchaseDetail firstLine = originalPurchaseDetail(501L, "第一行商品", "9.50", "5.00");
        InvPurchaseDetail secondLine = originalPurchaseDetail(502L, "第二行商品", "18.00", "4.00");
        PurchaseReturnFixture fixture = purchaseReturnServiceWithOriginalPurchaseDetail(firstLine, secondLine);

        assertThatThrownBy(() -> fixture.service.submitReturn(purchaseReturn(),
                List.of(purchaseReturnDetail(101L, "2.00")), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择原采购明细");
    }

    @Test
    @DisplayName("采购退货列表必须精确限定当前仓库")
    void shouldScopePurchaseReturnListToExactWarehouse() throws Exception
    {
        loginAsAdmin();
        InvPurchaseReturnServiceImpl service = purchaseReturnService(Map.of(20L, "WAREHOUSE"));
        InvPurchaseReturnMapper returnMapper = mock(InvPurchaseReturnMapper.class);
        AtomicReference<InvPurchaseReturn> captured = new AtomicReference<>();
        when(returnMapper.selectInvPurchaseReturnList(any(InvPurchaseReturn.class))).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return Collections.emptyList();
        });
        ReflectionTestUtils.setField(service, "purchaseReturnMapper", returnMapper);

        service.selectReturnList(new InvPurchaseReturn(), 20L);

        assertThat(captured.get().getShopDeptId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("我的采购退货按申请人和当前仓库过滤")
    void shouldScopeMyPurchaseReturnsToApplicantAndExactWarehouse() throws Exception
    {
        loginAsAdmin();
        InvPurchaseReturnServiceImpl service = purchaseReturnService(Map.of(20L, "WAREHOUSE"));
        InvPurchaseReturnMapper returnMapper = mock(InvPurchaseReturnMapper.class);
        AtomicReference<InvPurchaseReturn> captured = new AtomicReference<>();
        when(returnMapper.selectMyInvPurchaseReturnList(any(InvPurchaseReturn.class))).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return Collections.emptyList();
        });
        ReflectionTestUtils.setField(service, "purchaseReturnMapper", returnMapper);

        service.selectMyReturns(new InvPurchaseReturn(), 20L);

        assertThat(captured.get().getApplicantId()).isEqualTo(1L);
        assertThat(captured.get().getShopDeptId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("草稿也必须包含日期、原因、责任和至少一条明细")
    void shouldRejectIncompletePurchaseReturnDraft() throws Exception
    {
        loginAsAdmin();
        PurchaseReturnFixture fixture = purchaseReturnServiceWithOriginalPurchaseDetail(originalPurchaseDetail());
        InvPurchaseReturn input = purchaseReturn();
        input.setReturnDate(null);
        input.setReturnReason(null);
        input.setResponsibility(null);

        assertThatThrownBy(() -> fixture.service.saveDraft(input, Collections.emptyList(), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("退货日期");

        input.setReturnDate(new java.util.Date());
        assertThatThrownBy(() -> fixture.service.saveDraft(input, Collections.emptyList(), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("退货原因");

        input.setReturnReason("包装破损");
        assertThatThrownBy(() -> fixture.service.saveDraft(input, Collections.emptyList(), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("责任归属");

        input.setResponsibility("supplier");
        assertThatThrownBy(() -> fixture.service.saveDraft(input, Collections.emptyList(), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("至少一条退货明细");
    }

    @Test
    @DisplayName("采购退货责任归属必须来自固定枚举")
    void shouldRejectUnknownPurchaseReturnResponsibility() throws Exception
    {
        loginAsAdmin();
        PurchaseReturnFixture fixture = purchaseReturnServiceWithOriginalPurchaseDetail(originalPurchaseDetail());
        InvPurchaseReturn input = validPurchaseReturn();
        input.setResponsibility("frontend-forged");

        assertThatThrownBy(() -> fixture.service.saveDraft(input,
                List.of(purchaseReturnDetail(101L, "1.00")), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("责任归属");
    }

    @Test
    @DisplayName("来源采购单详情返回服务端计算的历史已退和可退数量")
    void shouldProjectServerCalculatedReturnableSourceOrderQuantities()
    {
        loginAsAdmin();
        InvPurchaseReturnServiceImpl service = purchaseReturnService(Map.of(20L, "WAREHOUSE"));
        InvPurchaseOrderMapper orderMapper = mock(InvPurchaseOrderMapper.class);
        InvPurchaseDetailMapper purchaseDetailMapper = mock(InvPurchaseDetailMapper.class);
        InvPurchaseReturnDetailMapper returnDetailMapper = mock(InvPurchaseReturnDetailMapper.class);
        InvPurchaseOrder source = new InvPurchaseOrder();
        source.setOrderId(10L);
        source.setStatus(InvStatusConstants.RECEIVED);
        source.setShopDeptId(20L);
        InvPurchaseDetail detail = originalPurchaseDetail();
        when(orderMapper.selectInvPurchaseOrderById(10L)).thenReturn(source);
        when(purchaseDetailMapper.selectInvPurchaseDetailByOrderId(10L)).thenReturn(List.of(detail));
        when(returnDetailMapper.sumHistoricalReturnQuantityByPurchaseDetailId(10L, 501L, null))
                .thenReturn(new BigDecimal("2.00"));
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "purchaseDetailMapper", purchaseDetailMapper);
        ReflectionTestUtils.setField(service, "purchaseReturnDetailMapper", returnDetailMapper);

        InvPurchaseOrder result = service.getReturnableSourceOrder(10L, 20L);

        assertThat(result.getDetails()).singleElement().satisfies(item -> {
            assertThat(item.getHistoricalReturnedQuantity()).isEqualByComparingTo("2.00");
            assertThat(item.getReturnableQuantity()).isEqualByComparingTo("3.00");
        });
    }

    @Test
    @DisplayName("来源采购单列表必须精确限定当前仓库")
    void shouldScopeReturnableSourceOrderListToExactWarehouse()
    {
        loginAsAdmin();
        InvPurchaseReturnServiceImpl service = purchaseReturnService(Map.of(20L, "WAREHOUSE"));
        InvPurchaseOrderMapper orderMapper = mock(InvPurchaseOrderMapper.class);
        AtomicReference<InvPurchaseOrder> captured = new AtomicReference<>();
        when(orderMapper.selectReturnablePurchaseOrderList(any(InvPurchaseOrder.class)))
                .thenAnswer(invocation -> {
                    captured.set(invocation.getArgument(0));
                    return Collections.emptyList();
                });
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", orderMapper);

        service.selectReturnableSourceOrders(new InvPurchaseOrder(), 20L);

        assertThat(captured.get().getShopDeptId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("采购退货附件拒绝脚本协议和路径穿越")
    void shouldRejectUnsafePurchaseReturnAttachmentUrls() throws Exception
    {
        loginAsAdmin();
        PurchaseReturnFixture fixture = purchaseReturnServiceWithOriginalPurchaseDetail(originalPurchaseDetail());
        InvPurchaseReturn scriptUrl = validPurchaseReturn();
        scriptUrl.setAttachmentUrls("javascript:alert(1)");

        assertThatThrownBy(() -> fixture.service.saveDraft(scriptUrl,
                List.of(purchaseReturnDetail(101L, "1.00")), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("附件地址无效");

        InvPurchaseReturn traversalUrl = validPurchaseReturn();
        traversalUrl.setAttachmentUrls("/profile/upload/../private/secret.pdf");
        assertThatThrownBy(() -> fixture.service.saveDraft(traversalUrl,
                List.of(purchaseReturnDetail(101L, "1.00")), 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("附件地址无效");
    }

    @Test
    @DisplayName("取消操作必须以锁后状态为准")
    void shouldRecheckLockedStatusBeforeCancelling()
    {
        loginAsAdmin();
        InvPurchaseReturnServiceImpl service = purchaseReturnService(Map.of(20L, "WAREHOUSE"));
        InvPurchaseReturnMapper returnMapper = mock(InvPurchaseReturnMapper.class);
        InvPurchaseReturn visible = validPurchaseReturn();
        visible.setReturnId(1L);
        visible.setShopDeptId(20L);
        visible.setStatus(InvStatusConstants.DRAFT);
        InvPurchaseReturn locked = validPurchaseReturn();
        locked.setReturnId(1L);
        locked.setShopDeptId(20L);
        locked.setStatus(InvStatusConstants.RETURNED);
        when(returnMapper.selectInvPurchaseReturnById(1L)).thenReturn(visible);
        when(returnMapper.selectInvPurchaseReturnByIdForUpdate(1L)).thenReturn(locked);
        ReflectionTestUtils.setField(service, "purchaseReturnMapper", returnMapper);

        assertThatThrownBy(() -> service.cancelReturn(1L, 20L))
                .isInstanceOf(ServiceException.class);
        verify(returnMapper, never()).updateInvPurchaseReturn(any(InvPurchaseReturn.class));
        verify(returnMapper, never()).updateInvPurchaseReturnStatus(anyLong(), any(), any());
    }

    private static InvPurchaseReturnServiceImpl purchaseReturnService(Map<Long, String> deptTypes)
    {
        InvPurchaseReturnServiceImpl service = new InvPurchaseReturnServiceImpl();
        service.deptScopeMapper = new FakeDeptScopeMapper(deptTypes);
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", sourcePurchaseOrderMapper());
        return service;
    }

    private static InvPurchaseReturnServiceImpl purchasReturnServiceWithQuantityFixtures(
            InvPurchaseReturnDetail... storedDetails) throws Exception
    {
        InvPurchaseReturnServiceImpl service = purchaseReturnService(Map.of(20L, "WAREHOUSE"));
        InvPurchaseReturnMapper returnMapper = mock(InvPurchaseReturnMapper.class);
        InvPurchaseReturnDetailMapper detailMapper = mock(InvPurchaseReturnDetailMapper.class);
        InvPurchaseDetailMapper purchaseDetailMapper = mock(InvPurchaseDetailMapper.class);
        InvNumberSequenceMapper numberSequenceMapper = mock(InvNumberSequenceMapper.class);
        AtomicReference<InvPurchaseReturn> storedReturn = new AtomicReference<>();
        AtomicReference<List<InvPurchaseReturnDetail>> detailsRef = new AtomicReference<>(new ArrayList<>());

        doAnswer(invocation -> {
            InvPurchaseReturn purchaseReturn = invocation.getArgument(0);
            purchaseReturn.setReturnId(1L);
            storedReturn.set(purchaseReturn);
            return 1;
        }).when(returnMapper).insertInvPurchaseReturn(any(InvPurchaseReturn.class));
        doAnswer(invocation -> {
            List<InvPurchaseReturnDetail> details = new ArrayList<>(invocation.getArgument(0));
            detailsRef.set(details);
            return details.size();
        }).when(detailMapper).batchInsertInvPurchaseReturnDetail(any(List.class));
        when(returnMapper.updateInvPurchaseReturn(any(InvPurchaseReturn.class))).thenAnswer(invocation -> {
            InvPurchaseReturn update = invocation.getArgument(0);
            InvPurchaseReturn stored = storedReturn.get();
            if (stored == null) return 0;
            if (update.getStatus() != null) stored.setStatus(update.getStatus());
            return 1;
        });
        when(returnMapper.updateInvPurchaseReturnStatus(anyLong(), any(), any())).thenAnswer(invocation -> {
            InvPurchaseReturn stored = storedReturn.get();
            if (stored == null) return 0;
            stored.setStatus(invocation.getArgument(1));
            return 1;
        });
        when(returnMapper.selectInvPurchaseReturnById(1L)).thenAnswer(invocation -> storedReturn.get());
        when(returnMapper.selectInvPurchaseReturnByIdForUpdate(1L)).thenAnswer(invocation -> storedReturn.get());
        when(detailMapper.selectInvPurchaseReturnDetailByReturnId(1L)).thenAnswer(invocation -> detailsRef.get());
        when(detailMapper.selectInvPurchaseReturnDetailByReturnIdForUpdate(1L)).thenAnswer(invocation -> detailsRef.get());
        when(numberSequenceMapper.selectLastInsertId()).thenReturn(1L);
        InvPurchaseDetail purchaseDetail = new InvPurchaseDetail();
        purchaseDetail.setProductId(101L);
        purchaseDetail.setProductName("测试商品");
        purchaseDetail.setReceivedQuantity(new BigDecimal("5.00"));
        purchaseDetail.setStockedQuantity(new BigDecimal("5.00"));
        when(purchaseDetailMapper.selectInvPurchaseDetailByOrderId(10L)).thenReturn(List.of(purchaseDetail));
        stubCurrentFacts(service, purchaseDetailMapper, purchaseDetail);
        when(detailMapper.sumHistoricalReturnQuantity(10L, 101L, 1L)).thenReturn(BigDecimal.ZERO);

        ReflectionTestUtils.setField(service, "purchaseReturnMapper", returnMapper);
        ReflectionTestUtils.setField(service, "purchaseReturnDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "purchaseDetailMapper", purchaseDetailMapper);
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", sourcePurchaseOrderMapper());
        ReflectionTestUtils.setField(service, "numberSequenceMapper", numberSequenceMapper);
        return service;
    }

    private static PurchaseReturnFixture purchaseReturnServiceWithOriginalPurchaseDetail(
            InvPurchaseDetail... purchaseDetails) throws Exception
    {
        InvPurchaseReturnServiceImpl service = purchaseReturnService(Map.of(20L, "WAREHOUSE"));
        InvPurchaseReturnMapper returnMapper = mock(InvPurchaseReturnMapper.class);
        InvPurchaseReturnDetailMapper detailMapper = mock(InvPurchaseReturnDetailMapper.class);
        InvPurchaseDetailMapper purchaseDetailMapper = mock(InvPurchaseDetailMapper.class);
        InvNumberSequenceMapper numberSequenceMapper = mock(InvNumberSequenceMapper.class);
        AtomicReference<InvPurchaseReturn> storedReturn = new AtomicReference<>();
        AtomicReference<List<InvPurchaseReturnDetail>> detailsRef = new AtomicReference<>(new ArrayList<>());

        doAnswer(invocation -> {
            InvPurchaseReturn purchaseReturn = invocation.getArgument(0);
            purchaseReturn.setReturnId(1L);
            storedReturn.set(purchaseReturn);
            return 1;
        }).when(returnMapper).insertInvPurchaseReturn(any(InvPurchaseReturn.class));
        doAnswer(invocation -> {
            List<InvPurchaseReturnDetail> details = new ArrayList<>(invocation.getArgument(0));
            detailsRef.set(details);
            return details.size();
        }).when(detailMapper).batchInsertInvPurchaseReturnDetail(any(List.class));
        when(returnMapper.updateInvPurchaseReturn(any(InvPurchaseReturn.class))).thenAnswer(invocation -> {
            InvPurchaseReturn update = invocation.getArgument(0);
            InvPurchaseReturn stored = storedReturn.get();
            if (stored == null) return 0;
            if (update.getStatus() != null) stored.setStatus(update.getStatus());
            return 1;
        });
        when(returnMapper.updateInvPurchaseReturnStatus(anyLong(), any(), any())).thenAnswer(invocation -> {
            InvPurchaseReturn stored = storedReturn.get();
            if (stored == null) return 0;
            stored.setStatus(invocation.getArgument(1));
            return 1;
        });
        when(returnMapper.selectInvPurchaseReturnById(1L)).thenAnswer(invocation -> storedReturn.get());
        when(returnMapper.selectInvPurchaseReturnByIdForUpdate(1L)).thenAnswer(invocation -> storedReturn.get());
        when(detailMapper.selectInvPurchaseReturnDetailByReturnId(1L)).thenAnswer(invocation -> detailsRef.get());
        when(detailMapper.selectInvPurchaseReturnDetailByReturnIdForUpdate(1L)).thenAnswer(invocation -> detailsRef.get());
        when(numberSequenceMapper.selectLastInsertId()).thenReturn(1L);
        when(purchaseDetailMapper.selectInvPurchaseDetailByOrderId(10L)).thenReturn(List.of(purchaseDetails));
        stubCurrentFacts(service, purchaseDetailMapper, purchaseDetails);
        when(detailMapper.sumHistoricalReturnQuantity(10L, 101L, 1L)).thenReturn(BigDecimal.ZERO);

        ReflectionTestUtils.setField(service, "purchaseReturnMapper", returnMapper);
        ReflectionTestUtils.setField(service, "purchaseReturnDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "purchaseDetailMapper", purchaseDetailMapper);
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", sourcePurchaseOrderMapper());
        ReflectionTestUtils.setField(service, "numberSequenceMapper", numberSequenceMapper);
        return new PurchaseReturnFixture(service, storedReturn, detailsRef);
    }

    @Test
    @DisplayName("到货待检不能作为普通退货额度，部分合格只允许退合格部分")
    void pendingReceiptsMustNotConsumePreviouslyStockedGoods() throws Exception
    {
        loginAsAdmin();
        InvPurchaseDetail pending = originalPurchaseDetail();
        pending.setReceivedQuantity(new BigDecimal("100"));
        pending.setStockedQuantity(BigDecimal.ZERO);
        PurchaseReturnFixture fixture = purchaseReturnServiceWithOriginalPurchaseDetail(pending);
        assertThatThrownBy(() -> fixture.service.submitReturn(purchaseReturn(),
                List.of(purchaseReturnDetail(101L, "1")), 20L))
                .isInstanceOf(ServiceException.class).hasMessageContaining("入库数量");

        InvPurchaseDetail partial = originalPurchaseDetail();
        partial.setReceivedQuantity(new BigDecimal("100"));
        partial.setStockedQuantity(new BigDecimal("20"));
        PurchaseReturnFixture partialFixture = purchaseReturnServiceWithOriginalPurchaseDetail(partial);
        partialFixture.service.submitReturn(purchaseReturn(), List.of(purchaseReturnDetail(101L, "20")), 20L);
        assertThat(partialFixture.returnRef.get().getStatus()).isEqualTo(InvStatusConstants.SUBMITTED);
    }

    private static void stubCurrentFacts(InvPurchaseReturnServiceImpl service,
            InvPurchaseDetailMapper mapper, InvPurchaseDetail... details)
    {
        when(mapper.selectInvPurchaseDetailByOrderIdForUpdate(10L)).thenReturn(List.of(details));
        List<InvInboundRecord> facts = new ArrayList<>();
        for (InvPurchaseDetail detail : details)
        {
            InvInboundRecord fact = new InvInboundRecord();
            fact.setPurchaseOrderId(10L); fact.setPurchaseDetailId(detail.getDetailId());
            fact.setProductId(detail.getProductId()); fact.setQuantity(detail.getStockedQuantity());
            fact.setQcResult("passed"); facts.add(fact);
        }
        InvInboundRecordMapper inbounds = mock(InvInboundRecordMapper.class);
        when(inbounds.selectByOrderIdForUpdate(10L)).thenReturn(facts);
        ReflectionTestUtils.setField(service, "inboundRecordMapper", inbounds);
    }

    private static InvPurchaseDetail originalPurchaseDetail()
    {
        return originalPurchaseDetail(501L, "原采购商品", "9.50", "5.00");
    }

    private static InvPurchaseDetail originalPurchaseDetail(Long detailId, String productName, String unitPrice,
            String receivedQuantity)
    {
        InvPurchaseDetail purchaseDetail = new InvPurchaseDetail();
        purchaseDetail.setDetailId(detailId);
        purchaseDetail.setProductId(101L);
        purchaseDetail.setProductName(productName);
        purchaseDetail.setSku("SKU-ORIG");
        purchaseDetail.setSpec("100g");
        purchaseDetail.setUnit("盒");
        purchaseDetail.setUnitPrice(new BigDecimal(unitPrice));
        purchaseDetail.setReceivedQuantity(new BigDecimal(receivedQuantity));
        purchaseDetail.setStockedQuantity(new BigDecimal(receivedQuantity));
        return purchaseDetail;
    }

    private static void loginAsAdmin()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        LoginUser loginUser = new LoginUser();
        SysUser sysUser = new SysUser();
        sysUser.setDeptId(20L);
        loginUser.setSysUser(sysUser);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static InvPurchaseReturn purchaseReturn()
    {
        InvPurchaseReturn purchaseReturn = new InvPurchaseReturn();
        purchaseReturn.setPurchaseOrderId(10L);
        purchaseReturn.setReturnTitle("采购退货");
        purchaseReturn.setSupplierName("供应商A");
        purchaseReturn.setReturnDate(new java.util.Date());
        purchaseReturn.setReturnReason("包装破损");
        purchaseReturn.setResponsibility("supplier");
        return purchaseReturn;
    }

    private static InvPurchaseReturn validPurchaseReturn()
    {
        InvPurchaseReturn purchaseReturn = purchaseReturn();
        purchaseReturn.setReturnDate(new java.util.Date());
        purchaseReturn.setReturnReason("包装破损");
        purchaseReturn.setResponsibility("supplier");
        return purchaseReturn;
    }

    private static InvPurchaseOrderMapper sourcePurchaseOrderMapper()
    {
        InvPurchaseOrderMapper mapper = mock(InvPurchaseOrderMapper.class);
        InvPurchaseOrder source = new InvPurchaseOrder();
        source.setOrderId(10L);
        source.setOrderNo("PO-SOURCE");
        source.setSupplierName("来源供应商");
        source.setShopDeptId(20L);
        when(mapper.selectInvPurchaseOrderById(10L)).thenReturn(source);
        when(mapper.selectInvPurchaseOrderByIdForUpdate(10L)).thenReturn(source);
        return mapper;
    }

    private static InvPurchaseReturnDetail purchaseReturnDetail(Long productId, String quantity)
    {
        InvPurchaseReturnDetail detail = new InvPurchaseReturnDetail();
        detail.setProductId(productId);
        detail.setProductName("测试商品");
        detail.setQuantity(new BigDecimal(quantity));
        detail.setUnitPrice(BigDecimal.ONE);
        return detail;
    }

    private record PurchaseReturnFixture(
            InvPurchaseReturnServiceImpl service,
            AtomicReference<InvPurchaseReturn> returnRef,
            AtomicReference<List<InvPurchaseReturnDetail>> detailsRef)
    {
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
}
