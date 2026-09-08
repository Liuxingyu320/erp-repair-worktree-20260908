package com.erp.common.sensitive.config;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.utils.SecurityBypassUtils;
import com.erp.common.sensitive.annotation.Sensitive;
import com.erp.common.sensitive.enums.DesensitizedType;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * 数据脱敏序列化过滤
 *
 * @author erp
 */
public class SensitiveJsonSerializer extends StdSerializer<String>
{
    private static final String ALL_PERMISSION = "*:*:*";

    private final DesensitizedType desensitizedType;

    public SensitiveJsonSerializer()
    {
        super(String.class);
        this.desensitizedType = null;
    }

    public SensitiveJsonSerializer(DesensitizedType desensitizedType)
    {
        super(String.class);
        this.desensitizedType = desensitizedType;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException
    {
        if (desensitizedType != null && desensitization())
        {
            gen.writeString(desensitizedType.desensitizer().apply(value));
        }
        else
        {
            gen.writeString(value);
        }
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) throws DatabindException
    {
        Sensitive annotation = property.getAnnotation(Sensitive.class);
        if (Objects.nonNull(annotation) && Objects.equals(String.class, property.getType().getRawClass()))
        {
            return new SensitiveJsonSerializer(annotation.desensitizedType());
        }
        return ctxt.findValueSerializer(property.getType());
    }

    /**
     * 是否需要脱敏处理
     */
    private boolean desensitization()
    {
        try
        {
            // 管理员不脱敏
            return !currentUserIsAdmin();
        }
        catch (Exception e)
        {
            return true;
        }
    }

    private boolean currentUserIsAdmin()
    {
        Object loginUser = SecurityContextHolder.get(SecurityConstants.LOGIN_USER, Object.class);
        return SecurityBypassUtils.adminRoleBypassEnabled()
                && loginUserHas(loginUser, "getRoles", UserConstants.SUPER_ADMIN_ROLE_KEY)
                || SecurityBypassUtils.wildcardPermissionBypassEnabled()
                && loginUserHas(loginUser, "getPermissions", ALL_PERMISSION)
                || UserConstants.isAdmin(SecurityContextHolder.getUserId());
    }

    private boolean loginUserHas(Object loginUser, String getterName, String expected)
    {
        if (loginUser == null)
        {
            return false;
        }
        try
        {
            Method getter = loginUser.getClass().getMethod(getterName);
            Object values = getter.invoke(loginUser);
            return values instanceof Collection<?> collection && collection.contains(expected);
        }
        catch (ReflectiveOperationException e)
        {
            return false;
        }
    }
}
