package com.erp.common.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.log.enums.OperatorType;

/**
 * 自定义操作日志记录注解
 * 
 * @author erp
 *
 */
@Target({ ElementType.PARAMETER, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log
{
    /**
     * 模块
     */
    public String title() default "";

    /**
     * 功能
     */
    public BusinessType businessType() default BusinessType.OTHER;

    /**
     * 操作人类别
     */
    public OperatorType operatorType() default OperatorType.MANAGE;

    /**
     * 是否保存请求的参数
     */
    @Deprecated
    public boolean isSaveRequestData() default false;

    /**
     * 是否保存响应的参数
     */
    @Deprecated
    public boolean isSaveResponseData() default false;

    /**
     * 允许进入操作日志短摘要的请求字段名。字段仍会经过中央敏感策略校验。
     */
    public String[] includeParamNames() default {};

    /**
     * 排除指定的请求参数
     */
    @Deprecated
    public String[] excludeParamNames() default {};
}
