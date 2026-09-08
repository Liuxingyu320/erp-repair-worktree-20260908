package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.dto.OaSignTaskBatchSendRequest;
import com.erp.oa.domain.dto.OaSignTaskRetryRequest;
import com.erp.oa.domain.vo.OaSignTaskBatchSendResult;
import com.erp.oa.domain.vo.OaSignTaskDetail;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.oa.service.IOaSignTaskService;

@DisplayName("签约任务批量发送")
class OaSignTaskBatchSendServiceTest
{
    private static final Long SIGN_SCOPE_DEPT_ID = 1171L;
    private static final String IP_ADDRESS = "10.0.0.8";
    private static final String USER_AGENT = "batch-test";

    private OaSignHrAccessService hrAccessService;
    private ShopScopeService signScopeService;
    private OaSignTaskMapper taskMapper;
    private IOaSignTaskService taskService;
    private OaSignOnboardImportRowMapper onboardRowMapper;
    private OaSignPackageMapper packageMapper;
    private IOaSignPackageService packageService;
    private OaSignTaskBatchSendService service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("901");
        hrAccessService = mock(OaSignHrAccessService.class);
        signScopeService = mock(ShopScopeService.class);
        taskMapper = mock(OaSignTaskMapper.class);
        taskService = mock(IOaSignTaskService.class);
        onboardRowMapper = mock(OaSignOnboardImportRowMapper.class);
        packageMapper = mock(OaSignPackageMapper.class);
        packageService = mock(IOaSignPackageService.class);
        service = new OaSignTaskBatchSendService(hrAccessService, signScopeService,
                taskMapper, taskService, onboardRowMapper,
                packageMapper, packageService);
        when(signScopeService.resolveScopeDeptIds(SIGN_SCOPE_DEPT_ID))
                .thenReturn(List.of(SIGN_SCOPE_DEPT_ID));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("单项发送失败不阻断后续任务并返回逐项汇总")
    void shouldContinueAfterOneItemFails()
    {
        OaSignTask firstReady = task(1L, "READY_TO_SEND", 101L);
        OaSignTask firstFailed = task(1L, "FAILED", 101L);
        firstFailed.setFailureCode("SEND_FAILED");
        OaSignTask secondReady = task(2L, "READY_TO_SEND", 102L);
        OaSignTask secondSent = task(2L, "PENDING_SIGN", 102L);
        when(taskMapper.selectOaSignTaskById(1L)).thenReturn(firstReady, firstFailed);
        when(taskMapper.selectOaSignTaskById(2L)).thenReturn(secondReady);
        when(taskService.send(eq(1L), any(OaSignTaskRetryRequest.class),
                eq(SIGN_SCOPE_DEPT_ID), eq(IP_ADDRESS), eq(USER_AGENT)))
                .thenThrow(new IllegalStateException("first send failed"));
        when(taskService.send(eq(2L), any(OaSignTaskRetryRequest.class),
                eq(SIGN_SCOPE_DEPT_ID), eq(IP_ADDRESS), eq(USER_AGENT)))
                .thenReturn(detail(secondSent));

        OaSignTaskBatchSendResult result = service.batchSend(
                request("batch-partial", 1L, 2L), SIGN_SCOPE_DEPT_ID,
                IP_ADDRESS, USER_AGENT);

        verify(hrAccessService).requireCurrentHr();
        verify(taskService).send(eq(2L), any(OaSignTaskRetryRequest.class),
                eq(SIGN_SCOPE_DEPT_ID), eq(IP_ADDRESS), eq(USER_AGENT));
        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getSentCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getBlockedCount()).isZero();
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getTaskId()).isEqualTo(1L);
        assertThat(result.getItems().get(0).getResult()).isEqualTo("FAILED");
        assertThat(result.getItems().get(0).getTaskStatus()).isEqualTo("FAILED");
        assertThat(result.getItems().get(1).getTaskId()).isEqualTo(2L);
        assertThat(result.getItems().get(1).getResult()).isEqualTo("SENT");
        assertThat(result.getItems().get(1).getTaskStatus()).isEqualTo("PENDING_SIGN");
    }

    @Test
    @DisplayName("同组织其他HR的任务逐项失败关闭且不触发发送")
    void shouldBlockTaskAssignedToAnotherHr()
    {
        OaSignTask anotherOwnersTask = task(3L, "READY_TO_SEND", 103L);
        anotherOwnersTask.setAssignedHrUserId(902L);
        when(taskMapper.selectOaSignTaskById(3L)).thenReturn(anotherOwnersTask);
        doThrow(new com.erp.common.core.exception.ServiceException(
                "签约任务不存在或未分配给当前合同经办人"))
                .when(hrAccessService).requireTaskOwner(anotherOwnersTask);

        OaSignTaskBatchSendResult result = service.batchSend(
                request("other-owner", 3L), SIGN_SCOPE_DEPT_ID,
                IP_ADDRESS, USER_AGENT);

        assertThat(result.getBlockedCount()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getPackageId()).isNull();
            assertThat(item.getTaskStatus()).isNull();
            assertThat(item.getMessage()).contains("无权访问");
        });
        verify(taskService, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("同批重复任务去重且同请求重放不会再次发送")
    void shouldDeduplicateAndNotResendOnReplay()
    {
        OaSignTask ready = task(7L, "READY_TO_SEND", 107L);
        ready.setRetryCount(3);
        OaSignTask sent = task(7L, "PENDING_SIGN", 107L);
        sent.setRetryCount(3);
        when(taskMapper.selectOaSignTaskById(7L)).thenReturn(ready, sent);
        when(taskService.send(eq(7L), any(OaSignTaskRetryRequest.class),
                eq(SIGN_SCOPE_DEPT_ID), eq(IP_ADDRESS), eq(USER_AGENT)))
                .thenReturn(detail(sent));
        OaSignTaskBatchSendRequest action = request("same-batch-request", 7L, 7L);

        OaSignTaskBatchSendResult first = service.batchSend(
                action, SIGN_SCOPE_DEPT_ID, IP_ADDRESS, USER_AGENT);
        OaSignTaskBatchSendResult replay = service.batchSend(
                action, SIGN_SCOPE_DEPT_ID, IP_ADDRESS, USER_AGENT);

        ArgumentCaptor<OaSignTaskRetryRequest> sendAction =
                ArgumentCaptor.forClass(OaSignTaskRetryRequest.class);
        verify(taskService, times(1)).send(eq(7L), sendAction.capture(),
                eq(SIGN_SCOPE_DEPT_ID), eq(IP_ADDRESS), eq(USER_AGENT));
        assertThat(sendAction.getValue().getRequestId()).isEqualTo(
                OaSignTaskBatchSendService.itemRequestId("same-batch-request", 7L, 3));
        assertThat(first.getTotalCount()).isEqualTo(1);
        assertThat(first.getSentCount()).isEqualTo(1);
        assertThat(first.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getResult()).isEqualTo("SENT"));
        assertThat(replay.getTotalCount()).isEqualTo(1);
        assertThat(replay.getAlreadySentCount()).isEqualTo(1);
        assertThat(replay.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getResult()).isEqualTo("ALREADY_SENT"));
    }

    @Test
    @DisplayName("只对上次发送失败项使用稳定恢复请求后再发送")
    void shouldRecoverSendFailedItemBeforeSending()
    {
        OaSignTask failed = task(9L, "FAILED", 109L);
        failed.setFailureCode("SEND_FAILED");
        failed.setRetryCount(2);
        OaSignTask recovered = task(9L, "READY_TO_SEND", 109L);
        recovered.setRetryCount(2);
        OaSignTask sent = task(9L, "PENDING_SIGN", 109L);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(failed);
        when(taskService.retry(eq(9L), any(OaSignTaskRetryRequest.class),
                eq(SIGN_SCOPE_DEPT_ID), eq(IP_ADDRESS), eq(USER_AGENT)))
                .thenReturn(detail(recovered));
        when(taskService.send(eq(9L), any(OaSignTaskRetryRequest.class),
                eq(SIGN_SCOPE_DEPT_ID), eq(IP_ADDRESS), eq(USER_AGENT)))
                .thenReturn(detail(sent));

        OaSignTaskBatchSendResult result = service.batchSend(
                request("retry-failed-item", 9L), SIGN_SCOPE_DEPT_ID,
                IP_ADDRESS, USER_AGENT);

        ArgumentCaptor<OaSignTaskRetryRequest> retryAction =
                ArgumentCaptor.forClass(OaSignTaskRetryRequest.class);
        ArgumentCaptor<OaSignTaskRetryRequest> sendAction =
                ArgumentCaptor.forClass(OaSignTaskRetryRequest.class);
        verify(taskService).retry(eq(9L), retryAction.capture(),
                eq(SIGN_SCOPE_DEPT_ID), eq(IP_ADDRESS), eq(USER_AGENT));
        verify(taskService).send(eq(9L), sendAction.capture(),
                eq(SIGN_SCOPE_DEPT_ID), eq(IP_ADDRESS), eq(USER_AGENT));
        assertThat(retryAction.getValue().getRequestId()).isEqualTo(
                OaSignTaskBatchSendService.retryRequestId("retry-failed-item", 9L, 2));
        assertThat(sendAction.getValue().getRequestId()).isEqualTo(
                OaSignTaskBatchSendService.itemRequestId("retry-failed-item", 9L, 2));
        assertThat(result.getSentCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
    }

    @Test
    @DisplayName("先留唯一签名流程只在HR显式发送时交付最终文件")
    void shouldExplicitlySendPreparedStagedFinalWithoutCallingInitialSendAgain()
    {
        OaSignTask waitingCompany = task(12L, "PENDING_COMPANY", 112L);
        waitingCompany.setAssignedHrUserId(901L);
        OaSignTask pendingConfirm = task(12L, "PENDING_FINAL_CONFIRM", 112L);
        pendingConfirm.setAssignedHrUserId(901L);
        OaSignPackage prepared = new OaSignPackage();
        prepared.setPackageId(112L);
        prepared.setTaskId(12L);
        prepared.setSigningSequence("SIGNATURE_FIRST");
        prepared.setStatus("pending_company");
        prepared.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        prepared.setFinalDocumentVersion("SP-112-V3");
        prepared.setFinalDocumentRootHash("a".repeat(64));
        when(taskMapper.selectOaSignTaskById(12L))
                .thenReturn(waitingCompany, pendingConfirm);
        when(packageMapper.selectOaSignPackageById(112L)).thenReturn(prepared);

        OaSignTaskBatchSendResult result = service.batchSend(
                request("send-final-only", 12L), SIGN_SCOPE_DEPT_ID,
                IP_ADDRESS, USER_AGENT);

        verify(packageService).sendStagedSignatureFirstFinalForSystem(
                eq(112L), eq(12L), eq(901L), any(String.class));
        verify(taskService, never()).send(any(), any(), any(), any(), any());
        assertThat(result.getSentCount()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getTaskStatus()).isEqualTo("PENDING_FINAL_CONFIRM");
            assertThat(item.getMessage()).contains("不再签名");
        });
    }

    @Test
    @DisplayName("最终文件发送失败且仍待发送时不得误报已发送")
    void shouldKeepPreparedFinalRetryableWhenDedicatedSendFails()
    {
        OaSignTask waitingCompany = task(13L, "PENDING_COMPANY", 113L);
        waitingCompany.setAssignedHrUserId(901L);
        OaSignPackage prepared = new OaSignPackage();
        prepared.setPackageId(113L);
        prepared.setTaskId(13L);
        prepared.setSigningSequence("SIGNATURE_FIRST");
        prepared.setStatus("pending_company");
        prepared.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        prepared.setFinalDocumentVersion("SP-113-V3");
        prepared.setFinalDocumentRootHash("b".repeat(64));
        when(taskMapper.selectOaSignTaskById(13L))
                .thenReturn(waitingCompany, waitingCompany);
        when(packageMapper.selectOaSignPackageById(113L)).thenReturn(prepared);
        when(packageService.sendStagedSignatureFirstFinalForSystem(
                eq(113L), eq(13L), eq(901L), any(String.class)))
                .thenThrow(new IllegalStateException("final send failed"));

        OaSignTaskBatchSendResult result = service.batchSend(
                request("failed-final-send", 13L), SIGN_SCOPE_DEPT_ID,
                IP_ADDRESS, USER_AGENT);

        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getAlreadySentCount()).isZero();
        assertThat(result.getSentCount()).isZero();
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getResult()).isEqualTo("FAILED");
            assertThat(item.getTaskStatus()).isEqualTo("PENDING_COMPANY");
            assertThat(item.getMessage()).contains("未发送");
        });
        verify(onboardRowMapper, never()).markSentByTaskId(any());
        verify(taskService, never()).send(any(), any(), any(), any(), any());
    }

    private OaSignTaskBatchSendRequest request(String requestId, Long... taskIds)
    {
        OaSignTaskBatchSendRequest request = new OaSignTaskBatchSendRequest();
        request.setRequestId(requestId);
        request.setTaskIds(List.of(taskIds));
        return request;
    }

    private OaSignTask task(Long taskId, String status, Long packageId)
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId(taskId);
        task.setShopDeptId(SIGN_SCOPE_DEPT_ID);
        task.setPackageId(packageId);
        task.setStatus(status);
        return task;
    }

    private OaSignTaskDetail detail(OaSignTask task)
    {
        OaSignTaskDetail detail = new OaSignTaskDetail();
        detail.setTask(task);
        return detail;
    }
}
