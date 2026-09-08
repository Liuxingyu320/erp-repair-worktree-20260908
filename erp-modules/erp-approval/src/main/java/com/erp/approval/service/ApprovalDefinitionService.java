package com.erp.approval.service;

import static com.erp.approval.constant.ApprovalDefinitionConstants.APPROVAL_UNIQUE_BEST;
import static com.erp.approval.constant.ApprovalDefinitionConstants.MISSING_BLOCK;
import static com.erp.approval.constant.ApprovalDefinitionConstants.MISSING_SKIP_WARN;
import static com.erp.approval.constant.ApprovalDefinitionConstants.SELF_BLOCK;
import static com.erp.approval.constant.ApprovalDefinitionConstants.SELF_SKIP_THROUGH;
import static com.erp.approval.constant.ApprovalDefinitionConstants.STRATEGY_BUSINESS;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.approval.constant.ApprovalBusinessCodes;
import com.erp.approval.constant.ApprovalDefinitionConstants;
import com.erp.approval.domain.ApprovalRule;
import com.erp.approval.domain.ApprovalRuleCondition;
import com.erp.approval.domain.ApprovalRuleVersion;
import com.erp.approval.domain.ApprovalTemplate;
import com.erp.approval.domain.ApprovalVersionNode;
import com.erp.approval.domain.dto.ApprovalCandidateView;
import com.erp.approval.domain.dto.ApprovalNodePreview;
import com.erp.approval.domain.dto.ApprovalPreviewRequest;
import com.erp.approval.domain.dto.ApprovalRoutePreview;
import com.erp.approval.domain.dto.ApprovalRuleDetail;
import com.erp.approval.domain.dto.ApprovalRuleSaveRequest;
import com.erp.approval.domain.dto.ApprovalValidationDetail;
import com.erp.approval.domain.dto.ApprovalVersionSaveRequest;
import com.erp.approval.mapper.ApprovalDefinitionMapper;
import com.erp.approval.mapper.ApprovalTemplateMapper;
import com.erp.approval.support.ApprovalJsonSupport;
import com.erp.common.core.exception.ServiceException;

@Service
public class ApprovalDefinitionService
{
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private final ApprovalTemplateMapper templateMapper;
    private final ApprovalDefinitionMapper definitionMapper;
    private final ApprovalCoverageValidationService validationService;
    private final ApprovalRoutePlanner routePlanner;
    private final ApprovalJsonSupport jsonSupport;

    public ApprovalDefinitionService(ApprovalTemplateMapper templateMapper,
            ApprovalDefinitionMapper definitionMapper,
            ApprovalCoverageValidationService validationService,
            ApprovalRoutePlanner routePlanner, ApprovalJsonSupport jsonSupport)
    {
        this.templateMapper = templateMapper;
        this.definitionMapper = definitionMapper;
        this.validationService = validationService;
        this.routePlanner = routePlanner;
        this.jsonSupport = jsonSupport;
    }

    public List<ApprovalTemplate> listTemplates(ApprovalTemplate filter)
    {
        return templateMapper.selectTemplateList(filter == null
                ? new ApprovalTemplate() : filter);
    }

    public Map<String, Object> getTemplate(Long templateId)
    {
        ApprovalTemplate template = requireTemplate(templateId);
        ApprovalRule filter = new ApprovalRule();
        filter.setTemplateId(templateId);
        return Map.of("template", template,
                "rules", definitionMapper.selectRuleList(filter));
    }

    public List<ApprovalRule> listRules(ApprovalRule filter)
    {
        return definitionMapper.selectRuleList(filter == null
                ? new ApprovalRule() : filter);
    }

    public ApprovalRuleDetail getRule(Long ruleId)
    {
        ApprovalRule rule = requireRule(ruleId);
        ApprovalRuleDetail detail = new ApprovalRuleDetail();
        detail.setTemplate(requireTemplate(rule.getTemplateId()));
        detail.setRule(rule);
        List<ApprovalRuleVersion> versions = definitionMapper
                .selectVersionsByRuleId(ruleId);
        for (ApprovalRuleVersion version : versions)
        {
            populateVersion(version);
        }
        detail.setVersions(versions);
        return detail;
    }

    @Transactional
    public ApprovalRuleDetail createRule(ApprovalRuleSaveRequest request,
            String operator)
    {
        ApprovalTemplate template = requireTemplate(request.getTemplateId());
        ApprovalRule rule = mapRule(request);
        validateSelector(rule);
        rule.setRuleStatus(ApprovalDefinitionConstants.STATUS_DRAFT);
        rule.setLatestVersionNo(0);
        rule.setCreateBy(operator);
        try
        {
            definitionMapper.insertRule(rule);
        }
        catch (DuplicateKeyException exception)
        {
            throw new ServiceException("审批规则编码已存在");
        }
        createDraftInternal(rule, null, template, operator);
        return getRule(rule.getRuleId());
    }

    @Transactional
    public ApprovalRuleDetail updateRule(Long ruleId,
            ApprovalRuleSaveRequest request, String operator)
    {
        ApprovalRule rule = requireRule(ruleId);
        if (ApprovalDefinitionConstants.STATUS_ACTIVE.equals(rule.getRuleStatus()))
        {
            throw new ServiceException("已发布规则的适用范围不可直接修改，请新建规则");
        }
        if (request.getExpectedLockVersion() == null)
        {
            throw new ServiceException("缺少规则乐观锁版本");
        }
        rule.setRuleName(request.getRuleName());
        rule.setScopeType(request.getScopeType());
        rule.setScopeId(request.getScopeId());
        rule.setScopeName(request.getScopeName());
        rule.setBusinessSubtype(ApprovalRuleMatchService.normalizeSubtype(
                request.getBusinessSubtype()));
        rule.setRemark(request.getRemark());
        rule.setUpdateBy(operator);
        validateSelector(rule);
        if (definitionMapper.updateRuleWithLock(rule,
                request.getExpectedLockVersion()) != 1)
        {
            throw concurrent();
        }
        return getRule(ruleId);
    }

    @Transactional
    public ApprovalRuleVersion createDraft(Long ruleId, Long sourceVersionId,
            String operator)
    {
        ApprovalRule rule = requireRule(ruleId);
        if (definitionMapper.selectDraftVersionByRuleId(ruleId) != null)
        {
            throw new ServiceException("当前规则已有未发布草稿");
        }
        ApprovalTemplate template = requireTemplate(rule.getTemplateId());
        ApprovalRuleVersion source = sourceVersionId == null
                ? (rule.getCurrentVersionId() == null ? null
                        : definitionMapper.selectRuleVersionById(
                                rule.getCurrentVersionId()))
                : definitionMapper.selectRuleVersionById(sourceVersionId);
        if (source != null && !Objects.equals(source.getRuleId(), ruleId))
        {
            throw new ServiceException("复制源版本不属于当前规则");
        }
        return createDraftInternal(rule, source, template, operator);
    }

    @Transactional
    public ApprovalRuleVersion saveDraft(Long versionId,
            ApprovalVersionSaveRequest request, String operator)
    {
        ApprovalRuleVersion version = requireVersion(versionId);
        if (!ApprovalDefinitionConstants.VERSION_DRAFT.equals(
                version.getVersionStatus()))
        {
            throw new ServiceException("已发布版本不可修改，请复制新草稿");
        }
        ApprovalRule rule = requireRule(version.getRuleId());
        ApprovalTemplate template = requireTemplate(rule.getTemplateId());
        List<ApprovalRuleCondition> conditions = copyConditions(
                request.getConditions(), versionId, operator);
        List<ApprovalVersionNode> nodes = copyNodes(request.getNodes(),
                versionId, operator);
        if (ApprovalBusinessCodes.INV_TRANSFER.equals(template.getBusinessCode()))
        {
            requireTransferShape(nodes);
        }
        definitionMapper.deleteConditionsByVersionId(versionId);
        definitionMapper.deleteNodesByVersionId(versionId);
        for (ApprovalRuleCondition condition : conditions)
        {
            definitionMapper.insertCondition(condition);
        }
        for (ApprovalVersionNode node : nodes)
        {
            definitionMapper.insertNode(node);
        }
        version.setConditions(conditions);
        version.setNodes(nodes);
        version.setDefinitionSnapshot(snapshot(rule, version));
        version.setDefinitionChecksum(null);
        version.setRemark(request.getRemark());
        version.setUpdateBy(operator);
        if (definitionMapper.updateDraftVersionWithLock(version,
                request.getExpectedLockVersion()) != 1)
        {
            throw concurrent();
        }
        return requireVersionPopulated(versionId);
    }

    @Transactional
    public ApprovalRuleVersion publish(Long versionId, Long expectedLockVersion,
            String remark, Long operatorId, String operator)
    {
        ApprovalRuleVersion version = requireVersionPopulated(versionId);
        if (!ApprovalDefinitionConstants.VERSION_DRAFT.equals(
                version.getVersionStatus()))
        {
            throw new ServiceException("只能发布草稿版本");
        }
        ApprovalRule rule = requireRule(version.getRuleId());
        ApprovalTemplate template = templateMapper.selectTemplateByIdForUpdate(
                rule.getTemplateId());
        if (template == null)
        {
            throw new ServiceException("审批模板不存在");
        }
        if (ApprovalBusinessCodes.INV_TRANSFER.equals(template.getBusinessCode()))
        {
            requireTransferShape(version.getNodes());
        }
        ApprovalValidationDetail validation = validationService.validate(versionId,
                "PUBLISH", operatorId, operator);
        if (!"PASSED".equals(validation.run().getRunStatus()))
        {
            throw new ServiceException("发布前配置检查未通过，请先修复错误");
        }
        String snapshot = snapshot(rule, version);
        version.setDefinitionSnapshot(snapshot);
        version.setDefinitionChecksum(jsonSupport.sha256(snapshot));
        version.setPublishedByUserId(operatorId);
        version.setPublishedByName(operator);
        version.setPublishedTime(new Date());
        version.setRemark(remark);
        version.setUpdateBy(operator);
        if (definitionMapper.publishDraftVersionWithLock(version,
                expectedLockVersion) != 1)
        {
            throw concurrent();
        }
        definitionMapper.retirePublishedVersions(rule.getRuleId(), versionId,
                operator);
        Long ruleLock = rule.getLockVersion();
        rule.setCurrentVersionId(versionId);
        rule.setRuleStatus(ApprovalDefinitionConstants.STATUS_ACTIVE);
        rule.setUpdateBy(operator);
        if (definitionMapper.updateRuleWithLock(rule, ruleLock) != 1)
        {
            throw concurrent();
        }
        if (!ApprovalDefinitionConstants.STATUS_ACTIVE.equals(
                template.getTemplateStatus()))
        {
            Long templateLock = template.getLockVersion();
            template.setTemplateStatus(ApprovalDefinitionConstants.STATUS_ACTIVE);
            template.setUpdateBy(operator);
            if (templateMapper.updateTemplateWithLock(template, templateLock) != 1)
            {
                throw concurrent();
            }
        }
        return requireVersionPopulated(versionId);
    }

    @Transactional
    public void disableRule(Long ruleId, Long expectedLockVersion,
            String remark, String operator)
    {
        if (remark == null || remark.isBlank())
        {
            throw new ServiceException("停用原因不能为空");
        }
        if (remark.trim().length() > 500)
        {
            throw new ServiceException("停用原因不能超过500个字符");
        }
        ApprovalRule rule = requireRule(ruleId);
        rule.setRuleStatus(ApprovalDefinitionConstants.STATUS_DISABLED);
        rule.setRemark(remark.trim());
        rule.setUpdateBy(operator);
        if (definitionMapper.updateRuleWithLock(rule, expectedLockVersion) != 1)
        {
            throw concurrent();
        }
    }

    public ApprovalRoutePreview preview(Long versionId,
            ApprovalPreviewRequest request)
    {
        ApprovalRuleVersion version = requireVersionPopulated(versionId);
        ApprovalRule rule = requireRule(version.getRuleId());
        ApprovalTemplate template = requireTemplate(rule.getTemplateId());
        Long anchorDeptId = resolveAnchor(template.getBusinessCode(), request);
        ApprovalRoutePlan plan = routePlanner.plan(template, rule, version,
                anchorDeptId, request.getApplicantId(),
                ApprovalRuleMatchService.normalizeSubtype(
                        request.getBusinessSubtype()), request.getVariables());
        List<ApprovalNodePreview> nodes = plan.nodes().stream().map(item ->
                new ApprovalNodePreview(item.node().getNodeId(),
                        item.node().getNodeOrder(), item.node().getNodeCode(),
                        item.node().getNodeName(), item.candidates().stream()
                                .map(candidate -> new ApprovalCandidateView(
                                        candidate.userId(), candidate.userName(),
                                        candidate.deptId(), candidate.deptName(),
                                        candidate.postSort(), candidate.sourceType(),
                                        candidate.sourceCode(), candidate.reason()))
                                .toList(), item.skipped(), item.blocking(),
                        item.reason())).toList();
        return new ApprovalRoutePreview(versionId, anchorDeptId,
                plan.skipThroughOrder(), plan.valid(), nodes,
                plan.warnings(), plan.errors());
    }

    private ApprovalRuleVersion createDraftInternal(ApprovalRule rule,
            ApprovalRuleVersion source, ApprovalTemplate template,
            String operator)
    {
        Long expectedLock = rule.getLockVersion();
        int nextVersion = (rule.getLatestVersionNo() == null ? 0
                : rule.getLatestVersionNo()) + 1;
        rule.setLatestVersionNo(nextVersion);
        rule.setUpdateBy(operator);
        if (definitionMapper.updateRuleWithLock(rule, expectedLock) != 1)
        {
            throw concurrent();
        }
        ApprovalRuleVersion draft = new ApprovalRuleVersion();
        draft.setRuleId(rule.getRuleId());
        draft.setVersionNo(nextVersion);
        draft.setVersionStatus(ApprovalDefinitionConstants.VERSION_DRAFT);
        draft.setCreateBy(operator);
        draft.setRemark(source == null ? "新建草稿"
                : "复制版本 " + source.getVersionNo());
        definitionMapper.insertRuleVersion(draft);

        List<ApprovalRuleCondition> conditions = source == null ? List.of()
                : copyConditions(definitionMapper.selectConditionsByVersionId(
                        source.getVersionId()), draft.getVersionId(), operator);
        List<ApprovalVersionNode> nodes;
        if (source != null)
        {
            nodes = copyNodes(definitionMapper.selectNodesByVersionId(
                    source.getVersionId()), draft.getVersionId(), operator);
        }
        else if (ApprovalBusinessCodes.INV_TRANSFER.equals(
                template.getBusinessCode()))
        {
            nodes = transferNodes(draft.getVersionId(), operator);
        }
        else
        {
            nodes = List.of();
        }
        for (ApprovalRuleCondition item : conditions)
        {
            definitionMapper.insertCondition(item);
        }
        for (ApprovalVersionNode item : nodes)
        {
            definitionMapper.insertNode(item);
        }
        draft.setConditions(conditions);
        draft.setNodes(nodes);
        String definition = snapshot(rule, draft);
        draft.setDefinitionSnapshot(definition);
        draft.setUpdateBy(operator);
        if (definitionMapper.updateDraftVersionWithLock(draft, 0L) != 1)
        {
            throw concurrent();
        }
        return requireVersionPopulated(draft.getVersionId());
    }

    private ApprovalRule mapRule(ApprovalRuleSaveRequest request)
    {
        ApprovalRule rule = new ApprovalRule();
        rule.setTemplateId(request.getTemplateId());
        rule.setRuleCode(request.getRuleCode() == null ? null
                : request.getRuleCode().trim().toUpperCase());
        rule.setRuleName(request.getRuleName());
        rule.setScopeType(request.getScopeType());
        rule.setScopeId(request.getScopeId());
        rule.setScopeName(request.getScopeName());
        rule.setBusinessSubtype(ApprovalRuleMatchService.normalizeSubtype(
                request.getBusinessSubtype()));
        rule.setRemark(request.getRemark());
        return rule;
    }

    private void validateSelector(ApprovalRule rule)
    {
        if (rule.getRuleCode() == null || !CODE.matcher(rule.getRuleCode()).matches())
        {
            throw new ServiceException("规则编码必须是大写字母开头的大写字母/数字/下划线");
        }
        if (!List.of(ApprovalDefinitionConstants.SCOPE_ALL,
                ApprovalDefinitionConstants.SCOPE_AREA,
                ApprovalDefinitionConstants.SCOPE_STORE)
                .contains(rule.getScopeType()))
        {
            throw new ServiceException("适用范围类型无效");
        }
        if (ApprovalDefinitionConstants.SCOPE_ALL.equals(rule.getScopeType()))
        {
            rule.setScopeId(null);
            rule.setScopeName(null);
        }
        else if (rule.getScopeId() == null)
        {
            throw new ServiceException("门店/区域规则必须指定组织");
        }
    }

    private Long resolveAnchor(String businessCode, ApprovalPreviewRequest request)
    {
        Long supplied = request.getAnchorDeptId();
        if (!ApprovalBusinessCodes.INV_TRANSFER.equals(businessCode))
        {
            return supplied;
        }
        String type = string(request.getVariables().get("transferType"));
        Long source = longValue(request.getVariables().get("sourceDeptId"));
        Long target = longValue(request.getVariables().get("targetDeptId"));
        Long expected = "store_return".equalsIgnoreCase(type) ? source : target;
        if (expected == null)
        {
            if (supplied == null)
            {
                throw new ServiceException("调拨预览必须提供来源/目标门店及调拨类型");
            }
            return supplied;
        }
        if (supplied != null && !supplied.equals(expected))
        {
            throw new ServiceException("调拨锚点不符合规则：门店返仓取来源门店，其他取目标门店");
        }
        return expected;
    }

    private String snapshot(ApprovalRule rule, ApprovalRuleVersion version)
    {
        Map<String, Object> selector = new LinkedHashMap<>();
        selector.put("ruleCode", rule.getRuleCode());
        selector.put("ruleName", rule.getRuleName());
        selector.put("scopeType", rule.getScopeType());
        selector.put("scopeId", rule.getScopeId());
        selector.put("scopeName", rule.getScopeName());
        selector.put("businessSubtype", rule.getBusinessSubtype());
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("schemaVersion", 1);
        definition.put("versionNo", version.getVersionNo());
        definition.put("selector", selector);
        definition.put("conditions", semanticConditions(
                version.getConditions()));
        definition.put("nodes", semanticNodes(version.getNodes()));
        return jsonSupport.write(definition);
    }

    private List<Map<String, Object>> semanticConditions(
            List<ApprovalRuleCondition> conditions)
    {
        if (conditions == null)
        {
            return List.of();
        }
        return conditions.stream().map(item ->
        {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("conditionOrder", item.getConditionOrder());
            value.put("fieldCode", item.getFieldCode());
            value.put("operatorCode", item.getOperatorCode());
            value.put("valueType", item.getValueType());
            value.put("valueText", item.getValueText());
            return value;
        }).toList();
    }

    private List<Map<String, Object>> semanticNodes(
            List<ApprovalVersionNode> nodes)
    {
        if (nodes == null)
        {
            return List.of();
        }
        return nodes.stream().map(item ->
        {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("nodeOrder", item.getNodeOrder());
            value.put("nodeCode", item.getNodeCode());
            value.put("nodeName", item.getNodeName());
            value.put("strategyType", item.getStrategyType());
            value.put("strategyCode", item.getStrategyCode());
            value.put("strategyConfig", semanticStrategyConfig(
                    item.getStrategyConfig()));
            value.put("approvalMode", item.getApprovalMode());
            value.put("requiredCount", item.getRequiredCount());
            value.put("missingPolicy", item.getMissingPolicy());
            value.put("selfPolicy", item.getSelfPolicy());
            value.put("returnAllowed", item.getReturnAllowed());
            value.put("rejectAllowed", item.getRejectAllowed());
            return value;
        }).toList();
    }

    private Object semanticStrategyConfig(String strategyConfig)
    {
        if (strategyConfig == null || strategyConfig.isBlank())
        {
            return Map.of();
        }
        try
        {
            return jsonSupport.readMap(strategyConfig);
        }
        catch (RuntimeException exception)
        {
            throw new ServiceException("审批人策略配置必须是JSON对象")
                    .setDetailMessage(exception.getMessage());
        }
    }

    private List<ApprovalRuleCondition> copyConditions(
            List<ApprovalRuleCondition> source, Long versionId, String operator)
    {
        List<ApprovalRuleCondition> result = new ArrayList<>();
        if (source == null) return result;
        for (ApprovalRuleCondition item : source)
        {
            ApprovalRuleCondition copy = new ApprovalRuleCondition();
            copy.setVersionId(versionId);
            copy.setConditionOrder(item.getConditionOrder());
            copy.setFieldCode(item.getFieldCode());
            copy.setOperatorCode(item.getOperatorCode());
            copy.setValueType(item.getValueType());
            copy.setValueText(item.getValueText());
            copy.setRemark(item.getRemark());
            copy.setCreateBy(operator);
            result.add(copy);
        }
        return result;
    }

    private List<ApprovalVersionNode> copyNodes(List<ApprovalVersionNode> source,
            Long versionId, String operator)
    {
        List<ApprovalVersionNode> result = new ArrayList<>();
        if (source == null) return result;
        for (ApprovalVersionNode item : source)
        {
            ApprovalVersionNode copy = node(versionId, item.getNodeOrder(),
                    item.getNodeCode(), item.getNodeName(), item.getStrategyType(),
                    item.getStrategyCode(), item.getStrategyConfig(),
                    item.getMissingPolicy(), item.getSelfPolicy(), operator);
            copy.setApprovalMode(item.getApprovalMode());
            copy.setRequiredCount(item.getRequiredCount());
            copy.setReturnAllowed(item.getReturnAllowed());
            copy.setRejectAllowed(item.getRejectAllowed());
            copy.setRemark(item.getRemark());
            result.add(copy);
        }
        return result;
    }

    private List<ApprovalVersionNode> transferNodes(Long versionId, String operator)
    {
        return List.of(
                node(versionId, 1, "L4_MANAGER", "四级负责人",
                        STRATEGY_BUSINESS,
                        ApprovalDefinitionConstants.TRANSFER_LEVEL4, "{}",
                        MISSING_SKIP_WARN, SELF_SKIP_THROUGH, operator),
                node(versionId, 2, "L3_MANAGER", "三级负责人",
                        STRATEGY_BUSINESS,
                        ApprovalDefinitionConstants.TRANSFER_LEVEL3, "{}",
                        MISSING_SKIP_WARN, SELF_SKIP_THROUGH, operator),
                node(versionId, 3, "OPERATIONS_DIRECTOR", "运营总监",
                        STRATEGY_BUSINESS,
                        ApprovalDefinitionConstants.TRANSFER_OPERATIONS_DIRECTOR,
                        "{\"postCode\":\"yyzj\"}", MISSING_BLOCK,
                        SELF_SKIP_THROUGH, operator),
                node(versionId, 4, "GENERAL_MANAGER", "总经理",
                        STRATEGY_BUSINESS,
                        ApprovalDefinitionConstants.TRANSFER_GENERAL_MANAGER,
                        "{\"postCode\":\"zjl\",\"applicantForbidden\":true}",
                        MISSING_BLOCK, SELF_BLOCK, operator));
    }

    private ApprovalVersionNode node(Long versionId, int order, String code,
            String name, String strategyType, String strategyCode,
            String config, String missing, String self, String operator)
    {
        ApprovalVersionNode node = new ApprovalVersionNode();
        node.setVersionId(versionId);
        node.setNodeOrder(order);
        node.setNodeCode(code);
        node.setNodeName(name);
        node.setStrategyType(strategyType);
        node.setStrategyCode(strategyCode);
        node.setStrategyConfig(config);
        node.setApprovalMode(APPROVAL_UNIQUE_BEST);
        node.setRequiredCount(1);
        node.setMissingPolicy(missing);
        node.setSelfPolicy(self);
        node.setReturnAllowed("1");
        node.setRejectAllowed("1");
        node.setCreateBy(operator);
        return node;
    }

    private void requireTransferShape(List<ApprovalVersionNode> nodes)
    {
        List<ApprovalVersionNode> expected = transferNodes(-1L, "system");
        if (nodes == null || nodes.size() != expected.size())
        {
            throw new ServiceException("调拨审批必须保留固定四节点");
        }
        for (int index = 0; index < expected.size(); index++)
        {
            ApprovalVersionNode actual = nodes.get(index);
            ApprovalVersionNode fixed = expected.get(index);
            if (actual == null
                    || !Objects.equals(actual.getNodeOrder(), fixed.getNodeOrder())
                    || !Objects.equals(actual.getNodeCode(), fixed.getNodeCode())
                    || !Objects.equals(actual.getStrategyType(), fixed.getStrategyType())
                    || !Objects.equals(actual.getStrategyCode(), fixed.getStrategyCode())
                    || !Objects.equals(actual.getApprovalMode(), fixed.getApprovalMode())
                    || !Objects.equals(actual.getRequiredCount(), fixed.getRequiredCount())
                    || !Objects.equals(actual.getMissingPolicy(), fixed.getMissingPolicy())
                    || !Objects.equals(actual.getSelfPolicy(), fixed.getSelfPolicy())
                    || !Objects.equals(actual.getReturnAllowed(), fixed.getReturnAllowed())
                    || !Objects.equals(actual.getRejectAllowed(), fixed.getRejectAllowed())
                    || !Objects.equals(semanticStrategyConfig(actual.getStrategyConfig()),
                            semanticStrategyConfig(fixed.getStrategyConfig())))
            {
                throw new ServiceException("调拨四节点固定语义不允许修改");
            }
        }
    }

    private ApprovalRuleVersion requireVersionPopulated(Long versionId)
    {
        ApprovalRuleVersion version = requireVersion(versionId);
        populateVersion(version);
        return version;
    }

    private void populateVersion(ApprovalRuleVersion version)
    {
        version.setConditions(definitionMapper.selectConditionsByVersionId(
                version.getVersionId()));
        version.setNodes(definitionMapper.selectNodesByVersionId(
                version.getVersionId()));
    }

    private ApprovalTemplate requireTemplate(Long id)
    {
        ApprovalTemplate value = templateMapper.selectTemplateById(id);
        if (value == null) throw new ServiceException("审批模板不存在");
        return value;
    }

    private ApprovalRule requireRule(Long id)
    {
        ApprovalRule value = definitionMapper.selectRuleById(id);
        if (value == null) throw new ServiceException("审批规则不存在");
        return value;
    }

    private ApprovalRuleVersion requireVersion(Long id)
    {
        ApprovalRuleVersion value = definitionMapper.selectRuleVersionById(id);
        if (value == null) throw new ServiceException("审批版本不存在");
        return value;
    }

    private static ServiceException concurrent()
    {
        return new ServiceException("数据已被其他操作修改，请刷新后重试");
    }

    private static String string(Object value)
    {
        return value == null ? null : value.toString();
    }

    private static Long longValue(Object value)
    {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.valueOf(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }
}
