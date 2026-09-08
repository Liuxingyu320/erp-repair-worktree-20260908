package com.erp.common.log.support;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;

/**
 * 操作日志正文的中央最小化策略。
 *
 * 仅允许注解显式列出的安全标量；敏感字段名或可疑值直接丢弃，不以掩码形式保留原长度。
 */
@Component
public class AuditPayloadSanitizer
{
    private static final int MAX_TEXT_LENGTH = 128;
    private static final int MAX_COLLECTION_SIZE = 20;

    private static final Pattern BASE64_OR_HIGH_ENTROPY = Pattern.compile("^[A-Za-z0-9+/]{128,}={0,2}$");
    private static final Pattern JWT = Pattern.compile("^eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
    private static final Pattern LONG_ID_OR_BANK_NUMBER = Pattern.compile("(?<!\\d)\\d{15,19}(?!\\d)");
    private static final Pattern PRIVATE_PATH = Pattern.compile(
            "(?i)(^|[\\s\"'])(/Users/|/home/|/root/|/var/[^/]+/|[A-Z]:\\\\Users\\\\)");

    private static final String[] SENSITIVE_NAME_PARTS = {
            "password", "passwd", "secret", "token", "credential", "signature", "signimage",
            "bankaccount", "bankcard", "idnumber", "idcard", "identitynumber", "privatekey",
            "accesskey", "phonenumber", "mobile", "emergencycontact", "address"
    };

    /**
     * 从方法参数或参数对象中提取显式允许字段并生成短 JSON 摘要。
     *
     * @return 没有安全字段时返回 null
     */
    public String sanitizeAllowedFields(Object[] arguments, String[] parameterNames, String[] allowedNames)
    {
        if (arguments == null || allowedNames == null || allowedNames.length == 0)
        {
            return null;
        }

        Map<String, Object> safe = new LinkedHashMap<>();
        for (String allowedName : allowedNames)
        {
            if (StringUtils.isBlank(allowedName) || isSensitiveName(allowedName))
            {
                continue;
            }
            Object value = findValue(arguments, parameterNames, allowedName);
            Object safeValue = sanitizeValue(value);
            if (safeValue != null)
            {
                safe.put(allowedName, safeValue);
            }
        }
        return safe.isEmpty() ? null : JSON.toJSONString(safe);
    }

    /**
     * 异常只保留类别和稳定业务错误码，绝不复制异常消息或堆栈。
     */
    public String sanitizeError(Throwable error)
    {
        if (error == null)
        {
            return null;
        }
        String type = error.getClass().getSimpleName();
        if (error instanceof ServiceException serviceException && serviceException.getCode() != null)
        {
            return type + "(code=" + serviceException.getCode() + ")";
        }
        return type;
    }

    private Object findValue(Object[] arguments, String[] parameterNames, String allowedName)
    {
        for (int index = 0; index < arguments.length; index++)
        {
            if (parameterNames != null && index < parameterNames.length
                    && allowedName.equals(parameterNames[index]))
            {
                return arguments[index];
            }
        }
        for (Object argument : arguments)
        {
            Object value = readProperty(argument, allowedName);
            if (value != null)
            {
                return value;
            }
        }
        return null;
    }

    private Object readProperty(Object source, String name)
    {
        if (source == null)
        {
            return null;
        }
        if (source instanceof Map<?, ?> map)
        {
            return map.get(name);
        }
        try
        {
            BeanInfo beanInfo = Introspector.getBeanInfo(source.getClass(), Object.class);
            for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors())
            {
                if (name.equals(descriptor.getName()) && descriptor.getReadMethod() != null)
                {
                    Method getter = descriptor.getReadMethod();
                    if (!getter.canAccess(source) && !getter.trySetAccessible())
                    {
                        return null;
                    }
                    return getter.invoke(source);
                }
            }
        }
        catch (Exception ignored)
        {
            // 审计日志必须失败关闭：读取失败就不保留该字段。
        }
        return null;
    }

    private Object sanitizeValue(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>
                || value instanceof Date || value instanceof TemporalAccessor || value instanceof UUID)
        {
            return value;
        }
        if (value instanceof Character)
        {
            return value.toString();
        }
        if (value instanceof CharSequence)
        {
            String text = value.toString().trim();
            return isSuspiciousValue(text) ? null : text;
        }
        if (value instanceof Collection<?> collection)
        {
            if (collection.size() > MAX_COLLECTION_SIZE)
            {
                return null;
            }
            Collection<Object> safeValues = new ArrayList<>();
            for (Object item : collection)
            {
                Object safeItem = sanitizeValue(item);
                if (safeItem == null || safeItem instanceof Collection<?> || safeItem instanceof Map<?, ?>)
                {
                    return null;
                }
                safeValues.add(safeItem);
            }
            return safeValues;
        }
        return null;
    }

    private boolean isSensitiveName(String name)
    {
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if ("pwd".equals(normalized) || normalized.endsWith("pwd"))
        {
            return true;
        }
        for (String part : SENSITIVE_NAME_PARTS)
        {
            if (normalized.contains(part))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isSuspiciousValue(String value)
    {
        if (StringUtils.isBlank(value) || value.length() > MAX_TEXT_LENGTH)
        {
            return true;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains(";base64,")
                || lower.contains("-----begin private key-----")
                || lower.contains("-----begin rsa private key-----")
                || JWT.matcher(value).matches()
                || BASE64_OR_HIGH_ENTROPY.matcher(value).matches()
                || LONG_ID_OR_BANK_NUMBER.matcher(value).find()
                || PRIVATE_PATH.matcher(value).find();
    }
}
