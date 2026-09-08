package com.erp.approval.callback;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;

@Component
public class ApprovalBusinessCallbackRegistry
{
    private final Map<String, ApprovalBusinessCallback> callbacks;

    public ApprovalBusinessCallbackRegistry(List<ApprovalBusinessCallback> items)
    {
        Map<String, ApprovalBusinessCallback> registry = new LinkedHashMap<>();
        for (ApprovalBusinessCallback item : items)
        {
            ApprovalBusinessCallback previous = registry.putIfAbsent(
                    item.businessCode(), item);
            if (previous != null)
            {
                throw new IllegalStateException("重复的审批业务回调: "
                        + item.businessCode());
            }
        }
        callbacks = Map.copyOf(registry);
    }

    public boolean contains(String businessCode)
    {
        return callbacks.containsKey(businessCode);
    }

    public ApprovalBusinessCallback require(String businessCode)
    {
        ApprovalBusinessCallback callback = callbacks.get(businessCode);
        if (callback == null)
        {
            throw new ServiceException("未注册业务回调适配器: " + businessCode);
        }
        return callback;
    }
}
