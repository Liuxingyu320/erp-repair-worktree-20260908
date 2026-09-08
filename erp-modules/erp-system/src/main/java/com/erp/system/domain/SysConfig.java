package com.erp.system.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.annotation.Excel.ColumnType;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * 参数配置表 sys_config
 * 
 * @author erp
 */
public class SysConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 参数主键 */
    @Excel(name = "参数编号", cellType = ColumnType.NUMERIC)
    private Long configId;

    /** 参数名称 */
    @Excel(name = "参数名称")
    private String configName;

    /** 参数键名 */
    @Excel(name = "参数键名")
    private String configKey;

    /** 参数键值 */
    @Excel(name = "参数键值")
    private String configValue;

    /** 系统内置（Y是 N否） */
    @Excel(name = "系统内置", readConverterExp = "Y=是,N=否")
    private String configType;

    /** 配置分组 */
    @Excel(name = "配置分组")
    private String groupCode;

    /** 值类型：string/integer/decimal/boolean/json/enum/password */
    @Excel(name = "值类型")
    private String valueType;

    /** 是否敏感（Y是 N否） */
    @Excel(name = "敏感配置", readConverterExp = "Y=是,N=否")
    private String sensitiveFlag;

    /** 后端可执行的轻量校验规则 */
    private String validationRule;

    /** 同分组显示顺序 */
    private Integer displayOrder;

    /** 乐观锁版本 */
    private Integer version;

    /** 管理视图专用：敏感值是否已配置，不落库。 */
    private Boolean configured;

    /** 代码描述符专用：是否允许作为公开配置，不落库。 */
    private Boolean publicConfig;

    /** 代码描述符提供的默认说明，不落库。 */
    private String descriptorDescription;

    /** Explicit opt-in required when replacing a sensitive value. Not persisted. */
    private Boolean updateSensitiveValue;

    public Long getConfigId()
    {
        return configId;
    }

    public void setConfigId(Long configId)
    {
        this.configId = configId;
    }

    @NotBlank(message = "参数名称不能为空")
    @Size(min = 0, max = 100, message = "参数名称不能超过100个字符")
    public String getConfigName()
    {
        return configName;
    }

    public void setConfigName(String configName)
    {
        this.configName = configName;
    }

    @NotBlank(message = "参数键名长度不能为空")
    @Size(min = 0, max = 100, message = "参数键名长度不能超过100个字符")
    public String getConfigKey()
    {
        return configKey;
    }

    public void setConfigKey(String configKey)
    {
        this.configKey = configKey;
    }

    @Size(min = 0, max = 500, message = "参数键值长度不能超过500个字符")
    public String getConfigValue()
    {
        return configValue;
    }

    public void setConfigValue(String configValue)
    {
        this.configValue = configValue;
    }

    public String getConfigType()
    {
        return configType;
    }

    public void setConfigType(String configType)
    {
        this.configType = configType;
    }

    @Size(max = 64, message = "配置分组不能超过64个字符")
    public String getGroupCode()
    {
        return groupCode;
    }

    public void setGroupCode(String groupCode)
    {
        this.groupCode = groupCode;
    }

    @Size(max = 20, message = "值类型不能超过20个字符")
    public String getValueType()
    {
        return valueType;
    }

    public void setValueType(String valueType)
    {
        this.valueType = valueType;
    }

    public String getSensitiveFlag()
    {
        return sensitiveFlag;
    }

    public void setSensitiveFlag(String sensitiveFlag)
    {
        this.sensitiveFlag = sensitiveFlag;
    }

    @Size(max = 255, message = "校验规则不能超过255个字符")
    public String getValidationRule()
    {
        return validationRule;
    }

    public void setValidationRule(String validationRule)
    {
        this.validationRule = validationRule;
    }

    public Integer getDisplayOrder()
    {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder)
    {
        this.displayOrder = displayOrder;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public Boolean getConfigured()
    {
        return configured;
    }

    public void setConfigured(Boolean configured)
    {
        this.configured = configured;
    }

    public Boolean getPublicConfig()
    {
        return publicConfig;
    }

    public void setPublicConfig(Boolean publicConfig)
    {
        this.publicConfig = publicConfig;
    }

    public String getDescriptorDescription()
    {
        return descriptorDescription;
    }

    public void setDescriptorDescription(String descriptorDescription)
    {
        this.descriptorDescription = descriptorDescription;
    }

    public Boolean getUpdateSensitiveValue()
    {
        return updateSensitiveValue;
    }

    public void setUpdateSensitiveValue(Boolean updateSensitiveValue)
    {
        this.updateSensitiveValue = updateSensitiveValue;
    }
    
    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("configId", getConfigId())
            .append("configName", getConfigName())
            .append("configKey", getConfigKey())
            .append("configType", getConfigType())
            .append("groupCode", getGroupCode())
            .append("valueType", getValueType())
            .append("sensitiveFlag", getSensitiveFlag())
            .append("displayOrder", getDisplayOrder())
            .append("version", getVersion())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
