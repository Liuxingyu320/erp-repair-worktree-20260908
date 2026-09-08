package com.erp.system.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.SysConfig;

@DisplayName("系统配置可信描述符")
class SysConfigDescriptorRegistryTest
{
    private SysConfigDescriptorRegistry registry;

    @BeforeEach
    void setUp()
    {
        registry = new SysConfigDescriptorRegistry(new ObjectMapper());
    }

    @Test
    @DisplayName("未注册内置配置默认敏感且管理详情不回显")
    void unknownBuiltInConfigShouldFailClosedAndMaskValue()
    {
        SysConfig config = config("sys.future.secret", "top-secret", "Y");

        registry.prepareForManagement(config);

        assertThat(config.getSensitiveFlag()).isEqualTo("Y");
        assertThat(config.getPublicConfig()).isFalse();
        assertThat(config.getConfigured()).isTrue();
        assertThat(config.getConfigValue()).isNull();
        assertThat(config.getValueType()).isEqualTo("password");
    }

    @Test
    @DisplayName("注册整数配置统一执行范围校验")
    void registeredIntegerShouldValidateRange()
    {
        SysConfig config = config("sys.user.password.maxRetryCount", "100", "Y");

        assertThatThrownBy(() -> registry.prepareForWrite(config, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能大于 20");
    }

    @Test
    @DisplayName("敏感配置编辑留空时保留原值")
    void sensitiveUpdateShouldPreserveExistingValueWhenBlank()
    {
        SysConfig existing = config("jwt.secret", "01234567890123456789012345678901", "Y");
        existing.setSensitiveFlag("Y");
        SysConfig update = config("jwt.secret", "", "Y");

        registry.prepareForWrite(update, existing);

        assertThat(update.getConfigValue()).isEqualTo(existing.getConfigValue());
        assertThat(update.getSensitiveFlag()).isEqualTo("Y");
    }

    @Test
    @DisplayName("自定义 JSON 配置拒绝无效 JSON")
    void customJsonShouldBeParsedBeforeWrite()
    {
        SysConfig config = config("custom.rules", "{bad-json", "N");
        config.setValueType("json");
        config.setGroupCode("custom");

        assertThatThrownBy(() -> registry.prepareForWrite(config, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("json 类型");
    }

    @Test
    @DisplayName("配置对象字符串不包含原始敏感值")
    void configToStringShouldNotContainRawValue()
    {
        SysConfig config = config("jwt.secret", "never-log-this-secret", "Y");

        assertThat(config.toString()).doesNotContain("never-log-this-secret");
    }

    private SysConfig config(String key, String value, String type)
    {
        SysConfig config = new SysConfig();
        config.setConfigName(key);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setConfigType(type);
        return config;
    }
}
