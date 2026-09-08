package com.erp.oa.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;

/**
 * Canonical fingerprint for an immutable signing-plan version.
 *
 * <p>The publication service and the offline release verifier deliberately share this
 * implementation. Release checks must rebuild the complete snapshot and recompute this
 * fingerprint; comparing a persisted {@code version_hash} column with a constant is not
 * sufficient evidence.</p>
 */
@Component
public class OaSignPlanVersionFingerprint
{
    /*
     * Never use Spring's mutable application ObjectMapper here.  The persisted hash is a
     * protocol value, so its JSON rules must not change when global MVC serialization changes.
     * LinkedHashMap below fixes field order; these options make inclusion and scalar rendering
     * explicit as part of that protocol.
     */
    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, false)
            .configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);

    public String calculate(OaSignPlanVersion version)
    {
        if (version == null)
        {
            throw new ServiceException("签约方案版本不能为空");
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("planId", version.getPlanId());
        content.put("planName", version.getPlanName());
        content.put("scenario", version.getScenario());
        content.put("shopDeptId", version.getShopDeptId());
        content.put("legalEntityId", version.getLegalEntityId());
        content.put("legalEntityName", version.getLegalEntityName());
        content.put("ruleJson", version.getRuleJson());
        content.put("defaultValuesJson", version.getDefaultValuesJson());
        content.put("signDeadlineDays", version.getSignDeadlineDays());
        content.put("reminderPolicyJson", version.getReminderPolicyJson());
        content.put("autoSendConditionJson", version.getAutoSendConditionJson());
        List<Map<String, Object>> templateContent = new ArrayList<>();
        for (OaSignPlanVersionTemplate template : nonNullTemplates(version.getTemplates()))
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("templateId", template.getTemplateId());
            item.put("templateVersion", template.getTemplateVersion());
            item.put("templateType", template.getTemplateType());
            item.put("templateName", template.getTemplateName());
            item.put("sourceFileUrl", template.getSourceFileUrl());
            item.put("sourceFileHash", template.getSourceFileHash());
            item.put("requiredPlaceholders", template.getRequiredPlaceholders());
            item.put("sortOrder", template.getSortOrder());
            item.put("employeeVisible", template.getEmployeeVisible());
            item.put("readConfirmationRequired", template.getReadConfirmationRequired());
            item.put("employeeSignRequired", template.getEmployeeSignRequired());
            item.put("signaturePositionJson", template.getSignaturePositionJson());
            item.put("companySealPositionJson", template.getCompanySealPositionJson());
            item.put("companySealRequired", template.getCompanySealRequired());
            item.put("matchConditionJson", template.getMatchConditionJson());
            templateContent.add(item);
        }
        content.put("templates", templateContent);
        try
        {
            byte[] canonical = CANONICAL_MAPPER.writeValueAsString(content)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical));
        }
        catch (JsonProcessingException e)
        {
            throw new ServiceException("签约方案版本规范化失败")
                    .setDetailMessage(e.getMessage());
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    private List<OaSignPlanVersionTemplate> nonNullTemplates(
            List<OaSignPlanVersionTemplate> templates)
    {
        return templates == null ? List.of() : templates;
    }
}
