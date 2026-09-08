package com.erp.oa.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class OaSignOnboardGenerateRequest
{
    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号不能超过64个字符")
    private String requestId;
    @NotNull(message = "批次版本不能为空")
    @Positive(message = "批次版本无效")
    private Long batchVersion;
    @NotEmpty(message = "请选择需要生成的导入行")
    @Size(max = 100, message = "一次最多生成100行")
    private List<@Positive(message = "导入行编号无效") Long> rowIds = new ArrayList<>();
    private Boolean noExternalContractConfirmed;
    private String historicalReason;
    private String warningReason;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getBatchVersion() { return batchVersion; }
    public void setBatchVersion(Long batchVersion) { this.batchVersion = batchVersion; }
    public List<Long> getRowIds() { return new ArrayList<>(rowIds); }
    public void setRowIds(List<Long> rowIds)
    {
        this.rowIds = rowIds == null ? new ArrayList<>() : new ArrayList<>(rowIds);
    }
    public Boolean getNoExternalContractConfirmed() { return noExternalContractConfirmed; }
    public void setNoExternalContractConfirmed(Boolean value) { this.noExternalContractConfirmed = value; }
    public String getHistoricalReason() { return historicalReason; }
    public void setHistoricalReason(String historicalReason) { this.historicalReason = historicalReason; }
    public String getWarningReason() { return warningReason; }
    public void setWarningReason(String warningReason) { this.warningReason = warningReason; }
}
