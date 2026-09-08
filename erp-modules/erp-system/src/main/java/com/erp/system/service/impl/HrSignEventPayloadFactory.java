package com.erp.system.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.system.domain.SysHrLifecycleAction;

/**
 * 只从不可变生命周期动作及其冻结快照重建标准签约事件。
 */
@Service
public class HrSignEventPayloadFactory
{
    private static final String SOURCE_TYPE = "HR_LIFECYCLE_ACTION";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper objectMapper;
    private final ObjectWriter payloadWriter;

    public HrSignEventPayloadFactory(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
        this.payloadWriter = objectMapper.copy()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .writerFor(HrSignBusinessEvent.class);
    }

    public HrSignBusinessEvent fromAction(SysHrLifecycleAction action)
    {
        validateEnvelope(action);
        String scenario = scenario(action.getActionType());
        HrEmployeeSigningSnapshot before = readSnapshot(
                action.getBeforeSnapshotJson());
        HrEmployeeSigningSnapshot after = readSnapshot(
                action.getAfterSnapshotJson());
        Date occurredTime = action.getActualConfirmTime() == null
                ? action.getCreateTime() : action.getActualConfirmTime();
        if (occurredTime == null)
        {
            throw invalid();
        }

        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId("HR-ACTION:" + action.getActionId() + ":"
                + action.getVersion());
        event.setScenario(scenario);
        event.setEmployeeId(action.getEmployeeId());
        event.setSourceType(SOURCE_TYPE);
        event.setSourceBusinessId(String.valueOf(action.getActionId()));
        event.setSourceEventVersion(action.getVersion());
        event.setOccurredTime(occurredTime);
        event.setOperatorUserId(action.getOperatorUserId());
        event.setBeforeSnapshot(before);
        event.setAfterSnapshot(after);
        event.setAttributes(attributes(action, before, after, occurredTime));
        return event;
    }

    public String writePayload(HrSignBusinessEvent event)
    {
        try
        {
            return payloadWriter.writeValueAsString(event);
        }
        catch (JsonProcessingException serializationFailure)
        {
            throw new InvalidLifecycleActionException(
                    "生命周期事件序列化失败");
        }
    }

    private Map<String, Object> attributes(SysHrLifecycleAction action,
            HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after, Date occurredTime)
    {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("actionType", action.getActionType());
        attributes.put("sourceActionId", action.getActionId());
        attributes.put("sourceActionVersion", action.getVersion());
        switch (action.getActionType())
        {
            case "TRANSFER_CONFIRMED", "OFFBOARD_CONFIRMED" ->
                    addEffectiveActionAttributes(attributes, action,
                            occurredTime);
            case "RENEWAL_CONFIRMED", "RENEWAL_DECLINED" ->
                    addRenewalAttributes(attributes, action.getActionType(),
                            before, after);
            default ->
            {
                // Common envelope attributes are sufficient.
            }
        }
        return attributes;
    }

    private void addEffectiveActionAttributes(Map<String, Object> attributes,
            SysHrLifecycleAction action, Date occurredTime)
    {
        if (action.getEffectiveDate() == null)
        {
            throw invalid();
        }
        LocalDate operationDate = LocalDate.ofInstant(
                occurredTime.toInstant(), BUSINESS_ZONE);
        boolean historical = action.getEffectiveDate()
                .isBefore(operationDate);
        attributes.put("effectiveDate", action.getEffectiveDate().toString());
        attributes.put("actualConfirmTime",
                action.getActualConfirmTime() == null
                        ? occurredTime : action.getActualConfirmTime());
        attributes.put("historicalSupplement", historical);
        attributes.put("riskLevel", action.getRiskLevel());
        if (historical)
        {
            attributes.put("historicalReason", action.getHistoricalReason());
        }
    }

    private void addRenewalAttributes(Map<String, Object> attributes,
            String actionType, HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        if (before.getContractEndDate() == null)
        {
            throw invalid();
        }
        attributes.put("decision", "RENEWAL_CONFIRMED".equals(actionType)
                ? "RENEW" : "DECLINE");
        attributes.put("oldContractEndDate",
                before.getContractEndDate().toString());
        attributes.put("oldRenewalCount", before.getRenewalCount());
        attributes.put("newRenewalCount", after.getRenewalCount());
    }

    private HrEmployeeSigningSnapshot readSnapshot(String json)
    {
        try
        {
            if (json == null || json.isBlank())
            {
                throw invalid();
            }
            HrEmployeeSigningSnapshot snapshot = objectMapper.readValue(json,
                    HrEmployeeSigningSnapshot.class);
            if (snapshot == null)
            {
                throw invalid();
            }
            return snapshot;
        }
        catch (JsonProcessingException | IllegalArgumentException failure)
        {
            throw invalid();
        }
    }

    private void validateEnvelope(SysHrLifecycleAction action)
    {
        if (action == null || action.getActionId() == null
                || action.getVersion() == null
                || action.getEmployeeId() == null
                || action.getActionType() == null)
        {
            throw invalid();
        }
    }

    private String scenario(String actionType)
    {
        return switch (actionType)
        {
            case "ONBOARD_CONFIRMED" -> "ONBOARD";
            case "REGULARIZATION_CONFIRMED" -> "REGULARIZE";
            case "TRANSFER_CONFIRMED" -> "TRANSFER";
            case "RENEWAL_CONFIRMED", "RENEWAL_DECLINED" -> "RENEWAL";
            case "OFFBOARD_CONFIRMED" -> "OFFBOARD";
            default -> throw invalid();
        };
    }

    private InvalidLifecycleActionException invalid()
    {
        return new InvalidLifecycleActionException(
                "生命周期动作快照无效");
    }

    /**
     * 稳定、无敏感详情的本地 action 契约错误。
     */
    public static class InvalidLifecycleActionException
            extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        InvalidLifecycleActionException(String message)
        {
            super(message);
        }
    }
}
