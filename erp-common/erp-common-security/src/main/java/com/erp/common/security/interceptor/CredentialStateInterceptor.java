package com.erp.common.security.interceptor;

import java.time.Clock;
import java.util.Date;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import com.erp.common.security.annotation.AllowsTemporaryCredential;
import com.erp.common.security.exception.CredentialRestrictionException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

/** Enforces credential state after HeaderInterceptor has populated the context. */
public class CredentialStateInterceptor implements AsyncHandlerInterceptor
{
    private final Clock clock;

    public CredentialStateInterceptor()
    {
        this(Clock.systemUTC());
    }

    CredentialStateInterceptor(Clock clock)
    {
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        if (!(handler instanceof HandlerMethod handlerMethod))
        {
            return true;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null)
        {
            return true;
        }

        String state = loginUser.getCredentialState();
        if (SysUser.CREDENTIAL_STATE_ACTIVE.equals(state))
        {
            return true;
        }
        if (!SysUser.CREDENTIAL_STATE_TEMPORARY.equals(state)
                && !SysUser.CREDENTIAL_STATE_CHANGE_REQUIRED.equals(state))
        {
            throw new CredentialRestrictionException(CredentialRestrictionException.INVALID_STATE,
                    state, loginUser.getUserid(), "凭据状态异常，请联系管理员");
        }

        Date expiresAt = loginUser.getTemporaryPasswordExpiresAt();
        if (SysUser.CREDENTIAL_STATE_TEMPORARY.equals(state)
                && (expiresAt == null || !expiresAt.toInstant().isAfter(clock.instant())))
        {
            throw new CredentialRestrictionException(CredentialRestrictionException.TEMPORARY_EXPIRED,
                    state, loginUser.getUserid(), "临时密码已失效，请联系管理员重新生成");
        }
        if (allowsTemporaryCredential(handlerMethod))
        {
            return true;
        }
        throw new CredentialRestrictionException(CredentialRestrictionException.CHANGE_REQUIRED,
                state, loginUser.getUserid(), "请先修改密码后再继续使用系统");
    }

    private boolean allowsTemporaryCredential(HandlerMethod handlerMethod)
    {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), AllowsTemporaryCredential.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), AllowsTemporaryCredential.class);
    }
}
