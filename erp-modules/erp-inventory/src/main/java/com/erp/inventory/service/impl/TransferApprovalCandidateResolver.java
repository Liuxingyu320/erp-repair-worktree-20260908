package com.erp.inventory.service.impl;

import static com.erp.inventory.constant.InvTransferApprovalNodeRoles.GENERAL_MANAGER;
import static com.erp.inventory.constant.InvTransferApprovalNodeRoles.GENERAL_MANAGER_POST;
import static com.erp.inventory.constant.InvTransferApprovalNodeRoles.LEVEL3_HIGHEST;
import static com.erp.inventory.constant.InvTransferApprovalNodeRoles.LEVEL4_HIGHEST;
import static com.erp.inventory.constant.InvTransferApprovalNodeRoles.OPERATIONS_DIRECTOR;
import static com.erp.inventory.constant.InvTransferApprovalNodeRoles.OPERATIONS_DIRECTOR_POST;
import static com.erp.inventory.constant.InvTransferApprovalNodeRoles.STORE_ASSISTANT_POST;
import static com.erp.inventory.constant.InvTransferApprovalNodeRoles.STORE_MANAGER_POST;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvTransferApprovalNode;
import com.erp.inventory.domain.InvTransferApprovalRule;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.mapper.InvTransferApprovalCandidateMapper;

/** Resolves the four target-store approver pools without changing workflow state. */
@Component
public class TransferApprovalCandidateResolver
{
    private static final List<String> EXCLUDED_LEVEL3_POST_CODES = List.of(
            STORE_MANAGER_POST,
            STORE_ASSISTANT_POST,
            OPERATIONS_DIRECTOR_POST,
            GENERAL_MANAGER_POST);

    @Autowired
    private InvTransferApprovalCandidateMapper candidateMapper;

    public Resolution resolve(InvTransferOrder transfer, InvTransferApprovalRule rule)
    {
        Long targetDeptId = transfer == null ? null : transfer.getToDeptId();
        if (targetDeptId == null || candidateMapper.countActiveTargetStore(targetDeptId) == 0)
        {
            throw new ServiceException("调拨目标门店无效");
        }
        if (rule == null || rule.getNodes() == null || rule.getNodes().isEmpty())
        {
            throw new ServiceException("审批规则无审批节点");
        }

        Integer managerSort = candidateMapper.selectActivePostSortByCode(STORE_MANAGER_POST);
        Integer operationsSort = candidateMapper.selectActivePostSortByCode(OPERATIONS_DIRECTOR_POST);
        Integer generalManagerSort = candidateMapper.selectActivePostSortByCode(GENERAL_MANAGER_POST);
        Integer executiveBoundarySort = operationsSort == null || generalManagerSort == null
                ? null : Math.max(operationsSort, generalManagerSort);

        List<Candidate> level4 = candidates(candidateMapper.selectDirectStoreUsersByPostCode(
                targetDeptId, STORE_MANAGER_POST));
        if (level4.isEmpty())
        {
            level4 = candidates(candidateMapper.selectDirectStoreUsersByPostCode(
                    targetDeptId, STORE_ASSISTANT_POST));
        }

        List<Candidate> level3 = new ArrayList<>();
        if (managerSort != null && executiveBoundarySort != null
                && executiveBoundarySort < managerSort)
        {
            level3 = closestSortBand(candidates(candidateMapper.selectCoveredHigherPostUsers(
                    targetDeptId,
                    managerSort,
                    executiveBoundarySort,
                    EXCLUDED_LEVEL3_POST_CODES)));
        }

        List<Candidate> operations = candidates(candidateMapper.selectCoveredUsersByPostCode(
                targetDeptId, OPERATIONS_DIRECTOR_POST));
        List<Candidate> generalManagers = candidates(candidateMapper.selectCoveredUsersByPostCode(
                targetDeptId, GENERAL_MANAGER_POST));

        Map<String, List<Candidate>> candidatesByRole = new LinkedHashMap<>();
        candidatesByRole.put(LEVEL4_HIGHEST, level4);
        candidatesByRole.put(LEVEL3_HIGHEST, level3);
        candidatesByRole.put(OPERATIONS_DIRECTOR, operations);
        candidatesByRole.put(GENERAL_MANAGER, generalManagers);

        List<ResolvedNode> resolvedNodes = rule.getNodes().stream()
                .filter(Objects::nonNull)
                .filter(node -> candidatesByRole.containsKey(node.getNodeRole()))
                .sorted(Comparator.comparing(TransferApprovalCandidateResolver::nodeOrderValue))
                .map(node -> resolvedNode(node, candidatesByRole.get(node.getNodeRole())))
                .toList();

        List<String> warnings = dynamicWarnings(level4, level3);
        List<String> missingMandatoryRoles = new ArrayList<>();
        if (!hasNodeWithCandidates(resolvedNodes, OPERATIONS_DIRECTOR))
        {
            missingMandatoryRoles.add(OPERATIONS_DIRECTOR);
        }
        if (!hasNodeWithCandidates(resolvedNodes, GENERAL_MANAGER))
        {
            missingMandatoryRoles.add(GENERAL_MANAGER);
        }
        return new Resolution(resolvedNodes, warnings, missingMandatoryRoles,
                managerSort, executiveBoundarySort);
    }

    private static ResolvedNode resolvedNode(InvTransferApprovalNode node, List<Candidate> candidates)
    {
        Integer resolvedPostSort = candidates.stream()
                .map(Candidate::postSort)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        boolean required = OPERATIONS_DIRECTOR.equals(node.getNodeRole())
                || GENERAL_MANAGER.equals(node.getNodeRole());
        return new ResolvedNode(node, candidates, required, resolvedPostSort);
    }

    private static boolean hasNodeWithCandidates(List<ResolvedNode> nodes, String role)
    {
        return nodes.stream().anyMatch(node -> node.node() != null
                && role.equals(node.node().getNodeRole())
                && !node.candidates().isEmpty());
    }

    private static List<String> dynamicWarnings(List<Candidate> level4, List<Candidate> level3)
    {
        if (level4.isEmpty() && level3.isEmpty())
        {
            return List.of("未找到目标门店四级、三级负责人，本次审批直接进入运营总监");
        }
        if (level4.isEmpty())
        {
            return List.of("未找到目标门店四级负责人，本次审批从三级负责人开始");
        }
        if (level3.isEmpty())
        {
            return List.of("未找到目标门店三级负责人，本次审批跳过三级并进入运营总监");
        }
        return List.of();
    }

    private static List<Candidate> closestSortBand(List<Candidate> candidates)
    {
        Integer closestSort = candidates.stream()
                .map(Candidate::postSort)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
        if (closestSort == null)
        {
            return List.of();
        }
        return distinct(candidates.stream()
                .filter(candidate -> closestSort.equals(candidate.postSort()))
                .toList());
    }

    private static List<Candidate> candidates(List<Map<String, Object>> rows)
    {
        List<Candidate> candidates = new ArrayList<>();
        if (rows == null)
        {
            return candidates;
        }
        for (Map<String, Object> row : rows)
        {
            Long userId = asLong(firstPresent(row, "userId", "user_id", "USERID", "USER_ID"));
            if (userId == null)
            {
                continue;
            }
            String displayName = asString(firstPresent(
                    row, "userName", "user_name", "USERNAME", "USER_NAME"));
            Integer postSort = asInteger(firstPresent(
                    row, "postSort", "post_sort", "POSTSORT", "POST_SORT"));
            candidates.add(new Candidate(userId, safeDisplayName(displayName), postSort));
        }
        return distinct(candidates);
    }

    private static List<Candidate> distinct(List<Candidate> candidates)
    {
        Map<Long, Candidate> unique = new LinkedHashMap<>();
        for (Candidate candidate : candidates)
        {
            if (candidate != null && candidate.userId() != null)
            {
                unique.putIfAbsent(candidate.userId(), candidate);
            }
        }
        return List.copyOf(unique.values());
    }

    private static Object firstPresent(Map<String, Object> row, String... keys)
    {
        if (row == null)
        {
            return null;
        }
        for (String key : keys)
        {
            if (row.containsKey(key))
            {
                return row.get(key);
            }
        }
        return null;
    }

    private static Long asLong(Object value)
    {
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        try
        {
            return value == null ? null : Long.valueOf(value.toString());
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    private static Integer asInteger(Object value)
    {
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        try
        {
            return value == null ? null : Integer.valueOf(value.toString());
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    private static String asString(Object value)
    {
        return value == null ? null : value.toString();
    }

    private static String safeDisplayName(String displayName)
    {
        String value = displayName == null ? null : displayName.trim();
        if (value == null || value.isEmpty() || value.matches("^1\\d{10}$") || value.contains("@"))
        {
            return "姓名未配置";
        }
        return value;
    }

    private static int nodeOrderValue(InvTransferApprovalNode node)
    {
        return node.getNodeOrder() == null ? Integer.MAX_VALUE : node.getNodeOrder();
    }

    public record Candidate(Long userId, String displayName, Integer postSort)
    {
    }

    public record ResolvedNode(InvTransferApprovalNode node, List<Candidate> candidates,
            boolean required, Integer resolvedPostSort)
    {
        public ResolvedNode
        {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record Resolution(List<ResolvedNode> nodes, List<String> warnings,
            List<String> missingMandatoryRoles, Integer managerPostSort,
            Integer executiveBoundarySort)
    {
        public Resolution
        {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            missingMandatoryRoles = missingMandatoryRoles == null
                    ? List.of() : List.copyOf(missingMandatoryRoles);
        }

        public ResolvedNode node(String role)
        {
            return nodes.stream()
                    .filter(item -> item.node() != null
                            && Objects.equals(role, item.node().getNodeRole()))
                    .findFirst()
                    .orElse(null);
        }

        public List<String> activeRoles()
        {
            return nodes.stream()
                    .filter(item -> !item.candidates().isEmpty())
                    .map(item -> item.node().getNodeRole())
                    .toList();
        }
    }
}
