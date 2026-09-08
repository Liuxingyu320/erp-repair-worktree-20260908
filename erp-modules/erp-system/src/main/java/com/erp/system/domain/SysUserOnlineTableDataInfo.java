package com.erp.system.domain;

import com.erp.common.core.web.page.TableDataInfo;

/**
 * 在线会话列表响应，补充 Redis 有界扫描的可见性元数据。
 */
public class SysUserOnlineTableDataInfo extends TableDataInfo
{
    private static final long serialVersionUID = 1L;

    /** 是否因安全扫描上限而截断 */
    private boolean truncated;

    /** 本次扫描最多返回的会话键数量 */
    private int scanLimit;

    /** Redis 游标本次实际读取的键数量 */
    private long scannedCount;

    /** 过期、空值或无法解析的会话数量 */
    private int invalidSessionCount;

    public static SysUserOnlineTableDataInfo from(TableDataInfo source)
    {
        SysUserOnlineTableDataInfo target = new SysUserOnlineTableDataInfo();
        target.setCode(source.getCode());
        target.setMsg(source.getMsg());
        target.setRows(source.getRows());
        target.setTotal(source.getTotal());
        return target;
    }

    public boolean isTruncated()
    {
        return truncated;
    }

    public void setTruncated(boolean truncated)
    {
        this.truncated = truncated;
    }

    public int getScanLimit()
    {
        return scanLimit;
    }

    public void setScanLimit(int scanLimit)
    {
        this.scanLimit = scanLimit;
    }

    public long getScannedCount()
    {
        return scannedCount;
    }

    public void setScannedCount(long scannedCount)
    {
        this.scannedCount = scannedCount;
    }

    public int getInvalidSessionCount()
    {
        return invalidSessionCount;
    }

    public void setInvalidSessionCount(int invalidSessionCount)
    {
        this.invalidSessionCount = invalidSessionCount;
    }
}
