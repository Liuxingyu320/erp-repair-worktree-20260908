package com.erp.system.domain.vo;

import com.erp.common.core.annotation.Excel;

/** Explicit export allow-list for system configuration. */
public class SysConfigExportVo
{
    @Excel(name = "参数编号")
    private Long configId;

    @Excel(name = "参数名称")
    private String configName;

    @Excel(name = "参数键名")
    private String configKey;

    @Excel(name = "参数键值")
    private String configValue;

    @Excel(name = "是否敏感", readConverterExp = "true=是,false=否")
    private boolean sensitive;

    @Excel(name = "已配置", readConverterExp = "true=是,false=否")
    private boolean valueConfigured;

    @Excel(name = "系统内置", readConverterExp = "Y=是,N=否")
    private String configType;

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }
    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public boolean isSensitive() { return sensitive; }
    public void setSensitive(boolean sensitive) { this.sensitive = sensitive; }
    public boolean isValueConfigured() { return valueConfigured; }
    public void setValueConfigured(boolean valueConfigured) { this.valueConfigured = valueConfigured; }
    public String getConfigType() { return configType; }
    public void setConfigType(String configType) { this.configType = configType; }
}
