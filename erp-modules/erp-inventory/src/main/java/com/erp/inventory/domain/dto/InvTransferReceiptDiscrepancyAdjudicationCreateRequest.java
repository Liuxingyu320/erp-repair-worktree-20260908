package com.erp.inventory.domain.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationCommand;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.ActionInput;

/** Browser-safe body for a future discrepancy adjudication write boundary. */
@JsonIgnoreProperties(ignoreUnknown = false)
public class InvTransferReceiptDiscrepancyAdjudicationCreateRequest
{
    @NotNull(message = "差异裁决依据令牌不能为空")
    @Pattern(regexp = "adjb_v1_[A-Za-z0-9_-]{43}",
            message = "差异裁决依据令牌格式无效")
    private String basisToken;

    @NotNull(message = "差异事项版本不能为空")
    @Pattern(regexp = "0|[1-9][0-9]{0,18}",
            message = "差异事项版本格式无效")
    @DecimalMax(value = "9223372036854775807",
            message = "差异事项版本超出范围")
    private String caseVersion;

    @NotBlank(message = "裁决说明不能为空")
    @Size(max = 500, message = "裁决说明不能超过500个字符")
    private String adjudicationNote;

    @NotBlank(message = "裁决证据不能为空")
    @Size(max = 2000, message = "裁决证据不能超过2000个字符")
    private String evidenceRefs;

    @NotEmpty(message = "裁决动作不能为空")
    @Size(max = 20, message = "单次裁决动作不能超过20条")
    private List<@NotNull(message = "裁决动作不能为空")
            @Valid InvTransferReceiptDiscrepancyAdjudicationActionRequest>
                    actions = new ArrayList<>();

    public String getBasisToken() { return basisToken; }
    public void setBasisToken(String value) { basisToken = value; }
    public String getCaseVersion() { return caseVersion; }
    public void setCaseVersion(String value) { caseVersion = value; }
    public String getAdjudicationNote() { return adjudicationNote; }
    public void setAdjudicationNote(String value) {
        adjudicationNote = value;
    }
    public String getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(String value) { evidenceRefs = value; }
    public List<InvTransferReceiptDiscrepancyAdjudicationActionRequest>
            getActions()
    {
        return actions == null ? null : Collections.unmodifiableList(
                new ArrayList<>(actions));
    }
    public void setActions(
            List<InvTransferReceiptDiscrepancyAdjudicationActionRequest>
                    value)
    {
        actions = value == null ? null : new ArrayList<>(value);
    }

    /** Combines future header/path authority with this body without facts. */
    public InvTransferReceiptDiscrepancyAdjudicationCommand toCommand(
            String requestId, Long discrepancyCaseId)
    {
        List<ActionInput> mapped = actions == null ? null : actions.stream()
                .map(value -> value == null ? null : value.toActionInput())
                .toList();
        return new InvTransferReceiptDiscrepancyAdjudicationCommand(
                requestId, basisToken, discrepancyCaseId, version(),
                adjudicationNote, evidenceRefs, mapped);
    }

    private Long version()
    {
        try
        {
            return Long.valueOf(caseVersion);
        }
        catch (RuntimeException invalid)
        {
            throw new ServiceException("差异事项版本格式无效");
        }
    }

    @Override
    public String toString()
    {
        return "InvTransferReceiptDiscrepancyAdjudicationCreateRequest["
                + "basisToken=[REDACTED], caseVersion=" + caseVersion
                + ", adjudicationNote=[REDACTED], evidenceRefs=[REDACTED]"
                + ", actionCount=" + (actions == null ? null
                        : actions.size())
                + "]";
    }
}
