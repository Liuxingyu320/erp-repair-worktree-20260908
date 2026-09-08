package com.erp.inventory.domain.dto;

import jakarta.validation.constraints.NotNull;

/** 人工重放调拨审批发起记录。 */
public class InvTransferApprovalStartReplayRequest
{
    @NotNull(message = "版本不能为空")
    private Long version;

    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
