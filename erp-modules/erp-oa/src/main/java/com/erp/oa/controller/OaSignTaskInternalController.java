package com.erp.oa.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.domain.R;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.service.impl.OaSignTaskOrchestrator;

/** Internal endpoint used only by the system HR event outbox. */
@RestController
@RequestMapping("/signTask/inner")
public class OaSignTaskInternalController
{
    private final OaSignTaskOrchestrator orchestrator;

    public OaSignTaskInternalController(OaSignTaskOrchestrator orchestrator)
    {
        this.orchestrator = orchestrator;
    }

    @InnerAuth
    @PostMapping("/events")
    public R<Long> receiveEvent(@RequestBody HrSignBusinessEvent event)
    {
        return R.ok(orchestrator.orchestrate(event));
    }
}
