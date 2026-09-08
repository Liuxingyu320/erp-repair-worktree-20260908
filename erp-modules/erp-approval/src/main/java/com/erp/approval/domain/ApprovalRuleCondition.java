package com.erp.approval.domain;

import com.erp.common.core.web.domain.BaseEntity;

/** Structured, whitelist-based condition in a rule version. */
public class ApprovalRuleCondition extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long conditionId;
    private Long versionId;
    private Integer conditionOrder;
    private String fieldCode;
    private String operatorCode;
    private String valueType;
    private String valueText;

    public Long getConditionId() { return conditionId; }
    public void setConditionId(Long conditionId) { this.conditionId = conditionId; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Integer getConditionOrder() { return conditionOrder; }
    public void setConditionOrder(Integer conditionOrder) { this.conditionOrder = conditionOrder; }
    public String getFieldCode() { return fieldCode; }
    public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
    public String getOperatorCode() { return operatorCode; }
    public void setOperatorCode(String operatorCode) { this.operatorCode = operatorCode; }
    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }
    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }
}
