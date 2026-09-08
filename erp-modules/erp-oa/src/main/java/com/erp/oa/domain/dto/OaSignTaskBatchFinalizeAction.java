package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Explicit company-and-seal decision for one pending-company signing task. */
public class OaSignTaskBatchFinalizeAction
{
    @NotNull(message = "任务编号不能为空")
    @Positive(message = "任务编号无效")
    private Long taskId;

    @NotNull(message = "签约包编号不能为空")
    @Positive(message = "签约包编号无效")
    private Long packageId;

    @NotNull(message = "请选择合同公司")
    @Positive(message = "合同公司编号无效")
    private Long legalEntityId;

    @Positive(message = "合同印章编号无效")
    private Long sealId;

    @Size(max = 500, message = "人工改选公司原因不能超过500个字符")
    private String correctionReason;

    @NotNull(message = "任务预期版本不能为空")
    @PositiveOrZero(message = "任务预期版本不能小于0")
    private Long expectedTaskVersion;

    @NotNull(message = "签约包预期版本不能为空")
    @PositiveOrZero(message = "签约包预期版本不能小于0")
    private Long expectedPackageVersion;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public Long getLegalEntityId() { return legalEntityId; }
    public void setLegalEntityId(Long legalEntityId) { this.legalEntityId = legalEntityId; }
    public Long getSealId() { return sealId; }
    public void setSealId(Long sealId) { this.sealId = sealId; }
    public String getCorrectionReason() { return correctionReason; }
    public void setCorrectionReason(String correctionReason) { this.correctionReason = correctionReason; }
    public Long getExpectedTaskVersion() { return expectedTaskVersion; }
    public void setExpectedTaskVersion(Long expectedTaskVersion) { this.expectedTaskVersion = expectedTaskVersion; }
    public Long getExpectedPackageVersion() { return expectedPackageVersion; }
    public void setExpectedPackageVersion(Long expectedPackageVersion) { this.expectedPackageVersion = expectedPackageVersion; }
}
