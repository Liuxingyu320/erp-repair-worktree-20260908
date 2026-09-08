package com.erp.common.log.aspect;

import java.util.Map;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NamedThreadLocal;
import org.springframework.stereotype.Component;
import com.erp.common.core.utils.ServletUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.ip.IpUtils;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessStatus;
import com.erp.common.log.service.AsyncLogService;
import com.erp.common.log.support.AuditPayloadSanitizer;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysOperLog;

/**
 * 操作日志记录处理
 * 
 * @author erp
 */
@Aspect
@Component
public class LogAspect
{
    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);

    /** 计算操作消耗时间 */
    private static final ThreadLocal<Long> TIME_THREADLOCAL = new NamedThreadLocal<Long>("Cost Time");

    /** 参数最大长度限制 */
    private static final int PARAM_MAX_LENGTH = 2000;

    @Autowired
    private AsyncLogService asyncLogService;

    @Autowired
    private AuditPayloadSanitizer auditPayloadSanitizer;

    /**
     * 处理请求前执行
     */
    @Before(value = "@annotation(controllerLog)")
    public void doBefore(JoinPoint joinPoint, Log controllerLog)
    {
        TIME_THREADLOCAL.set(System.currentTimeMillis());
    }

    /**
     * 处理完请求后执行
     *
     * @param joinPoint 切点
     */
    @AfterReturning(pointcut = "@annotation(controllerLog)", returning = "jsonResult")
    public void doAfterReturning(JoinPoint joinPoint, Log controllerLog, Object jsonResult)
    {
        handleLog(joinPoint, controllerLog, null, jsonResult);
    }

    /**
     * 拦截异常操作
     * 
     * @param joinPoint 切点
     * @param e 异常
     */
    @AfterThrowing(value = "@annotation(controllerLog)", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Log controllerLog, Exception e)
    {
        handleLog(joinPoint, controllerLog, e, null);
    }

    protected void handleLog(final JoinPoint joinPoint, Log controllerLog, final Exception e, Object jsonResult)
    {
        try
        {
            // *========数据库日志=========*//
            SysOperLog operLog = new SysOperLog();
            operLog.setStatus(BusinessStatus.SUCCESS.ordinal());
            // 请求的地址
            String ip = IpUtils.getIpAddr();
            operLog.setOperIp(ip);
            operLog.setOperUrl(StringUtils.substring(ServletUtils.getRequest().getRequestURI(), 0, 255));
            String username = SecurityUtils.getUsername();
            if (StringUtils.isNotBlank(username))
            {
                operLog.setOperName(username);
            }

            if (e != null)
            {
                operLog.setStatus(BusinessStatus.FAIL.ordinal());
                operLog.setErrorMsg(auditPayloadSanitizer.sanitizeError(e));
            }
            // 设置方法名称
            String className = joinPoint.getTarget().getClass().getName();
            String methodName = joinPoint.getSignature().getName();
            operLog.setMethod(className + "." + methodName + "()");
            // 设置请求方式
            operLog.setRequestMethod(ServletUtils.getRequest().getMethod());
            // 处理设置注解上的参数
            getControllerMethodDescription(joinPoint, controllerLog, operLog);
            // 设置消耗时间
            Long startTime = TIME_THREADLOCAL.get();
            operLog.setCostTime(startTime == null ? 0L : System.currentTimeMillis() - startTime);
            // 保存数据库
            asyncLogService.saveSysLog(operLog);
        }
        catch (Exception exp)
        {
            // 记录本地异常日志
            log.error("记录操作日志失败: {}", exp.getClass().getSimpleName());
        }
        finally
        {
            TIME_THREADLOCAL.remove();
        }
    }

    /**
     * 获取注解中对方法的描述信息 用于Controller层注解
     * 
     * @param log 日志
     * @param operLog 操作日志
     * @throws Exception
     */
    public void getControllerMethodDescription(JoinPoint joinPoint, Log log, SysOperLog operLog)
    {
        // 设置action动作
        operLog.setBusinessType(log.businessType().ordinal());
        // 设置标题
        operLog.setTitle(log.title());
        // 设置操作人类别
        operLog.setOperatorType(log.operatorType().ordinal());
        // 只有显式允许的短字段才能进入请求摘要；旧布尔开关不能绕过中央策略。
        if (log.includeParamNames().length > 0)
        {
            setRequestValue(joinPoint, operLog, log.includeParamNames());
        }
        // 响应对象不做任意序列化。成功/失败、耗时和稳定错误类型已由元数据表达。
    }

    /**
     * 获取请求的参数，放到log中
     * 
     * @param operLog 操作日志
     * @throws Exception 异常
     */
    private void setRequestValue(JoinPoint joinPoint, SysOperLog operLog, String[] includeParamNames)
    {
        Map<?, ?> paramsMap = ServletUtils.getParamMap(ServletUtils.getRequest());
        String[] parameterNames = joinPoint.getSignature() instanceof CodeSignature codeSignature
                ? codeSignature.getParameterNames() : new String[0];
        String summary = auditPayloadSanitizer.sanitizeAllowedFields(joinPoint.getArgs(), parameterNames,
                includeParamNames);
        if (StringUtils.isBlank(summary) && StringUtils.isNotEmpty(paramsMap))
        {
            summary = auditPayloadSanitizer.sanitizeAllowedFields(new Object[] { paramsMap },
                    new String[] { "requestParameters" }, includeParamNames);
        }
        if (StringUtils.isNotBlank(summary))
        {
            operLog.setOperParam(StringUtils.substring(summary, 0, PARAM_MAX_LENGTH));
        }
    }
}
