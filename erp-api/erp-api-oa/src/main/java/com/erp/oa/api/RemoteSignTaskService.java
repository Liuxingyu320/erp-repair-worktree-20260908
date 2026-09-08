package com.erp.oa.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.ServiceNameConstants;
import com.erp.common.core.domain.R;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.api.factory.RemoteSignTaskFallbackFactory;

/**
 * OA签约任务内部服务。
 */
@FeignClient(contextId = "remoteSignTaskService", value = ServiceNameConstants.OA_SERVICE,
        fallbackFactory = RemoteSignTaskFallbackFactory.class)
public interface RemoteSignTaskService
{
    @PostMapping("/signTask/inner/events")
    R<Long> publishEvent(@RequestBody HrSignBusinessEvent event,
            @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}
