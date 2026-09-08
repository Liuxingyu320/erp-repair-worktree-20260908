package com.erp.file.drive.service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import com.erp.common.core.utils.ServletUtils;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveAuditContext;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DriveOperationLog;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 最佳努力写入的云盘审计服务；审计故障不得回滚业务。
 */
@Service
public class DriveOperationLogService
{
    private static final Logger log = LoggerFactory.getLogger(DriveOperationLogService.class);
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9A-Fa-f:.]+");
    private static final int MAX_SUMMARY = 1000;
    private static final Set<String> ACTIONS = Set.of(
            DriveConstants.ACTION_CREATE_FOLDER,
            DriveConstants.ACTION_UPLOAD,
            DriveConstants.ACTION_OPEN,
            DriveConstants.ACTION_PREVIEW,
            DriveConstants.ACTION_DOWNLOAD,
            DriveConstants.ACTION_RENAME,
            DriveConstants.ACTION_MOVE,
            DriveConstants.ACTION_TRASH,
            DriveConstants.ACTION_RESTORE,
            DriveConstants.ACTION_PURGE,
            DriveConstants.ACTION_CLEANUP,
            DriveConstants.ACTION_QUOTA_POLICY_UPDATE,
            DriveConstants.ACTION_QUOTA_POLICY_DELETE,
            DriveConstants.ACTION_ORG_CONFIG_UPDATE,
            DriveConstants.ACTION_ORG_CONFIG_BATCH,
            DriveConstants.ACTION_ORG_TYPE_RULE_UPDATE,
            DriveConstants.ACTION_ORG_RECONCILE,
            DriveConstants.ACTION_CAPACITY_UPDATE);

    private final DriveOperationLogPersistence persistence;

    public DriveOperationLogService(DriveOperationLogPersistence persistence)
    {
        this.persistence = persistence;
    }

    public DriveAuditContext captureContext(DriveActor actor)
    {
        HttpServletRequest request = ServletUtils.getRequest();
        String requestId = request == null ? null : trim(request.getHeader("X-Request-Id"));
        if (requestId == null || !REQUEST_ID.matcher(requestId).matches())
        {
            requestId = UUID.randomUUID().toString();
        }

        String ipAddress = request == null ? "" : clientIp(request);
        String userAgent = request == null ? "" : sanitize(request.getHeader("User-Agent"), 500);
        return new DriveAuditContext(
                actor == null ? null : actor.userId(),
                actor == null ? null : actor.deptId(),
                sanitize(actor == null ? "" : actor.username(), 64),
                requestId,
                ipAddress,
                userAgent);
    }

    public void success(String action, DriveActor actor, DriveNode node,
            String beforeSummary, String afterSummary)
    {
        success(action, captureContext(actor), node, beforeSummary, afterSummary);
    }

    public void success(String action, DriveAuditContext context, DriveNode node,
            String beforeSummary, String afterSummary)
    {
        DriveOperationLog operation = operation(action, context, node,
                DriveConstants.RESULT_SUCCESS, null, beforeSummary, afterSummary);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    persistBestEffort(operation);
                }
            });
            return;
        }
        persistBestEffort(operation);
    }

    public void failure(String action, DriveActor actor, String errorCode)
    {
        failure(action, actor, null, errorCode, null, null);
    }

    public void failure(String action, DriveActor actor, DriveNode node,
            String errorCode, String beforeSummary, String afterSummary)
    {
        failure(action, captureContext(actor), node, errorCode, beforeSummary, afterSummary);
    }

    public void failure(String action, DriveAuditContext context, DriveNode node,
            String errorCode, String beforeSummary, String afterSummary)
    {
        DriveOperationLog operation = operation(action, context, node,
                DriveConstants.RESULT_FAILURE, errorCode, beforeSummary, afterSummary);
        persistBestEffort(operation);
    }

    private DriveOperationLog operation(String action, DriveAuditContext context,
            DriveNode node, String result, String errorCode,
            String beforeSummary, String afterSummary)
    {
        if (!ACTIONS.contains(action))
        {
            throw new IllegalArgumentException("unsupported drive audit action");
        }
        DriveOperationLog operation = new DriveOperationLog();
        operation.setSpaceId(node == null ? null : node.getSpaceId());
        operation.setNodeId(node == null ? null : node.getNodeId());
        operation.setAction(action);
        operation.setOperatorUserId(context.operatorUserId() == null ? 0L : context.operatorUserId());
        operation.setOperatorDeptId(context.operatorDeptId());
        operation.setOperatorName(context.operatorName());
        operation.setRequestId(context.requestId());
        operation.setIpAddress(context.ipAddress());
        operation.setUserAgent(context.userAgent());
        operation.setBeforeSummary(sanitizeSummary(beforeSummary));
        operation.setAfterSummary(sanitizeSummary(afterSummary));
        operation.setResult(result);
        operation.setErrorCode(sanitize(errorCode, 64));
        operation.setCreateTime(new Date());
        return operation;
    }

    private void persistBestEffort(DriveOperationLog operation)
    {
        try
        {
            persistence.insert(operation);
        }
        catch (RuntimeException ex)
        {
            log.error("drive_audit_write_failed action={} requestId={} spaceId={} nodeId={}",
                    operation.getAction(), operation.getRequestId(), operation.getSpaceId(),
                    operation.getNodeId(), ex);
        }
    }

    private static String clientIp(HttpServletRequest request)
    {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null)
        {
            int comma = forwarded.indexOf(',');
            String first = trim(comma >= 0 ? forwarded.substring(0, comma) : forwarded);
            if (isIpLiteral(first))
            {
                return first;
            }
        }
        String remote = trim(request.getRemoteAddr());
        return isIpLiteral(remote) ? remote : "";
    }

    private static boolean isIpLiteral(String value)
    {
        if (value == null || value.isEmpty())
        {
            return false;
        }
        if (value.indexOf(':') >= 0)
        {
            if (!IPV6_LITERAL.matcher(value).matches())
            {
                return false;
            }
            try
            {
                return InetAddress.getByName(value) instanceof Inet6Address;
            }
            catch (Exception ignored)
            {
                return false;
            }
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4)
        {
            return false;
        }
        for (String part : parts)
        {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit))
            {
                return false;
            }
            try
            {
                int number = Integer.parseInt(part);
                if (number < 0 || number > 255)
                {
                    return false;
                }
            }
            catch (NumberFormatException ignored)
            {
                return false;
            }
        }
        return true;
    }

    private static String sanitizeSummary(String value)
    {
        String sanitized = sanitize(value, MAX_SUMMARY);
        if (sanitized == null)
        {
            return null;
        }
        String lower = sanitized.toLowerCase(Locale.ROOT);
        if (lower.contains("storagekey") || lower.contains("storage_key")
                || lower.contains("authorization")
                || lower.contains("bearer ") || lower.contains("token="))
        {
            return "[REDACTED]";
        }
        return sanitized;
    }

    private static String sanitize(String value, int maxLength)
    {
        if (value == null)
        {
            return null;
        }
        StringBuilder safe = new StringBuilder(Math.min(value.length(), maxLength));
        value.codePoints().filter(codePoint -> !Character.isISOControl(codePoint))
                .forEach(codePoint -> {
                    if (safe.length() + Character.charCount(codePoint) <= maxLength)
                    {
                        safe.appendCodePoint(codePoint);
                    }
                });
        return safe.toString();
    }

    private static String trim(String value)
    {
        return value == null ? null : value.trim();
    }
}
