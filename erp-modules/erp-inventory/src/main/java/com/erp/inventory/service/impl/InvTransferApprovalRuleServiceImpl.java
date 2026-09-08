package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvTransferApprovalNodeRoles;
import com.erp.inventory.domain.InvTransferApprovalNode;
import com.erp.inventory.domain.InvTransferApprovalRule;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.dto.InvTransferApprovalRuleValidationRequest;
import com.erp.inventory.domain.vo.InvTransferApprovalRuleValidationResult;
import com.erp.inventory.mapper.InvTransferApprovalNodeMapper;
import com.erp.inventory.mapper.InvTransferApprovalRuleMapper;
import com.erp.inventory.service.IInvTransferApprovalRuleService;

@Service
public class InvTransferApprovalRuleServiceImpl extends InvBaseService implements IInvTransferApprovalRuleService
{
    private static final String STATUS_ENABLED = "0";
    private static final String DOCUMENT_TRANSFER = "transfer";
    private static final String TRANSFER_ALL = "all";
    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_DEPT = "dept";
    private static final String SCOPE_FROM_DEPT = "from_dept";
    private static final String SCOPE_TO_DEPT = "to_dept";
    private static final String SCOPE_REGION = "region";
    private static final String SCOPE_AREA = "area";
    private static final String CONDITION_NONE = "none";
    private static final String CONDITION_QUANTITY = "quantity";
    private static final String CONDITION_AMOUNT = "amount";
    private static final String APPROVAL_QUORUM = "quorum";
    private static final String APPROVAL_ALL_NODES = "all_nodes";
    private static final String APPROVAL_ANY_ONE = "any_one";
    private static final Set<String> SUPPORTED_TRANSFER_TYPES = Set.of(
            TRANSFER_ALL, "warehouse", "cross_store");
    private static final Set<String> SUPPORTED_SCOPE_TYPES = Set.of(
            SCOPE_ALL, SCOPE_DEPT, SCOPE_FROM_DEPT, SCOPE_TO_DEPT, SCOPE_REGION, SCOPE_AREA);
    private static final Set<String> SUPPORTED_OPERATORS = Set.of(">=", ">", "=", "==", "<=", "<");
    @Autowired
    private InvTransferApprovalRuleMapper ruleMapper;

    @Autowired
    private InvTransferApprovalNodeMapper nodeMapper;

    @Autowired
    private TransferApprovalCandidateResolver candidateResolver;

    @Override
    public List<InvTransferApprovalRule> selectRuleList(InvTransferApprovalRule rule, Long selectedShopDeptId)
    {
        List<InvTransferApprovalRule> rules = ruleMapper.selectRuleList(rule);
        List<InvTransferApprovalRule> visibleRules = new ArrayList<>();
        for (InvTransferApprovalRule item : rules)
        {
            if (isRuleScopeVisible(item, selectedShopDeptId))
            {
                visibleRules.add(item);
            }
        }
        return visibleRules;
    }

    @Override
    public InvTransferApprovalRule selectRuleById(Long ruleId, Long selectedShopDeptId)
    {
        InvTransferApprovalRule rule = loadRule(ruleId);
        assertRuleVisible(rule, selectedShopDeptId);
        rule.setNodes(nodeMapper.selectNodesByRuleId(ruleId));
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvTransferApprovalRule saveRule(InvTransferApprovalRule rule, Long selectedShopDeptId)
    {
        if (rule == null)
        {
            throw new ServiceException("审批规则不能为空");
        }
        applyRuleDefaults(rule);
        ensureSystemApprovalNodes(rule);
        if (STATUS_ENABLED.equals(rule.getStatus()) && rule.getValidationTargetDeptId() == null)
        {
            throw new ServiceException("启用审批规则前必须选择目标门店并完成候选人校验");
        }
        if (rule.getRuleId() != null)
        {
            InvTransferApprovalRule existing = loadRule(rule.getRuleId());
            assertRuleVisible(existing, selectedShopDeptId);
            assertExpectedVersion(existing, rule.getVersion());
        }
        assertRuleScopeManageable(rule, selectedShopDeptId);

        InvTransferApprovalRuleValidationRequest validationRequest = new InvTransferApprovalRuleValidationRequest();
        validationRequest.setRule(rule);
        if (rule.getValidationTargetDeptId() != null)
        {
            InvTransferOrder sample = new InvTransferOrder();
            sample.setTransferType(TRANSFER_ALL.equals(rule.getTransferType())
                    ? "cross_store" : rule.getTransferType());
            sample.setToDeptId(rule.getValidationTargetDeptId());
            sample.setTotalQuantity(rule.getConditionValue() == null
                    ? BigDecimal.ZERO : rule.getConditionValue());
            validationRequest.setTransferSample(sample);
        }
        InvTransferApprovalRuleValidationResult validation = validatePreparedRule(
                validationRequest, selectedShopDeptId, false);
        assertValidationAllowsSave(validation, Boolean.TRUE.equals(rule.getWarningAcknowledged()));
        if (rule.getRuleId() == null)
        {
            rule.setCreateBy(SecurityUtils.getUsername());
            rule.setVersion(1);
            if (ruleMapper.insertRule(rule) <= 0)
            {
                throw new ServiceException("审批规则保存失败，请刷新后重试");
            }
        }
        else
        {
            rule.setUpdateBy(SecurityUtils.getUsername());
            if (ruleMapper.updateRule(rule) <= 0)
            {
                throw new ServiceException("审批规则已被删除或修改，请刷新后重试");
            }
            rule.setVersion(rule.getVersion() + 1);
        }
        saveRuleNodes(rule.getRuleId(), rule.getNodes());
        return selectRuleById(rule.getRuleId(), selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteRuleById(Long ruleId, Integer expectedVersion, Long selectedShopDeptId)
    {
        InvTransferApprovalRule existing = loadRule(ruleId);
        assertRuleVisible(existing, selectedShopDeptId);
        assertExpectedVersion(existing, expectedVersion);
        nodeMapper.deleteNodesByRuleId(ruleId);
        int rows = ruleMapper.deleteRuleByIdAndVersion(ruleId, expectedVersion);
        if (rows == 0)
        {
            throw new ServiceException("审批规则已被其他管理员修改，请刷新后重试");
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveNodes(Long ruleId, List<InvTransferApprovalNode> nodes, Long selectedShopDeptId)
    {
        if (ruleId == null)
        {
            throw new ServiceException("审批规则ID不能为空");
        }
        assertRuleVisible(loadRule(ruleId), selectedShopDeptId);
        List<InvTransferApprovalNode> normalizedNodes = ensureSystemApprovalNodes();
        validateNodes(normalizedNodes);
        return saveRuleNodes(ruleId, normalizedNodes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteNodesByRuleId(Long ruleId, Long selectedShopDeptId)
    {
        assertRuleVisible(loadRule(ruleId), selectedShopDeptId);
        throw new ServiceException("固定四级审批节点不能单独删除，请删除整条审批规则");
    }

    @Override
    public InvTransferApprovalRule previewRule(InvTransferOrder transfer, Long selectedShopDeptId)
    {
        validatePreviewTransferScope(transfer, selectedShopDeptId);
        return matchRule(transfer);
    }

    @Override
    public InvTransferApprovalRule matchRule(InvTransferOrder transfer)
    {
        if (transfer == null)
        {
            throw new ServiceException("调拨单不能为空");
        }
        List<InvTransferApprovalRule> rules = ruleMapper.selectEnabledRulesForMatch(transfer);
        if (rules == null || rules.isEmpty())
        {
            return null;
        }
        List<InvTransferApprovalRule> matchedRules = new ArrayList<>();
        for (InvTransferApprovalRule rule : rules)
        {
            if (!matchesRuleBase(rule, transfer))
            {
                continue;
            }
            if (matchesCondition(rule, transfer))
            {
                matchedRules.add(rule);
            }
        }
        if (matchedRules.isEmpty())
        {
            return null;
        }
        matchedRules.sort(Comparator.comparing(InvTransferApprovalRuleServiceImpl::priorityValue)
                .thenComparing(InvTransferApprovalRuleServiceImpl::ruleIdValue));
        InvTransferApprovalRule matched = matchedRules.get(0);
        matched.setNodes(nodeMapper.selectNodesByRuleId(matched.getRuleId()));
        return matched;
    }

    @Override
    public InvTransferApprovalRuleValidationResult validateRule(
            InvTransferApprovalRuleValidationRequest request, Long selectedShopDeptId)
    {
        return validatePreparedRule(request, selectedShopDeptId, true);
    }

    private int saveRuleNodes(Long ruleId, List<InvTransferApprovalNode> nodes)
    {
        nodeMapper.deleteNodesByRuleId(ruleId);
        for (InvTransferApprovalNode node : nodes)
        {
            applyNodeDefaults(node, ruleId);
        }
        return nodeMapper.batchInsertNodes(nodes);
    }

    private InvTransferApprovalRule loadRule(Long ruleId)
    {
        InvTransferApprovalRule rule = ruleMapper.selectRuleById(ruleId);
        if (rule == null)
        {
            throw new ServiceException("审批规则不存在");
        }
        return rule;
    }

    private void assertExpectedVersion(InvTransferApprovalRule existing, Integer expectedVersion)
    {
        if (expectedVersion == null)
        {
            throw new ServiceException("审批规则版本不能为空，请刷新后重试");
        }
        if (!Objects.equals(existing.getVersion(), expectedVersion))
        {
            throw new ServiceException("审批规则版本已变化，当前为 v" + existing.getVersion() + "，请刷新后重试");
        }
    }

    private void assertRuleVisible(InvTransferApprovalRule rule, Long selectedShopDeptId)
    {
        if (!isRuleScopeVisible(rule, selectedShopDeptId))
        {
            throw new ServiceException("当前用户无权访问该审批规则");
        }
    }

    private boolean isRuleScopeVisible(InvTransferApprovalRule rule, Long selectedShopDeptId)
    {
        if (rule == null)
        {
            return false;
        }
        if (isAdminScopeBypass())
        {
            return true;
        }
        Long scopeRoot = resolveAndValidateShopDept(selectedShopDeptId);
        String scopeType = normalizedScopeType(rule);
        if (SCOPE_ALL.equals(scopeType))
        {
            return SecurityUtils.isAdmin();
        }
        return rule.getScopeId() != null && deptInScope(scopeRoot, rule.getScopeId());
    }

    private void assertRuleScopeManageable(InvTransferApprovalRule rule, Long selectedShopDeptId)
    {
        if (isAdminScopeBypass())
        {
            return;
        }
        Long scopeRoot = resolveAndValidateShopDept(selectedShopDeptId);
        String scopeType = normalizedScopeType(rule);
        if (SCOPE_ALL.equals(scopeType))
        {
            if (!SecurityUtils.isAdmin())
            {
                throw new ServiceException("非管理员不能配置全局审批规则");
            }
            return;
        }
        if (rule.getScopeId() == null || !deptInScope(scopeRoot, rule.getScopeId()))
        {
            throw new ServiceException("当前用户无权配置该审批规则范围");
        }
    }

    private void validatePreviewTransferScope(InvTransferOrder transfer, Long selectedShopDeptId)
    {
        if (transfer == null)
        {
            throw new ServiceException("调拨单不能为空");
        }
        if (isAdminScopeBypass())
        {
            return;
        }
        Long scopeRoot = resolveAndValidateShopDept(selectedShopDeptId);
        if (deptInScope(scopeRoot, transfer.getFromDeptId())
                || deptInScope(scopeRoot, transfer.getToDeptId())
                || deptInScope(scopeRoot, transfer.getFromWarehouseId())
                || deptInScope(scopeRoot, transfer.getToWarehouseId()))
        {
            return;
        }
        throw new ServiceException("当前用户无权预览该调拨单审批规则");
    }

    private String normalizedScopeType(InvTransferApprovalRule rule)
    {
        return StringUtils.isEmpty(rule.getScopeType()) ? SCOPE_ALL : rule.getScopeType();
    }

    private boolean isAdminScopeBypass()
    {
        return SecurityUtils.isAdmin();
    }

    private InvTransferApprovalRuleValidationResult validatePreparedRule(
            InvTransferApprovalRuleValidationRequest request, Long selectedShopDeptId,
            boolean includeSimulationDetails)
    {
        if (request == null || request.getRule() == null)
        {
            throw new ServiceException("审批规则不能为空");
        }
        InvTransferApprovalRule rule = request.getRule();
        applyRuleDefaults(rule);
        ensureSystemApprovalNodes(rule);
        if (rule.getRuleId() != null)
        {
            assertRuleVisible(loadRule(rule.getRuleId()), selectedShopDeptId);
        }
        assertRuleScopeManageable(rule, selectedShopDeptId);

        InvTransferOrder sample = request.getTransferSample();
        if (sample != null)
        {
            validatePreviewTransferScope(sample, selectedShopDeptId);
        }

        InvTransferApprovalRuleValidationResult result = new InvTransferApprovalRuleValidationResult();
        List<String> blockingIssues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> overlaps = new ArrayList<>();
        collectBasicIssues(rule, blockingIssues);
        if (blockingIssues.isEmpty() && STATUS_ENABLED.equals(rule.getStatus()))
        {
            collectConflictIssues(rule, selectedShopDeptId, blockingIssues, warnings, overlaps);
        }
        if (sample != null && sample.getToDeptId() != null && blockingIssues.stream()
                .noneMatch(message -> message.contains("审批节点")))
        {
            collectCandidateIssues(rule, sample, result, blockingIssues, warnings);
        }
        if (includeSimulationDetails && sample != null)
        {
            collectSimulationDetails(rule, sample, result);
        }
        else
        {
            result.setRuleId(rule.getRuleId());
            result.setRuleName(rule.getRuleName());
        }
        result.setBlockingIssues(distinctMessages(blockingIssues));
        result.setWarnings(distinctMessages(warnings));
        result.setOverlaps(distinctMessages(overlaps));
        result.setValid(result.getBlockingIssues().isEmpty());
        return result;
    }

    private void assertValidationAllowsSave(InvTransferApprovalRuleValidationResult result,
            boolean warningAcknowledged)
    {
        if (result == null || !result.isValid())
        {
            String message = result == null || result.getBlockingIssues().isEmpty()
                    ? "审批规则校验失败" : String.join("；", result.getBlockingIssues());
            throw new ServiceException(message);
        }
        if (!result.getWarnings().isEmpty() && !warningAcknowledged)
        {
            throw new ServiceException("审批规则存在警告，请确认后重试："
                    + String.join("；", result.getWarnings()));
        }
    }

    private void collectBasicIssues(InvTransferApprovalRule rule, List<String> blockingIssues)
    {
        if (StringUtils.isBlank(rule.getRuleName()))
        {
            blockingIssues.add("规则名称不能为空");
        }
        if (!DOCUMENT_TRANSFER.equals(rule.getDocumentType()))
        {
            blockingIssues.add("当前只支持调拨单审批规则");
        }
        if (!SUPPORTED_TRANSFER_TYPES.contains(rule.getTransferType()))
        {
            blockingIssues.add("调拨类型不受支持");
        }
        if (!SUPPORTED_SCOPE_TYPES.contains(rule.getScopeType()))
        {
            blockingIssues.add("适用范围类型不受支持");
        }
        if (!SCOPE_ALL.equals(rule.getScopeType()) && rule.getScopeId() == null)
        {
            blockingIssues.add("非全局规则必须选择适用范围");
        }
        if (!CONDITION_NONE.equals(rule.getConditionType())
                && !CONDITION_QUANTITY.equals(rule.getConditionType()))
        {
            blockingIssues.add(CONDITION_AMOUNT.equals(rule.getConditionType())
                    ? "调拨金额条件暂未支持" : "条件类型不受支持");
        }
        if (CONDITION_QUANTITY.equals(rule.getConditionType()))
        {
            if (!SUPPORTED_OPERATORS.contains(rule.getConditionOperator()))
            {
                blockingIssues.add("数量条件操作符不受支持");
            }
            if (rule.getConditionValue() == null || rule.getConditionValue().signum() < 0)
            {
                blockingIssues.add("数量条件值必须大于等于0");
            }
            else if ("<".equals(rule.getConditionOperator())
                    && rule.getConditionValue().signum() == 0)
            {
                blockingIssues.add("规则永远匹配不到：调拨数量不能小于0");
            }
        }
        if (rule.getPriority() == null || rule.getPriority() < 1 || rule.getPriority() > 9999)
        {
            blockingIssues.add("优先级必须在1到9999之间，数字越小越优先");
        }
        if (!"0".equals(rule.getStatus()) && !"1".equals(rule.getStatus()))
        {
            blockingIssues.add("规则状态不合法");
        }
        try
        {
            validateNodes(rule.getNodes());
        }
        catch (ServiceException ex)
        {
            blockingIssues.add(ex.getMessage());
        }
    }

    private void collectConflictIssues(InvTransferApprovalRule proposed, Long selectedShopDeptId,
            List<String> blockingIssues, List<String> warnings, List<String> overlaps)
    {
        List<InvTransferApprovalRule> existingRules = ruleMapper.selectRuleList(new InvTransferApprovalRule());
        if (existingRules == null)
        {
            return;
        }
        for (InvTransferApprovalRule existing : existingRules)
        {
            if (existing == null || !STATUS_ENABLED.equals(existing.getStatus())
                    || Objects.equals(existing.getRuleId(), proposed.getRuleId())
                    || !DOCUMENT_TRANSFER.equals(existing.getDocumentType())
                    || !transferTypesOverlap(existing, proposed)
                    || !scopesOverlap(existing, proposed)
                    || !conditionsOverlap(existing, proposed))
            {
                continue;
            }
            String existingLabel = isRuleScopeVisible(existing, selectedShopDeptId)
                    ? safeRuleLabel(existing) : "受限范围中的现有规则";
            String overlap = "与" + existingLabel + "的适用范围和条件重叠";
            overlaps.add(overlap);
            int existingPriority = priorityValue(existing);
            int proposedPriority = priorityValue(proposed);
            if (existingPriority == proposedPriority)
            {
                blockingIssues.add("优先级冲突：" + overlap + "，且优先级均为" + proposedPriority);
                continue;
            }
            if (existingPriority < proposedPriority && ruleCovers(existing, proposed))
            {
                blockingIssues.add("规则永远匹配不到：" + existingLabel + "优先级更高且完整覆盖本规则");
            }
            else if (proposedPriority < existingPriority && ruleCovers(proposed, existing))
            {
                warnings.add("保存后" + existingLabel + "可能永远匹配不到，请确认优先级设计");
            }
            else
            {
                warnings.add(overlap + "，将按优先级" + Math.min(existingPriority, proposedPriority)
                        + "的规则先匹配");
            }
        }
    }

    private void collectCandidateIssues(InvTransferApprovalRule rule, InvTransferOrder sample,
            InvTransferApprovalRuleValidationResult result, List<String> blockingIssues,
            List<String> warnings)
    {
        try
        {
            TransferApprovalCandidateResolver.Resolution resolution = candidateResolver.resolve(sample, rule);
            warnings.addAll(resolution.warnings());
            for (String missingRole : resolution.missingMandatoryRoles())
            {
                blockingIssues.add(missingRoleMessage(missingRole));
            }
            result.setNodes(toValidationNodes(resolution));
        }
        catch (ServiceException ex)
        {
            blockingIssues.add(ex.getMessage());
        }
    }

    private void collectSimulationDetails(InvTransferApprovalRule proposed, InvTransferOrder sample,
            InvTransferApprovalRuleValidationResult result)
    {
        List<String> proposedNonMatchReasons = describeNonMatchReasons(proposed, sample);
        List<InvTransferApprovalRule> candidates = new ArrayList<>();
        List<InvTransferApprovalRule> persisted = ruleMapper.selectRuleList(new InvTransferApprovalRule());
        if (persisted != null)
        {
            for (InvTransferApprovalRule rule : persisted)
            {
                if (rule != null && !Objects.equals(rule.getRuleId(), proposed.getRuleId()))
                {
                    candidates.add(rule);
                }
            }
        }
        candidates.add(proposed);
        candidates.sort(Comparator.comparing(InvTransferApprovalRuleServiceImpl::priorityValue)
                .thenComparing(InvTransferApprovalRuleServiceImpl::ruleIdValue));

        InvTransferApprovalRule matchedRule = null;
        for (InvTransferApprovalRule candidate : candidates)
        {
            try
            {
                if (matchesRuleBase(candidate, sample) && matchesCondition(candidate, sample))
                {
                    matchedRule = candidate;
                    break;
                }
            }
            catch (ServiceException ignored)
            {
                // 已由结构化校验结果说明，不让单条存量坏数据中断模拟。
            }
        }
        result.setMatched(matchedRule != null);
        if (matchedRule == null)
        {
            result.setRuleId(null);
            result.setRuleName(null);
            result.setNonMatchReasons(proposedNonMatchReasons.isEmpty()
                    ? List.of("没有启用规则能够匹配该样例调拨单") : proposedNonMatchReasons);
            return;
        }
        result.setRuleId(matchedRule.getRuleId());
        result.setRuleName(matchedRule.getRuleName());
        List<String> matchReasons = new ArrayList<>();
        matchReasons.add("调拨类型命中：" + matchedRule.getTransferType());
        matchReasons.add("适用范围命中：" + normalizedScopeType(matchedRule));
        matchReasons.add(CONDITION_NONE.equals(matchedRule.getConditionType())
                ? "条件命中：不限制" : "数量条件命中：" + matchedRule.getConditionOperator()
                        + " " + matchedRule.getConditionValue());
        matchReasons.add("最终按优先级" + priorityValue(matchedRule) + "选中");
        result.setMatchReasons(matchReasons);
        if (!Objects.equals(matchedRule.getRuleId(), proposed.getRuleId()) || proposed.getRuleId() == null
                && matchedRule != proposed)
        {
            List<String> reasons = new ArrayList<>(proposedNonMatchReasons);
            if (reasons.isEmpty())
            {
                reasons.add("本规则也命中样例，但规则「" + matchedRule.getRuleName()
                        + "」优先级更高");
            }
            result.setNonMatchReasons(reasons);
            populateMatchedRuleNodes(matchedRule, sample, result);
        }
        else
        {
            result.setNonMatchReasons(proposedNonMatchReasons);
        }
    }

    private void populateMatchedRuleNodes(InvTransferApprovalRule matchedRule, InvTransferOrder sample,
            InvTransferApprovalRuleValidationResult result)
    {
        if (matchedRule.getRuleId() == null || candidateResolver == null)
        {
            return;
        }
        try
        {
            matchedRule.setNodes(nodeMapper.selectNodesByRuleId(matchedRule.getRuleId()));
            result.setNodes(toValidationNodes(candidateResolver.resolve(sample, matchedRule)));
        }
        catch (ServiceException ignored)
        {
            // 匹配结果仍可展示，候选人问题由候选人校验项单独给出。
        }
    }

    private List<String> describeNonMatchReasons(InvTransferApprovalRule rule, InvTransferOrder sample)
    {
        List<String> reasons = new ArrayList<>();
        if (!STATUS_ENABLED.equals(rule.getStatus()))
        {
            reasons.add("本规则已停用");
        }
        if (!DOCUMENT_TRANSFER.equals(rule.getDocumentType()))
        {
            reasons.add("单据类型不是调拨单");
        }
        if (!matchesTransferType(rule, sample))
        {
            reasons.add("样例调拨类型不在本规则范围内");
        }
        if (!matchesScope(rule, sample))
        {
            reasons.add("样例来源/目标组织不在本规则范围内");
        }
        try
        {
            if (!matchesCondition(rule, sample))
            {
                reasons.add("样例数量不满足本规则条件");
            }
        }
        catch (ServiceException ex)
        {
            reasons.add(ex.getMessage());
        }
        return reasons;
    }

    private List<InvTransferApprovalRuleValidationResult.NodeResult> toValidationNodes(
            TransferApprovalCandidateResolver.Resolution resolution)
    {
        List<InvTransferApprovalRuleValidationResult.NodeResult> nodes = new ArrayList<>();
        for (TransferApprovalCandidateResolver.ResolvedNode resolved : resolution.nodes())
        {
            InvTransferApprovalNode source = resolved.node();
            InvTransferApprovalRuleValidationResult.NodeResult node =
                    new InvTransferApprovalRuleValidationResult.NodeResult();
            node.setNodeOrder(source.getNodeOrder());
            node.setNodeName(source.getNodeName());
            node.setNodeRole(source.getNodeRole());
            node.setPostCode(source.getPostCode());
            node.setPostName(source.getPostName());
            node.setCandidateSource(candidateSource(source.getNodeRole()));
            List<InvTransferApprovalRuleValidationResult.Candidate> candidates = new ArrayList<>();
            for (TransferApprovalCandidateResolver.Candidate candidate : resolved.candidates())
            {
                candidates.add(new InvTransferApprovalRuleValidationResult.Candidate(
                        candidate.userId(), candidate.displayName()));
            }
            node.setCandidates(candidates);
            if (candidates.isEmpty())
            {
                node.setStatus(resolved.required() ? "blocked" : "skipped");
                node.setMessage(resolved.required() ? "必审节点没有有效审批人" : "没有候选人，将向上收敛");
            }
            else
            {
                node.setStatus("ready");
                node.setMessage("已解析" + candidates.size() + "名候选人");
            }
            nodes.add(node);
        }
        return nodes;
    }

    private static String candidateSource(String nodeRole)
    {
        if (InvTransferApprovalNodeRoles.LEVEL4_HIGHEST.equals(nodeRole))
        {
            return "目标门店店长，缺失时回退店长助理";
        }
        if (InvTransferApprovalNodeRoles.LEVEL3_HIGHEST.equals(nodeRole))
        {
            return "负责目标门店且处于店长与高层之间的最近岗位档";
        }
        if (InvTransferApprovalNodeRoles.OPERATIONS_DIRECTOR.equals(nodeRole))
        {
            return "运营总监岗位与目标门店负责范围";
        }
        if (InvTransferApprovalNodeRoles.GENERAL_MANAGER.equals(nodeRole))
        {
            return "总经理岗位与目标门店负责范围";
        }
        return "规则节点配置";
    }

    private static String missingRoleMessage(String role)
    {
        if (InvTransferApprovalNodeRoles.OPERATIONS_DIRECTOR.equals(role))
        {
            return "审批人为空：目标门店未配置有效运营总监";
        }
        if (InvTransferApprovalNodeRoles.GENERAL_MANAGER.equals(role))
        {
            return "审批人为空：目标门店未配置有效总经理";
        }
        return "审批人为空：必审节点" + role + "没有有效候选人";
    }

    private static List<String> distinctMessages(List<String> messages)
    {
        return new ArrayList<>(new LinkedHashSet<>(messages));
    }

    private boolean transferTypesOverlap(InvTransferApprovalRule first, InvTransferApprovalRule second)
    {
        return TRANSFER_ALL.equals(first.getTransferType())
                || TRANSFER_ALL.equals(second.getTransferType())
                || Objects.equals(first.getTransferType(), second.getTransferType());
    }

    private boolean scopesOverlap(InvTransferApprovalRule first, InvTransferApprovalRule second)
    {
        String firstType = normalizedScopeType(first);
        String secondType = normalizedScopeType(second);
        if (SCOPE_ALL.equals(firstType) || SCOPE_ALL.equals(secondType))
        {
            return true;
        }
        if ((scopeCanMatchFrom(firstType) && scopeCanMatchTo(secondType))
                || (scopeCanMatchTo(firstType) && scopeCanMatchFrom(secondType)))
        {
            return true;
        }
        return scopeLocationsOverlap(first, second);
    }

    private boolean scopeLocationsOverlap(InvTransferApprovalRule first, InvTransferApprovalRule second)
    {
        if (first.getScopeId() == null || second.getScopeId() == null)
        {
            return false;
        }
        if (Objects.equals(first.getScopeId(), second.getScopeId()))
        {
            return true;
        }
        boolean firstHierarchy = isHierarchyScope(first);
        boolean secondHierarchy = isHierarchyScope(second);
        return firstHierarchy && deptInScope(first.getScopeId(), second.getScopeId())
                || secondHierarchy && deptInScope(second.getScopeId(), first.getScopeId());
    }

    private static boolean scopeCanMatchFrom(String scopeType)
    {
        return SCOPE_DEPT.equals(scopeType) || SCOPE_FROM_DEPT.equals(scopeType)
                || SCOPE_REGION.equals(scopeType) || SCOPE_AREA.equals(scopeType);
    }

    private static boolean scopeCanMatchTo(String scopeType)
    {
        return SCOPE_DEPT.equals(scopeType) || SCOPE_TO_DEPT.equals(scopeType)
                || SCOPE_REGION.equals(scopeType) || SCOPE_AREA.equals(scopeType);
    }

    private static boolean isHierarchyScope(InvTransferApprovalRule rule)
    {
        return SCOPE_REGION.equals(rule.getScopeType()) || SCOPE_AREA.equals(rule.getScopeType());
    }

    private boolean conditionsOverlap(InvTransferApprovalRule first, InvTransferApprovalRule second)
    {
        ConditionRange firstRange = conditionRange(first);
        ConditionRange secondRange = conditionRange(second);
        return firstRange != null && secondRange != null && firstRange.overlaps(secondRange);
    }

    private boolean ruleCovers(InvTransferApprovalRule covering, InvTransferApprovalRule covered)
    {
        ConditionRange coveringRange = conditionRange(covering);
        ConditionRange coveredRange = conditionRange(covered);
        return transferTypeCovers(covering, covered)
                && scopeCovers(covering, covered)
                && coveringRange != null && coveredRange != null
                && coveringRange.covers(coveredRange);
    }

    private static boolean transferTypeCovers(InvTransferApprovalRule covering,
            InvTransferApprovalRule covered)
    {
        return TRANSFER_ALL.equals(covering.getTransferType())
                || Objects.equals(covering.getTransferType(), covered.getTransferType());
    }

    private boolean scopeCovers(InvTransferApprovalRule covering, InvTransferApprovalRule covered)
    {
        String coveringType = normalizedScopeType(covering);
        String coveredType = normalizedScopeType(covered);
        if (SCOPE_ALL.equals(coveringType))
        {
            return true;
        }
        if (SCOPE_ALL.equals(coveredType) || covering.getScopeId() == null || covered.getScopeId() == null)
        {
            return false;
        }
        if (Objects.equals(coveringType, coveredType)
                && Objects.equals(covering.getScopeId(), covered.getScopeId()))
        {
            return true;
        }
        if (SCOPE_DEPT.equals(coveringType)
                && (SCOPE_FROM_DEPT.equals(coveredType) || SCOPE_TO_DEPT.equals(coveredType)))
        {
            return Objects.equals(covering.getScopeId(), covered.getScopeId());
        }
        if (SCOPE_REGION.equals(coveringType) || SCOPE_AREA.equals(coveringType))
        {
            return deptInScope(covering.getScopeId(), covered.getScopeId());
        }
        return false;
    }

    private ConditionRange conditionRange(InvTransferApprovalRule rule)
    {
        if (StringUtils.isEmpty(rule.getConditionType()) || CONDITION_NONE.equals(rule.getConditionType()))
        {
            return new ConditionRange(BigDecimal.ZERO, true, null, false);
        }
        if (!CONDITION_QUANTITY.equals(rule.getConditionType()) || rule.getConditionValue() == null
                || !SUPPORTED_OPERATORS.contains(rule.getConditionOperator()))
        {
            return null;
        }
        BigDecimal value = rule.getConditionValue();
        return switch (rule.getConditionOperator())
        {
            case ">" -> new ConditionRange(value, false, null, false);
            case "=", "==" -> new ConditionRange(value, true, value, true);
            case "<" -> new ConditionRange(BigDecimal.ZERO, true, value, false);
            case "<=" -> new ConditionRange(BigDecimal.ZERO, true, value, true);
            default -> new ConditionRange(value, true, null, false);
        };
    }

    private static String safeRuleLabel(InvTransferApprovalRule rule)
    {
        String name = StringUtils.isBlank(rule.getRuleName()) ? "未命名规则" : rule.getRuleName();
        return "规则「" + name + "」";
    }

    private static final class ConditionRange
    {
        private final BigDecimal lower;
        private final boolean lowerInclusive;
        private final BigDecimal upper;
        private final boolean upperInclusive;

        private ConditionRange(BigDecimal lower, boolean lowerInclusive,
                BigDecimal upper, boolean upperInclusive)
        {
            this.lower = lower;
            this.lowerInclusive = lowerInclusive;
            this.upper = upper;
            this.upperInclusive = upperInclusive;
        }

        private boolean overlaps(ConditionRange other)
        {
            BigDecimal intersectionLower = max(lower, other.lower);
            BigDecimal intersectionUpper = min(upper, other.upper);
            if (intersectionUpper == null || intersectionLower == null)
            {
                return true;
            }
            int comparison = intersectionLower.compareTo(intersectionUpper);
            if (comparison < 0)
            {
                return true;
            }
            return comparison == 0 && includes(intersectionLower) && other.includes(intersectionLower);
        }

        private boolean covers(ConditionRange other)
        {
            boolean lowerCovered = lower == null || other.lower != null
                    && (lower.compareTo(other.lower) < 0
                            || lower.compareTo(other.lower) == 0
                                    && (lowerInclusive || !other.lowerInclusive));
            boolean upperCovered = upper == null || other.upper != null
                    && (upper.compareTo(other.upper) > 0
                            || upper.compareTo(other.upper) == 0
                                    && (upperInclusive || !other.upperInclusive));
            return lowerCovered && upperCovered;
        }

        private boolean includes(BigDecimal value)
        {
            boolean aboveLower = lower == null || value.compareTo(lower) > 0
                    || value.compareTo(lower) == 0 && lowerInclusive;
            boolean belowUpper = upper == null || value.compareTo(upper) < 0
                    || value.compareTo(upper) == 0 && upperInclusive;
            return aboveLower && belowUpper;
        }

        private static BigDecimal max(BigDecimal first, BigDecimal second)
        {
            if (first == null) return second;
            if (second == null) return first;
            return first.max(second);
        }

        private static BigDecimal min(BigDecimal first, BigDecimal second)
        {
            if (first == null) return second;
            if (second == null) return first;
            return first.min(second);
        }
    }

    private void ensureSystemApprovalNodes(InvTransferApprovalRule rule)
    {
        rule.setNodes(ensureSystemApprovalNodes());
    }

    private List<InvTransferApprovalNode> ensureSystemApprovalNodes()
    {
        return new ArrayList<>(List.of(
                systemNode(1, "四级负责人（店长/店助）",
                        InvTransferApprovalNodeRoles.LEVEL4_HIGHEST, null, null),
                systemNode(2, "三级负责人（店长与高层之间）",
                        InvTransferApprovalNodeRoles.LEVEL3_HIGHEST, null, null),
                systemNode(3, "运营总监",
                        InvTransferApprovalNodeRoles.OPERATIONS_DIRECTOR,
                        InvTransferApprovalNodeRoles.OPERATIONS_DIRECTOR_POST, "运营总监"),
                systemNode(4, "总经理",
                        InvTransferApprovalNodeRoles.GENERAL_MANAGER,
                        InvTransferApprovalNodeRoles.GENERAL_MANAGER_POST, "总经理")));
    }

    private InvTransferApprovalNode systemNode(Integer order, String name, String role,
            String postCode, String postName)
    {
        InvTransferApprovalNode node = new InvTransferApprovalNode();
        node.setNodeOrder(order);
        node.setNodeName(name);
        node.setNodeRole(role);
        node.setPostCode(postCode);
        node.setPostName(postName);
        node.setApprovalMode(APPROVAL_ANY_ONE);
        node.setRequiredCount(1);
        return node;
    }

    private void validateNodes(List<InvTransferApprovalNode> nodes)
    {
        if (nodes == null || nodes.isEmpty())
        {
            throw new ServiceException("审批节点不能为空");
        }
        for (InvTransferApprovalNode node : nodes)
        {
            if (node.getNodeOrder() == null || node.getNodeOrder() <= 0)
            {
                throw new ServiceException("审批节点顺序必须大于0");
            }
            if (APPROVAL_QUORUM.equals(node.getApprovalMode())
                    && (node.getRequiredCount() == null || node.getRequiredCount() <= 0))
            {
                throw new ServiceException("节点指定人数必须大于0");
            }
        }
    }

    private void applyRuleDefaults(InvTransferApprovalRule rule)
    {
        if (StringUtils.isEmpty(rule.getDocumentType()))
        {
            rule.setDocumentType(DOCUMENT_TRANSFER);
        }
        if (StringUtils.isEmpty(rule.getTransferType()))
        {
            rule.setTransferType(TRANSFER_ALL);
        }
        if (StringUtils.isEmpty(rule.getScopeType()))
        {
            rule.setScopeType(SCOPE_ALL);
        }
        if (StringUtils.isEmpty(rule.getConditionType()))
        {
            rule.setConditionType(CONDITION_NONE);
        }
        if (StringUtils.isEmpty(rule.getConditionOperator()))
        {
            rule.setConditionOperator(">=");
        }
        rule.setApprovalMode(APPROVAL_ALL_NODES);
        rule.setRequiredCount(0);
        if (StringUtils.isEmpty(rule.getRejectAction()))
        {
            rule.setRejectAction("back_to_draft");
        }
        rule.setAllowSelfApprove("0");
        if (rule.getPriority() == null)
        {
            rule.setPriority(100);
        }
        if (StringUtils.isEmpty(rule.getStatus()))
        {
            rule.setStatus(STATUS_ENABLED);
        }
    }

    private void applyNodeDefaults(InvTransferApprovalNode node, Long ruleId)
    {
        node.setRuleId(ruleId);
        if (StringUtils.isEmpty(node.getNodeRole()))
        {
            node.setNodeRole("post");
        }
        if (StringUtils.isEmpty(node.getApprovalMode()))
        {
            node.setApprovalMode(APPROVAL_ANY_ONE);
        }
        if (node.getRequiredCount() == null)
        {
            node.setRequiredCount(1);
        }
        if (node.getNodeId() == null)
        {
            node.setCreateBy(SecurityUtils.getUsername());
        }
        else
        {
            node.setUpdateBy(SecurityUtils.getUsername());
        }
    }

    private boolean matchesRuleBase(InvTransferApprovalRule rule, InvTransferOrder transfer)
    {
        return STATUS_ENABLED.equals(rule.getStatus())
                && DOCUMENT_TRANSFER.equals(rule.getDocumentType())
                && matchesTransferType(rule, transfer)
                && matchesScope(rule, transfer);
    }

    private boolean matchesTransferType(InvTransferApprovalRule rule, InvTransferOrder transfer)
    {
        String transferType = rule.getTransferType();
        return TRANSFER_ALL.equals(transferType)
                || (transfer.getTransferType() != null && transfer.getTransferType().equals(transferType));
    }

    private boolean matchesScope(InvTransferApprovalRule rule, InvTransferOrder transfer)
    {
        String scopeType = rule.getScopeType();
        if (StringUtils.isEmpty(scopeType) || SCOPE_ALL.equals(scopeType))
        {
            return true;
        }
        Long scopeId = rule.getScopeId();
        if (scopeId == null)
        {
            return false;
        }
        if (SCOPE_FROM_DEPT.equals(scopeType))
        {
            return scopeId.equals(transfer.getFromDeptId());
        }
        if (SCOPE_TO_DEPT.equals(scopeType))
        {
            return scopeId.equals(transfer.getToDeptId());
        }
        if (SCOPE_REGION.equals(scopeType))
        {
            return deptInScope(scopeId, transfer.getFromDeptId()) || deptInScope(scopeId, transfer.getToDeptId());
        }
        if (SCOPE_DEPT.equals(scopeType))
        {
            return scopeId.equals(transfer.getFromDeptId()) || scopeId.equals(transfer.getToDeptId());
        }
        if (SCOPE_AREA.equals(scopeType))
        {
            return deptInScope(scopeId, transfer.getFromDeptId()) || deptInScope(scopeId, transfer.getToDeptId());
        }
        return false;
    }

    private boolean deptInScope(Long scopeDeptId, Long targetDeptId)
    {
        return targetDeptId != null && deptScopeMapper.countDeptInScope(scopeDeptId, targetDeptId) > 0;
    }

    private boolean matchesCondition(InvTransferApprovalRule rule, InvTransferOrder transfer)
    {
        String conditionType = rule.getConditionType();
        if (StringUtils.isEmpty(conditionType) || CONDITION_NONE.equals(conditionType))
        {
            return true;
        }
        if (CONDITION_AMOUNT.equals(conditionType))
        {
            throw new ServiceException("调拨金额条件暂未支持");
        }
        if (CONDITION_QUANTITY.equals(conditionType))
        {
            return compare(transfer.getTotalQuantity(), rule.getConditionValue(), rule.getConditionOperator());
        }
        return false;
    }

    private static boolean compare(BigDecimal actual, BigDecimal expected, String operator)
    {
        if (actual == null || expected == null)
        {
            return false;
        }
        int result = actual.compareTo(expected);
        if (StringUtils.isEmpty(operator) || ">=".equals(operator))
        {
            return result >= 0;
        }
        if (">".equals(operator))
        {
            return result > 0;
        }
        if ("=".equals(operator) || "==".equals(operator))
        {
            return result == 0;
        }
        if ("<=".equals(operator))
        {
            return result <= 0;
        }
        if ("<".equals(operator))
        {
            return result < 0;
        }
        return false;
    }

    private static Integer priorityValue(InvTransferApprovalRule rule)
    {
        return rule.getPriority() == null ? Integer.MAX_VALUE : rule.getPriority();
    }

    private static Long ruleIdValue(InvTransferApprovalRule rule)
    {
        return rule.getRuleId() == null ? Long.MAX_VALUE : rule.getRuleId();
    }

}
