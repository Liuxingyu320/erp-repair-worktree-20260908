package com.erp.file.drive.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * 写入对象存储前建立的全局容量预占；只有确认对象不存在后才可释放。
 */
public class DriveUploadReservation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private String reservationId;
    private Long spaceId;
    private String storageKey;
    private Long reservedBytes;
    private String status;
    private Date expireTime;
    private Integer retryCount;
    private Date nextRetryTime;
    private Integer version;
    private String lastErrorCode;

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public Long getReservedBytes() { return reservedBytes; }
    public void setReservedBytes(Long reservedBytes) { this.reservedBytes = reservedBytes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Date getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Date nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
}
