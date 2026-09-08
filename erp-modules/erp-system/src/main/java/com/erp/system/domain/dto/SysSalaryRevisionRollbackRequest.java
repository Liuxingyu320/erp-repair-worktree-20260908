package com.erp.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 将历史薪资修订恢复为一个新版本的请求。 */
public class SysSalaryRevisionRollbackRequest
{
    @NotNull(message = "当前方案版本不能为空")
    private Integer expectedVersion;

    @NotBlank(message = "回滚原因不能为空")
    @Size(max = 500, message = "回滚原因不能超过500个字符")
    private String reason;

    private Boolean emergencyCorrection;

    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Boolean getEmergencyCorrection() { return emergencyCorrection; }
    public void setEmergencyCorrection(Boolean emergencyCorrection) { this.emergencyCorrection = emergencyCorrection; }
}
