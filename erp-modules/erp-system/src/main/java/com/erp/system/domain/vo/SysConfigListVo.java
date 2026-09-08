package com.erp.system.domain.vo;

import java.util.Date;

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
    private Date createTime;
    private Date updateTime;

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
