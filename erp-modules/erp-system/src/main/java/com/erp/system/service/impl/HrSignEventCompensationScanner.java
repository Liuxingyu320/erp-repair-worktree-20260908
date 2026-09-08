package com.erp.system.service.impl;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.erp.system.domain.SysHrLifecycleAction;
import com.erp.system.mapper.SysHrLifecycleActionMapper;

/**
 * 只扫描不可变生命周期动作与 Outbox 缺口，不读取员工当前档案。
 */
@Service
@ConditionalOnProperty(prefix = "hr.sign.lifecycle-automation", name = "enabled",
        havingValue = "true", matchIfMissing = false)
public class HrSignEventCompensationScanner
{
    private static final Logger log = LoggerFactory.getLogger(
            HrSignEventCompensationScanner.class);

    private final SysHrLifecycleActionMapper actionMapper;
    private final HrSignEventCompensationService compensationService;

    public HrSignEventCompensationScanner(
            SysHrLifecycleActionMapper actionMapper,
            HrSignEventCompensationService compensationService)
    {
        this.actionMapper = actionMapper;
        this.compensationService = compensationService;
    }

    @Scheduled(
            initialDelayString = "${hr.sign.outbox.compensation-initial-delay-ms:60000}",
            fixedDelayString = "${hr.sign.outbox.compensation-fixed-delay-ms:300000}")
    public void compensateMissingOutboxes()
    {
        List<SysHrLifecycleAction> actions = actionMapper
                .selectConfirmedActionsWithoutOutbox(100);
        if (actions == null)
        {
            return;
        }
        for (SysHrLifecycleAction action : actions)
        {
            try
            {
                compensationService.ensureOutbox(action);
            }
            catch (RuntimeException failure)
            {
                log.error("补偿签约事件失败，actionId={}, actionType={}, errorType={}",
                        action == null ? null : action.getActionId(),
                        action == null ? null : action.getActionType(),
                        failure.getClass().getSimpleName());
            }
        }
    }
}
