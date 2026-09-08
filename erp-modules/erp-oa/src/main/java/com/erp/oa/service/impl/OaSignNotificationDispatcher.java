package com.erp.oa.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.domain.OaSignNotificationOutbox;
import com.erp.system.api.RemoteNotificationService;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.api.domain.UserNotificationResult;
import feign.FeignException;

/** Dispatches durable signing notifications without holding a business transaction open. */
@Service
public class OaSignNotificationDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(OaSignNotificationDispatcher.class);
    private static final int[] RETRY_MINUTES = { 1, 5, 30, 120, 360 };

    private final OaSignNotificationOutboxService outboxService;
    private final RemoteNotificationService remoteNotificationService;
    private final Clock clock;

    @Autowired
    public OaSignNotificationDispatcher(OaSignNotificationOutboxService outboxService,
            RemoteNotificationService remoteNotificationService)
    {
        this(outboxService, remoteNotificationService, Clock.systemUTC());
    }

    OaSignNotificationDispatcher(OaSignNotificationOutboxService outboxService,
            RemoteNotificationService remoteNotificationService, Clock clock)
    {
        this.outboxService = outboxService;
        this.remoteNotificationService = remoteNotificationService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 30_000L)
    public void dispatchDue()
    {
        Instant now = clock.instant();
        List<OaSignNotificationOutbox> rows = outboxService.selectDue(
                Date.from(now), Date.from(now.minusSeconds(300)), 100);
        for (OaSignNotificationOutbox row : rows)
        {
            dispatchOne(row, now);
        }
    }

    private void dispatchOne(OaSignNotificationOutbox row, Instant now)
    {
        boolean claimed = false;
        try
        {
            if (!outboxService.claim(row))
            {
                return;
            }
            claimed = true;
            UserNotificationCommand command = commandFrom(row);
            R<UserNotificationResult> response = remoteNotificationService.publish(
                    command, SecurityConstants.INNER);
            handleResponse(row, response, now);
        }
        catch (RuntimeException exception)
        {
            if (!claimed)
            {
                log.warn("签约通知调度失败，outboxId={}", row.getOutboxId(), exception);
                return;
            }
            if (exception instanceof InvalidPayloadException)
            {
                outboxService.markDead(row, "INVALID_PAYLOAD");
                return;
            }
            Integer httpStatus = httpStatus(exception);
            if (httpStatus != null)
            {
                String error = "REMOTE_HTTP_" + httpStatus;
                if (isPermanentHttpStatus(httpStatus))
                {
                    outboxService.markDead(row, error);
                }
                else
                {
                    retryOrDead(row, now, error);
                }
                return;
            }
            String error = isTimeout(exception) ? "REMOTE_TIMEOUT" : "REMOTE_UNAVAILABLE";
            retryOrDead(row, now, error);
        }
    }

    private void handleResponse(OaSignNotificationOutbox row,
            R<UserNotificationResult> response, Instant now)
    {
        if (response != null && R.isSuccess(response) && response.getData() != null)
        {
            UserNotificationResult result = response.getData();
            String status = StringUtils.isBlank(result.getStatus()) ? "UNKNOWN" : result.getStatus();
            if (result.getAccepted())
            {
                outboxService.markSent(row, status);
                return;
            }
            if ("PERMANENT_FAILURE".equalsIgnoreCase(status))
            {
                outboxService.markDead(row, status);
                return;
            }
            retryOrDead(row, now, status);
            return;
        }

        String error = response == null || StringUtils.isBlank(response.getMsg())
                ? "REMOTE_UNAVAILABLE" : response.getMsg();
        if (isPermanent(response, error))
        {
            outboxService.markDead(row, error);
        }
        else
        {
            retryOrDead(row, now, error);
        }
    }

    private void retryOrDead(OaSignNotificationOutbox row, Instant now, String error)
    {
        int retryCount = row.getRetryCount() == null ? 0 : row.getRetryCount();
        if (retryCount >= RETRY_MINUTES.length)
        {
            outboxService.markDead(row, error);
            return;
        }
        int nextRetryCount = retryCount + 1;
        outboxService.markRetry(row, nextRetryCount,
                Date.from(now.plusSeconds(RETRY_MINUTES[retryCount] * 60L)), error);
    }

    private static UserNotificationCommand commandFrom(OaSignNotificationOutbox row)
    {
        try
        {
            JSONObject payload = JSON.parseObject(row.getPayloadJson());
            String routeType = payload == null ? null : payload.getString("routeType");
            String routeParamsJson = payload == null ? null : payload.getString("routeParams");
            JSONObject routeParams = JSON.parseObject(routeParamsJson);
            String routeIdName = "OA_SIGN_HR_TASK".equals(routeType) ? "taskId"
                    : "OA_SIGN_PACKAGE_SIGN".equals(routeType) ? "packageId" : null;
            Long routeId = routeIdName == null || routeParams == null
                    ? null : routeParams.getLong(routeIdName);
            if (row.getRecipientUserId() == null || StringUtils.isBlank(row.getChannel())
                    || StringUtils.isBlank(row.getBusinessKey()) || payload == null
                    || StringUtils.isBlank(payload.getString("title"))
                    || StringUtils.isBlank(payload.getString("body"))
                    || routeId == null || routeId <= 0
                    || (!OaSignNotificationOutboxService.CHANNEL_IN_APP.equals(row.getChannel())
                    && !OaSignNotificationOutboxService.CHANNEL_MOBILE_PUSH.equals(row.getChannel())))
            {
                throw new InvalidPayloadException();
            }
            UserNotificationCommand command = new UserNotificationCommand();
            command.setChannel(row.getChannel());
            command.setRecipientUserId(row.getRecipientUserId());
            command.setBusinessKey(row.getBusinessKey());
            command.setTitle(payload.getString("title"));
            command.setBody(payload.getString("body"));
            command.setRouteType(routeType);
            command.setRouteParams(routeParamsJson);
            return command;
        }
        catch (InvalidPayloadException exception)
        {
            throw exception;
        }
        catch (RuntimeException exception)
        {
            throw new InvalidPayloadException();
        }
    }

    private static boolean isPermanent(R<UserNotificationResult> response, String error)
    {
        if (response != null && isPermanentHttpStatus(response.getCode()))
        {
            return true;
        }
        String normalized = error == null ? "" : error.toLowerCase(Locale.ROOT);
        return normalized.contains("invalid user") || normalized.contains("user invalid")
                || normalized.contains("permission") || normalized.contains("forbidden")
                || normalized.contains("invalid parameter") || normalized.contains("bad request")
                || normalized.contains("无效用户") || normalized.contains("用户无效")
                || normalized.contains("权限") || normalized.contains("无权")
                || normalized.contains("参数无效") || normalized.contains("不能为空")
                || normalized.contains("长度不能超过") || normalized.contains("只支持")
                || normalized.contains("格式不正确");
    }

    private static boolean isPermanentHttpStatus(int status)
    {
        return status >= 400 && status < 500 && status != 408 && status != 425 && status != 429;
    }

    private static boolean isTimeout(Throwable throwable)
    {
        Throwable current = throwable;
        while (current != null)
        {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("timed out"))
            {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Integer httpStatus(Throwable throwable)
    {
        Throwable current = throwable;
        while (current != null)
        {
            if (current instanceof FeignException feignException && feignException.status() > 0)
            {
                return feignException.status();
            }
            current = current.getCause();
        }
        return null;
    }

    private static final class InvalidPayloadException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
    }
}
