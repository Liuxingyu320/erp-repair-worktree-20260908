package com.erp.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Safe configuration row for management screens. */
public class SysConfigListVo
{
    private Long configId;
    private String configName;
    private String configKey;
    private String configValue;
    private String configType;
    private boolean sensitive;
    private boolean valueConfigured;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private Integer version;
    private String groupCode;
    private String valueType;
    private String sensitiveFlag;
    private String validationRule;
    private Integer displayOrder;
    private Boolean publicConfig;
    private String descriptorDescription;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }
    public String getSensitiveFlag() { return sensitiveFlag; }
    public void setSensitiveFlag(String sensitiveFlag) { this.sensitiveFlag = sensitiveFlag; }
    public String getValidationRule() { return validationRule; }
    public void setValidationRule(String validationRule) { this.validationRule = validationRule; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public Boolean getPublicConfig() { return publicConfig; }
    public void setPublicConfig(Boolean publicConfig) { this.publicConfig = publicConfig; }
    public String getDescriptorDescription() { return descriptorDescription; }
    public void setDescriptorDescription(String descriptorDescription) { this.descriptorDescription = descriptorDescription; }

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }
    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public String getConfigType() { return configType; }
    public void setConfigType(String configType) { this.configType = configType; }
    public boolean isSensitive() { return sensitive; }
    public void setSensitive(boolean sensitive) { this.sensitive = sensitive; }
    public boolean isValueConfigured() { return valueConfigured; }
    public void setValueConfigured(boolean valueConfigured) { this.valueConfigured = valueConfigured; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
