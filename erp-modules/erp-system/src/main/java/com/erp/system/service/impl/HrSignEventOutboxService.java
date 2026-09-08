package com.erp.system.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.system.domain.SysHrSignEventOutbox;
import com.erp.system.mapper.SysHrSignEventOutboxMapper;

/**
 * 人事签约事件发件箱短事务状态服务。
 */
@Service
public class HrSignEventOutboxService
{
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final String CONFLICT_MESSAGE = "签约事件发件箱状态已变化，请等待下一轮调度";

    private final SysHrSignEventOutboxMapper mapper;

    public HrSignEventOutboxService(SysHrSignEventOutboxMapper mapper)
    {
        this.mapper = mapper;
    }

    public List<SysHrSignEventOutbox> selectDue(Date dueTime,
            Date staleSendingBefore, int limit)
    {
        if (dueTime == null || staleSendingBefore == null)
        {
            throw new ServiceException("签约事件调度时间不能为空");
        }
        int boundedLimit = Math.max(1, Math.min(limit, MAX_BATCH_SIZE));
        return mapper.selectDueOutboxes(dueTime, staleSendingBefore,
                boundedLimit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public boolean claim(SysHrSignEventOutbox row)
    {
        if (row == null || row.getOutboxId() == null
                || row.getVersion() == null
                || StringUtils.isBlank(row.getStatus()))
        {
            return false;
        }
        int affected = mapper.claimForSending(row.getOutboxId(),
                row.getStatus(), row.getVersion());
        if (affected != 1)
        {
            return false;
        }
        row.setStatus("SENDING");
        row.setVersion(row.getVersion() + 1);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void markSent(SysHrSignEventOutbox row, Long remoteTaskId,
            Integer lastHttpStatus)
    {
        int affected = mapper.markSent(row.getOutboxId(), row.getVersion(),
                remoteTaskId, lastHttpStatus);
        assertUpdated(affected);
        row.setStatus("SENT");
        row.setVersion(row.getVersion() + 1);
        row.setRemoteTaskId(remoteTaskId);
        row.setLastHttpStatus(lastHttpStatus);
        row.setLastError(null);
        row.setNextRetryTime(null);
        row.setSentTime(new Date());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void markRetry(SysHrSignEventOutbox row, Integer retryCount,
            Date nextRetryTime, Integer lastHttpStatus, String lastError)
    {
        String errorSummary = truncate(lastError);
        int affected = mapper.markRetry(row.getOutboxId(), row.getVersion(),
                retryCount, nextRetryTime, lastHttpStatus, errorSummary);
        assertUpdated(affected);
        row.setStatus("RETRY");
        row.setVersion(row.getVersion() + 1);
        row.setRetryCount(retryCount);
        row.setNextRetryTime(nextRetryTime);
        row.setLastHttpStatus(lastHttpStatus);
        row.setLastError(errorSummary);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void markDead(SysHrSignEventOutbox row, Integer lastHttpStatus,
            String lastError)
    {
        String errorSummary = truncate(lastError);
        int affected = mapper.markDead(row.getOutboxId(), row.getVersion(),
                lastHttpStatus, errorSummary);
        assertUpdated(affected);
        row.setStatus("DEAD");
        row.setVersion(row.getVersion() + 1);
        row.setNextRetryTime(null);
        row.setLastHttpStatus(lastHttpStatus);
        row.setLastError(errorSummary);
    }

    private String truncate(String value)
    {
        if (value == null || value.length() <= MAX_ERROR_LENGTH)
        {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }

    private void assertUpdated(int affected)
    {
        if (affected != 1)
        {
            throw new ServiceException(CONFLICT_MESSAGE);
        }
    }
}
