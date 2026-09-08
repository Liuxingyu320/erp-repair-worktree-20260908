package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public class InvReceiveItem
{
    @NotNull(message = "明细ID不能为空")
    private Long detailId;

    @NotNull(message = "收货数量不能为空")
    @DecimalMin(value = "0.0001", message = "收货数量必须大于0")
    @Digits(integer = 14, fraction = 4, message = "收货数量最多14位整数和4位小数")
    private BigDecimal receiveQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal damagedQuantity;
    private String discrepancyNote;
    private String attachmentRefs;

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long detailId) { this.detailId = detailId; }
    public BigDecimal getReceiveQuantity() { return receiveQuantity; }
    public void setReceiveQuantity(BigDecimal receiveQuantity) { this.receiveQuantity = receiveQuantity; }
    public BigDecimal getRejectedQuantity() { return rejectedQuantity; }
    public void setRejectedQuantity(BigDecimal value) { rejectedQuantity = value; }
    public BigDecimal getDamagedQuantity() { return damagedQuantity; }
    public void setDamagedQuantity(BigDecimal value) { damagedQuantity = value; }
    public String getDiscrepancyNote() { return discrepancyNote; }
    public void setDiscrepancyNote(String value) { discrepancyNote = value; }
    public String getAttachmentRefs() { return attachmentRefs; }
    public void setAttachmentRefs(String value) { attachmentRefs = value; }
}
