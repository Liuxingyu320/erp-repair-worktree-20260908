package com.erp.oa.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * HR batch company/seal choice for signature-first onboarding rows.
 *
 * <p>The employee signature is already frozen on the row's current data request. This action
 * only selects contract master data and, on execute, generates the formal draft. It never sends
 * a task automatically.</p>
 */
public class OaSignOnboardCompanyWorkRequest
{
    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号长度不能超过64")
    private String requestId;

    @Valid
    @NotEmpty(message = "请选择待选公司盖章记录")
    @Size(max = 20, message = "一次最多处理20条")
    private List<Item> items = new ArrayList<>();

    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public List<Item> getItems() { return new ArrayList<>(items); }
    public void setItems(List<Item> value)
    {
        items = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    public static class Item
    {
        @NotNull(message = "导入批次不能为空")
        @Positive(message = "导入批次无效")
        private Long batchId;

        @NotNull(message = "导入行不能为空")
        @Positive(message = "导入行无效")
        private Long rowId;

        @NotNull(message = "行版本不能为空")
        @Positive(message = "行版本无效")
        private Long version;

        @NotNull(message = "合同公司不能为空")
        @Positive(message = "合同公司无效")
        private Long legalEntityId;

        @NotNull(message = "合同印章不能为空")
        @Positive(message = "合同印章无效")
        private Long sealId;

        private Boolean noExternalContractConfirmed;

        @Size(max = 500, message = "历史补签原因不能超过500字")
        private String historicalReason;

        @Size(max = 500, message = "警告确认原因不能超过500字")
        private String warningReason;

        public Long getBatchId() { return batchId; }
        public void setBatchId(Long value) { batchId = value; }
        public Long getRowId() { return rowId; }
        public void setRowId(Long value) { rowId = value; }
        public Long getVersion() { return version; }
        public void setVersion(Long value) { version = value; }
        public Long getLegalEntityId() { return legalEntityId; }
        public void setLegalEntityId(Long value) { legalEntityId = value; }
        public Long getSealId() { return sealId; }
        public void setSealId(Long value) { sealId = value; }
        public Boolean getNoExternalContractConfirmed() { return noExternalContractConfirmed; }
        public void setNoExternalContractConfirmed(Boolean value)
        {
            noExternalContractConfirmed = value;
        }
        public String getHistoricalReason() { return historicalReason; }
        public void setHistoricalReason(String value) { historicalReason = value; }
        public String getWarningReason() { return warningReason; }
        public void setWarningReason(String value) { warningReason = value; }
    }
}
