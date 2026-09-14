package com.erp.system.domain.vo;

/** Unfiltered read count and the original published recipient snapshot count. */
public class SysNoticeReadSummary
{
    private long readCount;
    private long recipientCount;
    public long getReadCount() { return readCount; }
    public void setReadCount(long readCount) { this.readCount = readCount; }
    public long getRecipientCount() { return recipientCount; }
    public void setRecipientCount(long recipientCount) { this.recipientCount = recipientCount; }
}
