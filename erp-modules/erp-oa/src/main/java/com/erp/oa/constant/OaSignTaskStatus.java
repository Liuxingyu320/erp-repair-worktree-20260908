package com.erp.oa.constant;

public enum OaSignTaskStatus
{
    NEW,
    VALIDATING,
    NEEDS_DATA,
    DRAFT_CREATED,
    WAITING_HR_CONFIRM,
    READY_TO_SEND,
    SENDING,
    FAILED,
    PENDING_SIGN,
    VIEWED,
    PENDING_COMPANY,
    PENDING_FINAL_CONFIRM,
    SIGNED(true),
    REFUSED(true),
    EXPIRED(true),
    CANCELLED(true),
    NO_ACTION(true);

    private final boolean terminal;

    OaSignTaskStatus()
    {
        this(false);
    }

    OaSignTaskStatus(boolean terminal)
    {
        this.terminal = terminal;
    }

    public boolean isTerminal()
    {
        return terminal;
    }
}
