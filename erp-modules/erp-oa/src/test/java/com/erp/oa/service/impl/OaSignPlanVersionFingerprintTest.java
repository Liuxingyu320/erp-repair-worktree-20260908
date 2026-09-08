package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;

@DisplayName("签约方案版本正式指纹")
class OaSignPlanVersionFingerprintTest
{
    private static final String SNAPSHOT =
            "oa/sign/release/labor-placement-plan-snapshots-v1.json";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final OaSignPlanVersionFingerprint fingerprint =
            new OaSignPlanVersionFingerprint();

    @Test
    @DisplayName("按正式字段顺序重建两个v7候选完整快照并得到迁移锁定哈希")
    void shouldRecomputeReviewedCandidateHashesFromCompleteSnapshots() throws Exception
    {
        JsonNode ledger = readLedger();
        OaSignPlanVersionTemplate replacement = objectMapper.treeToValue(
                ledger.required("replacementTemplate"), OaSignPlanVersionTemplate.class);

        for (JsonNode sourceNode : ledger.withArray("sourceVersions"))
        {
            OaSignPlanVersion candidate = candidate(sourceNode, replacement);

            assertThat(fingerprint.calculate(candidate))
                    .as("planId=%s formal candidate hash", candidate.getPlanId())
                    .isEqualTo(sourceNode.required("expectedCandidateHash").asText());
        }
    }

    @Test
    @DisplayName("任一业务快照字段或模板字段漂移都会改变正式指纹")
    void shouldRejectPersistedHashWhenAnySnapshotFieldDrifts() throws Exception
    {
        JsonNode ledger = readLedger();
        JsonNode sourceNode = ledger.withArray("sourceVersions").get(0);
        OaSignPlanVersionTemplate replacement = objectMapper.treeToValue(
                ledger.required("replacementTemplate"), OaSignPlanVersionTemplate.class);
        OaSignPlanVersion candidate = candidate(sourceNode, replacement);
        String expected = sourceNode.required("expectedCandidateHash").asText();
        assertThat(fingerprint.calculate(candidate)).isEqualTo(expected);

        candidate.setSignDeadlineDays(candidate.getSignDeadlineDays() + 1);
        assertThat(fingerprint.calculate(candidate)).isNotEqualTo(expected);
        candidate.setSignDeadlineDays(candidate.getSignDeadlineDays() - 1);
        candidate.getTemplates().get(0).setEmployeeVisible("N");
        assertThat(fingerprint.calculate(candidate)).isNotEqualTo(expected);
    }

    @Test
    @DisplayName("全局ObjectMapper排序或空值策略变化不会改变指纹协议")
    void shouldIgnoreMutableApplicationObjectMapperConfiguration() throws Exception
    {
        JsonNode ledger = readLedger();
        ObjectMapper globallyCustomized = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        OaSignPlanVersionTemplate replacement = globallyCustomized.treeToValue(
                ledger.required("replacementTemplate"), OaSignPlanVersionTemplate.class);

        for (JsonNode sourceNode : ledger.withArray("sourceVersions"))
        {
            OaSignPlanVersion candidate = candidate(
                    globallyCustomized, sourceNode, replacement);
            assertThat(new OaSignPlanVersionFingerprint().calculate(candidate))
                    .isEqualTo(sourceNode.required("expectedCandidateHash").asText());
        }
    }

    private OaSignPlanVersion candidate(JsonNode sourceNode,
            OaSignPlanVersionTemplate replacement) throws Exception
    {
        return candidate(objectMapper, sourceNode, replacement);
    }

    private OaSignPlanVersion candidate(ObjectMapper mapper, JsonNode sourceNode,
            OaSignPlanVersionTemplate replacement) throws Exception
    {
        OaSignPlanVersion candidate = mapper.treeToValue(
                sourceNode, OaSignPlanVersion.class);
        List<OaSignPlanVersionTemplate> templates = new ArrayList<>();
        for (JsonNode templateNode : sourceNode.withArray("templates"))
        {
            OaSignPlanVersionTemplate source = mapper.treeToValue(
                    templateNode, OaSignPlanVersionTemplate.class);
            if (OaSignTemplateType.ONBOARD_LABOR_CONTRACT.equals(
                    source.getTemplateType()))
            {
                OaSignPlanVersionTemplate labor = mapper.convertValue(
                        replacement, OaSignPlanVersionTemplate.class);
                labor.setSortOrder(source.getSortOrder());
                labor.setMatchConditionJson(source.getMatchConditionJson());
                templates.add(labor);
            }
            else
            {
                templates.add(source);
            }
        }
        candidate.setTemplates(templates);
        return candidate;
    }

    private JsonNode readLedger() throws Exception
    {
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(SNAPSHOT))
        {
            assertThat(input).as("tracked release hash fixture").isNotNull();
            return objectMapper.readTree(input);
        }
    }
}
