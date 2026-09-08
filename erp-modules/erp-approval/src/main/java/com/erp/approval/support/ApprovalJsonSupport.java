package com.erp.approval.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Centralized canonical JSON and checksum handling for immutable snapshots. */
@Component
public class ApprovalJsonSupport
{
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public ApprovalJsonSupport(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String write(Object value)
    {
        try
        {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("审批定义JSON序列化失败")
                    .setDetailMessage(exception.getMessage());
        }
    }

    public Map<String, Object> readMap(String json)
    {
        if (json == null || json.isBlank())
        {
            return Collections.emptyMap();
        }
        try
        {
            return objectMapper.readValue(json, MAP_TYPE);
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("审批策略配置JSON无效")
                    .setDetailMessage(exception.getMessage());
        }
    }

    public String sha256(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value)
                            .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest)
            {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
