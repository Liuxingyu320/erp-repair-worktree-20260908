package com.erp.system.domain.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** HR对一个待处理合同续签周期作出的明确决定。 */
public class HrRenewalDecisionRequest
{
    public enum Decision
    {
        RENEW,
        DECLINE
    }

    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId长度不能超过64个字符")
    private String requestId;

    /** The archive entry freezes the old contract cycle shown to HR. */
    @Size(max = 128, message = "旧合同周期标识无效")
    private String expectedCycleKey;
    public String getExpectedCycleKey() { return expectedCycleKey; }
    public void setExpectedCycleKey(String value) { expectedCycleKey = value; }


    @NotNull(message = "续签决定不能为空")
    private Decision decision;

    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private String contractTypeCode;
    private String contractTermCode;
    private Long legalEntityId;
    private String legalEntityCode;
    private String legalEntityName;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Decision getDecision() { return decision; }
    public void setDecision(Decision decision) { this.decision = decision; }
    public LocalDate getContractStartDate() { return contractStartDate; }
    public void setContractStartDate(LocalDate contractStartDate) { this.contractStartDate = contractStartDate; }
    public LocalDate getContractEndDate() { return contractEndDate; }
    public void setContractEndDate(LocalDate contractEndDate) { this.contractEndDate = contractEndDate; }
    public String getContractTypeCode() { return contractTypeCode; }
    public void setContractTypeCode(String contractTypeCode) { this.contractTypeCode = contractTypeCode; }
    public String getContractTermCode() { return contractTermCode; }
    public void setContractTermCode(String contractTermCode) { this.contractTermCode = contractTermCode; }
    public Long getLegalEntityId() { return legalEntityId; }
    public void setLegalEntityId(Long legalEntityId) { this.legalEntityId = legalEntityId; }
    public String getLegalEntityCode() { return legalEntityCode; }
    public void setLegalEntityCode(String legalEntityCode) { this.legalEntityCode = legalEntityCode; }
    public String getLegalEntityName() { return legalEntityName; }
    public void setLegalEntityName(String legalEntityName) { this.legalEntityName = legalEntityName; }
}
