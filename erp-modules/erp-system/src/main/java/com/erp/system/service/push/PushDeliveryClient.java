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
        if (command.getRecipientUserId() == null || command.getRecipientUserId() <= 0)
        {
            throw new ServiceException("推送接收用户不能为空");
        }
        String routeType = command.getRouteType().trim();
        try
        {
            JSONObject routeParams = JSON.parseObject(command.getRouteParams());
            Map<String, String> data = new LinkedHashMap<>();
            data.put("routeType", routeType);
            // Ownership always comes from the targeted command, never from route parameters.
            data.put("recipientUserId", String.valueOf(command.getRecipientUserId()));
            switch (routeType)
            {
                case "OA_SIGN_HR_TASK" -> data.put("taskId", routeId(routeParams, "taskId"));
                case "OA_SIGN_PACKAGE_SIGN" -> data.put("packageId", routeId(routeParams, "packageId"));
                case "HR_HEALTH_CERT_DUE" ->
                {
                    data.put("employeeId", routeId(routeParams, "userId"));
                    data.put("certificateId", routeId(routeParams, "certificateId"));
                }
                case "USER_NOTIFICATION" ->
                {
                    if (routeParams != null && routeParams.containsKey("notificationId"))
                    {
                        data.put("notificationId", routeId(routeParams, "notificationId"));
                    }
                }
                default -> throw new ServiceException("推送路由类型不受支持");
            }
            String businessKey = command.getBusinessKey() == null
                    ? null : command.getBusinessKey().trim();
            if (businessKey == null || businessKey.isEmpty() || businessKey.length() > 180)
            {
                throw new ServiceException("推送业务键无效");
            }
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

    private static String routeId(JSONObject params, String key)
    {
        Object rawId = params == null ? null : params.get(key);
        String idText = rawId == null ? null : String.valueOf(rawId);
        if (idText == null || !idText.matches("[1-9][0-9]{0,18}"))
        {
            throw new ServiceException("推送路由ID不能为空");
        }
        return String.valueOf(Long.parseLong(idText));
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
