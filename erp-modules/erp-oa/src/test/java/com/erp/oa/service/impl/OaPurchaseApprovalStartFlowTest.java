package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.redis.service.RedisService;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.domain.OaPurchase;
import com.erp.oa.domain.OaPurchaseApprovalStartOutbox;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.mapper.OaPurchaseMapper;
import com.erp.oa.service.BusinessFeatureGate;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("OA采购审批发起可恢复流程")
class OaPurchaseApprovalStartFlowTest
{
    @AfterEach
    void cleanSecurityContext()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("提交只在本地事务内落 SUBMITTING 和快照发件箱")
    void shouldPersistSubmittingAndOutboxWithoutCallingRemote()
            throws Exception
    {
        loginAsAdmin();
        OaPurchaseMapper mapper = mock(OaPurchaseMapper.class);
        RemoteApprovalService remote = mock(RemoteApprovalService.class);
        OaPurchaseApprovalStartOutboxService outboxService = mock(
                OaPurchaseApprovalStartOutboxService.class);
        OaPurchaseApprovalStartAfterCommitTrigger trigger = mock(
                OaPurchaseApprovalStartAfterCommitTrigger.class);
        OaPurchase current = purchase("draft", 3L, 0, null);
        OaPurchase saved = purchase("draft", 4L, 0, null);
        when(mapper.selectOaPurchaseByIdForUpdate(88L)).thenReturn(current);
        when(mapper.updateOaPurchase(any())).thenReturn(1);
        when(mapper.selectOaPurchaseById(88L)).thenReturn(saved);
        when(mapper.markApprovalSubmitting(88L, "draft", 4L, 1,
                "admin")).thenReturn(1);
        OaPurchaseApprovalStartOutbox outbox =
                new OaPurchaseApprovalStartOutbox();
        outbox.setOutboxId(10L);
        when(outboxService.enqueue(any(), any(), eq("admin")))
                .thenReturn(outbox);
        OaPurchase request = purchase("draft", 3L, 0, null);

        OaPurchase result = service(mapper, remote, outboxService, trigger)
                .submitPurchase(request, 201L);

        ArgumentCaptor<ApprovalStartRequest> startRequest = ArgumentCaptor
                .forClass(ApprovalStartRequest.class);
        verify(outboxService).enqueue(eq(saved), startRequest.capture(),
                eq("admin"));
        assertThat(startRequest.getValue().getIdempotencyKey())
                .isEqualTo("OA_PURCHASE:88:1");
        assertThat(startRequest.getValue().getBusinessRound()).isEqualTo(1);
        assertThat(startRequest.getValue().getVariables())
                .containsEntry("title", "门店耗材采购")
                .containsEntry("amount", new BigDecimal("128.50"));
        assertThat(result.getStatus()).isEqualTo("submitting");
        assertThat(result.getApprovalRound()).isEqualTo(1);
        assertThat(result.getApprovalInstanceId()).isNull();
        assertThat(result.getRowVersion()).isEqualTo(5L);
        verify(trigger).trigger(10L);
        verify(remote, never()).start(any(), eq(SecurityConstants.INNER));
        assertThat(OaPurchaseServiceImpl.class
                .getMethod("submitPurchase", OaPurchase.class, Long.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    @DisplayName("重复提交 SUBMITTING 采购单复用当前轮发件箱")
    void shouldReuseCurrentOutboxForDuplicateSubmittingRequest()
    {
        loginAsAdmin();
        OaPurchaseMapper mapper = mock(OaPurchaseMapper.class);
        RemoteApprovalService remote = mock(RemoteApprovalService.class);
        OaPurchaseApprovalStartOutboxService outboxService = mock(
                OaPurchaseApprovalStartOutboxService.class);
        OaPurchaseApprovalStartAfterCommitTrigger trigger = mock(
                OaPurchaseApprovalStartAfterCommitTrigger.class);
        OaPurchase current = purchase("submitting", 5L, 1, null);
        when(mapper.selectOaPurchaseByIdForUpdate(88L)).thenReturn(current);
        OaPurchaseApprovalStartOutbox existing =
                new OaPurchaseApprovalStartOutbox();
        existing.setOutboxId(10L);
        when(outboxService.selectByPurchaseRound(88L, 1))
                .thenReturn(existing);

        OaPurchase result = service(mapper, remote, outboxService, trigger)
                .submitPurchase(purchase("submitting", 5L, 1, null), 201L);

        assertThat(result).isSameAs(current);
        verify(trigger).trigger(10L);
        verify(outboxService, never()).enqueue(any(), any(), any());
        verify(mapper, never()).markApprovalSubmitting(any(), any(), any(),
                any(), any());
        verify(remote, never()).start(any(), any());
    }

    @Test
    @DisplayName("审批实例尚未关联时早到回调明确返回可重试")
    void shouldRetryEarlyCallbackUntilInstanceIsLinked()
    {
        OaPurchaseMapper mapper = mock(OaPurchaseMapper.class);
        OaPurchase submitting = purchase("submitting", 5L, 2, null);
        when(mapper.selectOaPurchaseByIdForUpdate(88L))
                .thenReturn(submitting);
        ApprovalBusinessCallbackRequest request =
                new ApprovalBusinessCallbackRequest();
        request.setEventKey("APPROVE:9001:1");
        request.setInstanceId(9001L);
        request.setBusinessCode("OA_PURCHASE");
        request.setBusinessId("88");
        request.setBusinessRound(2);
        request.setAction("APPROVE");
        request.setPayload("{\"targetStatus\":\"APPROVED\"}");

        ApprovalBusinessCallbackResponse response = service(mapper,
                mock(RemoteApprovalService.class),
                mock(OaPurchaseApprovalStartOutboxService.class),
                mock(OaPurchaseApprovalStartAfterCommitTrigger.class))
                        .applyApprovalCallback(request);

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getInvalidated()).isFalse();
        assertThat(response.getCode()).isEqualTo("APPROVAL_LINK_PENDING");
        verify(mapper, never()).updateOaPurchase(any());
    }

    private static OaPurchaseServiceImpl service(OaPurchaseMapper mapper,
            RemoteApprovalService remote,
            OaPurchaseApprovalStartOutboxService outboxService,
            OaPurchaseApprovalStartAfterCommitTrigger trigger)
    {
        return new OaPurchaseServiceImpl(mapper,
                mock(OaDeptScopeMapper.class), mock(ShopScopeService.class),
                mock(RedisService.class), remote, new ObjectMapper(),
                mock(BusinessFeatureGate.class), outboxService, trigger);
    }

    private static OaPurchase purchase(String status, Long version,
            Integer round, Long instanceId)
    {
        OaPurchase purchase = new OaPurchase();
        purchase.setPurchaseId(88L);
        purchase.setTitle("门店耗材采购");
        purchase.setApplicantId(1L);
        purchase.setApplicantName("admin");
        purchase.setApplicantDeptId(100L);
        purchase.setApplicantDeptName("运营部");
        purchase.setShopDeptId(201L);
        purchase.setShopDeptName("测试门店");
        purchase.setAmount(new BigDecimal("128.50"));
        purchase.setReason("日常补货");
        purchase.setStatus(status);
        purchase.setApprovalRound(round);
        purchase.setApprovalInstanceId(instanceId);
        purchase.setRowVersion(version);
        return purchase;
    }

    private static void loginAsAdmin()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
    }
}
