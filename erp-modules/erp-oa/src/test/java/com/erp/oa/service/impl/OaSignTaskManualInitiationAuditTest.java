package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.constant.OaSignOperatorType;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.mapper.OaHrRenewalGuardMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.oa.service.IOaSignTaskService;

@DisplayName("历史员工首次发起审计")
class OaSignTaskManualInitiationAuditTest
{
    @Test
    @DisplayName("首次NEW迁移把确认、请求、操作人和原因写入不可变任务事件而非员工可见备注")
    void auditIsStoredOnTaskEventInsteadOfPackageRemark()
    {
        IOaSignTaskService taskService = mock(IOaSignTaskService.class);
        OaSignTaskMapper taskMapper = mock(OaSignTaskMapper.class);
        OaSignTaskEventService eventService = mock(OaSignTaskEventService.class);
        OaSignPlanVersionMapper versionMapper = mock(OaSignPlanVersionMapper.class);
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        IOaSignPackageService packageService = mock(IOaSignPackageService.class);
        OaHrRenewalGuardMapper renewalGuardMapper = mock(OaHrRenewalGuardMapper.class);
        OaSignNotificationOutboxService outboxService = mock(OaSignNotificationOutboxService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        OaSignTaskOrchestrator orchestrator = new OaSignTaskOrchestrator(List.of(), taskService,
                taskMapper, eventService, versionMapper, packageMapper, packageService,
                renewalGuardMapper, outboxService, transactionManager, new ObjectMapper());

        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setEmployeeId(201L);
        task.setShopDeptId(1171L);
        task.setScenario("ONBOARD");
        task.setStatus("NEW");
        task.setVersion(0L);
        when(taskMapper.selectOaSignTaskById(9L)).thenReturn(task);
        String itemRequestId = "MI:0123456789abcdef01234567:201:0";
        when(eventService.transition(eq(task), eq(OaSignTaskStatus.VALIDATING),
                eq(OaSignOperatorType.HR), eq(101L), any(), any(), eq(itemRequestId),
                isNull(), isNull()))
                .thenAnswer(invocation -> {
                    task.setStatus("VALIDATING");
                    return task;
                });

        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setSourceType("HR_PROFILE_CONTRACT_INITIATION");
        event.setOperatorUserId(101L);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("noExternalContractConfirmed", true);
        attributes.put("initiationRequestId", itemRequestId);
        attributes.put("initiationBatchRequestId", "init-201");
        attributes.put("initiationReason",
                "补发原因;requestId=forged\noperatorUserId=999=evil" + "很长".repeat(600));
        event.setAttributes(attributes);

        ReflectionTestUtils.invokeMethod(orchestrator,
                "startOrResumeValidation", 9L, event);

        ArgumentCaptor<String> reasonCode = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> reasonDetail = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<OaSignOperatorType> operatorType =
                ArgumentCaptor.forClass(OaSignOperatorType.class);
        ArgumentCaptor<Long> operatorUserId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        verify(eventService).transition(eq(task), eq(OaSignTaskStatus.VALIDATING),
                operatorType.capture(), operatorUserId.capture(), reasonCode.capture(),
                reasonDetail.capture(), requestId.capture(), isNull(), isNull());
        assertThat(operatorType.getValue()).isEqualTo(OaSignOperatorType.HR);
        assertThat(operatorUserId.getValue()).isEqualTo(101L);
        assertThat(requestId.getValue()).isEqualTo(itemRequestId);
        assertThat(reasonCode.getValue()).isEqualTo("MANUAL_ONBOARD_INITIATION");
        assertThat(reasonDetail.getValue())
                .startsWith("noExternalContractConfirmed=true;requestId=" + itemRequestId
                        + ";initiationBatchRequestId=init-201;operatorUserId=101;initiationReason=")
                .hasSizeLessThanOrEqualTo(1000)
                .doesNotContain(";requestId=forged", "\r", "\n", "=evil");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setRemark("规则备注");
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(55L);
        version.setPlanId(5L);
        version.setPlanName("入职方案");
        ReflectionTestUtils.invokeMethod(orchestrator,
                "bindServerOwnedDraft", signPackage, task, event, version);
        assertThat(signPackage.getRemark()).isEqualTo("规则备注")
                .doesNotContain("补发原因", "无系统外合同");
    }
}
