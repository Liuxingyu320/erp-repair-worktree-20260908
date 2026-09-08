package com.erp.system.domain.vo;

/** 账户因连续密码错误产生的临时锁定状态。 */
public class SysLoginLockStateVo
{
    private String userName;
    private boolean locked;
    private int retryCount;
    private long remainingSeconds;
    private boolean canUnlock;

    public SysLoginLockStateVo()
    {
    }

    public SysLoginLockStateVo(String userName, boolean locked, int retryCount, long remainingSeconds,
            boolean canUnlock)
    {
        this.userName = userName;
        this.locked = locked;
        this.retryCount = retryCount;
        this.remainingSeconds = remainingSeconds;
        this.canUnlock = canUnlock;
    }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public long getRemainingSeconds() { return remainingSeconds; }
    public void setRemainingSeconds(long remainingSeconds) { this.remainingSeconds = remainingSeconds; }
    public boolean isCanUnlock() { return canUnlock; }
    public void setCanUnlock(boolean canUnlock) { this.canUnlock = canUnlock; }
}
