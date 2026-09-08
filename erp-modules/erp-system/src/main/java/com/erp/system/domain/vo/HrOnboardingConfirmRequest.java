package com.erp.system.domain.vo;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

public class HrOnboardingConfirmRequest implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Integer version;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date actualEntryDate;
    private String conflictAction;
    private Long bindUserId;
    private String idempotencyKey;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Date getActualEntryDate() { return actualEntryDate; }
    public void setActualEntryDate(Date actualEntryDate) { this.actualEntryDate = actualEntryDate; }
    public String getConflictAction() { return conflictAction; }
    public void setConflictAction(String conflictAction) { this.conflictAction = conflictAction; }
    public Long getBindUserId() { return bindUserId; }
    public void setBindUserId(Long bindUserId) { this.bindUserId = bindUserId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
