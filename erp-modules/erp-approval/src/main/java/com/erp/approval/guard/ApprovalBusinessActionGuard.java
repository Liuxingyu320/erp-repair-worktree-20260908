package com.erp.approval.guard;

import com.erp.approval.domain.ApprovalInstance;

/** Business-owner validation run before an approval action is persisted. */
public interface ApprovalBusinessActionGuard
{
    String businessCode();

    void validate(ApprovalInstance instance, String action, Long operatorId);
}
