package com.erp.oa.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignTaskStatus;

@Component
public class OaSignTaskStateMachine
{
    private static final Map<OaSignTaskStatus, EnumSet<OaSignTaskStatus>> ALLOWED =
            new EnumMap<>(OaSignTaskStatus.class);

    static
    {
        allow(OaSignTaskStatus.NEW, OaSignTaskStatus.VALIDATING);
        allow(OaSignTaskStatus.VALIDATING, OaSignTaskStatus.NEEDS_DATA,
                OaSignTaskStatus.DRAFT_CREATED, OaSignTaskStatus.NO_ACTION, OaSignTaskStatus.FAILED);
        allow(OaSignTaskStatus.NEEDS_DATA, OaSignTaskStatus.VALIDATING, OaSignTaskStatus.CANCELLED);
        allow(OaSignTaskStatus.DRAFT_CREATED, OaSignTaskStatus.WAITING_HR_CONFIRM,
                OaSignTaskStatus.READY_TO_SEND, OaSignTaskStatus.FAILED,
                OaSignTaskStatus.CANCELLED);
        allow(OaSignTaskStatus.WAITING_HR_CONFIRM, OaSignTaskStatus.READY_TO_SEND,
                OaSignTaskStatus.NEEDS_DATA, OaSignTaskStatus.FAILED,
                OaSignTaskStatus.CANCELLED);
        allow(OaSignTaskStatus.READY_TO_SEND, OaSignTaskStatus.SENDING,
                OaSignTaskStatus.WAITING_HR_CONFIRM, OaSignTaskStatus.CANCELLED);
        allow(OaSignTaskStatus.SENDING, OaSignTaskStatus.PENDING_SIGN,
                OaSignTaskStatus.PENDING_FINAL_CONFIRM, OaSignTaskStatus.FAILED);
        allow(OaSignTaskStatus.FAILED, OaSignTaskStatus.VALIDATING,
                OaSignTaskStatus.READY_TO_SEND, OaSignTaskStatus.CANCELLED);
        allow(OaSignTaskStatus.PENDING_SIGN, OaSignTaskStatus.VIEWED, OaSignTaskStatus.PENDING_COMPANY,
                OaSignTaskStatus.PENDING_FINAL_CONFIRM,
                OaSignTaskStatus.REFUSED, OaSignTaskStatus.EXPIRED, OaSignTaskStatus.CANCELLED);
        allow(OaSignTaskStatus.VIEWED, OaSignTaskStatus.PENDING_COMPANY,
                OaSignTaskStatus.PENDING_FINAL_CONFIRM,
                OaSignTaskStatus.REFUSED,
                OaSignTaskStatus.EXPIRED, OaSignTaskStatus.CANCELLED);
        allow(OaSignTaskStatus.PENDING_COMPANY, OaSignTaskStatus.PENDING_FINAL_CONFIRM,
                OaSignTaskStatus.CANCELLED);
        allow(OaSignTaskStatus.PENDING_FINAL_CONFIRM, OaSignTaskStatus.SIGNED,
                OaSignTaskStatus.REFUSED, OaSignTaskStatus.EXPIRED, OaSignTaskStatus.CANCELLED);
    }

    public void assertAllowed(OaSignTaskStatus from, OaSignTaskStatus to)
    {
        if (from == null || to == null || !ALLOWED.getOrDefault(from,
                EnumSet.noneOf(OaSignTaskStatus.class)).contains(to))
        {
            throw new ServiceException("当前签约任务状态不允许执行此操作");
        }
    }

    private static void allow(OaSignTaskStatus from, OaSignTaskStatus... targets)
    {
        ALLOWED.put(from, EnumSet.of(targets[0], targets));
    }
}
