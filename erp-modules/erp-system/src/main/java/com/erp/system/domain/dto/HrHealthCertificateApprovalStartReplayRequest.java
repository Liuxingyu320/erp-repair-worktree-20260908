package com.erp.system.domain.dto;

import jakarta.validation.constraints.NotNull;

/** 人工重放健康证审批发起记录。 */
public class HrHealthCertificateApprovalStartReplayRequest
{
    @NotNull(message = "版本不能为空")
    private Long version;

    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
