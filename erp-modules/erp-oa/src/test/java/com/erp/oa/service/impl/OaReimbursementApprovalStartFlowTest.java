package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
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
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.config.OaReimbursementProperties;
import com.erp.oa.domain.OaReimbursement;
import com.erp.oa.domain.OaReimbursementApprovalStartOutbox;
import com.erp.oa.domain.OaReimbursementItem;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.mapper.OaReimbursementMapper;
import com.erp.oa.service.BusinessFeatureGate;
import com.erp.oa.service.invoice.OaInvoiceRecognitionCoordinator;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("OA报销审批发起可恢复流程")
class OaReimbursementApprovalStartFlowTest
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
        OaReimbursementMapper mapper = mock(OaReimbursementMapper.class);
        RemoteApprovalService remote = mock(RemoteApprovalService.class);
        OaReimbursementApprovalStartOutboxService outboxService = mock(
                OaReimbursementApprovalStartOutboxService.class);
        OaReimbursementApprovalStartAfterCommitTrigger trigger = mock(
                OaReimbursementApprovalStartAfterCommitTrigger.class);
        BusinessFeatureGate featureGate = mock(BusinessFeatureGate.class);
        OaReimbursement current = reimbursement("draft", 3L, 0, null);
        OaReimbursement saved = reimbursement("draft", 4L, 0, null);
        when(mapper.selectByIdForUpdate(88L)).thenReturn(current);
        when(mapper.updateReimbursement(any())).thenReturn(1);
        when(mapper.insertItem(any())).thenReturn(1);
        when(mapper.selectById(88L)).thenReturn(saved);
        when(mapper.selectItemsByReimbursementId(88L))
                .thenReturn(List.of(completeItem()));
        when(mapper.selectInvoicesByReimbursementId(88L))
                .thenReturn(List.of());
        when(mapper.countInvoices(88L)).thenReturn(1);
        when(mapper.countInvoicesNotReady(88L)).thenReturn(0);
        when(mapper.markApprovalSubmitting(88L, "draft", 4L, 1,
                "admin")).thenReturn(1);
        OaReimbursementApprovalStartOutbox outbox =
                new OaReimbursementApprovalStartOutbox();
        outbox.setOutboxId(10L);
        when(outboxService.enqueue(any(), any(), eq("admin")))
                .thenReturn(outbox);
        OaReimbursement request = reimbursement("draft", 3L, 0, null);

        OaReimbursement result = service(mapper, remote, featureGate,
                outboxService, trigger).submit(request, 201L);

        ArgumentCaptor<ApprovalStartRequest> startRequest = ArgumentCaptor
                .forClass(ApprovalStartRequest.class);
        verify(outboxService).enqueue(eq(saved), startRequest.capture(),
                eq("admin"));
        assertThat(startRequest.getValue().getIdempotencyKey())
                .isEqualTo("OA_REIMBURSEMENT:88:1");
        assertThat(startRequest.getValue().getBusinessRound()).isEqualTo(1);
        assertThat(startRequest.getValue().getVariables())
                .containsEntry("title", "门店耗材报销")
                .containsEntry("amount", new BigDecimal("128.50"));
        assertThat(result.getStatus()).isEqualTo("submitting");
        assertThat(result.getApprovalRound()).isEqualTo(1);
        assertThat(result.getApprovalInstanceId()).isNull();
        assertThat(result.getRowVersion()).isEqualTo(5L);
        verify(featureGate).requireEnabled(BusinessFeatureGate.REIMBURSEMENT);
        verify(trigger).trigger(10L);
        verify(remote, never()).start(any(), eq(SecurityConstants.INNER));
        assertThat(OaReimbursementServiceImpl.class
                .getMethod("submit", OaReimbursement.class, Long.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    @DisplayName("重复提交 SUBMITTING 报销单复用当前轮发件箱")
    void shouldReuseCurrentOutboxForDuplicateSubmittingRequest()
    {
        loginAsAdmin();
        OaReimbursementMapper mapper = mock(OaReimbursementMapper.class);
        RemoteApprovalService remote = mock(RemoteApprovalService.class);
        OaReimbursementApprovalStartOutboxService outboxService = mock(
                OaReimbursementApprovalStartOutboxService.class);
        OaReimbursementApprovalStartAfterCommitTrigger trigger = mock(
                OaReimbursementApprovalStartAfterCommitTrigger.class);
        OaReimbursement current = reimbursement("submitting", 5L, 1, null);
        when(mapper.selectByIdForUpdate(88L)).thenReturn(current);
        when(mapper.selectById(88L)).thenReturn(current);
        when(mapper.selectItemsByReimbursementId(88L))
                .thenReturn(List.of(completeItem()));
        when(mapper.selectInvoicesByReimbursementId(88L))
                .thenReturn(List.of());
        OaReimbursementApprovalStartOutbox existing =
                new OaReimbursementApprovalStartOutbox();
        existing.setOutboxId(10L);
        when(outboxService.selectByReimbursementRound(88L, 1))
                .thenReturn(existing);

        OaReimbursement result = service(mapper, remote,
                mock(BusinessFeatureGate.class), outboxService, trigger)
                        .submit(reimbursement("submitting", 5L, 1, null),
                                201L);

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
        OaReimbursementMapper mapper = mock(OaReimbursementMapper.class);
        OaReimbursement submitting = reimbursement("submitting", 5L, 2, null);
        when(mapper.selectByIdForUpdate(88L)).thenReturn(submitting);
        ApprovalBusinessCallbackRequest request =
                new ApprovalBusinessCallbackRequest();
        request.setEventKey("APPROVE:9001:1");
        request.setInstanceId(9001L);
        request.setBusinessCode("OA_REIMBURSEMENT");
        request.setBusinessId("88");
        request.setBusinessRound(2);
        request.setAction("APPROVE");
        request.setPayload("{\"targetStatus\":\"APPROVED\"}");

        ApprovalBusinessCallbackResponse response = service(mapper,
                mock(RemoteApprovalService.class),
                mock(BusinessFeatureGate.class),
                mock(OaReimbursementApprovalStartOutboxService.class),
                mock(OaReimbursementApprovalStartAfterCommitTrigger.class))
                        .applyApprovalCallback(request);

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getInvalidated()).isFalse();
        assertThat(response.getCode()).isEqualTo("APPROVAL_LINK_PENDING");
        verify(mapper, never()).updateReimbursement(any());
    }

    private static OaReimbursementServiceImpl service(
            OaReimbursementMapper mapper, RemoteApprovalService remote,
            BusinessFeatureGate featureGate,
            OaReimbursementApprovalStartOutboxService outboxService,
            OaReimbursementApprovalStartAfterCommitTrigger trigger)
    {
        return new OaReimbursementServiceImpl(mapper,
                mock(OaDeptScopeMapper.class), mock(ShopScopeService.class),
                remote, outboxService, trigger, new ObjectMapper(),
                featureGate, mock(OaReimbursementFileStorageService.class),
                mock(OaInvoiceRecognitionCoordinator.class),
                new OaReimbursementProperties());
    }

    private static OaReimbursement reimbursement(String status, Long version,
            Integer round, Long instanceId)
    {
        OaReimbursement reimbursement = new OaReimbursement();
        reimbursement.setReimbursementId(88L);
        reimbursement.setTitle("门店耗材报销");
        reimbursement.setPurpose("日常补货");
        reimbursement.setApplicantId(1L);
        reimbursement.setApplicantName("admin");
        reimbursement.setApplicantDeptId(100L);
        reimbursement.setApplicantDeptName("运营部");
        reimbursement.setShopDeptId(201L);
        reimbursement.setShopDeptName("测试门店");
        reimbursement.setTotalAmount(new BigDecimal("128.50"));
        reimbursement.setExportStatus("not_exported");
        reimbursement.setStatus(status);
        reimbursement.setApprovalRound(round);
        reimbursement.setApprovalInstanceId(instanceId);
        reimbursement.setRowVersion(version);
        reimbursement.setItems(List.of(completeItem()));
        return reimbursement;
    }

    private static OaReimbursementItem completeItem()
    {
        OaReimbursementItem item = new OaReimbursementItem();
        item.setExpenseType("耗材费");
        item.setExpenseDate(new Date());
        item.setDescription("门店日常耗材");
        item.setClaimedAmount(new BigDecimal("128.50"));
        return item;
    }

    private static void loginAsAdmin()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
    }
}
