package com.erp.approval.service;

import static com.erp.approval.constant.ApprovalDefinitionConstants.APPROVAL_ALL;
import static com.erp.approval.constant.ApprovalDefinitionConstants.APPROVAL_ANY_ONE;
import static com.erp.approval.constant.ApprovalDefinitionConstants.APPROVAL_UNIQUE_BEST;
import static com.erp.approval.constant.ApprovalDefinitionConstants.MISSING_BLOCK;
import static com.erp.approval.constant.ApprovalDefinitionConstants.MISSING_SKIP_WARN;
import static com.erp.approval.constant.ApprovalDefinitionConstants.SELF_ALLOW;
import static com.erp.approval.constant.ApprovalDefinitionConstants.SELF_BLOCK;
import static com.erp.approval.constant.ApprovalDefinitionConstants.SELF_SKIP;
import static com.erp.approval.constant.ApprovalDefinitionConstants.SELF_SKIP_THROUGH;
import static com.erp.approval.constant.ApprovalDefinitionConstants.SELF_SKIP_THROUGH_LEGACY;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.approval.callback.ApprovalBusinessCallbackRegistry;
import com.erp.approval.candidate.ApprovalCandidateResolverRegistry;
import com.erp.approval.candidate.ApprovalDirectoryDept;
import com.erp.approval.constant.ApprovalBusinessCodes;
import com.erp.approval.constant.ApprovalDefinitionConstants;
import com.erp.approval.domain.ApprovalRule;
import com.erp.approval.domain.ApprovalRuleCondition;
import com.erp.approval.domain.ApprovalRuleVersion;
import com.erp.approval.domain.ApprovalTemplate;
import com.erp.approval.domain.ApprovalValidationIssue;
import com.erp.approval.domain.ApprovalValidationRun;
import com.erp.approval.domain.ApprovalVersionNode;
import com.erp.approval.domain.dto.ApprovalValidationDetail;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.mapper.ApprovalDefinitionMapper;
import com.erp.approval.mapper.ApprovalTemplateMapper;
import com.erp.approval.mapper.ApprovalValidationMapper;
import com.erp.approval.support.ApprovalJsonSupport;
import com.erp.common.core.exception.ServiceException;

/** Persists publish/manual coverage checks instead of returning ephemeral warnings. */
@Service
public class ApprovalCoverageValidationService
{
    private static final List<String> TRANSFER_NODE_CODES = List.of(
            "L4_MANAGER", "L3_MANAGER", "OPERATIONS_DIRECTOR",
            "GENERAL_MANAGER");
    private static final List<String> TRANSFER_STRATEGIES = List.of(
            ApprovalDefinitionConstants.TRANSFER_LEVEL4,
            ApprovalDefinitionConstants.TRANSFER_LEVEL3,
            ApprovalDefinitionConstants.TRANSFER_OPERATIONS_DIRECTOR,
            ApprovalDefinitionConstants.TRANSFER_GENERAL_MANAGER);

    private final ApprovalTemplateMapper templateMapper;
    private final ApprovalDefinitionMapper definitionMapper;
    private final ApprovalValidationMapper validationMapper;
    private final ApprovalCandidateDirectoryMapper directoryMapper;
    private final ApprovalCandidateResolverRegistry resolverRegistry;
    private final ApprovalBusinessCallbackRegistry callbackRegistry;
    private final ApprovalRuleMatchService ruleMatchService;
    private final ApprovalRoutePlanner routePlanner;
    private final ApprovalJsonSupport jsonSupport;

    public ApprovalCoverageValidationService(ApprovalTemplateMapper templateMapper,
            ApprovalDefinitionMapper definitionMapper,
            ApprovalValidationMapper validationMapper,
            ApprovalCandidateDirectoryMapper directoryMapper,
            ApprovalCandidateResolverRegistry resolverRegistry,
            ApprovalBusinessCallbackRegistry callbackRegistry,
            ApprovalRuleMatchService ruleMatchService,
            ApprovalRoutePlanner routePlanner, ApprovalJsonSupport jsonSupport)
    {
        this.templateMapper = templateMapper;
        this.definitionMapper = definitionMapper;
        this.validationMapper = validationMapper;
        this.directoryMapper = directoryMapper;
        this.resolverRegistry = resolverRegistry;
        this.callbackRegistry = callbackRegistry;
        this.ruleMatchService = ruleMatchService;
        this.routePlanner = routePlanner;
        this.jsonSupport = jsonSupport;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApprovalValidationDetail validate(Long versionId, String type,
            Long operatorId, String operatorName)
    {
        ApprovalRuleVersion version = requireVersion(versionId);
        ApprovalRule rule = requireRule(version.getRuleId());
        ApprovalTemplate template = requireTemplate(rule.getTemplateId());
        version.setConditions(definitionMapper.selectConditionsByVersionId(versionId));
        version.setNodes(definitionMapper.selectNodesByVersionId(versionId));

        ApprovalValidationRun run = new ApprovalValidationRun();
        run.setTemplateId(template.getTemplateId());
        run.setRuleId(rule.getRuleId());
        run.setRuleVersionId(versionId);
        run.setValidationType(type == null || type.isBlank() ? "MANUAL" : type);
        run.setRunStatus("RUNNING");
        run.setStartedByUserId(operatorId);
        run.setStartedByName(operatorName);
        run.setStartedTime(new Date());
        Map<String, Object> scopeSnapshot = new LinkedHashMap<>();
        scopeSnapshot.put("scopeType", rule.getScopeType());
        scopeSnapshot.put("scopeId", rule.getScopeId());
        scopeSnapshot.put("businessSubtype",
                ApprovalRuleMatchService.normalizeSubtype(
                        rule.getBusinessSubtype()));
        run.setScopeSnapshot(jsonSupport.write(scopeSnapshot));
        run.setCreateBy(operatorName);
        validationMapper.insertValidationRun(run);

        List<ApprovalValidationIssue> issues = new ArrayList<>();
        validateDefinition(template, rule, version, issues);
        validateCoverage(template, rule, version, issues);
        for (ApprovalValidationIssue issue : issues)
        {
            issue.setRunId(run.getRunId());
            issue.setTemplateId(template.getTemplateId());
            issue.setRuleId(rule.getRuleId());
            issue.setRuleVersionId(versionId);
            issue.setCreateBy(operatorName);
            validationMapper.insertValidationIssue(issue);
        }
        int errors = (int) issues.stream()
                .filter(item -> "ERROR".equals(item.getSeverity())).count();
        int warnings = issues.size() - errors;
        run.setTotalCount(issues.size());
        run.setErrorCount(errors);
        run.setWarningCount(warnings);
        run.setRunStatus(errors == 0 ? "PASSED" : "FAILED");
        run.setFinishedTime(new Date());
        run.setUpdateBy(operatorName);
        validationMapper.updateValidationRunResult(run);
        return new ApprovalValidationDetail(run, issues);
    }

    public ApprovalValidationDetail getDetail(Long runId)
    {
        ApprovalValidationRun run = validationMapper.selectValidationRunById(runId);
        if (run == null)
        {
            throw new ServiceException("配置检查批次不存在");
        }
        return new ApprovalValidationDetail(run,
                validationMapper.selectIssuesByRunId(runId));
    }

    private void validateDefinition(ApprovalTemplate template, ApprovalRule rule,
            ApprovalRuleVersion version, List<ApprovalValidationIssue> issues)
    {
        List<ApprovalRule> conflicts = ruleMatchService
                .samePrecisionConflicts(rule, version.getConditions());
        if (!conflicts.isEmpty())
        {
            issue(issues, "ERROR", "RULE_SAME_PRECISION_CONFLICT", rule,
                    null, "存在同业务、同范围、同子类型的已发布规则: "
                            + String.join(",", conflicts.stream()
                                    .map(ApprovalRule::getRuleCode).toList()),
                    "停用冲突规则或调整适用范围");
        }
        if (!callbackRegistry.contains(template.getBusinessCode()))
        {
            issue(issues, "ERROR", "CALLBACK_ADAPTER_MISSING", rule, null,
                    "未注册业务回调适配器: " + template.getBusinessCode(),
                    "部署业务回调适配器后重新发布");
        }

        Set<Integer> conditionOrders = new HashSet<>();
        boolean conditionsValid = true;
        for (ApprovalRuleCondition condition : safe(version.getConditions()))
        {
            if (condition.getConditionOrder() == null
                    || !conditionOrders.add(condition.getConditionOrder()))
            {
                issue(issues, "ERROR", "CONDITION_ORDER_INVALID", rule, null,
                        "条件顺序为空或重复", "重新编排条件顺序");
            }
            try
            {
                ruleMatchService.validateCondition(condition);
            }
            catch (RuntimeException exception)
            {
                conditionsValid = false;
                issue(issues, "ERROR", "CONDITION_INVALID", rule, null,
                        exception.getMessage(), "仅使用白名单字段和操作符");
            }
        }
        if (conditionsValid && !ruleMatchService.conditionsSatisfiable(
                version.getConditions()))
        {
            issue(issues, "ERROR", "CONDITION_NEVER_MATCHES", rule, null,
                    "审批条件互相矛盾，该规则永远无法命中",
                    "调整同一字段的等值/范围条件");
        }

        List<ApprovalVersionNode> nodes = safe(version.getNodes()).stream()
                .sorted(Comparator.comparing(item -> item.getNodeOrder() == null
                        ? Integer.MAX_VALUE : item.getNodeOrder())).toList();
        if (nodes.isEmpty())
        {
            issue(issues, "ERROR", "NODE_EMPTY", rule, null,
                    "审批版本没有节点", "至少配置一个审批节点");
            return;
        }
        Set<String> nodeCodes = new HashSet<>();
        for (int index = 0; index < nodes.size(); index++)
        {
            ApprovalVersionNode node = nodes.get(index);
            if (node.getNodeOrder() == null || node.getNodeOrder() != index + 1)
            {
                issue(issues, "ERROR", "NODE_ORDER_INVALID", rule, node,
                        "节点顺序必须从1连续递增", "重新编排节点");
            }
            if (node.getNodeCode() == null || !nodeCodes.add(node.getNodeCode()))
            {
                issue(issues, "ERROR", "NODE_CODE_DUPLICATE", rule, node,
                        "节点编码为空或重复", "配置唯一节点编码");
            }
            if (!List.of(APPROVAL_UNIQUE_BEST, APPROVAL_ANY_ONE, APPROVAL_ALL)
                    .contains(node.getApprovalMode()))
            {
                issue(issues, "ERROR", "APPROVAL_MODE_INVALID", rule, node,
                        "多人处理模式无效", "使用受控多人模式");
            }
            if ((APPROVAL_UNIQUE_BEST.equals(node.getApprovalMode())
                    || APPROVAL_ANY_ONE.equals(node.getApprovalMode()))
                    && !Integer.valueOf(1).equals(node.getRequiredCount()))
            {
                issue(issues, "ERROR", "REQUIRED_COUNT_INVALID", rule, node,
                        "唯一最优/任一通过的所需人数必须为1",
                        "将requiredCount设为1");
            }
            if (APPROVAL_ALL.equals(node.getApprovalMode())
                    && (node.getRequiredCount() == null
                            || node.getRequiredCount() < 1))
            {
                issue(issues, "ERROR", "REQUIRED_COUNT_INVALID", rule, node,
                        "所有人通过的所需人数必须大于0",
                        "将requiredCount设为正整数");
            }
            if (!List.of(MISSING_BLOCK, MISSING_SKIP_WARN)
                    .contains(node.getMissingPolicy()))
            {
                issue(issues, "ERROR", "MISSING_POLICY_INVALID", rule, node,
                        "缺人策略无效", "使用BLOCK或SKIP_WARN");
            }
            if (!List.of(SELF_BLOCK, SELF_SKIP, SELF_ALLOW,
                    SELF_SKIP_THROUGH, SELF_SKIP_THROUGH_LEGACY)
                    .contains(node.getSelfPolicy()))
            {
                issue(issues, "ERROR", "SELF_POLICY_INVALID", rule, node,
                        "自审策略无效", "使用模板允许的预置策略");
            }
            if (!List.of("0", "1").contains(node.getReturnAllowed())
                    || !List.of("0", "1").contains(node.getRejectAllowed()))
            {
                issue(issues, "ERROR", "NODE_ACTION_POLICY_INVALID", rule,
                        node, "退回/拒绝开关必须为0或1",
                        "使用布尔开关值0或1");
            }
            if (!resolverRegistry.supports(node))
            {
                issue(issues, "ERROR", "CANDIDATE_RESOLVER_MISSING", rule, node,
                        "审批人解析器未注册或冲突", "检查strategyType/strategyCode");
            }
        }
        if (ApprovalBusinessCodes.INV_TRANSFER.equals(template.getBusinessCode()))
        {
            validateTransferTopology(rule, nodes, issues);
        }
    }

    private void validateTransferTopology(ApprovalRule rule,
            List<ApprovalVersionNode> nodes,
            List<ApprovalValidationIssue> issues)
    {
        if (nodes.size() != 4)
        {
            issue(issues, "ERROR", "TRANSFER_TOPOLOGY_FIXED", rule, null,
                    "调拨审批必须保留固定四级节点", "恢复内置四节点模板");
            return;
        }
        for (int index = 0; index < 4; index++)
        {
            ApprovalVersionNode node = nodes.get(index);
            if (!TRANSFER_NODE_CODES.get(index).equals(node.getNodeCode())
                    || !TRANSFER_STRATEGIES.get(index).equals(
                            node.getStrategyCode()))
            {
                issue(issues, "ERROR", "TRANSFER_NODE_FIXED", rule, node,
                        "调拨节点编码、策略或顺序被修改",
                        "恢复四级、三级、运营总监、总经理顺序");
            }
            boolean executive = index >= 2;
            String expectedMissing = executive ? MISSING_BLOCK : MISSING_SKIP_WARN;
            if (!expectedMissing.equals(node.getMissingPolicy()))
            {
                issue(issues, "ERROR", "TRANSFER_MISSING_POLICY_FIXED", rule,
                        node, executive ? "高层节点缺人必须阻断"
                                : "四级/三级缺人应跳过并告警",
                        "恢复内置缺人策略");
            }
            String expectedSelf = index == 3 ? SELF_BLOCK : SELF_SKIP_THROUGH;
            if (!expectedSelf.equals(node.getSelfPolicy())
                    && !(index < 3 && SELF_SKIP_THROUGH_LEGACY.equals(
                            node.getSelfPolicy())))
            {
                issue(issues, "ERROR", "TRANSFER_SELF_POLICY_FIXED", rule, node,
                        index == 3 ? "总经理不能发起调拨"
                                : "提交人命中层级时应跳过该层及以下",
                        "恢复内置自审策略");
            }
            if (!APPROVAL_UNIQUE_BEST.equals(node.getApprovalMode()))
            {
                issue(issues, "ERROR", "TRANSFER_APPROVAL_MODE_FIXED", rule,
                        node, "调拨四级节点必须使用唯一最优人",
                        "恢复UNIQUE_BEST");
            }
        }
    }

    private void validateCoverage(ApprovalTemplate template, ApprovalRule rule,
            ApprovalRuleVersion version, List<ApprovalValidationIssue> issues)
    {
        List<ApprovalDirectoryDept> anchors = directoryMapper.selectActiveAnchors(
                rule.getScopeType(), rule.getScopeId(),
                template.getBusinessCode());
        if (anchors.isEmpty())
        {
            issue(issues, "ERROR", "SCOPE_WITHOUT_ACTIVE_ORG", rule, null,
                    "适用范围内没有有效门店/仓库", "检查组织范围配置");
            return;
        }
        validateOverlappingScopes(rule, version.getConditions(), anchors,
                issues);
        for (ApprovalDirectoryDept anchor : anchors)
        {
            ApprovalRoutePlan plan = routePlanner.plan(template, rule, version,
                    anchor.getDeptId(), null, rule.getBusinessSubtype(), Map.of());
            for (String error : plan.errors())
            {
                issue(issues, "ERROR", "CANDIDATE_COVERAGE_MISSING", rule,
                        null, anchor.getDeptName() + ": " + error,
                        "为该组织补齐责任岗位、权限和在职人员", anchor);
            }
            for (String warning : plan.warnings())
            {
                issue(issues, "WARNING", "CANDIDATE_COVERAGE_WARNING", rule,
                        null, anchor.getDeptName() + ": " + warning,
                        "确认动态跳过结果是否符合业务预期", anchor);
            }
        }
    }

    /**
     * Two ancestor areas can both contain the same store while having the same
     * matching precision. Publishing both would make runtime selection
     * ambiguous, even though their scope ids differ.
     */
    private void validateOverlappingScopes(ApprovalRule candidate,
            List<ApprovalRuleCondition> candidateConditions,
            List<ApprovalDirectoryDept> anchors,
            List<ApprovalValidationIssue> issues)
    {
        if (!ApprovalDefinitionConstants.SCOPE_AREA.equals(
                candidate.getScopeType()))
        {
            return;
        }
        String subtype = ApprovalRuleMatchService.normalizeSubtype(
                candidate.getBusinessSubtype());
        Map<Long, ApprovalRule> conflicts = new LinkedHashMap<>();
        Map<Long, Set<String>> affectedAnchors = new LinkedHashMap<>();
        Map<Long, Boolean> conditionOverlap = new LinkedHashMap<>();
        for (ApprovalDirectoryDept anchor : anchors)
        {
            for (ApprovalRule active : definitionMapper
                    .selectActiveRulesForMatch(candidate.getTemplateId(),
                            anchor.getDeptId(), subtype))
            {
                if (Objects.equals(active.getRuleId(),
                            candidate.getRuleId())
                        || !ApprovalDefinitionConstants.SCOPE_AREA.equals(
                                active.getScopeType())
                        || Objects.equals(active.getScopeId(),
                                candidate.getScopeId())
                        || !subtype.equals(ApprovalRuleMatchService
                                .normalizeSubtype(active.getBusinessSubtype()))
                        || !conditionOverlap.computeIfAbsent(active.getRuleId(),
                                ignored -> ruleMatchService.conditionsCanOverlap(
                                        candidateConditions,
                                        definitionMapper
                                                .selectConditionsByVersionId(
                                                        active.getCurrentVersionId()))))
                {
                    continue;
                }
                conflicts.putIfAbsent(active.getRuleId(), active);
                affectedAnchors.computeIfAbsent(active.getRuleId(),
                        ignored -> new LinkedHashSet<>()).add(
                                anchor.getDeptName() == null
                                ? String.valueOf(anchor.getDeptId())
                                : anchor.getDeptName());
            }
        }
        for (Map.Entry<Long, ApprovalRule> entry : conflicts.entrySet())
        {
            String anchorNames = String.join("、",
                    affectedAnchors.getOrDefault(entry.getKey(), Set.of()));
            issue(issues, "ERROR", "RULE_OVERLAPPING_AREA_CONFLICT",
                    candidate, null,
                    "区域范围与已发布规则 " + entry.getValue().getRuleCode()
                            + " 在同一精度下重叠"
                            + (anchorNames.isBlank() ? ""
                                    : "，受影响组织: " + anchorNames),
                    "停用冲突规则，或调整区域范围/业务子类型");
        }
    }

    private void issue(List<ApprovalValidationIssue> issues, String severity,
            String code, ApprovalRule rule, ApprovalVersionNode node,
            String message, String suggestion)
    {
        issue(issues, severity, code, rule, node, message, suggestion, null);
    }

    private void issue(List<ApprovalValidationIssue> issues, String severity,
            String code, ApprovalRule rule, ApprovalVersionNode node,
            String message, String suggestion, ApprovalDirectoryDept anchor)
    {
        ApprovalValidationIssue issue = new ApprovalValidationIssue();
        issue.setSeverity(severity);
        issue.setIssueCode(code);
        issue.setScopeType(rule.getScopeType());
        issue.setScopeId(anchor == null ? rule.getScopeId() : anchor.getDeptId());
        issue.setScopeName(anchor == null ? rule.getScopeName() : anchor.getDeptName());
        issue.setBusinessSubtype(rule.getBusinessSubtype());
        issue.setNodeCode(node == null ? null : node.getNodeCode());
        issue.setNodeName(node == null ? null : node.getNodeName());
        issue.setIssueMessage(message);
        issue.setSuggestion(suggestion);
        issues.add(issue);
    }

    private ApprovalRuleVersion requireVersion(Long versionId)
    {
        ApprovalRuleVersion value = definitionMapper.selectRuleVersionById(versionId);
        if (value == null) throw new ServiceException("审批版本不存在");
        return value;
    }

    private ApprovalRule requireRule(Long ruleId)
    {
        ApprovalRule value = definitionMapper.selectRuleById(ruleId);
        if (value == null) throw new ServiceException("审批规则不存在");
        return value;
    }

    private ApprovalTemplate requireTemplate(Long templateId)
    {
        ApprovalTemplate value = templateMapper.selectTemplateById(templateId);
        if (value == null) throw new ServiceException("审批模板不存在");
        return value;
    }

    private static <T> List<T> safe(List<T> value)
    {
        return value == null ? List.of() : value;
    }
}
