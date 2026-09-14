package com.erp.system.domain.dto;

/** String binding preserves Long-sized IDs and rejects fractional JSON numbers on validation. */
public class SysUserNotificationReadAllRequest
{
    private String snapshotMaxId;
    public String getSnapshotMaxId() { return snapshotMaxId; }
    public void setSnapshotMaxId(String value) { snapshotMaxId = value; }
    public Long validate() { return SysUserNotificationPageQuery.parseSnapshot(snapshotMaxId, true); }
}
