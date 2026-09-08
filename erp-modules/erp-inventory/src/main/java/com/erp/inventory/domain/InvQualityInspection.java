package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;

/** 不可覆盖的行级质检记录。 */
public class InvQualityInspection extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long inspectionId;
    private String inspectionNo;
    private Long receiptBatchId;
    private Long batchDetailId;
    private Long purchaseOrderId;
    private Long purchaseDetailId;
    private BigDecimal inspectedQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal concessionQuantity;
    private String conclusion;
    private String defectLevel;
    private String defectReason;
    private Long inspectorUserId;
    private String inspectorName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date inspectionTime;
    private List<InvQualityInspectionAttachment> attachments;

    public Long getInspectionId() { return inspectionId; }
    public void setInspectionId(Long inspectionId) { this.inspectionId = inspectionId; }
    public String getInspectionNo() { return inspectionNo; }
    public void setInspectionNo(String inspectionNo) { this.inspectionNo = inspectionNo; }
    public Long getReceiptBatchId() { return receiptBatchId; }
    public void setReceiptBatchId(Long receiptBatchId) { this.receiptBatchId = receiptBatchId; }
    public Long getBatchDetailId() { return batchDetailId; }
    public void setBatchDetailId(Long batchDetailId) { this.batchDetailId = batchDetailId; }
    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    public Long getPurchaseDetailId() { return purchaseDetailId; }
    public void setPurchaseDetailId(Long purchaseDetailId) { this.purchaseDetailId = purchaseDetailId; }
    public BigDecimal getInspectedQuantity() { return inspectedQuantity; }
    public void setInspectedQuantity(BigDecimal inspectedQuantity) { this.inspectedQuantity = inspectedQuantity; }
    public BigDecimal getAcceptedQuantity() { return acceptedQuantity; }
    public void setAcceptedQuantity(BigDecimal acceptedQuantity) { this.acceptedQuantity = acceptedQuantity; }
    public BigDecimal getRejectedQuantity() { return rejectedQuantity; }
    public void setRejectedQuantity(BigDecimal rejectedQuantity) { this.rejectedQuantity = rejectedQuantity; }
    public BigDecimal getConcessionQuantity() { return concessionQuantity; }
    public void setConcessionQuantity(BigDecimal concessionQuantity) { this.concessionQuantity = concessionQuantity; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public String getDefectLevel() { return defectLevel; }
    public void setDefectLevel(String defectLevel) { this.defectLevel = defectLevel; }
    public String getDefectReason() { return defectReason; }
    public void setDefectReason(String defectReason) { this.defectReason = defectReason; }
    public Long getInspectorUserId() { return inspectorUserId; }
    public void setInspectorUserId(Long inspectorUserId) { this.inspectorUserId = inspectorUserId; }
    public String getInspectorName() { return inspectorName; }
    public void setInspectorName(String inspectorName) { this.inspectorName = inspectorName; }
    public Date getInspectionTime() { return inspectionTime; }
    public void setInspectionTime(Date inspectionTime) { this.inspectionTime = inspectionTime; }
    public List<InvQualityInspectionAttachment> getAttachments() { return attachments; }
    public void setAttachments(List<InvQualityInspectionAttachment> attachments) { this.attachments = attachments; }
}
