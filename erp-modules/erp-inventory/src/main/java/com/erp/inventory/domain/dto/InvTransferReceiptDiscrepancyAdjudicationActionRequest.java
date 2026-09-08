package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.ActionInput;

/** Browser-authored action shape; amounts and execution state stay server-side. */
@JsonIgnoreProperties(ignoreUnknown = false)
public class InvTransferReceiptDiscrepancyAdjudicationActionRequest
{
    @NotNull(message = "裁决动作序号不能为空")
    @Min(value = 1, message = "裁决动作序号必须从1开始")
    @Max(value = 20, message = "裁决动作序号不能超过20")
    private Integer sequence;

    @NotNull(message = "裁决动作类型不能为空")
    @Pattern(regexp = "reship|return_to_source|damage_write_off|"
            + "responsibility_adjustment|transport_loss_write_off",
            message = "裁决动作类型无效")
    private String actionType;

    @NotNull(message = "裁决动作数量不能为空")
    @DecimalMin(value = "0", inclusive = false,
            message = "裁决动作数量必须大于0")
    @Digits(integer = 14, fraction = 4,
            message = "裁决动作数量最多14位整数和4位小数")
    private BigDecimal quantity;

    @NotNull(message = "裁决责任方不能为空")
    @Pattern(regexp = "source|target|carrier|company",
            message = "裁决责任方无效")
    private String responsibleParty;

    @NotBlank(message = "裁决动作说明不能为空")
    @Size(max = 500, message = "裁决动作说明不能超过500个字符")
    private String note;

    public Integer getSequence() { return sequence; }
    public void setSequence(Integer value) { sequence = value; }
    public String getActionType() { return actionType; }
    public void setActionType(String value) { actionType = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public String getResponsibleParty() { return responsibleParty; }
    public void setResponsibleParty(String value) {
        responsibleParty = value;
    }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }

    public ActionInput toActionInput()
    {
        return new ActionInput(sequence, actionType, quantity,
                responsibleParty, note);
    }

    @Override
    public String toString()
    {
        return "InvTransferReceiptDiscrepancyAdjudicationActionRequest["
                + "sequence=" + sequence + ", actionType=" + actionType
                + ", quantity=" + quantity + ", responsibleParty="
                + responsibleParty + ", note=[REDACTED]]";
    }
}
