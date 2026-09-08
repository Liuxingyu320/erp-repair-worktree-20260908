package com.erp.common.log.sanitize;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.multipart.MultipartFile;
import com.alibaba.fastjson2.JSON;

/**
 * 操作日志敏感数据净化器。
 *
 * <p>日志属于低信任输出：无法识别或无法读取的对象只记录类型，不回退到
 * {@code toString()}，避免序列化失败时把口令、令牌等原始值写入日志。</p>
 */
public class SensitiveLogSanitizer
{
    public static final String REDACTED = "[REDACTED]";

    private static final String CIRCULAR = "[CIRCULAR]";
    private static final String MAX_DEPTH_REACHED = "[MAX_DEPTH]";
    private static final String TRUNCATED = "[TRUNCATED]";
    private static final int DEFAULT_MAX_DEPTH = 6;
    private static final int DEFAULT_MAX_COLLECTION_SIZE = 50;
    private static final int DEFAULT_MAX_STRING_LENGTH = 1000;

    private static final Set<String> SECRET_KEYS = Set.of(
            "password", "oldpassword", "newpassword", "confirmpassword", "passwd", "pwd",
            "temporarypassword", "initialpassword", "credential", "credentials",
            "token", "accesstoken", "refreshtoken", "idtoken", "authorization", "authentication",
            "cookie", "setcookie", "secret", "clientsecret", "apikey", "privatekey",
            "mfasecret", "otp", "totp", "captcha", "verificationcode");

    private static final Set<String> PHONE_KEYS = Set.of(
            "phone", "phonenumber", "mobile", "mobilenumber", "cellphone", "tel", "telephone");
    private static final Set<String> ID_KEYS = Set.of(
            "idnumber", "idcard", "identitynumber", "identitycard", "citizenid", "certificatenumber");
    private static final Set<String> BANK_KEYS = Set.of(
            "bankaccount", "bankcard", "bankcardnumber", "accountnumber", "cardnumber");

    private static final Pattern KEY_VALUE_SECRET_PATTERN = Pattern.compile(
            "(?i)(\\b(?:password|old[_-]?password|new[_-]?password|confirm[_-]?password|passwd|pwd|"
                    + "temporary[_-]?password|token|access[_-]?token|refresh[_-]?token|authorization|"
                    + "cookie|secret|client[_-]?secret|api[_-]?key|private[_-]?key|mfa[_-]?secret|otp|totp)\\b"
                    + "\\s*[=:]\\s*)([\"']?)([^,;\\s}\"]+|[^\"]*)([\"']?)");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern MOBILE_TEXT_PATTERN = Pattern.compile(
            "(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern ID_TEXT_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{4})\\d{10}([0-9Xx]{4})(?![0-9Xx])");
    private static final Pattern BANK_TEXT_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{4})\\d{8,11}(\\d{4})(?!\\d)");

    public Object sanitize(Object value, String... extraSecretKeys)
    {
        return sanitizeValue(value, 0, new IdentityHashMap<>(), normalizedExtraKeys(extraSecretKeys));
    }

    public String sanitizeToJson(Object value, int maxLength, String... extraSecretKeys)
    {
        try
        {
            String json = JSON.toJSONString(sanitize(value, extraSecretKeys));
            return truncate(json, maxLength);
        }
        catch (Exception ignored)
        {
            return "{\"value\":\"[UNAVAILABLE]\"}";
        }
    }

    /**
     * 净化数据库中已有的 JSON 内容；兼容历史上的非标准 JSON 文本。
     */
    public String sanitizeJsonText(String value)
    {
        if (value == null)
        {
            return null;
        }
        try
        {
            return sanitizeToJson(JSON.parse(value), DEFAULT_MAX_STRING_LENGTH * 2);
        }
        catch (Exception ignored)
        {
            return sanitizeText(value);
        }
    }

    /**
     * 净化异常消息等自由文本，并限制长度。
     */
    public String sanitizeText(String value)
    {
        if (value == null)
        {
            return null;
        }
        String sanitized = BEARER_PATTERN.matcher(value).replaceAll("Bearer " + REDACTED);
        Matcher matcher = KEY_VALUE_SECRET_PATTERN.matcher(sanitized);
        StringBuffer result = new StringBuffer();
        while (matcher.find())
        {
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + REDACTED));
        }
        matcher.appendTail(result);
        String personalDataMasked = MOBILE_TEXT_PATTERN.matcher(result.toString()).replaceAll("$1****$2");
        personalDataMasked = ID_TEXT_PATTERN.matcher(personalDataMasked).replaceAll("$1****$2");
        personalDataMasked = BANK_TEXT_PATTERN.matcher(personalDataMasked).replaceAll("$1****$2");
        return truncate(personalDataMasked, DEFAULT_MAX_STRING_LENGTH);
    }

    private Object sanitizeValue(Object value, int depth, IdentityHashMap<Object, Boolean> seen,
            Set<String> extraSecretKeys)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof CharSequence || value instanceof Character)
        {
            return sanitizeText(String.valueOf(value));
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>
                || value instanceof Date || value instanceof TemporalAccessor)
        {
            return value;
        }
        if (value instanceof MultipartFile file)
        {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("type", "file");
            summary.put("name", sanitizeText(file.getOriginalFilename()));
            summary.put("size", file.getSize());
            return summary;
        }
        if (value instanceof Throwable throwable)
        {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("type", throwable.getClass().getSimpleName());
            summary.put("message", sanitizeText(throwable.getMessage()));
            return summary;
        }
        if (depth >= DEFAULT_MAX_DEPTH)
        {
            return MAX_DEPTH_REACHED;
        }
        if (seen.put(value, Boolean.TRUE) != null)
        {
            return CIRCULAR;
        }
        try
        {
            if (value instanceof Map<?, ?> map)
            {
                return sanitizeMap(map, depth, seen, extraSecretKeys);
            }
            if (value instanceof Collection<?> collection)
            {
                return sanitizeCollection(collection, depth, seen, extraSecretKeys);
            }
            if (value.getClass().isArray())
            {
                return sanitizeArray(value, depth, seen, extraSecretKeys);
            }
            return sanitizeBean(value, depth, seen, extraSecretKeys);
        }
        finally
        {
            seen.remove(value);
        }
    }

    private Map<String, Object> sanitizeMap(Map<?, ?> map, int depth, IdentityHashMap<Object, Boolean> seen,
            Set<String> extraSecretKeys)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet())
        {
            if (count++ >= DEFAULT_MAX_COLLECTION_SIZE)
            {
                result.put("_truncated", TRUNCATED);
                break;
            }
            String key = sanitizeText(String.valueOf(entry.getKey()));
            result.put(key, sanitizeProperty(key, entry.getValue(), depth, seen, extraSecretKeys));
        }
        return result;
    }

    private List<Object> sanitizeCollection(Collection<?> collection, int depth,
            IdentityHashMap<Object, Boolean> seen, Set<String> extraSecretKeys)
    {
        List<Object> result = new ArrayList<>();
        int count = 0;
        for (Object item : collection)
        {
            if (count++ >= DEFAULT_MAX_COLLECTION_SIZE)
            {
                result.add(TRUNCATED);
                break;
            }
            result.add(sanitizeValue(item, depth + 1, seen, extraSecretKeys));
        }
        return result;
    }

    private List<Object> sanitizeArray(Object array, int depth, IdentityHashMap<Object, Boolean> seen,
            Set<String> extraSecretKeys)
    {
        List<Object> result = new ArrayList<>();
        int length = Math.min(Array.getLength(array), DEFAULT_MAX_COLLECTION_SIZE);
        for (int index = 0; index < length; index++)
        {
            result.add(sanitizeValue(Array.get(array, index), depth + 1, seen, extraSecretKeys));
        }
        if (Array.getLength(array) > DEFAULT_MAX_COLLECTION_SIZE)
        {
            result.add(TRUNCATED);
        }
        return result;
    }

    private Map<String, Object> sanitizeBean(Object bean, int depth, IdentityHashMap<Object, Boolean> seen,
            Set<String> extraSecretKeys)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        try
        {
            BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass(), Object.class);
            int count = 0;
            for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors())
            {
                if (count++ >= DEFAULT_MAX_COLLECTION_SIZE)
                {
                    result.put("_truncated", TRUNCATED);
                    break;
                }
                Method readMethod = descriptor.getReadMethod();
                if (readMethod == null || readMethod.getParameterCount() != 0)
                {
                    continue;
                }
                try
                {
                    if (!readMethod.canAccess(bean))
                    {
                        readMethod.setAccessible(true);
                    }
                    Object propertyValue = readMethod.invoke(bean);
                    result.put(descriptor.getName(), sanitizeProperty(descriptor.getName(), propertyValue,
                            depth, seen, extraSecretKeys));
                }
                catch (Exception ignored)
                {
                    result.put(descriptor.getName(), "[UNAVAILABLE]");
                }
            }
        }
        catch (Exception ignored)
        {
            result.put("type", bean.getClass().getSimpleName());
        }
        if (result.isEmpty())
        {
            result.put("type", bean.getClass().getSimpleName());
        }
        return result;
    }

    private Object sanitizeProperty(String key, Object value, int depth, IdentityHashMap<Object, Boolean> seen,
            Set<String> extraSecretKeys)
    {
        String normalizedKey = normalizeKey(key);
        if (SECRET_KEYS.contains(normalizedKey) || extraSecretKeys.contains(normalizedKey))
        {
            return REDACTED;
        }
        if (PHONE_KEYS.contains(normalizedKey))
        {
            return maskPhone(value);
        }
        if (ID_KEYS.contains(normalizedKey) || BANK_KEYS.contains(normalizedKey))
        {
            return maskIdentifier(value);
        }
        return sanitizeValue(value, depth + 1, seen, extraSecretKeys);
    }

    private String maskPhone(Object value)
    {
        String text = value == null ? null : String.valueOf(value);
        if (text == null || text.length() < 7)
        {
            return text == null ? null : REDACTED;
        }
        return text.substring(0, 3) + "****" + text.substring(text.length() - 4);
    }

    private String maskIdentifier(Object value)
    {
        String text = value == null ? null : String.valueOf(value);
        if (text == null || text.length() < 8)
        {
            return text == null ? null : REDACTED;
        }
        return text.substring(0, 4) + "****" + text.substring(text.length() - 4);
    }

    private Set<String> normalizedExtraKeys(String... keys)
    {
        if (keys == null || keys.length == 0)
        {
            return Collections.emptySet();
        }
        java.util.HashSet<String> result = new java.util.HashSet<>();
        for (String key : keys)
        {
            if (key != null)
            {
                result.add(normalizeKey(key));
            }
        }
        return result;
    }

    private String normalizeKey(String key)
    {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String truncate(String value, int maxLength)
    {
        if (value == null || maxLength <= 0 || value.length() <= maxLength)
        {
            return value;
        }
        return value.substring(0, maxLength) + TRUNCATED;
    }
}
