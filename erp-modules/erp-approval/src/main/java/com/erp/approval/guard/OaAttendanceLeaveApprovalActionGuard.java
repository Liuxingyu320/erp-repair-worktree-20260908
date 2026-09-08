package com.erp.approval.guard;

import static com.erp.approval.constant.ApprovalRuntimeConstants.ACTION_APPROVE;

import org.springframework.stereotype.Component;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationRequest;
import com.erp.approval.api.domain.ApprovalBusinessActionValidationResponse;
import com.erp.approval.constant.ApprovalBusinessCodes;
import com.erp.approval.domain.ApprovalInstance;
import com.erp.approval.guard.client.OaApprovalActionGuardClient;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;

/** Fail-closed live evidence validation for leave approvals. */
@Component
public class OaAttendanceLeaveApprovalActionGuard
        implements ApprovalBusinessActionGuard
{
    private final OaApprovalActionGuardClient client;

    public OaAttendanceLeaveApprovalActionGuard(
            OaApprovalActionGuardClient client)
    {
        this.client = client;
    }

    @Override
    public String businessCode()
    {
        return ApprovalBusinessCodes.OA_ATTENDANCE_LEAVE;
    }

    @Override
    public void validate(ApprovalInstance instance, String action,
            Long operatorId)
    {
        // Missing evidence must never prevent a reviewer from returning or
        // rejecting a request so the applicant can correct it.
        if (!ACTION_APPROVE.equals(action)) return;
        ApprovalBusinessActionValidationRequest request =
                new ApprovalBusinessActionValidationRequest();
        request.setInstanceId(instance.getInstanceId());
        request.setBusinessCode(instance.getBusinessCode());
        request.setBusinessId(instance.getBusinessId());
        request.setBusinessRound(instance.getBusinessRound());
        request.setAction(action);
        request.setApplicantUserId(instance.getApplicantUserId());
        request.setAnchorDeptId(instance.getAnchorDeptId());
        request.setBusinessSnapshot(instance.getBusinessSnapshot());

        R<ApprovalBusinessActionValidationResponse> result;
        try
        {
            result = client.validateAction(request, SecurityConstants.INNER);
        }
        catch (RuntimeException exception)
        {
            throw new ServiceException("请假审批证据校验服务暂不可用");
        }
        if (result == null || !R.isSuccess(result) || result.getData() == null)
        {
            String message = result == null ? null : result.getMsg();
            throw new ServiceException(message == null || message.isBlank()
                    ? "请假审批证据校验服务无响应" : message);
        }
        ApprovalBusinessActionValidationResponse response = result.getData();
        if (!Boolean.TRUE.equals(response.getAccepted()))
        {
            String message = response.getMessage();
            throw new ServiceException(message == null || message.isBlank()
                    ? "请假审批证据校验未通过" : message);
        }
    }
}
