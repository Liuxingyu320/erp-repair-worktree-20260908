package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.inventory.constant.InvTransferApprovalNodeRoles;
import com.erp.inventory.domain.InvTransferApprovalNode;
import com.erp.inventory.domain.InvTransferApprovalRule;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.mapper.InvTransferApprovalCandidateMapper;
import com.erp.inventory.service.impl.TransferApprovalCandidateResolver.Candidate;
import com.erp.inventory.service.impl.TransferApprovalCandidateResolver.Resolution;

@DisplayName("调拨四级审批候选人解析")
class TransferApprovalCandidateResolverTest
{
    @Test
    @DisplayName("三级只取高层与店长之间最接近店长的一档")
    void shouldResolveClosestLevelInsideExecutiveAndManagerBounds()
    {
        FakeCandidateMapper mapper = new FakeCandidateMapper()
                .postSort("dz", 5).postSort("yyzj", 1).postSort("zjl", 1)
                .direct("dz", candidate(10L, "storeManager", 5))
                .higher(candidate(20L, "closest", 4), candidate(21L, "middle", 3))
                .covered("yyzj", candidate(30L, "operationsDirector", 1))
                .covered("zjl", candidate(40L, "generalManager", 1));

        Resolution result = resolver(mapper).resolve(transfer(), fourNodeRule());

        assertThat(mapper.lastExecutiveBoundarySort).isEqualTo(1);
        assertThat(mapper.lastManagerPostSort).isEqualTo(5);
        assertThat(result.node(InvTransferApprovalNodeRoles.LEVEL3_HIGHEST).candidates())
                .extracting(Candidate::userId)
                .containsExactly(20L);
        assertThat(result.node(InvTransferApprovalNodeRoles.LEVEL3_HIGHEST).resolvedPostSort())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("四级三级缺失时仍保留运营总监总经理")
    void shouldKeepMandatoryExecutivesWhenDynamicLevelsMissing()
    {
        FakeCandidateMapper mapper = new FakeCandidateMapper()
                .postSort("dz", 5).postSort("yyzj", 1).postSort("zjl", 1)
                .covered("yyzj", candidate(30L, "operationsDirector", 1))
                .covered("zjl", candidate(40L, "generalManager", 1));

        Resolution result = resolver(mapper).resolve(transfer(), fourNodeRule());

        assertThat(result.activeRoles()).containsExactly(
                InvTransferApprovalNodeRoles.OPERATIONS_DIRECTOR,
                InvTransferApprovalNodeRoles.GENERAL_MANAGER);
        assertThat(result.warnings()).containsExactly(
                "未找到目标门店四级、三级负责人，本次审批直接进入运营总监");
        assertThat(result.missingMandatoryRoles()).isEmpty();
    }

    @Test
    @DisplayName("运营总监或总经理缺失时标记为阻断候选角色")
    void shouldBlockWhenMandatoryExecutiveCandidatesAreMissing()
    {
        FakeCandidateMapper mapper = new FakeCandidateMapper()
                .postSort("dz", 5).postSort("yyzj", 1).postSort("zjl", 1)
                .covered("yyzj", candidate(30L, "operationsDirector", 1));

        Resolution result = resolver(mapper).resolve(transfer(), fourNodeRule());

        assertThat(result.missingMandatoryRoles())
                .containsExactly(InvTransferApprovalNodeRoles.GENERAL_MANAGER);
    }

    @Test
    @DisplayName("目标门店无店长时只使用店长助理兜底")
    void shouldUseAssistantOnlyWhenManagerIsMissing()
    {
        FakeCandidateMapper mapper = new FakeCandidateMapper()
                .postSort("dz", 5).postSort("yyzj", 1).postSort("zjl", 1)
                .direct("dzzy", candidate(11L, "assistant", 6))
                .covered("yyzj", candidate(30L, "operationsDirector", 1))
                .covered("zjl", candidate(40L, "generalManager", 1));

        Resolution result = resolver(mapper).resolve(transfer(), fourNodeRule());

        assertThat(mapper.directCalls).containsExactly("202:dz", "202:dzzy");
        assertThat(result.node(InvTransferApprovalNodeRoles.LEVEL4_HIGHEST).candidates())
                .extracting(Candidate::userId)
                .containsExactly(11L);
    }

    @Test
    @DisplayName("候选人快照不保存手机号或邮箱式登录名")
    void shouldMaskSensitiveCandidateDisplayNames()
    {
        FakeCandidateMapper mapper = new FakeCandidateMapper()
                .postSort("dz", 5).postSort("yyzj", 1).postSort("zjl", 1)
                .direct("dz", candidate(10L, "13800138000", 5))
                .covered("yyzj", candidate(30L, "director@example.com", 1))
                .covered("zjl", candidate(40L, "金总", 1));

        Resolution result = resolver(mapper).resolve(transfer(), fourNodeRule());

        assertThat(result.node(InvTransferApprovalNodeRoles.LEVEL4_HIGHEST).candidates())
                .extracting(Candidate::displayName)
                .containsExactly("姓名未配置");
        assertThat(result.node(InvTransferApprovalNodeRoles.OPERATIONS_DIRECTOR).candidates())
                .extracting(Candidate::displayName)
                .containsExactly("姓名未配置");
    }

    private static TransferApprovalCandidateResolver resolver(FakeCandidateMapper mapper)
    {
        TransferApprovalCandidateResolver resolver = new TransferApprovalCandidateResolver();
        ReflectionTestUtils.setField(resolver, "candidateMapper", mapper);
        return resolver;
    }

    private static InvTransferOrder transfer()
    {
        InvTransferOrder transfer = new InvTransferOrder();
        transfer.setToDeptId(202L);
        return transfer;
    }

    private static InvTransferApprovalRule fourNodeRule()
    {
        InvTransferApprovalRule rule = new InvTransferApprovalRule();
        rule.setRuleId(6L);
        rule.setRuleName("门店调拨四级审批");
        rule.setNodes(List.of(
                node(1, "四级负责人（店长/店助）", InvTransferApprovalNodeRoles.LEVEL4_HIGHEST, null),
                node(2, "三级负责人（店长与高层之间）", InvTransferApprovalNodeRoles.LEVEL3_HIGHEST, null),
                node(3, "运营总监", InvTransferApprovalNodeRoles.OPERATIONS_DIRECTOR, "yyzj"),
                node(4, "总经理", InvTransferApprovalNodeRoles.GENERAL_MANAGER, "zjl")));
        return rule;
    }

    private static InvTransferApprovalNode node(Integer order, String name, String role, String postCode)
    {
        InvTransferApprovalNode node = new InvTransferApprovalNode();
        node.setNodeOrder(order);
        node.setNodeName(name);
        node.setNodeRole(role);
        node.setPostCode(postCode);
        node.setApprovalMode("any_one");
        node.setRequiredCount(1);
        return node;
    }

    private static Map<String, Object> candidate(Long userId, String userName, Integer postSort)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("userId", userId);
        row.put("userName", userName);
        row.put("postSort", postSort);
        return row;
    }

    private static class FakeCandidateMapper implements InvTransferApprovalCandidateMapper
    {
        private final Map<String, Integer> postSorts = new HashMap<>();
        private final Map<String, List<Map<String, Object>>> direct = new HashMap<>();
        private final Map<String, List<Map<String, Object>>> covered = new HashMap<>();
        private List<Map<String, Object>> higher = new ArrayList<>();
        private final List<String> directCalls = new ArrayList<>();
        private Integer lastManagerPostSort;
        private Integer lastExecutiveBoundarySort;

        private FakeCandidateMapper postSort(String postCode, Integer postSort)
        {
            postSorts.put(postCode, postSort);
            return this;
        }

        @SafeVarargs
        private final FakeCandidateMapper direct(String postCode, Map<String, Object>... rows)
        {
            direct.put(postCode, new ArrayList<>(List.of(rows)));
            return this;
        }

        @SafeVarargs
        private final FakeCandidateMapper higher(Map<String, Object>... rows)
        {
            higher = new ArrayList<>(List.of(rows));
            return this;
        }

        @SafeVarargs
        private final FakeCandidateMapper covered(String postCode, Map<String, Object>... rows)
        {
            covered.put(postCode, new ArrayList<>(List.of(rows)));
            return this;
        }

        @Override
        public Integer selectActivePostSortByCode(String postCode)
        {
            return postSorts.get(postCode);
        }

        @Override
        public int countActiveTargetStore(Long targetDeptId)
        {
            return 1;
        }

        @Override
        public List<Map<String, Object>> selectDirectStoreUsersByPostCode(Long targetDeptId, String postCode)
        {
            directCalls.add(targetDeptId + ":" + postCode);
            return direct.getOrDefault(postCode, Collections.emptyList());
        }

        @Override
        public List<Map<String, Object>> selectCoveredHigherPostUsers(Long targetDeptId,
                Integer managerPostSort, Integer executiveBoundarySort, List<String> excludedPostCodes)
        {
            lastManagerPostSort = managerPostSort;
            lastExecutiveBoundarySort = executiveBoundarySort;
            return higher;
        }

        @Override
        public List<Map<String, Object>> selectCoveredUsersByPostCode(Long targetDeptId, String postCode)
        {
            return covered.getOrDefault(postCode, Collections.emptyList());
        }

        @Override
        public List<Map<String, Object>> selectUsersByDeptAndPostCode(Long deptId, String postCode)
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> selectPostCodesByUserId(Long userId)
        {
            return Collections.emptyList();
        }

        @Override
        public List<Map<String, Object>> selectSafeDisplayNamesByUserIds(List<Long> userIds)
        {
            return Collections.emptyList();
        }
    }
}
