package com.erp.oa.domain.vo;

/** Seven dashboard counters returned by one scoped database aggregation. */
public class OaSignTaskMetrics
{
    private long needsData;
    private long confirm;
    private long failed;
    private long viewed;
    private long dueSoon;
    private long refused;
    private long completedMonth;

    public long getNeedsData() { return needsData; }
    public void setNeedsData(long needsData) { this.needsData = needsData; }
    public long getConfirm() { return confirm; }
    public void setConfirm(long confirm) { this.confirm = confirm; }
    public long getFailed() { return failed; }
    public void setFailed(long failed) { this.failed = failed; }
    public long getViewed() { return viewed; }
    public void setViewed(long viewed) { this.viewed = viewed; }
    public long getDueSoon() { return dueSoon; }
    public void setDueSoon(long dueSoon) { this.dueSoon = dueSoon; }
    public long getRefused() { return refused; }
    public void setRefused(long refused) { this.refused = refused; }
    public long getCompletedMonth() { return completedMonth; }
    public void setCompletedMonth(long completedMonth) { this.completedMonth = completedMonth; }
}
