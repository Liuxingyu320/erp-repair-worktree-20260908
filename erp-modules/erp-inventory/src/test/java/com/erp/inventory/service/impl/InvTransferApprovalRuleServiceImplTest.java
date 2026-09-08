package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvTransferApprovalNode;
import com.erp.inventory.domain.InvTransferApprovalRule;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.dto.InvTransferApprovalRuleValidationRequest;
import com.erp.inventory.domain.vo.InvTransferApprovalRuleValidationResult;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTransferApprovalNodeMapper;
import com.erp.inventory.mapper.InvTransferApprovalRuleMapper;

@DisplayName("调拨审批规则服务")
class InvTransferApprovalRuleServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        System.clearProperty("erp.security.legacy-user-id-admin");
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("多条规则命中时选择优先级更低的规则")
    void shouldPickLowerPriorityRuleWhenTwoRulesMatch()
    {
        InvTransferApprovalRule defaultRule = rule(100L, "all", "none", null, 100);
        InvTransferApprovalRule quantityRule = rule(200L, "cross_store", "quantity", new BigDecimal("10"), 10);
        InvTransferApprovalRuleServiceImpl service = newService(defaultRule, quantityRule);

        InvTransferOrder transfer = transfer("cross_store", new BigDecimal("12"));

        InvTransferApprovalRule matched = service.matchRule(transfer);

        assertThat(matched.getRuleId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("条件规则不满足时回退默认规则")
    void shouldFallbackToDefaultRuleWhenNoConditionRuleMatches()
    {
        InvTransferApprovalRule defaultRule = rule(100L, "all", "none", null, 100);
        InvTransferApprovalRule quantityRule = rule(200L, "cross_store", "quantity", new BigDecimal("10"), 10);
        InvTransferApprovalRuleServiceImpl service = newService(defaultRule, quantityRule);

        InvTransferOrder transfer = transfer("cross_store", new BigDecimal("3"));

        InvTransferApprovalRule matched = service.matchRule(transfer);

        assertThat(matched.getRuleId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("相同优先级且条件重叠时阻止保存")
    void shouldBlockOverlappingRulesWithSamePriority()
    {
        enableAdmin();
        InvTransferApprovalRule existing = rule(100L, "all", "none", null, 10);
        InvTransferApprovalRule proposed = rule(null, "cross_store", "quantity", BigDecimal.TEN, 10);
        InvTransferApprovalRuleServiceImpl service = newService(existing);

        InvTransferApprovalRuleValidationResult result = service.validateRule(
                validationRequest(proposed, null), null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getBlockingIssues()).anyMatch(message -> message.contains("优先级冲突"));
        assertThat(result.getOverlaps()).hasSize(1);
    }

    @Test
    @DisplayName("更高优先级规则完整覆盖时报告永远无法命中")
    void shouldReportUnreachableRuleWhenCoveredByHigherPriority()
    {
        enableAdmin();
        InvTransferApprovalRule existing = rule(100L, "all", "none", null, 5);
        InvTransferApprovalRule proposed = rule(null, "cross_store", "quantity", BigDecimal.TEN, 20);
        InvTransferApprovalRuleServiceImpl service = newService(existing);

        InvTransferApprovalRuleValidationResult result = service.validateRule(
                validationRequest(proposed, null), null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getBlockingIssues()).anyMatch(message -> message.contains("永远匹配不到"));
    }

    @Test
    @DisplayName("覆盖存量规则的警告必须确认后才能保存")
    void shouldRequireAcknowledgementBeforeSavingWarningRule()
    {
        enableAdmin();
        InvTransferApprovalRule existing = rule(100L, "all", "none", null, 100);
        InvTransferApprovalRule proposed = rule(null, "all", "none", null, 10);
        InvTransferApprovalRuleServiceImpl service = newService(existing);

        assertThatThrownBy(() -> service.saveRule(proposed, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存在警告");

        proposed.setWarningAcknowledged(true);
        assertThat(service.saveRule(proposed, null).getRuleId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("启用规则缺少目标门店候选人校验时拒绝保存")
    void shouldRejectEnabledRuleWithoutValidationTarget()
    {
        enableAdmin();
        InvTransferApprovalRule proposed = rule(null, "cross_store", "none", null, 10);
        proposed.setValidationTargetDeptId(null);
        InvTransferApprovalRuleServiceImpl service = newService();

        assertThatThrownBy(() -> service.saveRule(proposed, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("目标门店");
    }

    @Test
    @DisplayName("并发删除规则后更新失败且不写入孤儿节点")
    void shouldRejectUpdateWhenRuleDisappearedBeforeWrite()
    {
        enableAdmin();
        InvTransferApprovalRule existing = rule(401L, "cross_store", "none", null, 10);
        InvTransferApprovalRuleServiceImpl service = newService(existing);
        FakeRuleMapper mapper = (FakeRuleMapper) ReflectionTestUtils.getField(service, "ruleMapper");
        mapper.writeResult = 0;

        assertThatThrownBy(() -> service.saveRule(existing, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已被删除或修改");

        FakeNodeMapper nodeMapper = (FakeNodeMapper) ReflectionTestUtils.getField(service, "nodeMapper");
        assertThat(nodeMapper.lastInserted).isEmpty();
    }

    @Test
    @DisplayName("过期版本不能覆盖其他管理员已保存的审批规则")
    void shouldRejectStaleRuleVersionBeforeUpdate()
    {
        enableAdmin();
        InvTransferApprovalRule persisted = rule(401L, "cross_store", "none", null, 10);
        persisted.setVersion(2);
        InvTransferApprovalRule stale = rule(401L, "cross_store", "none", null, 20);
        stale.setVersion(1);
        InvTransferApprovalRuleServiceImpl service = newService(persisted);

        assertThatThrownBy(() -> service.saveRule(stale, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("版本已变化");

        FakeNodeMapper nodeMapper = (FakeNodeMapper) ReflectionTestUtils.getField(service, "nodeMapper");
        assertThat(nodeMapper.lastInserted).isEmpty();
    }

    @Test
    @DisplayName("模拟返回最终命中规则和未命中原因")
    void shouldSimulateWinningRuleAndExplainProposedRuleLoss()
    {
        enableAdmin();
        InvTransferApprovalRule existing = rule(100L, "all", "none", null, 5);
        InvTransferApprovalRule proposed = rule(null, "cross_store", "quantity", BigDecimal.TEN, 20);
        InvTransferApprovalRuleServiceImpl service = newService(existing);
        InvTransferOrder sample = transfer("cross_store", new BigDecimal("12"));
        sample.setFromDeptId(null);
        sample.setToDeptId(null);

        InvTransferApprovalRuleValidationResult result = service.validateRule(
                validationRequest(proposed, sample), null);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getRuleId()).isEqualTo(100L);
        assertThat(result.getMatchReasons()).anyMatch(message -> message.contains("优先级5"));
        assertThat(result.getNonMatchReasons()).anyMatch(message -> message.contains("优先级更高"));
    }

    @Test
    @DisplayName("保存规则时固定为四级三级运营总监总经理")
    void shouldNormalizeRuleToFourSystemNodes()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("operator");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper);
        InvTransferApprovalRule rule = scopedRule(null, "dept", 100L, 10);
        rule.setApprovalMode("quorum");
        rule.setRequiredCount(0);
        rule.setAllowSelfApprove("1");
        rule.setNodes(List.of(node()));

        service.saveRule(rule, 100L);

        FakeNodeMapper nodeMapper = (FakeNodeMapper) ReflectionTestUtils.getField(service, "nodeMapper");
        assertThat(nodeMapper.lastInserted).extracting(InvTransferApprovalNode::getNodeRole)
                .containsExactly("level4_highest", "level3_highest",
                        "operations_director", "general_manager");
        assertThat(nodeMapper.lastInserted).extracting(InvTransferApprovalNode::getNodeOrder)
                .containsExactly(1, 2, 3, 4);
        assertThat(nodeMapper.lastInserted).extracting(InvTransferApprovalNode::getPostCode)
                .containsExactly(null, null, "yyzj", "zjl");
        assertThat(nodeMapper.lastInserted).extracting(InvTransferApprovalNode::getApprovalMode)
                .containsOnly("any_one");
        assertThat(nodeMapper.lastInserted).extracting(InvTransferApprovalNode::getRequiredCount)
                .containsOnly(1);
        assertThat(rule.getApprovalMode()).isEqualTo("all_nodes");
        assertThat(rule.getRequiredCount()).isZero();
        assertThat(rule.getAllowSelfApprove()).isEqualTo("0");
    }

    @Test
    @DisplayName("单独保存节点时也不能改变四级审批模板")
    void shouldIgnoreCallerNodesWhenSavingNodesSeparately()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("operator");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L);
        InvTransferApprovalRule existing = scopedRule(401L, "dept", 100L, 10);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper, existing);

        service.saveNodes(401L, List.of(node()), 100L);

        FakeNodeMapper nodeMapper = (FakeNodeMapper) ReflectionTestUtils.getField(service, "nodeMapper");
        assertThat(nodeMapper.lastInserted).extracting(InvTransferApprovalNode::getNodeRole)
                .containsExactly("level4_highest", "level3_highest",
                        "operations_director", "general_manager");
    }

    @Test
    @DisplayName("不能单独删除固定四级审批节点")
    void shouldRejectDeletingSystemNodesSeparately()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("operator");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L);
        InvTransferApprovalRule existing = scopedRule(401L, "dept", 100L, 10);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper, existing);

        assertThatThrownBy(() -> service.deleteNodesByRuleId(401L, 100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("固定四级审批节点不能单独删除");
    }

    @Test
    @DisplayName("非管理员只能看到所选店铺范围内的调拨审批规则")
    void shouldOnlyListRulesInsideSelectedShopScopeForNonAdmin()
    {
        SecurityContextHolder.setUserId("9");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L)
                .addScopedDept(100L, 101L);
        InvTransferApprovalRule visibleRule = scopedRule(401L, "dept", 101L, 10);
        InvTransferApprovalRule hiddenRule = scopedRule(402L, "dept", 999L, 20);
        InvTransferApprovalRule globalRule = scopedRule(403L, "all", null, 30);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper, visibleRule, hiddenRule, globalRule);

        List<InvTransferApprovalRule> rules = service.selectRuleList(new InvTransferApprovalRule(), 100L);

        assertThat(rules).extracting(InvTransferApprovalRule::getRuleId).containsExactly(401L);
    }

    @Test
    @DisplayName("非管理员不能查询店铺范围外的调拨审批规则详情")
    void shouldRejectRuleDetailOutsideSelectedShopScopeForNonAdmin()
    {
        SecurityContextHolder.setUserId("9");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper, scopedRule(402L, "dept", 999L, 20));

        assertThatThrownBy(() -> service.selectRuleById(402L, 100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权访问该审批规则");
    }

    @Test
    @DisplayName("非管理员不能保存店铺范围外的调拨审批规则")
    void shouldRejectSavingRuleOutsideSelectedShopScopeForNonAdmin()
    {
        SecurityContextHolder.setUserId("9");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper);
        InvTransferApprovalRule rule = scopedRule(null, "dept", 999L, 10);
        rule.setNodes(Collections.singletonList(node()));

        assertThatThrownBy(() -> service.saveRule(rule, 100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权配置该审批规则范围");
    }

    @Test
    @DisplayName("非管理员不能配置全局调拨审批规则")
    void shouldRejectGlobalRuleForNonAdmin()
    {
        SecurityContextHolder.setUserId("9");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper);
        InvTransferApprovalRule rule = scopedRule(null, "all", null, 10);
        rule.setNodes(Collections.singletonList(node()));

        assertThatThrownBy(() -> service.saveRule(rule, 100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("非管理员不能配置全局审批规则");
    }

    @Test
    @DisplayName("删除规则前必须校验原规则范围")
    void shouldRejectDeletingRuleOutsideSelectedShopScopeForNonAdmin()
    {
        SecurityContextHolder.setUserId("9");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper, scopedRule(402L, "dept", 999L, 20));

        assertThatThrownBy(() -> service.deleteRuleById(402L, 1, 100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权访问该审批规则");
    }

    @Test
    @DisplayName("预览审批规则前必须校验调拨单属于当前店铺范围")
    void shouldRejectPreviewWhenTransferOutsideSelectedShopScope()
    {
        SecurityContextHolder.setUserId("9");
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 100L);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper, scopedRule(401L, "dept", 101L, 10));
        InvTransferOrder transfer = transfer("cross_store", BigDecimal.ONE);
        transfer.setFromDeptId(201L);
        transfer.setToDeptId(202L);

        assertThatThrownBy(() -> service.previewRule(transfer, 100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权预览该调拨单审批规则");
    }

    @Test
    @DisplayName("管理员预览全局审批规则不受残留店铺请求头限制")
    void adminShouldPreviewGlobalRuleWhenShopHeaderRemains()
    {
        System.setProperty("erp.security.legacy-user-id-admin", "true");
        SecurityContextHolder.setUserId("1");
        InvTransferApprovalRule globalRule = rule(501L, "all", "none", null, 10);
        InvTransferApprovalRuleServiceImpl service = newService(globalRule);
        InvTransferOrder transfer = new InvTransferOrder();
        transfer.setTotalQuantity(BigDecimal.ZERO);

        InvTransferApprovalRule matched = service.previewRule(transfer, 100L);

        assertThat(matched).isNotNull();
        assertThat(matched.getRuleId()).isEqualTo(501L);
    }

    @Test
    @DisplayName("调拨不命中无关区域规则")
    void shouldNotMatchUnrelatedAreaScopeRule()
    {
        InvTransferApprovalRule areaRule = scopedRule(300L, "area", 999L, 10);
        InvTransferApprovalRuleServiceImpl service = newService(areaRule);

        InvTransferOrder transfer = transfer("cross_store", BigDecimal.ONE);

        assertThat(service.matchRule(transfer)).isNull();
    }

    @Test
    @DisplayName("调拨不命中无关大区规则")
    void shouldNotMatchUnrelatedRegionScopeRule()
    {
        InvTransferApprovalRule regionRule = scopedRule(301L, "region", 999L, 10);
        InvTransferApprovalRuleServiceImpl service = newService(regionRule);

        InvTransferOrder transfer = transfer("cross_store", BigDecimal.ONE);

        assertThat(service.matchRule(transfer)).isNull();
    }

    @Test
    @DisplayName("调拨命中来源部门规则")
    void shouldMatchFromDeptScopeRule()
    {
        InvTransferApprovalRule deptRule = scopedRule(302L, "dept", 101L, 10);
        InvTransferApprovalRuleServiceImpl service = newService(deptRule);

        InvTransferOrder transfer = transfer("cross_store", BigDecimal.ONE);

        assertThat(service.matchRule(transfer).getRuleId()).isEqualTo(302L);
    }

    @Test
    @DisplayName("调拨命中目标部门规则")
    void shouldMatchToDeptScopeRule()
    {
        InvTransferApprovalRule toDeptRule = scopedRule(303L, "to_dept", 102L, 10);
        InvTransferApprovalRuleServiceImpl service = newService(toDeptRule);

        InvTransferOrder transfer = transfer("cross_store", BigDecimal.ONE);

        assertThat(service.matchRule(transfer).getRuleId()).isEqualTo(303L);
    }

    @Test
    @DisplayName("调拨命中来源部门祖先区域规则")
    void shouldMatchRegionScopeRuleWhenScopeDeptIsAncestorOfFromDept()
    {
        InvTransferApprovalRule regionRule = scopedRule(304L, "region", 100L, 10);
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addScopedDept(100L, 101L);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper, regionRule);

        InvTransferOrder transfer = transfer("cross_store", BigDecimal.ONE);

        InvTransferApprovalRule matched = service.matchRule(transfer);

        assertThat(matched).isNotNull();
        assertThat(matched.getRuleId()).isEqualTo(304L);
        assertThat(deptScopeMapper.scopeChecks).contains("100:101");
    }

    @Test
    @DisplayName("调拨命中目标部门祖先大区规则")
    void shouldMatchAreaScopeRuleWhenScopeDeptIsAncestorOfToDept()
    {
        InvTransferApprovalRule areaRule = scopedRule(305L, "area", 200L, 10);
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addScopedDept(200L, 102L);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper, areaRule);

        InvTransferOrder transfer = transfer("cross_store", BigDecimal.ONE);

        InvTransferApprovalRule matched = service.matchRule(transfer);

        assertThat(matched).isNotNull();
        assertThat(matched.getRuleId()).isEqualTo(305L);
        assertThat(deptScopeMapper.scopeChecks).contains("200:101", "200:102");
    }

    @Test
    @DisplayName("部门规则不按祖先关系宽松命中")
    void shouldKeepDeptScopeRuleExactWhenScopeDeptIsAncestor()
    {
        InvTransferApprovalRule deptRule = scopedRule(306L, "dept", 100L, 10);
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addScopedDept(100L, 101L);
        InvTransferApprovalRuleServiceImpl service = newService(deptScopeMapper, deptRule);

        InvTransferOrder transfer = transfer("cross_store", BigDecimal.ONE);

        assertThat(service.matchRule(transfer)).isNull();
        assertThat(deptScopeMapper.scopeChecks).isEmpty();
    }

    private static InvTransferApprovalRuleServiceImpl newService(InvTransferApprovalRule... rules)
    {
        return newService(new FakeDeptScopeMapper(), rules);
    }

    private static InvTransferApprovalRuleServiceImpl newService(FakeDeptScopeMapper deptScopeMapper,
            InvTransferApprovalRule... rules)
    {
        InvTransferApprovalRuleServiceImpl service = new InvTransferApprovalRuleServiceImpl();
        ReflectionTestUtils.setField(service, "ruleMapper", new FakeRuleMapper(rules));
        ReflectionTestUtils.setField(service, "nodeMapper", new FakeNodeMapper());
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        ReflectionTestUtils.setField(service, "candidateResolver", new TransferApprovalCandidateResolver()
        {
            @Override
            public Resolution resolve(InvTransferOrder transfer, InvTransferApprovalRule rule)
            {
                return new Resolution(List.of(), List.of(), List.of(), null, null);
            }
        });
        return service;
    }

    private static void enableAdmin()
    {
        System.setProperty("erp.security.legacy-user-id-admin", "true");
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
    }

    private static InvTransferApprovalRuleValidationRequest validationRequest(
            InvTransferApprovalRule rule, InvTransferOrder sample)
    {
        InvTransferApprovalRuleValidationRequest request = new InvTransferApprovalRuleValidationRequest();
        request.setRule(rule);
        request.setTransferSample(sample);
        return request;
    }

    private static InvTransferApprovalRule rule(Long ruleId, String transferType,
            String conditionType, BigDecimal conditionValue, Integer priority)
    {
        InvTransferApprovalRule rule = new InvTransferApprovalRule();
        rule.setRuleId(ruleId);
        rule.setRuleName("规则" + ruleId);
        rule.setDocumentType("transfer");
        rule.setTransferType(transferType);
        rule.setScopeType("all");
        rule.setConditionType(conditionType);
        rule.setConditionOperator(">=");
        rule.setConditionValue(conditionValue);
        rule.setApprovalMode("all_nodes");
        rule.setRequiredCount(0);
        rule.setRejectAction("back_to_draft");
        rule.setAllowSelfApprove("0");
        rule.setPriority(priority);
        rule.setStatus("0");
        rule.setVersion(1);
        rule.setNodes(Collections.singletonList(node()));
        rule.setValidationTargetDeptId(102L);
        return rule;
    }

    private static InvTransferApprovalRule scopedRule(Long ruleId, String scopeType, Long scopeId, Integer priority)
    {
        InvTransferApprovalRule rule = rule(ruleId, "cross_store", "none", null, priority);
        rule.setScopeType(scopeType);
        rule.setScopeId(scopeId);
        if (scopeId != null)
        {
            rule.setValidationTargetDeptId(scopeId);
        }
        return rule;
    }

    private static InvTransferApprovalNode node()
    {
        InvTransferApprovalNode node = new InvTransferApprovalNode();
        node.setNodeOrder(1);
        node.setNodeName("店长审批");
        node.setNodeRole("post");
        node.setApprovalMode("any_one");
        node.setRequiredCount(1);
        return node;
    }

    private static InvTransferOrder transfer(String transferType, BigDecimal totalQuantity)
    {
        InvTransferOrder transfer = new InvTransferOrder();
        transfer.setTransferType(transferType);
        transfer.setFromDeptId(101L);
        transfer.setToDeptId(102L);
        transfer.setTotalQuantity(totalQuantity);
        return transfer;
    }

    private static class FakeRuleMapper implements InvTransferApprovalRuleMapper
    {
        private final List<InvTransferApprovalRule> rules;
        private int writeResult = 1;

        private FakeRuleMapper(InvTransferApprovalRule... rules)
        {
            this.rules = new ArrayList<>();
            Collections.addAll(this.rules, rules);
        }

        @Override
        public List<InvTransferApprovalRule> selectRuleList(InvTransferApprovalRule rule)
        {
            return rules;
        }

        @Override
        public InvTransferApprovalRule selectRuleById(Long ruleId)
        {
            for (InvTransferApprovalRule rule : rules)
            {
                if (rule.getRuleId().equals(ruleId))
                {
                    return rule;
                }
            }
            return null;
        }

        @Override
        public List<InvTransferApprovalRule> selectEnabledRulesForMatch(InvTransferOrder transfer)
        {
            return rules;
        }

        @Override
        public int insertRule(InvTransferApprovalRule rule)
        {
            if (writeResult <= 0)
            {
                return writeResult;
            }
            rule.setRuleId(999L);
            rules.add(rule);
            return writeResult;
        }

        @Override
        public int updateRule(InvTransferApprovalRule rule)
        {
            return writeResult;
        }

        @Override
        public int deleteRuleByIdAndVersion(Long ruleId, Integer expectedVersion)
        {
            return 1;
        }
    }

    private static class FakeNodeMapper implements InvTransferApprovalNodeMapper
    {
        private List<InvTransferApprovalNode> lastInserted = Collections.emptyList();

        @Override
        public List<InvTransferApprovalNode> selectNodesByRuleId(Long ruleId)
        {
            return Collections.singletonList(node());
        }

        @Override
        public int batchInsertNodes(List<InvTransferApprovalNode> nodes)
        {
            lastInserted = new ArrayList<>(nodes);
            return nodes.size();
        }

        @Override
        public int deleteNodesByRuleId(Long ruleId)
        {
            return 1;
        }
    }

    private static class FakeDeptScopeMapper implements InvDeptScopeMapper
    {
        @Override public List<Long> selectUserAuthorizedInventoryDeptIds(Long userId) { return java.util.Collections.emptyList(); }
        @Override public List<Long> selectAllActiveInventoryDeptIds() { return java.util.Collections.emptyList(); }
        private final Set<String> scopedDepts = new HashSet<>();
        private final Set<String> userShops = new HashSet<>();
        private final List<String> scopeChecks = new ArrayList<>();

        private FakeDeptScopeMapper addScopedDept(Long scopeDeptId, Long targetDeptId)
        {
            scopedDepts.add(scopeDeptId + ":" + targetDeptId);
            return this;
        }

        private FakeDeptScopeMapper addUserShop(Long userId, Long deptId)
        {
            userShops.add(userId + ":" + deptId);
            return this;
        }

        @Override
        public List<Long> selectSubDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectRelatedDeptIds(Long deptId)
        {
            return Collections.singletonList(deptId);
        }

        @Override
        public List<Long> selectActiveRelatedDeptIdsForReplenishment(
                Long deptId)
        {
            return selectRelatedDeptIds(deptId);
        }

        @Override
        public List<Long> selectAncestorDeptIds(Long deptId)
        {
            return Collections.emptyList();
        }

        @Override
        public Long selectRawBusinessRootDeptId(Long deptId)
        {
            return deptId;
        }

        @Override
        public List<Long> selectUserStoreScopeDeptIds(Long userId)
        {
            return Collections.emptyList();
        }

        @Override
        public List<Long> selectAllStoreDeptIds()
        {
            return Collections.emptyList();
        }

        @Override
        public int countDeptInScope(Long scopeDeptId, Long targetDeptId)
        {
            String key = scopeDeptId + ":" + targetDeptId;
            scopeChecks.add(key);
            return scopeDeptId != null && targetDeptId != null
                    && (scopeDeptId.equals(targetDeptId) || scopedDepts.contains(key)) ? 1 : 0;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            return userId != null && deptId != null && userShops.contains(userId + ":" + deptId) ? 1 : 0;
        }

        @Override
        public String selectDeptNameById(Long deptId)
        {
            return "测试组织";
        }

        @Override
        public String selectDeptTypeById(Long deptId)
        {
            return "STORE";
        }
    }
}
