package com.erp.common.security.handler;

import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.exception.DemoModeException;
import com.erp.common.core.exception.InnerAuthException;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.exception.auth.NotPermissionException;
import com.erp.common.core.exception.auth.NotRoleException;
import com.erp.common.core.exception.auth.PasswordChangeRequiredException;
import com.erp.common.core.text.Convert;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.core.utils.html.EscapeUtil;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.exception.CredentialRestrictionException;

/**
 * 全局异常处理器
 *
 * @author erp
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final long CREDENTIAL_LOG_INTERVAL_MILLIS = 60_000L;

    private static final int CREDENTIAL_LOG_KEY_LIMIT = 10_000;

    private static final String INTERNAL_ERROR_MESSAGE = "系统处理失败，请稍后重试或联系管理员";

    private final ConcurrentHashMap<String, Long> credentialLogTimes = new ConcurrentHashMap<>();

    /** Forced credential changes are an expected business flow, not an application error. */
    @ExceptionHandler(CredentialRestrictionException.class)
    public AjaxResult handleCredentialRestrictionException(CredentialRestrictionException e,
            HttpServletRequest request)
    {
        logCredentialRestriction(e, request.getRequestURI());
        return AjaxResult.error(HttpStatus.CONFLICT, e.getMessage())
                .put("businessCode", e.getBusinessCode())
                .put("credentialState", e.getCredentialState());
    }

    private void logCredentialRestriction(CredentialRestrictionException e, String requestUri)
    {
        String key = String.valueOf(e.getUserId()) + '|' + e.getBusinessCode() + '|' + requestUri;
        long now = System.currentTimeMillis();
        Long previous = credentialLogTimes.putIfAbsent(key, now);
        if (previous == null || now - previous >= CREDENTIAL_LOG_INTERVAL_MILLIS)
        {
            credentialLogTimes.put(key, now);
            log.info("凭据状态门禁 userId={}, route={}, state={}, code={}",
                    e.getUserId(), requestUri, e.getCredentialState(), e.getBusinessCode());
        }
        if (credentialLogTimes.size() > CREDENTIAL_LOG_KEY_LIMIT)
        {
            credentialLogTimes.entrySet().removeIf(entry -> now - entry.getValue()
                    > CREDENTIAL_LOG_INTERVAL_MILLIS);
        }
    }

    /**
     * 权限码异常
     */
    @ExceptionHandler(NotPermissionException.class)
    public AjaxResult handleNotPermissionException(NotPermissionException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',权限码校验失败'{}'", requestURI, e.getMessage());
        return AjaxResult.error(HttpStatus.FORBIDDEN, "没有访问权限，请联系管理员授权");
    }

    /**
     * 角色权限异常
     */
    @ExceptionHandler(NotRoleException.class)
    public AjaxResult handleNotRoleException(NotRoleException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',角色权限校验失败'{}'", requestURI, e.getMessage());
        return AjaxResult.error(HttpStatus.FORBIDDEN, "没有访问权限，请联系管理员授权");
    }

    /**
     * 必须修改一次性密码
     */
    @ExceptionHandler(PasswordChangeRequiredException.class)
    public AjaxResult handlePasswordChangeRequiredException(PasswordChangeRequiredException e)
    {
        return AjaxResult.error(HttpStatus.PRECONDITION_REQUIRED, e.getMessage());
    }

    /**
     * 请求方式不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public AjaxResult handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',不支持'{}'请求", requestURI, e.getMethod());
        return AjaxResult.error(e.getMessage());
    }

    /**
     * 上传文件超过服务端传输限制
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public AjaxResult handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e,
            HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.warn("请求地址'{}',上传文件超过服务端允许大小", requestURI);
        return AjaxResult.error("上传文件超过服务端允许大小，请压缩文件或减少内容后重试");
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public AjaxResult handleServiceException(ServiceException e, HttpServletRequest request)
    {
        log.error(e.getMessage(), e);
        Integer code = e.getCode();
        return StringUtils.isNotNull(code) ? AjaxResult.error(code, e.getMessage()) : AjaxResult.error(e.getMessage());
    }

    /**
     * 请求路径中缺少必需的路径变量
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public AjaxResult handleMissingPathVariableException(MissingPathVariableException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求路径中缺少必需的路径变量'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error(String.format("请求路径中缺少必需的路径变量[%s]", e.getVariableName()));
    }

    /**
     * 请求参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public AjaxResult handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        String value = Convert.toStr(e.getValue());
        if (StringUtils.isNotEmpty(value))
        {
            value = EscapeUtil.clean(value);
        }
        log.error("请求参数类型不匹配'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error(String.format("请求参数类型不匹配，参数[%s]要求类型为：'%s'，但输入值为：'%s'", e.getName(), e.getRequiredType().getName(), value));
    }

    /**
     * 拦截未知的运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public AjaxResult handleRuntimeException(RuntimeException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生未知异常.", requestURI, e);
        return AjaxResult.error(INTERNAL_ERROR_MESSAGE);
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error(INTERNAL_ERROR_MESSAGE);
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(BindException.class)
    public AjaxResult handleBindException(BindException e)
    {
        log.error(e.getMessage(), e);
        String message = e.getAllErrors().get(0).getDefaultMessage();
        return AjaxResult.error(message);
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e)
    {
        log.error(e.getMessage(), e);
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        return AjaxResult.error(message);
    }

    /**
     * 内部认证异常
     */
    @ExceptionHandler(InnerAuthException.class)
    public AjaxResult handleInnerAuthException(InnerAuthException e)
    {
        return AjaxResult.error(e.getMessage());
    }

    /**
     * 演示模式异常
     */
    @ExceptionHandler(DemoModeException.class)
    public AjaxResult handleDemoModeException(DemoModeException e)
    {
        return AjaxResult.error("演示模式，不允许操作");
    }
}
