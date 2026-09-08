package com.erp.system.domain.vo;

/** 账户解锁的前后状态，供前端刷新和审计核对。 */
public class SysLoginUnlockResultVo
{
    private String userName;
    private boolean unlocked;
    private SysLoginLockStateVo before;
    private SysLoginLockStateVo after;

    public SysLoginUnlockResultVo()
    {
    }

    public SysLoginUnlockResultVo(String userName, boolean unlocked, SysLoginLockStateVo before,
            SysLoginLockStateVo after)
    {
        this.userName = userName;
        this.unlocked = unlocked;
        this.before = before;
        this.after = after;
    }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public boolean isUnlocked() { return unlocked; }
    public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }
    public SysLoginLockStateVo getBefore() { return before; }
    public void setBefore(SysLoginLockStateVo before) { this.before = before; }
    public SysLoginLockStateVo getAfter() { return after; }
    public void setAfter(SysLoginLockStateVo after) { this.after = after; }
}
