package com.erp.common.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防止同一用户、同一门店、同一请求在短时间内重复提交。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface IdempotentSubmit
{
    /**
     * 幂等占位保留时间，单位秒。
     */
    long timeout() default 10L;

    /**
     * 业务执行失败时是否释放占位，允许用户修正后立即重试。
     */
    boolean releaseOnFailure() default true;

    /**
     * Release the short-lived in-flight guard after a successful call. Use
     * this only when the business service has a durable idempotency key and
     * can replay the original result.
     */
    boolean releaseOnSuccess() default false;

    /**
     * 重复提交时返回的业务提示。
     */
    String message() default "请求处理中，请勿重复提交";
}
