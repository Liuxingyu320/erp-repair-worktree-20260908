package com.erp.common.security.aspect;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.ServletUtils;
import com.erp.common.core.utils.SignScopeHeaderUtils;
import com.erp.common.core.utils.ShopHeaderUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.service.IdempotentSubmitService;
import com.erp.common.security.utils.SecurityUtils;

@Aspect
@Component
public class IdempotentSubmitAspect
{
    private static final Logger log = LoggerFactory.getLogger(
            IdempotentSubmitAspect.class);
    private static final String CACHE_PREFIX = "idempotent:submit:";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Autowired
    private IdempotentSubmitService idempotentSubmitService;

    @Around("@annotation(idempotentSubmit)")
    public Object around(ProceedingJoinPoint point, IdempotentSubmit idempotentSubmit) throws Throwable
    {
        String key = buildKey(point);
        if (!idempotentSubmitService.tryAcquire(key, idempotentSubmit.timeout()))
        {
            throw new ServiceException(idempotentSubmit.message());
        }
        try
        {
            Object result = point.proceed();
            if (idempotentSubmit.releaseOnSuccess())
            {
                releaseQuietly(key);
            }
            return result;
        }
        catch (Throwable e)
        {
            if (idempotentSubmit.releaseOnFailure())
            {
                releaseQuietly(key);
            }
            throw e;
        }
    }

    private void releaseQuietly(String key)
    {
        try
        {
            idempotentSubmitService.release(key);
        }
        catch (RuntimeException ex)
        {
            log.warn("Failed to release idempotent submit key; TTL remains as fallback", ex);
        }
    }

    String buildKey(ProceedingJoinPoint point)
    {
        HttpServletRequest request = resolveRequest(point);
        String userId = String.valueOf(SecurityUtils.getUserId());
        String method = request == null ? "" : StringUtils.defaultString(request.getMethod());
        String uri = request == null ? "" : StringUtils.defaultString(request.getRequestURI());
        boolean signingRequest = isSigningRequest(uri);
        Long signScopeDeptId = signingRequest
                ? SignScopeHeaderUtils.resolveSignScopeDeptId(request) : null;
        String selectedDeptId = signScopeDeptId == null
                ? (request == null ? "" : StringUtils.defaultString(
                        request.getHeader(ShopHeaderUtils.SHOP_HEADER)))
                : String.valueOf(signScopeDeptId);
        String requestIdentity = resolveRequestIdentity(point, request);
        return CACHE_PREFIX + "user:" + userId
                + ":dept:" + selectedDeptId
                + ":method:" + method
                + ":uri:" + uri
                + ":" + requestIdentity;
    }

    private boolean isSigningRequest(String uri)
    {
        return isSameOrChildPath(uri, "/signPackage")
                || isSameOrChildPath(uri, "/signTask")
                || isSameOrChildPath(uri, "/oa/signPackage")
                || isSameOrChildPath(uri, "/oa/signTask");
    }

    private boolean isSameOrChildPath(String uri, String rootPath)
    {
        return rootPath.equals(uri) || uri.startsWith(rootPath + "/");
    }

    private HttpServletRequest resolveRequest(ProceedingJoinPoint point)
    {
        if (point != null && point.getArgs() != null)
        {
            for (Object arg : point.getArgs())
            {
                if (arg instanceof HttpServletRequest request)
                {
                    return request;
                }
            }
        }
        return ServletUtils.getRequest();
    }

    private String resolveRequestIdentity(ProceedingJoinPoint point, HttpServletRequest request)
    {
        String idempotencyKey = request == null ? null : request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (StringUtils.isNotEmpty(idempotencyKey))
        {
            return "client:" + idempotencyKey;
        }
        return "body:" + sha256(JSON.toJSONString(filterBusinessArgs(point == null ? null : point.getArgs())));
    }

    private List<Object> filterBusinessArgs(Object[] args)
    {
        List<Object> businessArgs = new ArrayList<>();
        if (args == null)
        {
            return businessArgs;
        }
        for (Object arg : args)
        {
            if (arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse
                    || arg instanceof MultipartFile
                    || arg instanceof MultipartFile[])
            {
                continue;
            }
            businessArgs.add(arg);
        }
        return businessArgs;
    }

    private String sha256(String value)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash)
            {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new ServiceException("请求幂等摘要生成失败");
        }
    }
}
