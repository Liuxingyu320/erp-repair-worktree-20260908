package com.erp.oa.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.erp.oa.domain.dto.OaSignPlanPublishRequest;
import com.erp.oa.domain.vo.OaSignPlanPublishPreview;
import com.erp.oa.domain.vo.OaSignPlanVersionPublishResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.constant.OaOnboardSalaryVersionPolicy;
import com.erp.oa.constant.OaSignScenarioCodes;
import com.erp.oa.constant.OaSignPlanScope;
import com.erp.oa.domain.OaSignPlan;
import com.erp.oa.domain.OaSignPlanTemplate;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.mapper.OaSignPlanVersionMapper;
import com.erp.oa.service.IOaSignPlanVersionService;

@Service
public class OaSignPlanVersionServiceImpl implements IOaSignPlanVersionService
{
    private static final Logger log = LoggerFactory.getLogger(OaSignPlanVersionServiceImpl.class);
    @Autowired private OaSignPlanPublishPreviewStore previewStore;

    private static final String ENABLED_SOURCE_STATUS = "0";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String MATCHING_ENABLED = "ENABLED";
    private static final String MATCHING_DISABLED = "DISABLED";
    private static final int DEFAULT_SIGN_DEADLINE_DAYS = 7;
    private static final int DEFAULT_SORT_ORDER = 100;
    private static final String APPENDED_CONFIRMATION_PAGE =
            "{\"mode\":\"APPENDED_CONFIRMATION_PAGE\"}";
    private static final Set<String> APPENDED_PLACEMENT_FIELDS = Set.of("mode");
    private static final Set<String> PLACED_PLACEMENT_FIELDS = Set.of(
            "mode", "pageNumber", "x", "y", "width", "height");
    private static final Set<String> LAST_PAGE_PLACEMENT_FIELDS = Set.of(
            "mode", "x", "y", "width", "height");
    private static final Set<String> REMINDER_POLICY_FIELDS = Set.of("daysBefore");
    private static final Set<String> ONBOARD_ROUTING_RULE_FIELDS = Set.of(
            "contractTypeCode", "jobGradeBand", "employmentType", "postLevel");
    private static final List<String> ONBOARD_LABOR_REQUIRED_TEMPLATES = List.of(
            OaSignTemplateType.ONBOARD_COMMITMENT,
            OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
            OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
            OaSignTemplateType.ONBOARD_SALARY_CONFIRM);
    private static final List<String> ONBOARD_SERVICE_REQUIRED_TEMPLATES = List.of(
            OaSignTemplateType.ONBOARD_COMMITMENT,
            OaSignTemplateType.ONBOARD_SERVICE_CONTRACT,
            OaSignTemplateType.ONBOARD_SERVICE_RECEIPT);
    private static final int MAX_REMINDER_OFFSETS = 8;
    private static final int MAX_REMINDER_DAYS = 30;

    @Autowired
    private OaSignPlanVersionMapper versionMapper;

    @Autowired
    private OaSignDocumentService documentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OaSignPlanVersionFingerprint versionFingerprint;

    private record Prepared(OaSignPlan source, OaSignPlanVersion candidate,
            List<OaSignPlanVersion> versions, OaSignPlanVersion existing) {}
    private record Publication(OaSignPlanVersion version, String action, List<Long> previousActiveIds) {}

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPlanPublishPreview previewPublish(Long planId, Long selectedShopDeptId)
    {
        Prepared prepared = preparePublication(planId);
        OaSignPlanVersion existing = prepared.existing();
        boolean restore = existing != null && MATCHING_DISABLED.equals(existing.getMatchingStatus());
        if (existing != null && !restore && !MATCHING_ENABLED.equals(existing.getMatchingStatus()))
            throw new ServiceException("历史方案匹配状态无效，请先核对");
        String action = existing == null ? "PUBLISHED" : restore ? "RESTORED" : "UNCHANGED";
        List<Long> active = activeVersionIds(prepared.versions());
        var issued = previewStore.issue(SecurityUtils.getUserId(), planId,
                prepared.candidate().getVersionHash(), active,
                existing == null ? null : existing.getVersionId(), restore);
        Integer versionNo = existing == null ? versionMapper.selectNextVersionNo(planId) : existing.getVersionNo();
        String message = restore
                ? "恢复历史版本V" + versionNo + "用于新任务，并停用本方案其他启用版本；已有签包保持原版本。"
                : existing == null ? "发布新版本用于新任务，并停用本方案其他启用版本；已有签包保持原版本。"
                : "内容与已启用版本V" + versionNo + "一致，无需重复发布。";
        return new OaSignPlanPublishPreview(issued.token(), planId, action,
                existing == null ? null : existing.getVersionId(), versionNo,
                restore ? existing.getVersionId() : null,
                prepared.versions().stream().filter(v -> MATCHING_ENABLED.equals(v.getMatchingStatus()))
                        .map(v -> new OaSignPlanPublishPreview.ActiveVersion(v.getVersionId(), v.getVersionNo())).toList(),
                message, new Date(issued.expiresAt()));
    }

    /** Legacy clients may publish new content, but cannot silently restore a disabled version. */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPlanVersion publish(Long planId, Long selectedShopDeptId)
    {
        return publishPrepared(preparePublication(planId), null).version();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPlanVersionPublishResult confirmPublish(Long planId,
            OaSignPlanPublishRequest request, Long selectedShopDeptId)
    {
        if (request == null) throw new ServiceException("请先预览并确认本次方案发布");
        var ticket = previewStore.require(request.previewToken(), SecurityUtils.getUserId());
        Prepared prepared = preparePublication(planId);
        OaSignPlanVersion existing = prepared.existing();
        if (!Objects.equals(ticket.planId(), planId)
                || !Objects.equals(ticket.versionHash(), prepared.candidate().getVersionHash())
                || !Objects.equals(ticket.activeVersionIds(), activeVersionIds(prepared.versions()))
                || !Objects.equals(ticket.targetVersionId(), existing == null ? null : existing.getVersionId())
                || !Objects.equals(request.restoreVersionId(), ticket.restore() ? ticket.targetVersionId() : null)
                || ticket.restore() != (existing != null && MATCHING_DISABLED.equals(existing.getMatchingStatus())))
            throw new ServiceException("方案内容或启用版本已变化，请重新预览后确认；请求结果不明时先查看当前版本");
        Publication publication = publishPrepared(prepared, request);
        OaSignPlanVersionPublishResult result = OaSignPlanVersionPublishResult.from(publication.version());
        result.setAction(publication.action());
        result.setPreviousActiveVersionIds(publication.previousActiveIds());
        return result;
    }

    private Prepared preparePublication(Long planId)
    {
        OaSignPlan source = versionMapper.lockPlanById(planId);
        assertGlobalSourcePlan(source);
        validateSourcePlan(source);
        // All matching mutations share plan -> published versions (ascending ID) ordering.
        List<OaSignPlanVersion> versions = versionMapper.lockPublishedVersionsByPlanId(planId);
        if (versions == null) versions = List.of();
        List<OaSignPlanTemplate> bindings = versionMapper.lockPlanTemplateBindings(planId);
        attachGloballyLockedTemplates(bindings);
        List<OaSignPlanVersionTemplate> templates = buildTemplateSnapshots(bindings, source.getScenario(), null);
        if (templates.stream().noneMatch(this::hasSigningStrategy))
            throw new ServiceException("签约方案未配置签名策略");
        validateOnboardRequiredTemplates(source, templates);
        OaSignPlanVersion candidate = buildVersionSnapshot(source, templates);
        candidate.setVersionHash(versionFingerprint.calculate(candidate));
        OaSignPlanVersion existing = versionMapper.selectByPlanIdAndVersionHash(planId, candidate.getVersionHash());
        return new Prepared(source, candidate, versions, existing);
    }

    private List<Long> activeVersionIds(List<OaSignPlanVersion> versions)
    {
        return versions.stream().filter(v -> MATCHING_ENABLED.equals(v.getMatchingStatus()))
                .map(OaSignPlanVersion::getVersionId).sorted().toList();
    }

    private Publication publishPrepared(Prepared prepared, OaSignPlanPublishRequest request)
    {
        OaSignPlanVersion candidate = prepared.candidate();
        OaSignPlanVersion existing = prepared.existing();
        List<Long> previousActive = activeVersionIds(prepared.versions());
        if (existing != null)
        {
            if (MATCHING_ENABLED.equals(existing.getMatchingStatus()))
            {
                if (!previousActive.equals(List.of(existing.getVersionId())))
                    throw new ServiceException("当前方案启用版本不一致，请先核对");
                existing.setTemplates(nonNullTemplates(versionMapper.selectTemplatesByVersionId(existing.getVersionId())));
                return new Publication(existing, "UNCHANGED", previousActive);
            }
            if (!MATCHING_DISABLED.equals(existing.getMatchingStatus()) || request == null
                    || !Objects.equals(existing.getVersionId(), request.restoreVersionId()))
                throw new ServiceException("相同内容的历史版本已停用，请预览并明确确认恢复该版本");
            if (versionMapper.enableForNewMatching(existing.getVersionId()) != 1)
                throw new ServiceException("历史方案恢复状态已变化，请重新预览");
            disableOtherVersions(candidate.getPlanId(), existing.getVersionId(), previousActive.size());
            OaSignPlanVersion actual = versionMapper.selectPlanVersionById(existing.getVersionId());
            if (actual == null || !MATCHING_ENABLED.equals(actual.getMatchingStatus())
                    || !PUBLISHED.equals(actual.getPublishStatus())
                    || !Objects.equals(actual.getPlanId(), candidate.getPlanId())
                    || !Objects.equals(actual.getVersionHash(), candidate.getVersionHash()))
                throw new ServiceException("历史方案恢复结果不一致，本次操作已回滚");
            actual.setTemplates(nonNullTemplates(versionMapper.selectTemplatesByVersionId(actual.getVersionId())));
            auditActivation(actual, "RESTORED", previousActive);
            return new Publication(actual, "RESTORED", previousActive);
        }
        Integer nextVersionNo = versionMapper.selectNextVersionNo(candidate.getPlanId());
        candidate.setVersionNo(nextVersionNo == null || nextVersionNo < 1 ? 1 : nextVersionNo);
        candidate.setPublishStatus(PUBLISHED);
        candidate.setMatchingStatus(MATCHING_ENABLED);
        candidate.setPublishedByUserId(SecurityUtils.getUserId());
        candidate.setPublishedBy(SecurityUtils.getUsername());
        candidate.setPublishedTime(new Date());
        if (candidate.getPublishedByUserId() == null || StringUtils.isBlank(candidate.getPublishedBy()))
            throw new ServiceException("无法记录签约方案发布人");
        if (versionMapper.insertPlanVersion(candidate) != 1 || candidate.getVersionId() == null)
            throw new ServiceException("签约方案版本发布失败");
        List<OaSignPlanVersionTemplate> templates = candidate.getTemplates();
        for (OaSignPlanVersionTemplate template : templates) template.setPlanVersionId(candidate.getVersionId());
        if (versionMapper.batchInsertPlanVersionTemplates(templates) != templates.size())
            throw new ServiceException("签约方案模板快照发布失败");
        disableOtherVersions(candidate.getPlanId(), candidate.getVersionId(), previousActive.size());
        auditActivation(candidate, "PUBLISHED", previousActive);
        return new Publication(candidate, "PUBLISHED", previousActive);
    }

    private void disableOtherVersions(Long planId, Long keepId, int expectedCount)
    {
        if (versionMapper.disableOtherPublishedMatchingVersions(planId, keepId) != expectedCount)
            throw new ServiceException("方案启用状态已变化，本次发布已回滚，请重新预览");
    }

    private void auditActivation(OaSignPlanVersion version, String action, List<Long> previousActive)
    {
        Long actorId = SecurityUtils.getUserId(), planId = version.getPlanId(), targetId = version.getVersionId();
        String hash = version.getVersionHash();
        List<Long> previous = List.copyOf(previousActive);
        Runnable record = () -> log.info("SIGN_PLAN_ACTIVATION action={} actor={} plan={} targetVersion={} previousActive={} contentHash={}",
                action, actorId, planId, targetId, previous, hash);
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override public void afterCommit() { record.run(); }
            });
        }
        else
        {
            // Direct non-Spring calls have no commit event; never label these as a committed activation.
            log.debug("SIGN_PLAN_ACTIVATION_NO_TRANSACTION action={} plan={} targetVersion={}", action, planId, targetId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSignPlanVersion disableForNewMatching(Long versionId, Long selectedShopDeptId)
    {
        OaSignPlanVersion observed = requireScopedVersion(versionId, selectedShopDeptId);
        assertGlobalSourcePlan(versionMapper.lockPlanById(observed.getPlanId()));
        List<OaSignPlanVersion> locked = versionMapper.lockPublishedVersionsByPlanId(observed.getPlanId());
        OaSignPlanVersion version = requireScopedLockedVersion(versionId, selectedShopDeptId);
        List<Long> previousActive = activeVersionIds(locked == null ? List.of() : locked);
        if (!PUBLISHED.equals(version.getPublishStatus()) && version.getPublishStatus() != null)
        {
            throw new ServiceException("仅已发布方案版本可停用新匹配");
        }
        if (!MATCHING_DISABLED.equals(version.getMatchingStatus()))
        {
            if (versionMapper.disableForNewMatching(versionId) != 1)
            {
                throw new ServiceException("签约方案版本停用失败");
            }
            version.setMatchingStatus(MATCHING_DISABLED);
            auditActivation(version, "DISABLED", previousActive);
        }
        version.setTemplates(nonNullTemplates(versionMapper.selectTemplatesByVersionId(versionId)));
        return version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUnreferencedVersion(Long versionId, Long selectedShopDeptId)
    {
        requireScopedLockedVersion(versionId, selectedShopDeptId);
        throw new ServiceException("已发布签约方案版本不可物理删除，只能停用新匹配");
    }

    private void assertGlobalSourcePlan(OaSignPlan source)
    {
        if (source == null || !OaSignPlanScope.isGlobal(source.getShopDeptId()))
        {
            throw new ServiceException("签约方案不存在或无权限访问");
        }
    }

    private void validateSourcePlan(OaSignPlan source)
    {
        if (!ENABLED_SOURCE_STATUS.equals(source.getStatus()))
        {
            throw new ServiceException("签约方案已停用，不能发布");
        }
        String normalizedScenario = OaSignScenarioCodes.normalizePackageScenario(source.getScenario());
        if (normalizedScenario == null)
        {
            throw new ServiceException("签约场景不能为空");
        }
        if (!OaSignScenarioCodes.isSupported(normalizedScenario))
        {
            throw new ServiceException("不支持的签约场景：" + source.getScenario().trim());
        }
        source.setScenario(normalizedScenario);
        if (OaSignScenarioCodes.ONBOARD.equals(
                OaSignScenarioCodes.normalizeTaskScenario(normalizedScenario))
                && "劳动合同".equals(StringUtils.trim(source.getEmploymentType())))
        {
            String socialType = OaOnboardSalaryVersionPolicy.normalizeSocialType(
                    source.getSocialType());
            String required = OaOnboardSalaryVersionPolicy.requiredSalaryVersion(socialType);
            if (required == null)
            {
                throw new ServiceException("入职劳动合同方案必须明确社保口径");
            }
            String selected = OaOnboardSalaryVersionPolicy.normalizeSalaryVersion(
                    source.getSalaryVersion());
            if (!required.equals(selected))
            {
                throw new ServiceException("入职劳动合同方案社保口径与薪酬版本冲突："
                        + socialType + "只能使用" + required + "版");
            }
            source.setSocialType(socialType);
            source.setSalaryVersion(required);
        }
        if (source.getSignDeadlineDays() != null
                && (source.getSignDeadlineDays() < 1 || source.getSignDeadlineDays() > 365))
        {
            throw new ServiceException("签署期限必须在1至365天之间");
        }
        if ("regularize".equals(normalizedScenario))
        {
            if (StringUtils.isNotBlank(source.getServicePersonType()))
            {
                throw new ServiceException("转正方案不支持劳务人员类型条件，请清空后发布");
            }
            if (StringUtils.isNotBlank(source.getInsuranceType()))
            {
                throw new ServiceException("转正方案不支持保险类型条件，请清空后发布");
            }
            ObjectNode configuredRules = parseObject(source.getRuleJson(), "方案规则");
            rejectUnsupportedRegularizationRule(configuredRules, "servicePersonType");
            rejectUnsupportedRegularizationRule(configuredRules, "insuranceType");
        }
    }

    private void rejectUnsupportedRegularizationRule(ObjectNode rules, String field)
    {
        JsonNode value = rules.get(field);
        if (value != null && !value.isNull())
        {
            String label = "servicePersonType".equals(field) ? "劳务人员类型" : "保险类型";
            throw new ServiceException("转正方案不支持" + label + "条件，请清空后发布");
        }
    }

    private void validateOnboardRequiredTemplates(OaSignPlan source,
            List<OaSignPlanVersionTemplate> templates)
    {
        if (!OaSignScenarioCodes.ONBOARD.equals(
                OaSignScenarioCodes.normalizeTaskScenario(source.getScenario())))
        {
            return;
        }
        ObjectNode rules = parseObject(source.getRuleJson(), "入职方案规则");
        boolean explicitRoutingConfigured = ONBOARD_ROUTING_RULE_FIELDS.stream()
                .anyMatch(rules::has);

        String explicitContract = normalizeOnboardContractType(
                textRule(rules, "contractTypeCode"), false);
        String legacyContract = normalizeOnboardContractType(
                textRule(rules, "employmentType"), true);
        String contractType = preferConsistentOnboardRule(
                explicitContract, legacyContract, "合同类型");
        if (contractType == null)
        {
            contractType = normalizeOnboardContractType(
                    normalizeText(source.getEmploymentType()), true);
        }

        String explicitBand = normalizeOnboardGradeBand(
                textRule(rules, "jobGradeBand"), false);
        String legacyBand = normalizeOnboardGradeBand(
                textRule(rules, "postLevel"), true);
        String gradeBand = preferConsistentOnboardRule(explicitBand, legacyBand, "等级段");
        if (gradeBand == null)
        {
            gradeBand = normalizeOnboardGradeBand(
                    normalizeText(source.getPostLevelSnapshot()), true);
        }
        if (!explicitRoutingConfigured && (contractType == null || gradeBand == null))
        {
            // 历史通用方案没有完整路由，保留原有发布兼容性。
            // 现行页面使用 employment_type + post_level_snapshot，两者齐全即必须校验。
            return;
        }
        if (contractType == null || gradeBand == null)
        {
            throw new ServiceException("入职方案规则必须明确合同类型和等级段");
        }

        List<String> required = new ArrayList<>("LABOR_CONTRACT".equals(contractType)
                ? ONBOARD_LABOR_REQUIRED_TEMPLATES : ONBOARD_SERVICE_REQUIRED_TEMPLATES);
        if ("7-9".equals(gradeBand))
        {
            required.add(OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE);
        }
        Set<String> configured = new HashSet<>();
        for (OaSignPlanVersionTemplate template : templates)
        {
            if (template != null && StringUtils.isNotBlank(template.getTemplateType()))
            {
                configured.add(template.getTemplateType().trim().toUpperCase(Locale.ROOT));
            }
        }
        List<String> missingLabels = required.stream()
                .filter(type -> !configured.contains(type))
                .map(type -> OaSignTemplateType.require(type).getLabel())
                .toList();
        if (!missingLabels.isEmpty())
        {
            throw new ServiceException("入职签约方案缺少必需模板："
                    + String.join("、", missingLabels));
        }
        validateUniversalOnboardCommitment(templates);
        if ("LABOR_CONTRACT".equals(contractType))
        {
            validateOnboardSalaryTemplate(source, templates);
        }
        else if (configured.contains(OaSignTemplateType.ONBOARD_SALARY_CONFIRM))
        {
            throw new ServiceException("入职劳务合同方案不能绑定薪酬结构确认书");
        }
    }

    private void validateUniversalOnboardCommitment(
            List<OaSignPlanVersionTemplate> templates)
    {
        OaSignPlanVersionTemplate commitment = templates.stream()
                .filter(Objects::nonNull)
                .filter(template -> OaSignTemplateType.ONBOARD_COMMITMENT.equalsIgnoreCase(
                        StringUtils.trim(template.getTemplateType())))
                .findFirst()
                .orElseThrow(() -> new ServiceException("入职签约方案缺少必需模板：入职承诺书"));
        ObjectNode conditions = parseObject(
                commitment.getMatchConditionJson(), "入职承诺书模板匹配条件");
        JsonNode employmentType = conditions.get("employmentType");
        if (employmentType == null || employmentType.isNull())
        {
            return;
        }
        if (!employmentType.isTextual())
        {
            throw new ServiceException("入职承诺书用工类型适用条件必须为文本");
        }
        if (StringUtils.isNotBlank(employmentType.asText()))
        {
            throw new ServiceException(
                    "入职承诺书必须同时适用于劳动合同和劳务合同，请清空用工类型限制");
        }
    }

    private void validateOnboardSalaryTemplate(OaSignPlan source,
            List<OaSignPlanVersionTemplate> templates)
    {
        String expected = OaOnboardSalaryVersionPolicy.requiredSalaryVersion(
                source.getSocialType());
        if (expected == null)
        {
            throw new ServiceException("入职劳动合同方案必须明确社保口径");
        }
        if (!expected.equals(OaOnboardSalaryVersionPolicy.normalizeSalaryVersion(
                source.getSalaryVersion())))
        {
            throw new ServiceException("入职劳动合同方案社保口径与薪酬版本冲突");
        }
        List<OaSignPlanVersionTemplate> salaryTemplates = templates.stream()
                .filter(template -> template != null
                        && OaSignTemplateType.ONBOARD_SALARY_CONFIRM.equalsIgnoreCase(
                                StringUtils.trim(template.getTemplateType())))
                .toList();
        if (salaryTemplates.size() != 1)
        {
            throw new ServiceException("入职劳动合同方案必须且只能绑定一份薪酬结构确认书");
        }
        String actual = OaOnboardSalaryVersionPolicy.normalizeSalaryVersion(
                textRule(parseObject(salaryTemplates.get(0).getMatchConditionJson(),
                        "模板匹配条件"), "salaryVersion"));
        if (!expected.equals(actual))
        {
            throw new ServiceException("薪酬版本" + expected
                    + "的方案只能绑定薪酬结构确认书（" + expected + "版）");
        }
    }

    private String textRule(ObjectNode rules, String field)
    {
        JsonNode value = rules.get(field);
        if (value == null || value.isNull())
        {
            return null;
        }
        if (!value.isTextual())
        {
            throw new ServiceException("入职方案规则字段" + field + "必须为文本");
        }
        return normalizeText(value.asText());
    }

    private String normalizeOnboardContractType(String value, boolean legacy)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = legacy ? value : value.toUpperCase(Locale.ROOT);
        if ((legacy && "劳动合同".equals(normalized))
                || (!legacy && "LABOR_CONTRACT".equals(normalized)))
        {
            return "LABOR_CONTRACT";
        }
        if ((legacy && "劳务合同".equals(normalized))
                || (!legacy && "SERVICE_CONTRACT".equals(normalized)))
        {
            return "SERVICE_CONTRACT";
        }
        throw new ServiceException("入职方案规则的合同类型仅支持劳动合同或劳务合同");
    }

    private String normalizeOnboardGradeBand(String value, boolean legacy)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        if ("2-4".equals(normalized) || "5-6".equals(normalized))
        {
            return normalized;
        }
        if ("7-8".equals(normalized) || "7-9".equals(normalized))
        {
            return "7-9";
        }
        if (legacy)
        {
            String gradeText = normalized.startsWith("P") ? normalized.substring(1) : normalized;
            if (gradeText.matches("[2-9]"))
            {
                int grade = Integer.parseInt(gradeText);
                return grade <= 4 ? "2-4" : grade <= 6 ? "5-6" : "7-9";
            }
        }
        throw new ServiceException("入职方案规则的等级段必须为2-4、5-6或7-9");
    }

    private String preferConsistentOnboardRule(String explicit, String legacy, String label)
    {
        if (explicit != null && legacy != null && !explicit.equals(legacy))
        {
            throw new ServiceException("入职方案规则的" + label + "配置冲突");
        }
        return explicit != null ? explicit : legacy;
    }

    private void attachGloballyLockedTemplates(List<OaSignPlanTemplate> bindings)
    {
        if (bindings == null || bindings.isEmpty())
        {
            return;
        }
        List<Long> templateIds = bindings.stream()
                .filter(Objects::nonNull)
                .map(OaSignPlanTemplate::getTemplateId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (templateIds.isEmpty())
        {
            return;
        }
        List<OaSignTemplate> lockedTemplates = versionMapper.lockTemplatesByIds(templateIds);
        Map<Long, OaSignTemplate> templatesById = new LinkedHashMap<>();
        if (lockedTemplates != null)
        {
            for (OaSignTemplate template : lockedTemplates)
            {
                if (template != null && template.getTemplateId() != null)
                {
                    templatesById.put(template.getTemplateId(), template);
                }
            }
        }
        for (OaSignPlanTemplate binding : bindings)
        {
            if (binding != null)
            {
                binding.setTemplate(templatesById.get(binding.getTemplateId()));
            }
        }
    }

    private List<OaSignPlanVersionTemplate> buildTemplateSnapshots(
            List<OaSignPlanTemplate> bindings, String planScenario, String legalEntityName)
    {
        if (bindings == null || bindings.isEmpty())
        {
            throw new ServiceException("签约方案至少需要一个模板");
        }
        List<OaSignPlanVersionTemplate> snapshots = new ArrayList<>();
        Set<String> templateTypes = new HashSet<>();
        for (OaSignPlanTemplate binding : bindings)
        {
            OaSignTemplate template = binding == null ? null : binding.getTemplate();
            if (template == null || !ENABLED_SOURCE_STATUS.equals(template.getStatus()))
            {
                throw new ServiceException("签约方案模板不存在或已停用");
            }
            String requestedTemplateType = normalizeRequiredText(
                    template.getTemplateType(), "模板类型不能为空");
            OaSignTemplateType.Option templateTypeOption = assertTemplateTypeAllowedForScenario(
                    requestedTemplateType, template.getScenario(), planScenario);
            String templateType = templateTypeOption.getCode();
            if (!templateTypes.add(templateType.toUpperCase(Locale.ROOT)))
            {
                throw new ServiceException("签约方案模板类型不能重复");
            }
            String fileUrl = normalizeRequiredText(template.getFileUrl(), "模板文件不存在");
            String actualHash = documentService.calculateFileUrlSha256(fileUrl);
            if (StringUtils.isBlank(actualHash))
            {
                throw new ServiceException("模板文件不存在：" + OaSignTemplateType.require(templateType).getLabel());
            }
            if (StringUtils.isBlank(template.getFileHash())
                    || !actualHash.equalsIgnoreCase(template.getFileHash().trim()))
            {
                throw new ServiceException("模板文件校验值不一致：" + OaSignTemplateType.require(templateType).getLabel());
            }
            documentService.assertTemplateContainsRequiredPlaceholders(templateType, fileUrl);
            documentService.assertTemplateLegalEntityCompatible(
                    templateType, fileUrl, legalEntityName);

            String employeeVisible;
            String readConfirmationRequired;
            String employeeSignRequired;
            if (!templateTypeOption.isEmployeeVisible())
            {
                employeeVisible = "N";
                readConfirmationRequired = "N";
                employeeSignRequired = "N";
            }
            else
            {
                employeeVisible = normalizeYesNo(template.getEmployeeVisible(), "Y", "员工可见要求");
                if ("N".equals(employeeVisible))
                {
                    readConfirmationRequired = "N";
                    employeeSignRequired = "N";
                }
                else
                {
                    employeeSignRequired = normalizeYesNo(template.getEmployeeSignRequired(),
                            templateTypeOption.isEmployeeSignRequired() ? "Y" : "N",
                            "员工签署要求");
                    readConfirmationRequired = normalizeYesNo(template.getReadConfirmationRequired(),
                            templateTypeOption.isReadConfirmationRequired() ? "Y" : "N",
                            "阅读确认要求");
                    if ("Y".equals(employeeSignRequired))
                    {
                        readConfirmationRequired = "Y";
                    }
                }
            }
            String signaturePosition = normalizePlacementJson(
                    template.getSignaturePositionJson(), "员工签名定位", true);
            if ("Y".equals(employeeSignRequired) && signaturePosition == null)
            {
                signaturePosition = APPENDED_CONFIRMATION_PAGE;
            }
            else if ("N".equals(employeeSignRequired) && signaturePosition != null)
            {
                throw new ServiceException("不需要员工签名的模板不得配置签名定位");
            }
            String companySealRequired = "N";
            if ("Y".equals(employeeVisible))
            {
                companySealRequired = normalizeExplicitYesNo(
                        template.getCompanySealRequired(), "企业章要求");
            }
            else if ("Y".equalsIgnoreCase(StringUtils.trim(template.getCompanySealRequired())))
            {
                throw new ServiceException("非员工可见模板不得要求盖章");
            }
            String sealPosition = normalizePlacementJson(
                    template.getCompanySealPositionJson(), "企业章定位", true);
            if ("Y".equals(companySealRequired) && sealPosition == null)
            {
                throw new ServiceException("需要盖章的模板缺少企业章定位");
            }
            if ("N".equals(companySealRequired) && sealPosition != null)
            {
                throw new ServiceException("不需要盖章的模板不得配置企业章定位");
            }

            OaSignPlanVersionTemplate snapshot = new OaSignPlanVersionTemplate();
            snapshot.setTemplateId(template.getTemplateId());
            snapshot.setTemplateVersion(normalizeText(template.getTemplateVersion()));
            snapshot.setTemplateType(templateType);
            snapshot.setTemplateName(normalizeText(template.getTemplateName()));
            snapshot.setSourceFileUrl(fileUrl);
            snapshot.setSourceFileHash(actualHash.toLowerCase(Locale.ROOT));
            snapshot.setRequiredPlaceholders(String.join(",",
                    templateTypeOption.getRequiredPlaceholders()));
            snapshot.setSortOrder(resolveSortOrder(binding.getSortOrder(), template.getSortOrder()));
            snapshot.setEmployeeVisible(employeeVisible);
            snapshot.setReadConfirmationRequired(readConfirmationRequired);
            snapshot.setEmployeeSignRequired(employeeSignRequired);
            snapshot.setSignaturePositionJson(signaturePosition);
            snapshot.setCompanySealPositionJson(sealPosition);
            snapshot.setCompanySealRequired(companySealRequired);
            snapshot.setMatchConditionJson(buildTemplateMatchCondition(template));
            snapshots.add(snapshot);
        }
        snapshots.sort(Comparator
                .comparing(OaSignPlanVersionTemplate::getSortOrder)
                .thenComparing(OaSignPlanVersionTemplate::getTemplateType)
                .thenComparing(OaSignPlanVersionTemplate::getTemplateId,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return snapshots;
    }

    private OaSignTemplateType.Option assertTemplateTypeAllowedForScenario(
            String templateType, String templateScenario, String planScenario)
    {
        String normalizedPlanScenario = OaSignScenarioCodes.normalizePackageScenario(planScenario);
        if (!OaSignScenarioCodes.isSupported(normalizedPlanScenario))
        {
            throw new ServiceException("不支持的签约场景：" + planScenario);
        }
        OaSignTemplateType.Option option;
        try
        {
            option = OaSignTemplateType.require(templateType.toUpperCase(Locale.ROOT));
        }
        catch (ServiceException ignored)
        {
            throw new ServiceException(scenarioLabel(normalizedPlanScenario)
                    + "方案模板类型不受支持：" + templateType);
        }
        String catalogScenario = OaSignScenarioCodes.normalizePackageScenario(option.getScenario());
        if (!normalizedPlanScenario.equals(catalogScenario))
        {
            throw new ServiceException(scenarioLabel(normalizedPlanScenario) + "方案只能绑定"
                    + scenarioLabel(normalizedPlanScenario) + "场景模板类型");
        }
        String registeredScenario = OaSignScenarioCodes.normalizePackageScenario(templateScenario);
        if (!catalogScenario.equals(registeredScenario))
        {
            throw new ServiceException("模板登记场景与模板类型不一致：" + option.getCode());
        }
        return option;
    }

    private String scenarioLabel(String scenario)
    {
        return switch (OaSignScenarioCodes.normalizeTaskScenario(scenario))
        {
            case OaSignScenarioCodes.ONBOARD -> "入职";
            case OaSignScenarioCodes.RENEWAL -> "续签";
            case OaSignScenarioCodes.TRANSFER -> "调岗";
            case OaSignScenarioCodes.REGULARIZE -> "转正";
            case OaSignScenarioCodes.OFFBOARD -> "离职";
            default -> "签约";
        };
    }

    private OaSignPlanVersion buildVersionSnapshot(OaSignPlan source,
            List<OaSignPlanVersionTemplate> templates)
    {
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setPlanId(source.getPlanId());
        version.setPlanName(normalizeText(source.getPlanName()));
        version.setScenario(OaSignScenarioCodes.normalizePackageScenario(source.getScenario()));
        version.setShopDeptId(source.getShopDeptId());
        // 公司不再属于签约方案的匹配条件。员工首次签名后，
        // 由归属部门自动识别并由经办人确认最终公司。
        version.setLegalEntityId(null);
        version.setLegalEntityName(null);
        version.setRuleJson(buildPlanRules(source));
        version.setDefaultValuesJson(buildPlanDefaults(source));
        version.setSignDeadlineDays(source.getSignDeadlineDays() == null
                ? DEFAULT_SIGN_DEADLINE_DAYS : source.getSignDeadlineDays());
        version.setReminderPolicyJson(normalizeReminderPolicy(
                source.getReminderPolicyJson(), version.getSignDeadlineDays()));
        version.setAutoSendConditionJson(normalizeJsonOrDefault(
                source.getAutoSendConditionJson(), "自动发送条件", "{\"enabled\":false}"));
        version.setTemplates(templates);
        return version;
    }

    private String buildPlanRules(OaSignPlan source)
    {
        Map<String, Object> legacy = new TreeMap<>();
        putIfPresent(legacy, "postName", source.getPostName());
        putIfPresent(legacy, "employmentType", source.getEmploymentType());
        putIfPresent(legacy, "socialType", source.getSocialType());
        putIfPresent(legacy, "servicePersonType", source.getServicePersonType());
        putIfPresent(legacy, "insuranceType", source.getInsuranceType());
        putIfPresent(legacy, "postLevel", source.getPostLevelSnapshot());
        putIfPresent(legacy, "salaryVersion", source.getSalaryVersion());
        return mergeJsonObject(source.getRuleJson(), legacy, "方案规则");
    }

    private String buildPlanDefaults(OaSignPlan source)
    {
        Map<String, Object> legacy = new TreeMap<>();
        putIfPresent(legacy, "entryDate", source.getEntryDate());
        putIfPresent(legacy, "contractStartDate", source.getContractStartDate());
        putIfPresent(legacy, "contractEndDate", source.getContractEndDate());
        putIfPresent(legacy, "probationStartDate", source.getProbationStartDate());
        putIfPresent(legacy, "probationEndDate", source.getProbationEndDate());
        putIfPresent(legacy, "baseSalary", source.getBaseSalary());
        putIfPresent(legacy, "postSalary", source.getPostSalary());
        putIfPresent(legacy, "fieldAllowance", source.getFieldAllowance());
        putIfPresent(legacy, "salaryTotal", source.getSalaryTotal());
        return mergeJsonObject(source.getDefaultValuesJson(), legacy, "方案默认值");
    }

    private String buildTemplateMatchCondition(OaSignTemplate template)
    {
        Map<String, Object> legacy = new TreeMap<>();
        putIfPresent(legacy, "scenario", template.getScenario());
        putIfPresent(legacy, "employmentType", template.getEmploymentType());
        putIfPresent(legacy, "socialType", template.getSocialType());
        putIfPresent(legacy, "postLevelScope", template.getPostLevelScope());
        putIfPresent(legacy, "salaryVersion", template.getSalaryVersion());
        return mergeJsonObject(template.getMatchConditionJson(), legacy, "模板匹配条件");
    }

    private String mergeJsonObject(String configuredJson, Map<String, Object> compatibilityValues,
            String label)
    {
        ObjectNode merged = parseObject(configuredJson, label);
        for (Map.Entry<String, Object> entry : compatibilityValues.entrySet())
        {
            if (!merged.has(entry.getKey()))
            {
                merged.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
            }
        }
        return writeCanonicalJson(merged, label);
    }

    private String normalizeJsonOrDefault(String json, String label, String defaultJson)
    {
        return writeCanonicalJson(parseObject(StringUtils.isBlank(json) ? defaultJson : json, label), label);
    }

    private String normalizeReminderPolicy(String json, Integer signDeadlineDays)
    {
        ObjectNode policy = parseObject(StringUtils.isBlank(json) ? "{}" : json, "提醒策略");
        policy.fieldNames().forEachRemaining(field -> {
            if (!REMINDER_POLICY_FIELDS.contains(field))
            {
                throw new ServiceException("提醒策略包含不支持的字段: " + field);
            }
        });
        if (policy.size() == 0)
        {
            return "{}";
        }
        JsonNode configuredDays = policy.get("daysBefore");
        if (configuredDays == null || !configuredDays.isArray()
                || configuredDays.isEmpty() || configuredDays.size() > MAX_REMINDER_OFFSETS)
        {
            throw new ServiceException("提醒策略daysBefore必须包含1至8个天数");
        }
        TreeSet<Integer> normalizedDays = new TreeSet<>();
        for (JsonNode configuredDay : configuredDays)
        {
            if (!configuredDay.isIntegralNumber() || !configuredDay.canConvertToInt())
            {
                throw new ServiceException("提醒策略daysBefore只能使用整数天数");
            }
            int day = configuredDay.intValue();
            if (day < 1 || day > MAX_REMINDER_DAYS)
            {
                throw new ServiceException("提醒策略daysBefore必须在1至30天之间");
            }
            if (signDeadlineDays == null || signDeadlineDays < 1 || day > signDeadlineDays)
            {
                throw new ServiceException("提醒策略daysBefore不能超过签署期限天数");
            }
            if (!normalizedDays.add(day))
            {
                throw new ServiceException("提醒策略daysBefore不能包含重复天数");
            }
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        ArrayNode days = normalized.putArray("daysBefore");
        normalizedDays.forEach(days::add);
        return writeCanonicalJson(normalized, "提醒策略");
    }

    private String normalizePlacementJson(String json, String label, boolean appendedPageAllowed)
    {
        if (StringUtils.isBlank(json))
        {
            return null;
        }
        ObjectNode placement = parseObject(json, label);
        JsonNode mode = placement.get("mode");
        if (mode == null || !mode.isTextual())
        {
            throw new ServiceException(label + "缺少明确mode");
        }
        if ("APPENDED_CONFIRMATION_PAGE".equals(mode.textValue()))
        {
            if (!appendedPageAllowed)
            {
                throw new ServiceException(label + "不支持追加确认页策略");
            }
            rejectUnknownPlacementFields(placement, APPENDED_PLACEMENT_FIELDS, label);
            return APPENDED_CONFIRMATION_PAGE;
        }
        if ("LAST_PAGE".equals(mode.textValue()))
        {
            rejectUnknownPlacementFields(placement, LAST_PAGE_PLACEMENT_FIELDS, label);
            double x = requiredNumericField(placement, "x", label).doubleValue();
            double y = requiredNumericField(placement, "y", label).doubleValue();
            double width = requiredNumericField(placement, "width", label).doubleValue();
            double height = requiredNumericField(placement, "height", label).doubleValue();
            validatePlacementCoordinates(x, y, width, height, label);
            return writeCanonicalJson(placement, label);
        }
        if (!"PLACED".equals(mode.textValue()))
        {
            throw new ServiceException(label + "的mode不支持");
        }
        rejectUnknownPlacementFields(placement, PLACED_PLACEMENT_FIELDS, label);
        int pageNumber = requiredNumericField(placement, "pageNumber", label).intValue();
        double x = requiredNumericField(placement, "x", label).doubleValue();
        double y = requiredNumericField(placement, "y", label).doubleValue();
        double width = requiredNumericField(placement, "width", label).doubleValue();
        double height = requiredNumericField(placement, "height", label).doubleValue();
        if (pageNumber < 1)
        {
            throw new ServiceException(label + "的pageNumber必须大于等于1");
        }
        validatePlacementCoordinates(x, y, width, height, label);
        return writeCanonicalJson(placement, label);
    }

    private void validatePlacementCoordinates(double x, double y, double width, double height,
            String label)
    {
        if (!isRepresentableFiniteFloat(x, false) || !isRepresentableFiniteFloat(y, false))
        {
            throw new ServiceException(label + "的x和y必须是可安全转换为float的非负有限数值");
        }
        if (!isRepresentableFiniteFloat(width, true)
                || !isRepresentableFiniteFloat(height, true))
        {
            throw new ServiceException(label
                    + "的width和height必须是可安全转换为float的大于0有限数值");
        }
    }

    private boolean isRepresentableFiniteFloat(double value, boolean positive)
    {
        float converted = (float) value;
        if (!Double.isFinite(value) || !Float.isFinite(converted)
                || (value != 0D && converted == 0F))
        {
            return false;
        }
        return positive ? converted > 0 : converted >= 0;
    }

    private void rejectUnknownPlacementFields(ObjectNode placement, Set<String> allowed, String label)
    {
        placement.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field))
            {
                throw new ServiceException(label + "包含未知字段：" + field);
            }
        });
    }

    private Number requiredNumericField(ObjectNode placement, String field, String label)
    {
        JsonNode value = placement.get(field);
        if (value == null || !value.isNumber())
        {
            throw new ServiceException(label + "缺少有效数值字段" + field);
        }
        if ("pageNumber".equals(field) && !value.isIntegralNumber())
        {
            throw new ServiceException(label + "的pageNumber必须是整数");
        }
        return value.numberValue();
    }

    private ObjectNode parseObject(String json, String label)
    {
        if (StringUtils.isBlank(json))
        {
            return objectMapper.createObjectNode();
        }
        try
        {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject())
            {
                throw new ServiceException(label + "必须是JSON对象");
            }
            return (ObjectNode) sortJson(node);
        }
        catch (JsonProcessingException e)
        {
            throw new ServiceException(label + "不是合法JSON").setDetailMessage(e.getMessage());
        }
    }

    private JsonNode sortJson(JsonNode node)
    {
        if (node.isObject())
        {
            ObjectNode sorted = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> sorted.set(key, sortJson(value)));
            return sorted;
        }
        if (node.isArray())
        {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(value -> sorted.add(sortJson(value)));
            return sorted;
        }
        return node.deepCopy();
    }

    private String writeCanonicalJson(JsonNode node, String label)
    {
        try
        {
            return objectMapper.writer()
                    .with(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
                    .writeValueAsString(sortJson(node));
        }
        catch (JsonProcessingException e)
        {
            throw new ServiceException(label + "规范化失败").setDetailMessage(e.getMessage());
        }
    }

    private boolean hasSigningStrategy(OaSignPlanVersionTemplate template)
    {
        return "Y".equals(template.getEmployeeSignRequired())
                || "Y".equals(template.getCompanySealRequired());
    }

    private String normalizeExplicitYesNo(String value, String label)
    {
        if (StringUtils.isBlank(value))
        {
            throw new ServiceException(label + "必须显式配置为Y或N");
        }
        return normalizeYesNo(value, null, label);
    }

    private String normalizeYesNo(String value, String defaultValue, String label)
    {
        String normalized = StringUtils.isBlank(value) ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
        if (!"Y".equals(normalized) && !"N".equals(normalized))
        {
            throw new ServiceException(label + "必须为Y或N");
        }
        return normalized;
    }

    private Integer resolveSortOrder(Integer bindingSortOrder, Integer templateSortOrder)
    {
        if (bindingSortOrder != null)
        {
            return bindingSortOrder;
        }
        return templateSortOrder == null ? DEFAULT_SORT_ORDER : templateSortOrder;
    }

    private OaSignPlanVersion requireScopedVersion(Long versionId, Long selectedShopDeptId)
    {
        OaSignPlanVersion version = versionMapper.selectPlanVersionById(versionId);
        if (version == null || !OaSignPlanScope.isGlobal(version.getShopDeptId()))
        {
            throw new ServiceException("签约方案版本不存在或无权限访问");
        }
        return version;
    }

    private OaSignPlanVersion requireScopedLockedVersion(Long versionId, Long selectedShopDeptId)
    {
        OaSignPlanVersion version = versionMapper.lockPlanVersionById(versionId);
        if (version == null || !OaSignPlanScope.isGlobal(version.getShopDeptId()))
        {
            throw new ServiceException("签约方案版本不存在或无权限访问");
        }
        return version;
    }

    private List<OaSignPlanVersionTemplate> nonNullTemplates(List<OaSignPlanVersionTemplate> templates)
    {
        return templates == null ? new ArrayList<>() : templates;
    }

    private String normalizeRequiredText(String value, String message)
    {
        String normalized = normalizeText(value);
        if (normalized == null)
        {
            throw new ServiceException(message);
        }
        return normalized;
    }

    private String normalizeText(String value)
    {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value)
    {
        if (value instanceof String text)
        {
            String normalized = normalizeText(text);
            if (normalized != null)
            {
                target.put(key, normalized);
            }
        }
        else if (value instanceof BigDecimal decimal)
        {
            target.put(key, new BigDecimal(decimal.stripTrailingZeros().toPlainString()));
        }
        else if (value != null)
        {
            target.put(key, value);
        }
    }
}
