package com.erp.system.service.impl;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.system.domain.SysHrLifecycleAction;
import com.erp.system.domain.SysHrSignEventOutbox;
import com.erp.system.mapper.SysHrSignEventOutboxMapper;

/**
 * 在独立短事务中确保不可变生命周期动作拥有唯一 Outbox。
 */
@Service
public class HrSignEventCompensationService
{
    private final SysHrSignEventOutboxMapper outboxMapper;
    private final HrSignEventPayloadFactory payloadFactory;

    public HrSignEventCompensationService(
            SysHrSignEventOutboxMapper outboxMapper,
            HrSignEventPayloadFactory payloadFactory)
    {
        this.outboxMapper = outboxMapper;
        this.payloadFactory = payloadFactory;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public SysHrSignEventOutbox ensureOutbox(SysHrLifecycleAction action)
    {
        if (action == null || action.getActionId() == null
                || action.getVersion() == null)
        {
            throw new ServiceException("补偿生命周期动作标识不能为空");
        }
        SysHrSignEventOutbox existing = outboxMapper
                .selectByActionAndEventVersion(action.getActionId(),
                        action.getVersion());
        if (existing != null)
        {
            return existing;
        }

        SysHrSignEventOutbox row;
        try
        {
            HrSignBusinessEvent event = payloadFactory.fromAction(action);
            row = row(action, payloadFactory.writePayload(event), "PENDING",
                    null);
        }
        catch (HrSignEventPayloadFactory.InvalidLifecycleActionException invalid)
        {
            row = row(action, "{}", "DEAD", "INVALID_ACTION_PAYLOAD");
        }

        try
        {
            if (outboxMapper.insertOutbox(row) != 1)
            {
                throw new ServiceException("补偿签约事件发件箱写入失败");
            }
            return row;
        }
        catch (DuplicateKeyException competition)
        {
            SysHrSignEventOutbox winner = outboxMapper
                    .selectByActionAndEventVersion(action.getActionId(),
                            action.getVersion());
            if (winner != null)
            {
                return winner;
            }
            throw competition;
        }
    }

    private SysHrSignEventOutbox row(SysHrLifecycleAction action,
            String payload, String status, String lastError)
    {
        SysHrSignEventOutbox row = new SysHrSignEventOutbox();
        row.setActionId(action.getActionId());
        row.setEventVersion(action.getVersion());
        row.setPayloadJson(payload);
        row.setStatus(status);
        row.setRetryCount(0);
        row.setVersion(0L);
        row.setNextRetryTime(null);
        row.setLastHttpStatus(null);
        row.setLastError(lastError);
        return row;
    }
}
