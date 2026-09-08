package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public class InvQualityCheckItem
{
    @NotNull(message = "收货批次明细不能为空")
    private Long batchDetailId;
    @NotNull(message = "本次检验数量不能为空")
    @DecimalMin(value = "0.0001", message = "本次检验数量必须大于0")
    @Digits(integer = 14, fraction = 4, message = "本次检验数量最多14位整数和4位小数")
    private BigDecimal inspectedQuantity;
    @NotNull(message = "合格数量不能为空")
    @DecimalMin(value = "0", message = "合格数量不能为负")
    @Digits(integer = 14, fraction = 4, message = "合格数量最多14位整数和4位小数")
    private BigDecimal acceptedQuantity;
    @NotNull(message = "不合格数量不能为空")
    @DecimalMin(value = "0", message = "不合格数量不能为负")
    @Digits(integer = 14, fraction = 4, message = "不合格数量最多14位整数和4位小数")
    private BigDecimal rejectedQuantity;
    @NotNull(message = "让步数量不能为空")
    @DecimalMin(value = "0", message = "让步数量不能为负")
    @Digits(integer = 14, fraction = 4, message = "让步数量最多14位整数和4位小数")
    private BigDecimal concessionQuantity;
    private String defectLevel;
    private String defectReason;
    private String remark;
    private List<String> attachmentUrls;

    public Long getBatchDetailId() { return batchDetailId; }
    public void setBatchDetailId(Long batchDetailId) { this.batchDetailId = batchDetailId; }
    public BigDecimal getInspectedQuantity() { return inspectedQuantity; }
    public void setInspectedQuantity(BigDecimal inspectedQuantity) { this.inspectedQuantity = inspectedQuantity; }
    public BigDecimal getAcceptedQuantity() { return acceptedQuantity; }
    public void setAcceptedQuantity(BigDecimal acceptedQuantity) { this.acceptedQuantity = acceptedQuantity; }
    public BigDecimal getRejectedQuantity() { return rejectedQuantity; }
    public void setRejectedQuantity(BigDecimal rejectedQuantity) { this.rejectedQuantity = rejectedQuantity; }
    public BigDecimal getConcessionQuantity() { return concessionQuantity; }
    public void setConcessionQuantity(BigDecimal concessionQuantity) { this.concessionQuantity = concessionQuantity; }
    public String getDefectLevel() { return defectLevel; }
    public void setDefectLevel(String defectLevel) { this.defectLevel = defectLevel; }
    public String getDefectReason() { return defectReason; }
    public void setDefectReason(String defectReason) { this.defectReason = defectReason; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<String> getAttachmentUrls() { return attachmentUrls; }
    public void setAttachmentUrls(List<String> attachmentUrls) { this.attachmentUrls = attachmentUrls; }
}
