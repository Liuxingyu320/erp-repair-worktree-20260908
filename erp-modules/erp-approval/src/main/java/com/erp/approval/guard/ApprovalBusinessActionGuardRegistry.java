package com.erp.approval.guard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.erp.approval.domain.ApprovalInstance;

/** Resolves at most one pre-action guard for each approval business code. */
@Component
public class ApprovalBusinessActionGuardRegistry
{
    private final Map<String, ApprovalBusinessActionGuard> guards;

    public ApprovalBusinessActionGuardRegistry(
            List<ApprovalBusinessActionGuard> items)
    {
        Map<String, ApprovalBusinessActionGuard> registry =
                new LinkedHashMap<>();
        for (ApprovalBusinessActionGuard item : items)
        {
            ApprovalBusinessActionGuard previous = registry.putIfAbsent(
                    item.businessCode(), item);
            if (previous != null)
            {
                throw new IllegalStateException("重复的审批动作校验器: "
                        + item.businessCode());
            }
        }
        guards = Map.copyOf(registry);
    }

    public void validate(ApprovalInstance instance, String action,
            Long operatorId)
    {
        if (instance == null) return;
        ApprovalBusinessActionGuard guard = guards.get(
                instance.getBusinessCode());
        if (guard != null) guard.validate(instance, action, operatorId);
    }
}
