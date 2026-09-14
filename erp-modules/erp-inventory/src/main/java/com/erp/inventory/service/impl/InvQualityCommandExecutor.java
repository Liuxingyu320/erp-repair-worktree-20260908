package com.erp.inventory.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvQualityCommand;
import com.erp.inventory.mapper.InvQualityCommandMapper;

@Service
public class InvQualityCommandExecutor
{
    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_SUCCEEDED = "SUCCEEDED";

    private static final Pattern REQUEST_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");

    private final InvQualityCommandMapper commandMapper;
    private final ObjectMapper canonicalMapper;

    public InvQualityCommandExecutor(InvQualityCommandMapper commandMapper,
            ObjectMapper objectMapper)
    {
        this.commandMapper = commandMapper;
        ObjectMapper commandMapperCopy = objectMapper.copy();
        commandMapperCopy.setConfig(commandMapperCopy.getSerializationConfig()
                .with(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS));
        commandMapperCopy.setConfig(commandMapperCopy.getDeserializationConfig()
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        this.canonicalMapper = commandMapperCopy;
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public <T> T execute(String requestId, String commandType,
            Long selectedDeptId, String resourceKey, Object requestPayload,
            Class<T> resultClass, Supplier<T> action)
    {
        String normalizedRequestId = requireRequestId(requestId);
        Long actorUserId = SecurityUtils.getUserId();
        String actorUsername = SecurityUtils.getUsername();
        requireExecutionContext(commandType, selectedDeptId, resourceKey,
                resultClass, action, actorUserId, actorUsername);

        String fingerprint = fingerprint(commandType, actorUserId,
                selectedDeptId, resourceKey, requestPayload);
        InvQualityCommand command = pendingCommand(normalizedRequestId,
                commandType, fingerprint, actorUserId, actorUsername,
                selectedDeptId, resourceKey);

        int inserted = commandMapper.insertIfAbsent(command);
        if (inserted == 0)
        {
            return replay(normalizedRequestId, commandType, fingerprint,
                    actorUserId, selectedDeptId, resourceKey, resultClass);
        }
        if (inserted != 1)
        {
            throw new ServiceException("质检命令请求占用失败，请刷新后重试");
        }

        T result = action.get();
        String resultPayload = serialize(result);
        int completed = commandMapper.completeIfPending(normalizedRequestId,
                fingerprint, resultClass.getName(), resultPayload);
        if (completed != 1)
        {
            throw new ServiceException("质检命令结果保存冲突，业务操作已回滚");
        }
        return result;
    }

    static String requireRequestId(String requestId)
    {
        String normalized = requestId == null ? null : requestId.trim();
        if (StringUtils.isEmpty(normalized)
                || !REQUEST_ID_PATTERN.matcher(normalized).matches())
        {
            throw new ServiceException(
                    "X-Request-Id 必须为 8-128 位字母、数字、点、下划线、冒号或连字符");
        }
        return normalized;
    }

    private void requireExecutionContext(String commandType,
            Long selectedDeptId, String resourceKey, Class<?> resultClass,
            Supplier<?> action, Long actorUserId, String actorUsername)
    {
        if (StringUtils.isEmpty(commandType) || commandType.length() > 64)
        {
            throw new ServiceException("质检命令类型无效");
        }
        if (selectedDeptId == null || selectedDeptId <= 0)
        {
            throw new ServiceException("质检命令缺少有效操作组织");
        }
        if (StringUtils.isEmpty(resourceKey) || resourceKey.length() > 128)
        {
            throw new ServiceException("质检命令资源标识无效");
        }
        if (actorUserId == null || actorUserId <= 0
                || StringUtils.isEmpty(actorUsername))
        {
            throw new ServiceException("质检命令缺少有效登录用户");
        }
        Objects.requireNonNull(resultClass, "resultClass");
        Objects.requireNonNull(action, "action");
    }

    private InvQualityCommand pendingCommand(String requestId,
            String commandType, String fingerprint, Long actorUserId,
            String actorUsername, Long selectedDeptId, String resourceKey)
    {
        InvQualityCommand command = new InvQualityCommand();
        command.setRequestId(requestId);
        command.setCommandType(commandType);
        command.setRequestFingerprint(fingerprint);
        command.setActorUserId(actorUserId);
        command.setActorUsername(actorUsername);
        command.setSelectedDeptId(selectedDeptId);
        command.setResourceKey(resourceKey);
        command.setStatus(STATUS_PENDING);
        return command;
    }

    private <T> T replay(String requestId, String commandType,
            String fingerprint, Long actorUserId, Long selectedDeptId,
            String resourceKey, Class<T> resultClass)
    {
        InvQualityCommand existing =
                commandMapper.selectByRequestIdForUpdate(requestId);
        if (existing == null)
        {
            throw new ServiceException("质检命令并发状态未就绪，请稍后重试");
        }
        if (!Objects.equals(existing.getCommandType(), commandType)
                || !Objects.equals(existing.getRequestFingerprint(),
                        fingerprint)
                || !Objects.equals(existing.getActorUserId(), actorUserId)
                || !Objects.equals(existing.getSelectedDeptId(),
                        selectedDeptId)
                || !Objects.equals(existing.getResourceKey(), resourceKey))
        {
            throw new ServiceException(
                    "X-Request-Id 已被不同的质检命令使用，已拒绝覆盖");
        }
        if (!STATUS_SUCCEEDED.equals(existing.getStatus()))
        {
            throw new ServiceException("相同质检命令仍在处理中，请稍后重试");
        }
        if (!Objects.equals(existing.getResultType(), resultClass.getName()))
        {
            throw new ServiceException("质检命令历史结果类型不匹配");
        }
        return deserialize(existing.getResultPayload(), resultClass);
    }

    private String fingerprint(String commandType, Long actorUserId,
            Long selectedDeptId, String resourceKey, Object requestPayload)
    {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("actorUserId", actorUserId);
        envelope.put("commandType", commandType);
        envelope.put("requestPayload", requestPayload);
        envelope.put("resourceKey", resourceKey);
        envelope.put("selectedDeptId", selectedDeptId);
        byte[] canonical = serialize(envelope)
                .getBytes(StandardCharsets.UTF_8);
        try
        {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

    private String serialize(Object value)
    {
        try
        {
            return canonicalMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("质检命令内容无法生成稳定指纹");
        }
    }

    private <T> T deserialize(String value, Class<T> resultClass)
    {
        try
        {
            return canonicalMapper.readValue(value, resultClass);
        }
        catch (JsonProcessingException | IllegalArgumentException exception)
        {
            throw new ServiceException("质检命令历史结果无法安全重放");
        }
    }
}
