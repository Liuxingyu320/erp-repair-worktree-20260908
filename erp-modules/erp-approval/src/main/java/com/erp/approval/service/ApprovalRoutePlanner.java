package com.erp.approval.service;

import static com.erp.approval.constant.ApprovalDefinitionConstants.APPROVAL_ALL;
import static com.erp.approval.constant.ApprovalDefinitionConstants.APPROVAL_ANY_ONE;
import static com.erp.approval.constant.ApprovalDefinitionConstants.APPROVAL_UNIQUE_BEST;
import static com.erp.approval.constant.ApprovalDefinitionConstants.MISSING_BLOCK;
import static com.erp.approval.constant.ApprovalDefinitionConstants.SELF_ALLOW;
import static com.erp.approval.constant.ApprovalDefinitionConstants.SELF_BLOCK;
import static com.erp.approval.constant.ApprovalDefinitionConstants.SELF_SKIP;
import static com.erp.approval.constant.ApprovalDefinitionConstants.SELF_SKIP_THROUGH;
import static com.erp.approval.constant.ApprovalDefinitionConstants.SELF_SKIP_THROUGH_LEGACY;
import static com.erp.approval.constant.ApprovalDefinitionConstants.TRANSFER_GENERAL_MANAGER;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import com.erp.approval.candidate.ApprovalCandidateContext;
import com.erp.approval.candidate.ApprovalCandidateResolution;
import com.erp.approval.candidate.ApprovalCandidateResolverRegistry;
import com.erp.approval.candidate.ResolvedApprovalCandidate;
import com.erp.approval.domain.ApprovalRule;
import com.erp.approval.domain.ApprovalRuleVersion;
import com.erp.approval.domain.ApprovalTemplate;
import com.erp.approval.domain.ApprovalVersionNode;
import com.erp.approval.service.ApprovalRoutePlan.PlannedNode;

/** Resolves a complete route once so every future task uses a frozen snapshot. */
@Service
public class ApprovalRoutePlanner
{
    private final ApprovalCandidateResolverRegistry resolverRegistry;

    public ApprovalRoutePlanner(ApprovalCandidateResolverRegistry resolverRegistry)
    {
        this.resolverRegistry = resolverRegistry;
    }

    public ApprovalRoutePlan plan(ApprovalTemplate template, ApprovalRule rule,
            ApprovalRuleVersion version, Long anchorDeptId, Long applicantId,
            String businessSubtype, Map<String, Object> variables)
    {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<MutableNode> mutable = new ArrayList<>();
        Integer skipThroughOrder = null;

        List<ApprovalVersionNode> nodes = version.getNodes() == null
                ? List.of() : version.getNodes().stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(
                                ApprovalRoutePlanner::orderValue))
                        .toList();
        for (ApprovalVersionNode node : nodes)
        {
            List<ResolvedApprovalCandidate> candidates = List.of();
            List<String> nodeWarnings = List.of();
            try
            {
                ApprovalCandidateResolution resolution = resolverRegistry.resolve(
                        new ApprovalCandidateContext(template, rule, version, node,
                                anchorDeptId, applicantId, businessSubtype, variables));
                candidates = distinct(resolution.candidates());
                nodeWarnings = resolution.warnings();
            }
            catch (RuntimeException exception)
            {
                errors.add(node.getNodeName() + ": " + exception.getMessage());
            }
            for (String warning : nodeWarnings)
            {
                warnings.add(node.getNodeName() + ": " + warning);
            }

            // Self-approval semantics must inspect the complete, de-duplicated
            // candidate set. UNIQUE_BEST is only a final cardinality policy.
            boolean applicantIncluded = applicantId != null && candidates.stream()
                    .anyMatch(item -> applicantId.equals(item.userId()));
            String selfPolicy = node.getSelfPolicy();
            if (applicantIncluded && TRANSFER_GENERAL_MANAGER.equals(
                    node.getStrategyCode()))
            {
                errors.add("总经理不能发起调拨审批");
            }
            else if (applicantIncluded && SELF_BLOCK.equals(selfPolicy))
            {
                errors.add(node.getNodeName() + ": 申请人不得自审");
            }
            else if (applicantIncluded && SELF_SKIP.equals(selfPolicy))
            {
                candidates = candidates.stream()
                        .filter(item -> !applicantId.equals(item.userId())).toList();
                warnings.add(node.getNodeName() + ": 已排除申请人");
            }
            else if (applicantIncluded && (SELF_SKIP_THROUGH.equals(selfPolicy)
                    || SELF_SKIP_THROUGH_LEGACY.equals(selfPolicy)))
            {
                skipThroughOrder = skipThroughOrder == null
                        ? node.getNodeOrder()
                        : Math.max(skipThroughOrder, node.getNodeOrder());
            }
            else if (applicantIncluded && !SELF_ALLOW.equals(selfPolicy))
            {
                errors.add(node.getNodeName() + ": 未知自审策略 " + selfPolicy);
            }
            if (APPROVAL_UNIQUE_BEST.equals(node.getApprovalMode())
                    && candidates.size() > 1)
            {
                warnings.add(node.getNodeName()
                        + ": 解析到多人，按稳定顺序取唯一最优人");
                candidates = List.of(candidates.get(0));
            }
            mutable.add(new MutableNode(node, candidates));
        }

        List<PlannedNode> planned = new ArrayList<>();
        for (MutableNode item : mutable)
        {
            ApprovalVersionNode node = item.node;
            boolean skipByApplicant = skipThroughOrder != null
                    && node.getNodeOrder() <= skipThroughOrder;
            boolean missing = item.candidates.isEmpty();
            boolean blocking = missing && MISSING_BLOCK.equals(node.getMissingPolicy())
                    && !skipByApplicant;
            boolean skipped = skipByApplicant || (missing && !blocking);
            String reason = null;
            if (skipByApplicant)
            {
                reason = "申请人命中当前或更高层级，跳过该层及以下层级";
            }
            else if (blocking)
            {
                reason = "必选节点无有效审批人";
                errors.add(node.getNodeName() + ": " + reason);
            }
            else if (missing)
            {
                reason = "未找到负责人，按配置跳过";
                warnings.add(node.getNodeName() + ": " + reason);
            }
            planned.add(new PlannedNode(node, item.candidates, skipped,
                    blocking, reason));
        }
        return new ApprovalRoutePlan(planned, skipThroughOrder,
                warnings, distinctText(errors));
    }

    private static List<ResolvedApprovalCandidate> distinct(
            List<ResolvedApprovalCandidate> source)
    {
        Map<Long, ResolvedApprovalCandidate> unique = new LinkedHashMap<>();
        if (source != null)
        {
            for (ResolvedApprovalCandidate candidate : source)
            {
                if (candidate != null && candidate.userId() != null)
                {
                    unique.putIfAbsent(candidate.userId(), candidate);
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private static List<String> distinctText(List<String> source)
    {
        return List.copyOf(new java.util.LinkedHashSet<>(source));
    }

    private static int orderValue(ApprovalVersionNode node)
    {
        return node.getNodeOrder() == null ? Integer.MAX_VALUE
                : node.getNodeOrder();
    }

    private static final class MutableNode
    {
        private final ApprovalVersionNode node;
        private final List<ResolvedApprovalCandidate> candidates;

        private MutableNode(ApprovalVersionNode node,
                List<ResolvedApprovalCandidate> candidates)
        {
            this.node = node;
            this.candidates = candidates;
        }
    }
}
