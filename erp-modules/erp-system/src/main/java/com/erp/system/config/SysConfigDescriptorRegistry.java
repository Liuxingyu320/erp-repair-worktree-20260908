package com.erp.system.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.system.domain.SysConfig;

/**
 * 系统配置的可信描述符注册表。
 *
 * <p>内置配置的安全属性以本注册表为准，不能通过管理接口降级；未注册的内置配置
 * 默认按敏感、不可公开处理。自定义配置可使用数据库元数据，但已标记为敏感后不允许
 * 在一次普通编辑中降级为非敏感。</p>
 */
@Component
public class SysConfigDescriptorRegistry
{
    public static final String TYPE_STRING = "string";
    public static final String TYPE_INTEGER = "integer";
    public static final String TYPE_DECIMAL = "decimal";
    public static final String TYPE_BOOLEAN = "boolean";
    public static final String TYPE_JSON = "json";
    public static final String TYPE_ENUM = "enum";
    public static final String TYPE_PASSWORD = "password";
    public static final String MASKED_VALUE = "******";

    private static final String YES = UserConstants.YES;
    private static final String NO = "N";
    private static final int DEFAULT_DISPLAY_ORDER = 100;

    private final ObjectMapper objectMapper;
    private final Map<String, Descriptor> descriptors;

    public SysConfigDescriptorRegistry(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
        Map<String, Descriptor> values = new LinkedHashMap<>();

        register(values, descriptor("audit.retention.days", "audit", TYPE_INTEGER, false, false,
                "min=1;max=3650", 10, "审计日志保留天数，超期数据应先归档再清理"));
        register(values, descriptor("sys.audit.retention.enabled", "audit", TYPE_BOOLEAN, false, false,
                null, 20, "是否启用审计保留治理"));

        register(values, descriptor("jwt.expiration", "security", TYPE_INTEGER, false, false,
                "min=5;max=10080", 10, "JWT 有效期，单位为分钟"));
        register(values, descriptor("jwt.secret", "security", TYPE_PASSWORD, true, false,
                "minLength=32;maxLength=500", 20, "JWT 签名密钥，只能重置，不能读取原值"));
        register(values, descriptor("security.inner.sign.key", "security", TYPE_PASSWORD, true, false,
                "minLength=32;maxLength=500", 30, "内部服务调用签名密钥，只能重置"));
        register(values, descriptor("sys.login.blackIPList", "security", TYPE_PASSWORD, true, false,
                "maxLength=500", 40, "登录黑名单规则，按敏感安全策略管理"));

        register(values, descriptor("sys.user.initPassword", "account", TYPE_PASSWORD, true, false,
                "minLength=8;maxLength=128", 10, "兼容性初始密码；新建账号优先使用系统生成的一次性密码"));
        register(values, descriptor("sys.account.chrtype", "account", TYPE_ENUM, false, false,
                "enum=0,1,2,3,4", 20, "密码字符策略：0 任意、1 数字、2 字母、3 字母数字、4 强密码"));
        register(values, descriptor("sys.account.initPasswordModify", "account", TYPE_ENUM, false, false,
                "enum=0,1", 30, "初始密码修改提醒策略"));
        register(values, descriptor("sys.account.passwordValidateDays", "account", TYPE_INTEGER, false, false,
                "min=0;max=365", 40, "密码更新周期，0 表示不限制"));
        register(values, descriptor("sys.account.registerUser", "account", TYPE_BOOLEAN, false, false,
                null, 50, "是否允许账号自助注册"));
        register(values, descriptor("sys.user.password.expireDays", "account", TYPE_INTEGER, false, false,
                "min=0;max=3650", 60, "密码有效天数，0 表示不过期"));
        register(values, descriptor("sys.user.password.maxRetryCount", "account", TYPE_INTEGER, false, false,
                "min=1;max=20", 70, "账号锁定前允许的连续失败次数"));
        register(values, descriptor("sys.user.password.minLength", "account", TYPE_INTEGER, false, false,
                "min=8;max=128", 80, "密码最小长度"));

        register(values, descriptor("sys.index.sideTheme", "appearance", TYPE_ENUM, false, true,
                "enum=theme-dark,theme-light", 10, "默认侧边栏主题"));
        register(values, descriptor("sys.index.skinName", "appearance", TYPE_ENUM, false, true,
                "enum=skin-blue,skin-green,skin-purple,skin-red,skin-yellow", 20, "默认皮肤样式"));

        register(values, descriptor("file.presigned.url.ttl", "file", TYPE_INTEGER, false, false,
                "min=30;max=86400", 10, "私有文件签名地址有效期，单位为秒"));
        register(values, descriptor("file.upload.maxSize", "file", TYPE_INTEGER, false, false,
                "min=1;max=2048", 20, "单文件上传上限，单位为 MB"));
        register(values, descriptor("file.upload.whitelist", "file", TYPE_STRING, false, false,
                "maxLength=500", 30, "允许上传的扩展名白名单"));
        register(values, descriptor("pda.scan.enabled", "inventory", TYPE_BOOLEAN, false, false,
                null, 10, "仓库 PDA 扫码作业开关"));

        register(values, descriptor("todo.approval.urgent.hours", "todo", TYPE_INTEGER, false, false,
                "min=1;max=720", 10, "审批待办进入紧急状态前的小时数"));
        register(values, descriptor("todo.contract.warning.days", "todo", TYPE_INTEGER, false, false,
                "min=1;max=3650", 20, "合同到期预警天数"));
        register(values, descriptor("todo.contract.urgent.days", "todo", TYPE_INTEGER, false, false,
                "min=0;max=3650", 30, "合同到期紧急天数"));
        register(values, descriptor("todo.summary.recent.limit", "todo", TYPE_INTEGER, false, false,
                "min=1;max=500", 40, "工作台最近事项数量上限"));

        register(values, descriptor("sign.hr.user-id", "hr", TYPE_INTEGER, false, false,
                "min=1", 10, "唯一 HR 负责人用户 ID"));
        register(values, descriptor("sign.renewal.decision-days", "hr", TYPE_INTEGER, false, false,
                "min=1;max=365", 20, "合同续签决策提前天数"));
        register(values, descriptor("hr.onboarding.post_entry_due_days", "hr", TYPE_INTEGER, false, false,
                "min=0;max=365", 30, "入职后资料补全期限"));
        register(values, descriptor("hr.onboarding.import_retention_days", "hr", TYPE_INTEGER, false, false,
                "min=1;max=3650", 40, "入职导入临时结果保留天数"));
        register(values, descriptor("hr.employee.no.prefix", "hr", TYPE_STRING, false, false,
                "minLength=1;maxLength=16", 50, "员工编号前缀"));

        descriptors = Collections.unmodifiableMap(values);
    }

    /** 将可信元数据应用到写请求，并统一校验值。 */
    public void prepareForWrite(SysConfig config, SysConfig existing)
    {
        if (config == null || StringUtils.isBlank(config.getConfigKey()))
        {
            throw new ServiceException("参数键名不能为空");
        }
        Descriptor descriptor = resolveForWrite(config, existing);
        applyMetadata(config, descriptor);

        if (descriptor.isSensitive() && existing != null && isMaskedOrEmpty(config.getConfigValue()))
        {
            config.setConfigValue(existing.getConfigValue());
        }
        validateValue(config.getConfigValue(), descriptor);
        if (config.getConfigValue() == null)
        {
            config.setConfigValue("");
        }
    }

    /** 为管理列表、详情和导出补齐元数据，并确保敏感原值不离开服务端。 */
    public SysConfig prepareForManagement(SysConfig config)
    {
        if (config == null)
        {
            return null;
        }
        Descriptor descriptor = resolve(config);
        boolean configured = StringUtils.isNotEmpty(config.getConfigValue());
        applyMetadata(config, descriptor);
        config.setConfigured(configured);
        config.setPublicConfig(descriptor.isPublicConfig());
        config.setDescriptorDescription(descriptor.getDescription());
        if (descriptor.isSensitive())
        {
            config.setConfigValue(null);
        }
        return config;
    }

    public boolean isSensitive(SysConfig config)
    {
        return config != null && resolve(config).isSensitive();
    }

    public List<Descriptor> registeredDescriptors()
    {
        return new ArrayList<>(descriptors.values());
    }

    private Descriptor resolveForWrite(SysConfig config, SysConfig existing)
    {
        Descriptor registered = descriptors.get(config.getConfigKey());
        if (registered != null)
        {
            return registered;
        }
        boolean builtIn = YES.equals(config.getConfigType()) || (existing != null && YES.equals(existing.getConfigType()));
        if (builtIn)
        {
            return descriptor(config.getConfigKey(), "security", TYPE_PASSWORD, true, false,
                    "maxLength=500", DEFAULT_DISPLAY_ORDER,
                    "未注册的内置配置按敏感且不可公开处理；请补充代码描述符后再开放读取");
        }
        String type = firstNotBlank(config.getValueType(), existing == null ? null : existing.getValueType(), TYPE_STRING);
        String group = firstNotBlank(config.getGroupCode(), existing == null ? null : existing.getGroupCode(), "custom");
        boolean existingSensitive = existing != null && YES.equals(existing.getSensitiveFlag());
        boolean sensitive = existingSensitive || YES.equals(config.getSensitiveFlag());
        String rule = config.getValidationRule() != null ? config.getValidationRule()
                : existing == null ? null : existing.getValidationRule();
        Integer order = config.getDisplayOrder();
        if (order == null && existing != null)
        {
            order = existing.getDisplayOrder();
        }
        if (order == null)
        {
            order = DEFAULT_DISPLAY_ORDER;
        }
        return descriptor(config.getConfigKey(), group, normalizeType(type), sensitive, false,
                rule, order, "自定义配置");
    }

    private Descriptor resolve(SysConfig config)
    {
        Descriptor registered = descriptors.get(config.getConfigKey());
        if (registered != null)
        {
            return registered;
        }
        if (YES.equals(config.getConfigType()))
        {
            return descriptor(config.getConfigKey(), "security", TYPE_PASSWORD, true, false,
                    "maxLength=500", DEFAULT_DISPLAY_ORDER,
                    "未注册的内置配置按敏感且不可公开处理；请补充代码描述符后再开放读取");
        }
        return descriptor(config.getConfigKey(), firstNotBlank(config.getGroupCode(), "custom"),
                normalizeType(firstNotBlank(config.getValueType(), TYPE_STRING)), YES.equals(config.getSensitiveFlag()),
                false, config.getValidationRule(), config.getDisplayOrder() == null ? DEFAULT_DISPLAY_ORDER : config.getDisplayOrder(),
                "自定义配置");
    }

    private void applyMetadata(SysConfig config, Descriptor descriptor)
    {
        config.setGroupCode(descriptor.getGroupCode());
        config.setValueType(descriptor.getValueType());
        config.setSensitiveFlag(descriptor.isSensitive() ? YES : NO);
        config.setValidationRule(descriptor.getValidationRule());
        config.setDisplayOrder(descriptor.getDisplayOrder());
        config.setPublicConfig(descriptor.isPublicConfig());
        config.setDescriptorDescription(descriptor.getDescription());
    }

    private void validateValue(String rawValue, Descriptor descriptor)
    {
        String value = rawValue == null ? "" : rawValue;
        if (!descriptor.isSensitive() && StringUtils.isBlank(value))
        {
            throw new ServiceException("参数键值不能为空");
        }
        BigDecimal numericValue = null;
        try
        {
            switch (descriptor.getValueType())
            {
                case TYPE_INTEGER:
                    new BigInteger(value.trim());
                    numericValue = new BigDecimal(value.trim());
                    break;
                case TYPE_DECIMAL:
                    numericValue = new BigDecimal(value.trim());
                    break;
                case TYPE_BOOLEAN:
                    if (!"true".equalsIgnoreCase(value.trim()) && !"false".equalsIgnoreCase(value.trim()))
                    {
                        throw new ServiceException("参数值必须是 true 或 false");
                    }
                    break;
                case TYPE_JSON:
                    JsonNode node = objectMapper.readTree(value);
                    if (node == null || node.isNull())
                    {
                        throw new ServiceException("JSON 参数值不能为空");
                    }
                    break;
                case TYPE_ENUM:
                case TYPE_STRING:
                case TYPE_PASSWORD:
                    break;
                default:
                    throw new ServiceException("不支持的参数值类型：" + descriptor.getValueType());
            }
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("参数值不符合 " + descriptor.getValueType() + " 类型");
        }
        validateRule(value, numericValue, descriptor.getValidationRule());
    }

    private void validateRule(String value, BigDecimal numericValue, String validationRule)
    {
        if (StringUtils.isBlank(validationRule))
        {
            return;
        }
        for (String part : validationRule.split(";"))
        {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2 || StringUtils.isBlank(pair[0]))
            {
                throw new ServiceException("配置校验规则格式不正确");
            }
            String name = pair[0].trim();
            String expected = pair[1].trim();
            switch (name)
            {
                case "min":
                    requireNumeric(numericValue, name);
                    if (numericValue.compareTo(new BigDecimal(expected)) < 0)
                    {
                        throw new ServiceException("参数值不能小于 " + expected);
                    }
                    break;
                case "max":
                    requireNumeric(numericValue, name);
                    if (numericValue.compareTo(new BigDecimal(expected)) > 0)
                    {
                        throw new ServiceException("参数值不能大于 " + expected);
                    }
                    break;
                case "minLength":
                    if (value.length() < Integer.parseInt(expected) && !value.isEmpty())
                    {
                        throw new ServiceException("参数值长度不能小于 " + expected);
                    }
                    break;
                case "maxLength":
                    if (value.length() > Integer.parseInt(expected))
                    {
                        throw new ServiceException("参数值长度不能大于 " + expected);
                    }
                    break;
                case "enum":
                    boolean matched = false;
                    for (String option : expected.split(","))
                    {
                        matched = matched || option.trim().equals(value.trim());
                    }
                    if (!matched)
                    {
                        throw new ServiceException("参数值必须是以下选项之一：" + expected);
                    }
                    break;
                default:
                    throw new ServiceException("不支持的配置校验规则：" + name);
            }
        }
    }

    private void requireNumeric(BigDecimal numericValue, String rule)
    {
        if (numericValue == null)
        {
            throw new ServiceException("校验规则 " + rule + " 只能用于数值类型");
        }
    }

    private boolean isMaskedOrEmpty(String value)
    {
        return StringUtils.isBlank(value) || MASKED_VALUE.equals(value);
    }

    private String normalizeType(String valueType)
    {
        String normalized = firstNotBlank(valueType, TYPE_STRING).toLowerCase(Locale.ROOT);
        switch (normalized)
        {
            case TYPE_STRING:
            case TYPE_INTEGER:
            case TYPE_DECIMAL:
            case TYPE_BOOLEAN:
            case TYPE_JSON:
            case TYPE_ENUM:
            case TYPE_PASSWORD:
                return normalized;
            default:
                throw new ServiceException("不支持的参数值类型：" + valueType);
        }
    }

    private static String firstNotBlank(String... values)
    {
        for (String value : values)
        {
            if (StringUtils.isNotBlank(value))
            {
                return value;
            }
        }
        return null;
    }

    private static void register(Map<String, Descriptor> values, Descriptor descriptor)
    {
        values.put(descriptor.getConfigKey(), descriptor);
    }

    private static Descriptor descriptor(String configKey, String groupCode, String valueType, boolean sensitive,
            boolean publicConfig, String validationRule, int displayOrder, String description)
    {
        return new Descriptor(configKey, groupCode, valueType, sensitive, publicConfig, validationRule, displayOrder, description);
    }

    /** 可安全返回给配置管理页面的描述元数据。 */
    public static final class Descriptor
    {
        private final String configKey;
        private final String groupCode;
        private final String valueType;
        private final boolean sensitive;
        private final boolean publicConfig;
        private final String validationRule;
        private final int displayOrder;
        private final String description;

        private Descriptor(String configKey, String groupCode, String valueType, boolean sensitive,
                boolean publicConfig, String validationRule, int displayOrder, String description)
        {
            this.configKey = configKey;
            this.groupCode = groupCode;
            this.valueType = valueType;
            this.sensitive = sensitive;
            this.publicConfig = publicConfig;
            this.validationRule = validationRule;
            this.displayOrder = displayOrder;
            this.description = description;
        }

        public String getConfigKey() { return configKey; }
        public String getGroupCode() { return groupCode; }
        public String getValueType() { return valueType; }
        public boolean isSensitive() { return sensitive; }
        public boolean isPublicConfig() { return publicConfig; }
        public String getValidationRule() { return validationRule; }
        public int getDisplayOrder() { return displayOrder; }
        public String getDescription() { return description; }
    }
}
