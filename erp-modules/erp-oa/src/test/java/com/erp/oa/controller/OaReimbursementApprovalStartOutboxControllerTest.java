package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.log.annotation.Log;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.domain.dto.OaReimbursementApprovalStartReplayRequest;
import com.erp.oa.domain.vo.OaReimbursementApprovalStartOutboxQuery;
import com.erp.oa.service.impl.OaReimbursementApprovalStartDispatcher;
import com.erp.oa.service.impl.OaReimbursementApprovalStartOutboxService;

@DisplayName("OA报销审批发起发件箱Controller")
class OaReimbursementApprovalStartOutboxControllerTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("人工重放事实提交后立即尝试派发")
    void shouldDispatchImmediatelyAfterReplay()
    {
        SecurityContextHolder.setUserName("operator");
        OaReimbursementApprovalStartOutboxService service = mock(
                OaReimbursementApprovalStartOutboxService.class);
        OaReimbursementApprovalStartDispatcher dispatcher = mock(
                OaReimbursementApprovalStartDispatcher.class);
        OaReimbursementApprovalStartReplayRequest request =
                new OaReimbursementApprovalStartReplayRequest();
        request.setVersion(8L);

        new OaReimbursementApprovalStartOutboxController(service, dispatcher)
                .replay(10L, request);

        InOrder order = inOrder(service, dispatcher);
        order.verify(service).replayFailed(argThat(query ->
                Long.valueOf(10L).equals(query.getOutboxId())), eq(8L),
                eq("operator"));
        order.verify(dispatcher).dispatchOneNow(10L);
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("重放接口保留权限、防重和日志脱敏契约")
    void replayEndpointShouldRemainSafeByContract() throws Exception
    {
        Method replay = OaReimbursementApprovalStartOutboxController.class
                .getMethod("replay", Long.class,
                        OaReimbursementApprovalStartReplayRequest.class);

        assertThat(replay.getAnnotation(RequiresPermissions.class).value())
                .containsExactly(
                        "oa:reimbursement:approvalStartOutbox:replay");
        assertThat(replay.getAnnotation(IdempotentSubmit.class).timeout())
                .isEqualTo(30);
        Log log = replay.getAnnotation(Log.class);
        assertThat(log).isNotNull();
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    @Test
    @DisplayName("列表和汇总使用独立只读运维权限")
    void readEndpointsShouldUseDedicatedOpsPermission() throws Exception
    {
        for (String methodName : new String[] { "list", "summary" })
        {
            Method method = OaReimbursementApprovalStartOutboxController.class
                    .getMethod(methodName,
                            OaReimbursementApprovalStartOutboxQuery.class);
            assertThat(method.getAnnotation(RequiresPermissions.class)
                    .value()).containsExactly(
                            "oa:reimbursement:approvalStartOutbox:list");
        }
    }
}
