package com.erp.approval.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.mapper.ApprovalRuntimeMapper;
import com.erp.approval.mapper.ApprovalTemplateMapper;
import com.erp.approval.support.ApprovalJsonSupport;
import com.erp.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("审批启动请求契约")
class ApprovalRuntimeServiceRequestContractTest
{
    @Test
    @DisplayName("同一幂等键不得更换申请部门后重放")
    void idempotentReplayShouldIncludeApplicantDepartment()
    {
        ApprovalRuntimeMapper runtimeMapper = mock(ApprovalRuntimeMapper.class);
        ApprovalJsonSupport jsonSupport = mock(ApprovalJsonSupport.class);
        ApprovalRuntimeService service = new ApprovalRuntimeService(
                mock(ApprovalTemplateMapper.class), runtimeMapper,
                mock(ApprovalCandidateDirectoryMapper.class),
                mock(ApprovalRuleMatchService.class),
                mock(ApprovalRoutePlanner.class),
                mock(ApprovalMonitorService.class), jsonSupport);
        ApprovalInstance existing = new ApprovalInstance();
        existing.setInstanceId(88L);
        existing.setBusinessCode("OA_PURCHASE");
        existing.setBusinessId("501");
        existing.setBusinessRound(1);
        existing.setApplicantUserId(9L);
        existing.setApplicantDeptId(42L);
        existing.setAnchorDeptId(7L);
        existing.setBusinessSubtype("ALL");
        existing.setBusinessDigest("same-digest");
        when(runtimeMapper.selectInstanceByIdempotencyKey("start-1"))
                .thenReturn(existing);
        when(jsonSupport.write(java.util.Map.of())).thenReturn("{}");
        when(jsonSupport.sha256("{}")).thenReturn("same-digest");
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setBusinessCode("OA_PURCHASE");
        request.setBusinessId("501");
        request.setBusinessRound(1);
        request.setApplicantId(9L);
        request.setApplicantDeptId(43L);
        request.setAnchorDeptId(7L);
        request.setIdempotencyKey("start-1");

        assertThatThrownBy(() -> service.start(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("幂等键")
                .hasMessageContaining("路由内容");
    }
}
