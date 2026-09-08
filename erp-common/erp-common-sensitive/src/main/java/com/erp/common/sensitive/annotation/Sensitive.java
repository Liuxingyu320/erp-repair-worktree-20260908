package com.erp.common.sensitive.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.erp.common.sensitive.config.SensitiveJsonSerializer;
import com.erp.common.sensitive.enums.DesensitizedType;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * 数据脱敏注解
 *
 * @author erp
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveJsonSerializer.class)
public @interface Sensitive
{
    DesensitizedType desensitizedType();
}