package com.erp.system.service.push;

import java.util.LinkedHashMap;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.domain.SysUserDeviceToken;

/**
 * 单设备移动推送边界。实现不得在结果或异常中包含原始设备令牌。
 */
public interface PushDeliveryClient
{
    String platform();

    DeliveryResult deliver(SysUserDeviceToken deviceToken, UserNotificationCommand command);

    static Map<String, String> whitelistedRouteData(UserNotificationCommand command)
    {
        if (command == null || command.getRouteType() == null)
        {
            throw new ServiceException("推送路由不能为空");
        }
        String routeType = command.getRouteType().trim();
        String idKey;
        if ("OA_SIGN_HR_TASK".equals(routeType))
        {
            idKey = "taskId";
        }
        else if ("OA_SIGN_PACKAGE_SIGN".equals(routeType))
        {
            idKey = "packageId";
        }
        else
        {
            throw new ServiceException("推送路由类型不受支持");
        }

        try
        {
            JSONObject routeParams = JSON.parseObject(command.getRouteParams());
            Object rawId = routeParams == null ? null : routeParams.get(idKey);
            String idText = rawId == null ? null : String.valueOf(rawId);
            if (idText == null || !idText.matches("[1-9][0-9]{0,18}"))
            {
                throw new ServiceException("推送路由ID不能为空");
            }
            String businessKey = command.getBusinessKey() == null
                    ? null : command.getBusinessKey().trim();
            if (businessKey == null || businessKey.isEmpty() || businessKey.length() > 180)
            {
                throw new ServiceException("推送业务键无效");
            }
            long id = Long.parseLong(idText);
            Map<String, String> data = new LinkedHashMap<>();
            data.put("routeType", routeType);
            data.put(idKey, String.valueOf(id));
            data.put("businessKey", businessKey);
            return data;
        }
        catch (ServiceException exception)
        {
            throw exception;
        }
        catch (RuntimeException exception)
        {
            throw new ServiceException("推送路由参数格式不正确");
        }
    }

    enum DeliveryStatus
    {
        DELIVERED,
        DISABLED,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE,
        INVALID_TOKEN
    }

    final class DeliveryResult
    {
        private final DeliveryStatus status;

        private final String providerCode;

        private DeliveryResult(DeliveryStatus status, String providerCode)
        {
            this.status = status;
            this.providerCode = sanitizeProviderCode(providerCode);
        }

        public static DeliveryResult delivered(String providerCode)
        {
            return new DeliveryResult(DeliveryStatus.DELIVERED, providerCode);
        }

        public static DeliveryResult disabled(String providerCode)
        {
            return new DeliveryResult(DeliveryStatus.DISABLED, providerCode);
        }

        public static DeliveryResult retryableFailure(String providerCode)
        {
            return new DeliveryResult(DeliveryStatus.RETRYABLE_FAILURE, providerCode);
        }

        public static DeliveryResult permanentFailure(String providerCode)
        {
            return new DeliveryResult(DeliveryStatus.PERMANENT_FAILURE, providerCode);
        }

        public static DeliveryResult invalidToken(String providerCode)
        {
            return new DeliveryResult(DeliveryStatus.INVALID_TOKEN, providerCode);
        }

        public DeliveryStatus getStatus()
        {
            return status;
        }

        public String getProviderCode()
        {
            return providerCode;
        }

        @Override
        public String toString()
        {
            return "DeliveryResult{status=" + status + ", providerCode='" + providerCode + "'}";
        }
    }

    final class DeliveryException extends Exception
    {
        private static final long serialVersionUID = 1L;

        private final DeliveryStatus status;

        private final String providerCode;

        private DeliveryException(DeliveryStatus status, String providerCode, Throwable cause)
        {
            super(sanitizeProviderCode(providerCode), cause);
            this.status = status;
            this.providerCode = sanitizeProviderCode(providerCode);
        }

        public static DeliveryException retryable(String providerCode)
        {
            return new DeliveryException(DeliveryStatus.RETRYABLE_FAILURE, providerCode, null);
        }

        public static DeliveryException retryable(String providerCode, Throwable cause)
        {
            return new DeliveryException(DeliveryStatus.RETRYABLE_FAILURE, providerCode, cause);
        }

        public static DeliveryException permanent(String providerCode, Throwable cause)
        {
            return new DeliveryException(DeliveryStatus.PERMANENT_FAILURE, providerCode, cause);
        }

        public static DeliveryException invalidToken(String providerCode)
        {
            return new DeliveryException(DeliveryStatus.INVALID_TOKEN, providerCode, null);
        }

        public DeliveryStatus getStatus()
        {
            return status;
        }

        public String getProviderCode()
        {
            return providerCode;
        }
    }

    private static String sanitizeProviderCode(String value)
    {
        if (value == null || value.isBlank())
        {
            return "UNKNOWN";
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return sanitized.substring(0, Math.min(64, sanitized.length()));
    }
}
