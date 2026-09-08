package com.erp.approval.service;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.erp.approval.constant.ApprovalBusinessCodes;
import com.erp.common.core.exception.ServiceException;

/** Single source of truth for business approval permission checks and todo routes. */
@Component
public class ApprovalPermissionPolicy
{
    private static final Map<String, String> PERMISSIONS = Map.of(
            ApprovalBusinessCodes.OA_PURCHASE, "oa:todo:approve",
            ApprovalBusinessCodes.OA_REIMBURSEMENT,
                    "oa:reimbursement:approve",
            ApprovalBusinessCodes.INV_TRANSFER, "inv:transfer:approve",
            ApprovalBusinessCodes.INV_STOCK_CHECK, "inv:stockCheck:approve",
            ApprovalBusinessCodes.HR_HEALTH_CERTIFICATE,
                    "hr:healthCertificate:review",
            ApprovalBusinessCodes.OA_ATTENDANCE_LEAVE,
                    "oa:attendance:leave:approve",
            ApprovalBusinessCodes.OA_ATTENDANCE_CORRECTION,
                    "oa:attendance:correction:approve");

    public String requiredPermission(String businessCode)
    {
        String permission = PERMISSIONS.get(businessCode);
        if (permission == null)
        {
            throw new ServiceException("未配置业务审批权限: " + businessCode);
        }
        return permission;
    }

    public Set<String> supportedBusinessCodes()
    {
        return PERMISSIONS.keySet();
    }

    /**
     * Permissions that may be attached directly to a candidate for a business.
     * Most approval flows use only the base permission; reimbursement adds a
     * dedicated finance permission for its second node.
     */
    public Set<String> candidatePermissions(String businessCode)
    {
        String required = requiredPermission(businessCode);
        if (ApprovalBusinessCodes.OA_REIMBURSEMENT.equals(businessCode))
        {
            return Set.of(required,
                    "oa:reimbursement:finance:approve");
        }
        return Set.of(required);
    }

    public boolean isCandidatePermissionAllowed(String businessCode,
            String candidatePermission)
    {
        return candidatePermissions(businessCode).contains(
                candidatePermission);
    }

    /**
     * Resolves the live permission for a candidate snapshot. Strategy source
     * codes that are not permissions continue to use the business base
     * permission.
     */
    public String permissionForCandidate(String businessCode,
            String candidateSourceCode)
    {
        if (candidateSourceCode != null
                && isCandidatePermissionAllowed(businessCode,
                        candidateSourceCode))
        {
            return candidateSourceCode;
        }
        return requiredPermission(businessCode);
    }
}
