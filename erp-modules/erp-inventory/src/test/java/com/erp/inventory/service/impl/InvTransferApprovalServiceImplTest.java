package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvTransferApprovalInstance;
import com.erp.inventory.domain.InvTransferApprovalNode;
import com.erp.inventory.domain.InvTransferApprovalRule;
import com.erp.inventory.domain.InvTransferApprovalTask;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferStatusLog;
import com.erp.inventory.domain.dto.InvTransferApprovalRequest;
import com.erp.inventory.mapper.InvTransferApprovalCandidateMapper;
import com.erp.inventory.mapper.InvTransferApprovalInstanceMapper;
import com.erp.inventory.mapper.InvTransferApprovalTaskMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferStatusLogMapper;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.service.IInvTransferApprovalRuleService;
import com.erp.inventory.domain.vo.InvTransferApprovalSummary;
import com.erp.inventory.domain.vo.InvTransferApprovalTrack;
import com.erp.inventory.domain.vo.InvTransferApprovalPreview;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("调拨审批快照服务")
class InvTransferApprovalServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("提交调拨单时创建审批实例和候选人快照")
    void shouldCreateApprovalSnapshotWhenTransferSubmitted()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("submitter");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .add(201L, "dz", user(101L, "fromManager"))
                .add(202L, "dz", user(202L, "toManager"));
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(101L, 201L)
                .addUserShop(202L, 202L);
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz"), node(2, "收货店长", "to_leader", "dz")),
                candidateMapper, instanceMapper, taskMapper, deptScopeMapper);

        InvTransferApprovalInstance instance = service.createInstanceForSubmit(transfer());

        assertThat(instance.getStatus()).isEqualTo("running");
        assertThat(instance.getCurrentNodeOrder()).isEqualTo(1);
        assertThat(instance.getRuleSnapshot()).contains("\"ruleId\":100", "\"approvalMode\":\"all_nodes\"");
        assertThat(instanceMapper.instances).hasSize(1);
        assertThat(taskMapper.tasks).hasSize(2);
        assertThat(taskMapper.tasks.get(0).getCandidateUserIds()).isEqualTo("101");
        assertThat(taskMapper.tasks.get(0).getCandidateUserNames()).isEqualTo("fromManager");
        assertThat(taskMapper.tasks.get(1).getCandidateUserIds()).isEqualTo("202");
        assertThat(taskMapper.tasks.get(1).getCandidateUserNames()).isEqualTo("toManager");
    }

    @Test
    @DisplayName("审批候选人必须具备对应调拨门店授权")
    void shouldFilterApprovalCandidatesByUserShopScope()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("submitter");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .add(201L, "dz", user(101L, "scopedManager"))
                .add(201L, "dz", user(102L, "unscopedManager"));
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(101L, 201L);
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz")),
                candidateMapper, new FakeInstanceMapper(), taskMapper, deptScopeMapper);

        service.createInstanceForSubmit(transfer());

        assertThat(taskMapper.tasks).hasSize(1);
        assertThat(taskMapper.tasks.get(0).getCandidateUserIds()).isEqualTo("101");
        assertThat(taskMapper.tasks.get(0).getCandidateUserNames()).isEqualTo("scopedManager");
    }

    @Test
    @DisplayName("门店要货审批按申请门店上级链解析配置岗位并跳过店长节点")
    void shouldResolveConfiguredPostsFromTargetStoreAncestorsAndSkipStoreManager()
    {
        SecurityContextHolder.setUserId("102");
        SecurityContextHolder.setUserName("liu123");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .add(104L, "yyjl", user(401L, "warehouseManager"))
                .add(107L, "dz", user(102L, "liu123"))
                .add(101L, "yyjl", user(201L, "operationManager"))
                .add(100L, "yyzj", user(301L, "operationDirector"));
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addAncestorDepts(107L, 101L, 100L)
                .addUserShop(201L, 107L)
                .addUserShop(301L, 107L);
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "店长", "post", "dz"),
                        node(2, "运营经理", "post", "yyjl"),
                        node(3, "运营总监", "post", "yyzj")),
                candidateMapper, instanceMapper, taskMapper, deptScopeMapper);
        InvTransferOrder transfer = transfer();
        transfer.setFromDeptId(104L);
        transfer.setToDeptId(107L);
        transfer.setTransferType("warehouse");

        InvTransferApprovalInstance instance = service.createInstanceForSubmit(transfer);

        assertThat(instance.getStatus()).isEqualTo("running");
        assertThat(instance.getCurrentNodeOrder()).isEqualTo(2);
        assertThat(taskMapper.tasks).hasSize(2);
        assertThat(taskMapper.tasks.get(0).getNodeOrder()).isEqualTo(2);
        assertThat(taskMapper.tasks.get(0).getCandidateUserIds()).isEqualTo("201");
        assertThat(taskMapper.tasks.get(0).getCandidateUserNames()).isEqualTo("operationManager");
        assertThat(taskMapper.tasks.get(1).getNodeOrder()).isEqualTo(3);
        assertThat(taskMapper.tasks.get(1).getCandidateUserIds()).isEqualTo("301");
        assertThat(taskMapper.tasks.get(1).getCandidateUserNames()).isEqualTo("operationDirector");
        assertThat(candidateMapper.calls).containsExactly("101:yyjl", "101:yyzj", "100:yyzj");
    }

    @Test
    @DisplayName("普通员工按目标门店店长和最接近的上级岗位进入固定审批")
    void shouldResolveTargetStoreManagerAndClosestHigherLeaderBeforeFixedNodes()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("employee");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .managerSort(5)
                .addDirect("dz", leader(101L, "managerA", 5), leader(102L, "managerB", 5))
                .addHigher(leader(201L, "upperA", 4), leader(202L, "tooHigh", 3))
                .add(202L, "yyzj", user(301L, "fixedOne"))
                .add(202L, "zjl", user(401L, "fixedTwo"));
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(301L, 202L)
                .addUserShop(401L, 202L);
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "四级负责人（店长/店助）", "level4_highest", null),
                        node(2, "三级负责人（店长上一级）", "level3_highest", null),
                        node(3, "固定审批1", "to_leader", "yyzj"),
                        node(4, "固定审批2", "to_leader", "zjl")),
                candidateMapper, new FakeInstanceMapper(), taskMapper, deptScopeMapper);

        InvTransferApprovalInstance instance = service.createInstanceForSubmit(transfer());

        assertThat(taskMapper.tasks).extracting(InvTransferApprovalTask::getCandidateUserIds)
                .containsExactly("101,102", "201", "301", "401");
        assertThat(instance.getRuleSnapshot()).contains("\"resolvedPostSort\":5", "\"resolvedPostSort\":4");
        assertThat(candidateMapper.directCalls).containsExactly("202:dz");
        assertThat(candidateMapper.lastExcludedPostCodes)
                .contains("dz", "dzzy", "yyzj", "zjl")
                .doesNotContain("sijifzr", "sanjifzr");
        assertThat(candidateMapper.lastExecutiveBoundarySort).isEqualTo(1);
    }

    @Test
    @DisplayName("目标门店没有店长时店长助理代替四级且三级仍以店长排序为基准")
    void shouldUseAssistantOnlyWhenManagerMissingAndKeepManagerSortAnchor()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("employee");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .managerSort(5)
                .addDirect("dzzy", leader(110L, "assistant", 6))
                .addHigher(leader(210L, "sameAsManager", 5), leader(211L, "upper", 4));
        FakeTaskMapper taskMapper = new FakeTaskMapper();

        dynamicOnlyService(candidateMapper, taskMapper, 999L).createInstanceForSubmit(transfer());

        assertThat(candidateMapper.directCalls).containsExactly("202:dz", "202:dzzy");
        assertThat(candidateMapper.lastManagerPostSort).isEqualTo(5);
        assertThat(taskMapper.tasks).extracting(InvTransferApprovalTask::getCandidateUserIds)
                .containsExactly("110", "211", "999");
    }

    @Test
    @DisplayName("有店长时不再把店长助理加入四级")
    void shouldNotMixAssistantWhenManagerExists()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("employee");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .managerSort(5)
                .addDirect("dz", leader(101L, "manager", 5))
                .addDirect("dzzy", leader(110L, "assistant", 6))
                .addHigher(leader(201L, "upper", 4));
        FakeTaskMapper taskMapper = new FakeTaskMapper();

        dynamicOnlyService(candidateMapper, taskMapper, 999L).createInstanceForSubmit(transfer());

        assertThat(candidateMapper.directCalls).containsExactly("202:dz");
        assertThat(taskMapper.tasks.get(0).getCandidateUserIds()).isEqualTo("101");
    }

    @Test
    @DisplayName("目标门店店长提交时跳过四级")
    void shouldSkipLevel4WhenTargetStoreManagerSubmits()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("manager");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .managerSort(5)
                .addDirect("dz", leader(101L, "manager", 5))
                .addHigher(leader(210L, "upper", 4));
        FakeTaskMapper taskMapper = new FakeTaskMapper();

        InvTransferApprovalInstance instance = dynamicOnlyService(candidateMapper, taskMapper, 999L)
                .createInstanceForSubmit(transfer());

        assertThat(instance.getCurrentNodeOrder()).isEqualTo(2);
        assertThat(taskMapper.tasks).extracting(InvTransferApprovalTask::getNodeOrder).containsExactly(2, 3);
    }

    @Test
    @DisplayName("无店长时作为四级候选的店长助理提交会跳过四级")
    void shouldSkipLevel4WhenFallbackAssistantSubmits()
    {
        SecurityContextHolder.setUserId("110");
        SecurityContextHolder.setUserName("assistant");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .managerSort(5)
                .addDirect("dzzy", leader(110L, "assistant", 6))
                .addHigher(leader(210L, "upper", 4));
        FakeTaskMapper taskMapper = new FakeTaskMapper();

        dynamicOnlyService(candidateMapper, taskMapper, 999L).createInstanceForSubmit(transfer());

        assertThat(taskMapper.tasks).extracting(InvTransferApprovalTask::getNodeOrder).containsExactly(2, 3);
    }

    @Test
    @DisplayName("有店长时店长助理提交仍经过店长和上级")
    void shouldTreatAssistantAsEmployeeWhenManagerExists()
    {
        SecurityContextHolder.setUserId("110");
        SecurityContextHolder.setUserName("assistant");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .managerSort(5)
                .addDirect("dz", leader(101L, "manager", 5))
                .addHigher(leader(210L, "upper", 4));
        FakeTaskMapper taskMapper = new FakeTaskMapper();

        dynamicOnlyService(candidateMapper, taskMapper, 999L).createInstanceForSubmit(transfer());

        assertThat(taskMapper.tasks).extracting(InvTransferApprovalTask::getNodeOrder).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("三级候选提交会跳过四级和三级")
    void shouldSkipBothDynamicNodesWhenUpperLeaderSubmits()
    {
        SecurityContextHolder.setUserId("210");
        SecurityContextHolder.setUserName("upper");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .managerSort(5)
                .addDirect("dz", leader(101L, "manager", 5))
                .addHigher(leader(210L, "upper", 4));
        FakeTaskMapper taskMapper = new FakeTaskMapper();

        InvTransferApprovalInstance instance = dynamicOnlyService(candidateMapper, taskMapper, 999L)
                .createInstanceForSubmit(transfer());

        assertThat(instance.getCurrentNodeOrder()).isEqualTo(3);
        assertThat(taskMapper.tasks).extracting(InvTransferApprovalTask::getNodeOrder).containsExactly(3);
        assertThat(candidateMapper.directCalls).containsExactly("202:dz");
        assertThat(candidateMapper.lastManagerPostSort).isEqualTo(5);
    }

    @Test
    @DisplayName("三级负责人发起固定四级流程时从运营总监开始")
    void shouldSkipThroughLevel3WhenLevel3CandidateSubmits()
    {
        SecurityContextHolder.setUserId("20");
        SecurityContextHolder.setUserName("level3Leader");
        FakeTaskMapper tasks = new FakeTaskMapper();

        InvTransferApprovalInstance instance = fourLevelService(standardFourLevelCandidates(), tasks)
                .createInstanceForSubmit(transfer());

        assertThat(tasks.tasks).extracting(InvTransferApprovalTask::getNodeOrder)
                .containsExactly(3, 4);
        assertThat(instance.getRuleSnapshot()).contains(
                "\"managerPostSort\":5", "\"executiveBoundarySort\":1");
    }

    @Test
    @DisplayName("运营总监发起固定四级流程时只保留总经理")
    void shouldSkipThroughOperationsDirectorWhenDirectorSubmits()
    {
        SecurityContextHolder.setUserId("30");
        SecurityContextHolder.setUserName("operationsDirector");
        FakeTaskMapper tasks = new FakeTaskMapper();

        fourLevelService(standardFourLevelCandidates(), tasks)
                .createInstanceForSubmit(transfer());

        assertThat(tasks.tasks).extracting(InvTransferApprovalTask::getNodeOrder)
                .containsExactly(4);
    }

    @Test
    @DisplayName("运营总监缺失时阻止提交")
    void shouldBlockSubmitWhenOperationsDirectorMissing()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("employee");
        FakeCandidateMapper candidates = new FakeCandidateMapper()
                .managerSort(5)
                .addDirect("dz", leader(10L, "storeManager", 5))
                .addHigher(leader(20L, "level3Leader", 4))
                .add(202L, "zjl", leader(40L, "generalManager", 1));

        assertThatThrownBy(() -> fourLevelService(candidates, new FakeTaskMapper())
                .createInstanceForSubmit(transfer()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("目标门店未配置有效运营总监");
    }

    @Test
    @DisplayName("总经理发起调拨时阻止提交")
    void shouldBlockSubmitWhenGeneralManagerIsSubmitter()
    {
        SecurityContextHolder.setUserId("40");
        SecurityContextHolder.setUserName("generalManager");

        assertThatThrownBy(() -> fourLevelService(
                standardFourLevelCandidates(), new FakeTaskMapper())
                .createInstanceForSubmit(transfer()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("总经理不能发起");
    }

    @Test
    @DisplayName("固定四级流程的目标门店无效时阻止提交")
    void shouldBlockSubmitWhenTargetStoreIsInvalid()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("employee");

        assertThatThrownBy(() -> fourLevelService(
                standardFourLevelCandidates().invalidTargetStore(), new FakeTaskMapper())
                .createInstanceForSubmit(transfer()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("调拨目标门店无效");
    }

    @Test
    @DisplayName("规则候选预览返回四节点和岗位边界")
    void shouldPreviewFourApprovalNodesForTargetStore()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("employee");

        InvTransferApprovalPreview preview = fourLevelService(
                standardFourLevelCandidates(), new FakeTaskMapper())
                .previewCandidates(100L, 202L, 202L);

        assertThat(preview.getNodes())
                .extracting(InvTransferApprovalPreview.Node::getNodeRole)
                .containsExactly("level4_highest", "level3_highest",
                        "operations_director", "general_manager");
        assertThat(preview.isBlocked()).isFalse();
        assertThat(preview.getManagerPostSort()).isEqualTo(5);
        assertThat(preview.getExecutiveBoundarySort()).isEqualTo(1);
        assertThat(preview.getNodes().get(0).getCandidateDisplayNames())
                .containsExactly("storeManager");
    }

    @Test
    @DisplayName("候选预览明确返回运营总监缺失阻断原因")
    void shouldPreviewMissingOperationsDirectorAsBlocked()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("employee");
        FakeCandidateMapper candidates = new FakeCandidateMapper()
                .managerSort(5)
                .add(202L, "zjl", leader(40L, "generalManager", 1));

        InvTransferApprovalPreview preview = fourLevelService(candidates, new FakeTaskMapper())
                .previewCandidates(100L, 202L, 202L);

        assertThat(preview.isBlocked()).isTrue();
        assertThat(preview.getBlockedReason()).contains("目标门店未配置有效运营总监");
        assertThat(preview.getNodes().get(2).getState()).isEqualTo("blocked");
    }

    @Test
    @DisplayName("目标门店无效时返回单独提醒并保留固定审批")
    void shouldWarnSeparatelyWhenTargetStoreIsInvalid()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("employee");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper().invalidTargetStore();
        FakeTaskMapper taskMapper = new FakeTaskMapper();

        InvTransferApprovalInstance instance = dynamicOnlyService(candidateMapper, taskMapper, 999L)
                .createInstanceForSubmit(transfer());

        assertThat(taskMapper.tasks).extracting(InvTransferApprovalTask::getNodeOrder).containsExactly(3);
        assertThat(approvalWarnings(instance)).containsExactly(
                "调拨目标门店无效，无法匹配四级、三级负责人，本次审批直接进入已配置的固定审批");
    }

    @Test
    @DisplayName("缺少四级负责人时继续三级审批并返回提醒")
    void shouldWarnAndContinueWhenLevel4LeaderMissing()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("employee");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .managerSort(5)
                .addHigher(leader(201L, "deptLeader", 4))
                .add(202L, "level2", user(301L, "level2Approver"));
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "四级负责人（店长/店助）", "level4_highest", null),
                        node(2, "三级负责人（店长上一级）", "level3_highest", null),
                        node(3, "二级审批", "to_leader", "level2")),
                candidateMapper, new FakeInstanceMapper(), taskMapper,
                new FakeDeptScopeMapper()
                        .addUserShop(201L, 202L)
                        .addUserShop(301L, 202L));

        InvTransferApprovalInstance instance = service.createInstanceForSubmit(transfer());

        assertThat(taskMapper.tasks).extracting(InvTransferApprovalTask::getNodeOrder).containsExactly(2, 3);
        assertThat(approvalWarnings(instance)).containsExactly(
                "未找到目标门店四级负责人，本次审批从三级负责人开始");
    }

    @Test
    @DisplayName("缺少三级负责人时跳过三级并返回提醒")
    void shouldWarnAndContinueWhenLevel3LeaderMissing()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("employee");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .managerSort(5)
                .addDirect("dz", leader(101L, "storeLeader", 5))
                .add(202L, "level2", user(301L, "level2Approver"));
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "四级负责人（店长/店助）", "level4_highest", null),
                        node(2, "三级负责人（店长上一级）", "level3_highest", null),
                        node(3, "二级审批", "to_leader", "level2")),
                candidateMapper, new FakeInstanceMapper(), taskMapper,
                new FakeDeptScopeMapper()
                        .addUserShop(101L, 202L)
                        .addUserShop(301L, 202L));

        InvTransferApprovalInstance instance = service.createInstanceForSubmit(transfer());

        assertThat(taskMapper.tasks).extracting(InvTransferApprovalTask::getNodeOrder).containsExactly(1, 3);
        assertThat(approvalWarnings(instance)).containsExactly(
                "未找到目标门店三级负责人，本次审批跳过三级并进入已配置的固定审批");
    }

    @Test
    @DisplayName("四级三级负责人都缺少时直接进入固定审批并返回合并提醒")
    void shouldWarnAndContinueWhenBothDynamicLeadersMissing()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("employee");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .managerSort(5)
                .add(202L, "level2", user(301L, "level2Approver"));
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "四级负责人（店长/店助）", "level4_highest", null),
                        node(2, "三级负责人（店长上一级）", "level3_highest", null),
                        node(3, "二级审批", "to_leader", "level2")),
                candidateMapper, new FakeInstanceMapper(), taskMapper,
                new FakeDeptScopeMapper().addUserShop(301L, 202L));

        InvTransferApprovalInstance instance = service.createInstanceForSubmit(transfer());

        assertThat(instance.getCurrentNodeOrder()).isEqualTo(3);
        assertThat(taskMapper.tasks).extracting(InvTransferApprovalTask::getNodeOrder).containsExactly(3);
        assertThat(approvalWarnings(instance)).containsExactly(
                "未找到目标门店四级、三级负责人，本次审批直接进入已配置的固定审批");
    }

    @Test
    @DisplayName("高级岗位提交调拨单也不能绕过有效审批节点")
    void shouldNotAutoApproveWhenSeniorPostSubmitsTransfer()
    {
        SecurityContextHolder.setUserId("301");
        SecurityContextHolder.setUserName("operationDirector");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .addUserPost(301L, "yyzj")
                .add(101L, "yyjl", user(201L, "operationManager"));
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(301L, 201L)
                .addUserShop(201L, 202L)
                .addAncestorDepts(202L, 101L);
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "店长", "post", "dz"),
                        node(2, "运营经理", "post", "yyjl"),
                        node(3, "运营总监", "post", "yyzj")),
                candidateMapper, instanceMapper, taskMapper, deptScopeMapper);

        InvTransferApprovalInstance instance = service.createInstanceForSubmit(transfer());

        assertThat(instance.getStatus()).isEqualTo("running");
        assertThat(instance.getCurrentNodeOrder()).isEqualTo(2);
        assertThat(instanceMapper.instances).hasSize(1);
        assertThat(taskMapper.tasks).hasSize(1);
        assertThat(taskMapper.tasks.get(0).getCandidateUserIds()).isEqualTo("201");
    }

    @Test
    @DisplayName("审批规则没有有效候选人时不能自动通过")
    void shouldRejectWhenApprovalRuleHasNoEffectiveCandidates()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("submitter");
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz")),
                new FakeCandidateMapper(), new FakeInstanceMapper(), new FakeTaskMapper());

        assertThatThrownBy(() -> service.createInstanceForSubmit(transfer()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("审批规则未配置有效审批人");
    }

    @Test
    @DisplayName("规则不允许自审时阻止全候选人都是提交人")
    void shouldBlockSelfApprovalWhenRuleDisallowsSelfApprove()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("submitter");
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .add(201L, "dz", user(101L, "submitter"));
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(101L, 201L);
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz")),
                candidateMapper, new FakeInstanceMapper(), new FakeTaskMapper(), deptScopeMapper);

        assertThatThrownBy(() -> service.createInstanceForSubmit(transfer()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能自审");
    }

    @Test
    @DisplayName("候选人权限按任务快照判断而不重新查询岗位成员")
    void shouldUseCandidateSnapshotEvenWhenPostMembersChangeLater()
    {
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper();
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz")),
                candidateMapper, new FakeInstanceMapper(), new FakeTaskMapper());
        InvTransferApprovalTask task = new InvTransferApprovalTask();
        task.setStatus("pending");
        task.setCandidateUserIds("101,102");

        assertThat(service.canApproveTask(task, 102L)).isTrue();
        assertThat(service.canApproveTask(task, 103L)).isFalse();
        assertThat(candidateMapper.calls).isEmpty();
    }

    @Test
    @DisplayName("审批调拨单前必须校验当前用户可选择该店铺")
    void shouldRejectApprovalWhenUserCannotSelectShop()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("fromManager");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper, orderMapper, new FakeStatusLogMapper(),
                new FakeDeptScopeMapper());
        orderMapper.stored = submittedTransfer("submitter", "700");
        instanceMapper.instances.add(runningInstance("back_to_draft", "1"));
        taskMapper.tasks.add(pendingTask(901L, 1, "dz", "101"));

        assertThatThrownBy(() -> service.approve(approvalRequest(901L, "approve", "同意"), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权选择该店铺");
    }

    @Test
    @DisplayName("审批调拨单前必须校验调拨单属于当前店铺范围")
    void shouldRejectApprovalWhenTransferIsOutsideSelectedShopScope()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("fromManager");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(101L, 201L);
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper, orderMapper, new FakeStatusLogMapper(),
                deptScopeMapper);
        InvTransferOrder transfer = submittedTransfer("submitter", "700");
        transfer.setFromDeptId(301L);
        transfer.setToDeptId(302L);
        orderMapper.stored = transfer;
        instanceMapper.instances.add(runningInstance("back_to_draft", "1"));
        taskMapper.tasks.add(pendingTask(901L, 1, "dz", "101"));

        assertThatThrownBy(() -> service.approve(approvalRequest(901L, "approve", "同意"), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权审批该调拨单");
    }

    @Test
    @DisplayName("审批人有店铺范围且调拨单命中该范围时允许审批")
    void shouldApproveWhenSelectedShopCanSeeTransfer()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("fromManager");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(101L, 201L)
                .addScopedDept(201L, 201L);
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper, orderMapper, new FakeStatusLogMapper(),
                deptScopeMapper);
        orderMapper.stored = submittedTransfer("submitter", "700");
        instanceMapper.instances.add(runningInstance("back_to_draft", "1"));
        taskMapper.tasks.add(pendingTask(901L, 1, "dz", "101"));

        service.approve(approvalRequest(901L, "approve", "同意"), 201L);

        assertThat(taskMapper.tasks.get(0).getStatus()).isEqualTo("approved");
    }

    @Test
    @DisplayName("同级并列候选任意一人通过后节点只推进一次")
    void shouldAdvanceTiedCandidateNodeOnlyOnce()
    {
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(101L, 202L)
                .addUserShop(102L, 202L);
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "四级负责人（店长/店助）", "level4_highest", null),
                        node(2, "三级负责人（店长上一级）", "level3_highest", null)),
                new FakeCandidateMapper(), instanceMapper, taskMapper, orderMapper,
                new FakeStatusLogMapper(), deptScopeMapper);
        orderMapper.stored = submittedTransfer("submitter", "700");
        InvTransferApprovalInstance instance = runningInstance("back_to_draft", "0");
        instance.setRuleSnapshot(snapshot("all_nodes", 0,
                node(1, "四级负责人（店长/店助）", "level4_highest", null),
                node(2, "三级负责人（店长上一级）", "level3_highest", null)));
        instanceMapper.instances.add(instance);
        InvTransferApprovalTask tiedTask = pendingTask(901L, 1, null, "101,102");
        taskMapper.tasks.add(tiedTask);
        taskMapper.tasks.add(pendingTask(902L, 2, null, "201"));

        SecurityContextHolder.setUserId("102");
        SecurityContextHolder.setUserName("managerB");
        service.approve(approvalRequest(901L, "approve", "同意"), 202L);

        assertThat(tiedTask.getStatus()).isEqualTo("approved");
        assertThat(instance.getCurrentNodeOrder()).isEqualTo(2);

        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("managerA");
        assertThatThrownBy(() -> service.approve(
                approvalRequest(901L, "approve", "重复审批"), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("审批任务不属于当前审批节点");
        assertThat(instance.getCurrentNodeOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("taskId为空时按当前节点和当前用户自动审批最终节点")
    void shouldApproveFinalNodeAndMoveTransferToApprovedWhenTaskIdMissing()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("approver");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper, orderMapper, statusLogMapper,
                approvalScope(101L, 201L));
        orderMapper.stored = submittedTransfer("submitter", "700");
        instanceMapper.instances.add(runningInstance("back_to_draft", "0"));
        taskMapper.tasks.add(pendingTask(901L, 1, "dz", "101"));
        InvTransferApprovalRequest request = approvalRequest(null, "approve", "同意调拨");

        service.approve(request, 201L);

        InvTransferApprovalTask task = taskMapper.tasks.get(0);
        assertThat(task.getStatus()).isEqualTo("approved");
        assertThat(task.getApproverId()).isEqualTo(101L);
        assertThat(task.getApproverName()).isEqualTo("approver");
        assertThat(task.getApproveTime()).isNotNull();
        assertThat(task.getComment()).isEqualTo("同意调拨");
        assertThat(instanceMapper.instances.get(0).getStatus()).isEqualTo("approved");
        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.APPROVED);
        assertThat(orderMapper.stored.getApprovedTime()).isNotNull();
        assertThat(statusLogMapper.logs).hasSize(1);
        assertThat(statusLogMapper.logs.get(0).getFromStatus()).isEqualTo(InvStatusConstants.SUBMITTED);
        assertThat(statusLogMapper.logs.get(0).getToStatus()).isEqualTo(InvStatusConstants.APPROVED);
        assertThat(statusLogMapper.logs.get(0).getAction()).isEqualTo("approve");
    }

    @Test
    @DisplayName("驳回策略为回到草稿时不归档")
    void shouldRejectToDraftWhenRejectActionBackToDraft()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("approver");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper, orderMapper, statusLogMapper,
                approvalScope(101L, 201L));
        orderMapper.stored = submittedTransfer("submitter", "700");
        InvTransferReservationService reservationService =
                org.mockito.Mockito.mock(
                        InvTransferReservationService.class);
        ReflectionTestUtils.setField(service, "transferReservationService",
                reservationService);
        InvTransferRevisionService revisionService =
                org.mockito.Mockito.mock(InvTransferRevisionService.class);
        ReflectionTestUtils.setField(service, "transferRevisionService",
                revisionService);
        instanceMapper.instances.add(runningInstance("back_to_draft", "0"));
        taskMapper.tasks.add(pendingTask(901L, 1, "dz", "101"));

        service.approve(approvalRequest(901L, "reject", "退回修改"), 201L);

        assertThat(taskMapper.tasks.get(0).getStatus()).isEqualTo("rejected");
        assertThat(instanceMapper.instances.get(0).getStatus()).isEqualTo("rejected");
        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.DRAFT);
        assertThat(orderMapper.stored.getArchivedTime()).isNull();
        assertThat(statusLogMapper.logs.get(0).getToStatus()).isEqualTo(InvStatusConstants.DRAFT);
        org.mockito.Mockito.verify(reservationService)
                .releaseAllRemaining(orderMapper.stored, "approver");
        org.mockito.Mockito.verify(revisionService).recordApprovalOutcome(
                orderMapper.stored,
                com.erp.inventory.constant.InvTransferRevisionStatuses.REJECTED,
                InvStatusConstants.DRAFT, "reject", "退回修改", 101L,
                "approver", 500L);
    }

    @Test
    @DisplayName("驳回策略为已驳回时进入记录并设置归档时间")
    void shouldRejectToRecordWhenRejectActionRejected()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("approver");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper, orderMapper, statusLogMapper,
                approvalScope(101L, 201L));
        orderMapper.stored = submittedTransfer("submitter", "700");
        instanceMapper.instances.add(runningInstance("rejected", "0"));
        taskMapper.tasks.add(pendingTask(901L, 1, "dz", "101"));

        service.approve(approvalRequest(901L, "reject", "资料不完整"), 201L);

        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.REJECTED);
        assertThat(orderMapper.stored.getArchivedTime()).isNotNull();
        assertThat(orderMapper.stored.getCloseReason()).isEqualTo("资料不完整");
        assertThat(statusLogMapper.logs.get(0).getToStatus()).isEqualTo(InvStatusConstants.REJECTED);
    }

    @Test
    @DisplayName("taskId为空且当前用户没有待审批任务时给出明确错误")
    void shouldRejectWhenTaskIdMissingAndNoCurrentUserPendingTask()
    {
        SecurityContextHolder.setUserId("102");
        SecurityContextHolder.setUserName("other");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper, orderMapper, new FakeStatusLogMapper(),
                approvalScope(102L, 201L));
        orderMapper.stored = submittedTransfer("submitter", "700");
        instanceMapper.instances.add(runningInstance("back_to_draft", "0"));
        taskMapper.tasks.add(pendingTask(901L, 1, "dz", "101"));

        assertThatThrownBy(() -> service.approve(approvalRequest(null, "approve", ""), 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未找到当前用户可审批的待审批任务");
    }

    @Test
    @DisplayName("审批请求拒绝无效ID、超长意见和空泛驳回原因")
    void shouldValidateApprovalCommandBoundary()
    {
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz")),
                new FakeCandidateMapper(), new FakeInstanceMapper(),
                new FakeTaskMapper(), new FakeTransferOrderMapper(),
                new FakeStatusLogMapper(), approvalScope(101L, 201L));

        InvTransferApprovalRequest invalidTransfer = approvalRequest(
                901L, "approve", "同意");
        invalidTransfer.setTransferId(0L);
        assertThatThrownBy(() -> service.approve(invalidTransfer, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("调拨单ID必须为正整数");

        InvTransferApprovalRequest invalidTask = approvalRequest(
                0L, "approve", "同意");
        assertThatThrownBy(() -> service.approve(invalidTask, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("审批任务ID必须为正整数");

        InvTransferApprovalRequest shortReason = approvalRequest(
                901L, "reject", " 不同意 ");
        assertThatThrownBy(() -> service.approve(shortReason, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("驳回原因至少填写4个字符");

        InvTransferApprovalRequest longComment = approvalRequest(
                901L, "approve", "a".repeat(501));
        assertThatThrownBy(() -> service.approve(longComment, 201L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("审批意见不能超过500个字符");
    }

    @Test
    @DisplayName("审批推进使用提交时快照而不受当前规则修改影响")
    void shouldApproveWithSnapshotEvenWhenCurrentRuleChanged()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("approver");
        InvTransferApprovalNode changedNode = node(1, "发货店长", "from_leader", "dz");
        changedNode.setApprovalMode("quorum");
        changedNode.setRequiredCount(2);
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferApprovalServiceImpl service = newService(
                rule(changedNode), new FakeCandidateMapper(), instanceMapper, taskMapper, orderMapper,
                new FakeStatusLogMapper(), approvalScope(101L, 201L));
        orderMapper.stored = submittedTransfer("submitter", "700");
        InvTransferApprovalInstance instance = runningInstance("back_to_draft", "0");
        instance.setRuleSnapshot(snapshot("all_nodes", 0, node(1, "发货店长", "from_leader", "dz")));
        instanceMapper.instances.add(instance);
        taskMapper.tasks.add(pendingTask(901L, 1, "dz", "101"));

        service.approve(approvalRequest(901L, "approve", "同意"), 201L);

        assertThat(instanceMapper.instances.get(0).getStatus()).isEqualTo("approved");
        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.APPROVED);
    }

    @Test
    @DisplayName("实例级任一通过按快照直接完成而不是继续推进后续节点")
    void shouldApproveInstanceWhenSnapshotApprovalModeAnyOne()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("approver");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz"), node(2, "收货店长", "to_leader", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper, orderMapper, new FakeStatusLogMapper(),
                approvalScope(101L, 201L));
        orderMapper.stored = submittedTransfer("submitter", "700");
        InvTransferApprovalInstance instance = runningInstance("back_to_draft", "0");
        instance.setApprovalMode("any_one");
        instance.setRuleSnapshot(snapshot("any_one", 0,
                node(1, "发货店长", "from_leader", "dz"), node(2, "收货店长", "to_leader", "dz")));
        instanceMapper.instances.add(instance);
        taskMapper.tasks.add(pendingTask(901L, 1, "dz", "101"));
        taskMapper.tasks.add(pendingTask(902L, 2, "dz", "202"));

        service.approve(approvalRequest(901L, "approve", "同意"), 201L);

        assertThat(instanceMapper.instances.get(0).getStatus()).isEqualTo("approved");
        assertThat(instanceMapper.instances.get(0).getCurrentNodeOrder()).isEqualTo(1);
        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.APPROVED);
    }

    @Test
    @DisplayName("实例级所有岗位按节点岗位快照判定而不合并同岗位编码")
    void shouldRequireAllNodePostsWhenSamePostCodeAppearsInDifferentNodes()
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("fromApprover");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(101L, 201L)
                .addUserShop(202L, 202L);
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz"), node(2, "收货店长", "to_leader", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper, orderMapper, new FakeStatusLogMapper(),
                deptScopeMapper);
        orderMapper.stored = submittedTransfer("submitter", "700");
        InvTransferApprovalInstance instance = runningInstance("back_to_draft", "0");
        instance.setApprovalMode("all_posts");
        instance.setRuleSnapshot(snapshot("all_posts", 0,
                node(1, "发货店长", "from_leader", "dz"), node(2, "收货店长", "to_leader", "dz")));
        instanceMapper.instances.add(instance);
        taskMapper.tasks.add(pendingTask(901L, 1, "dz", "101"));
        taskMapper.tasks.add(pendingTask(902L, 2, "dz", "202"));

        service.approve(approvalRequest(901L, "approve", "发货方同意"), 201L);

        assertThat(instanceMapper.instances.get(0).getStatus()).isEqualTo("running");
        assertThat(instanceMapper.instances.get(0).getCurrentNodeOrder()).isEqualTo(2);
        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.SUBMITTED);

        SecurityContextHolder.setUserId("202");
        SecurityContextHolder.setUserName("toApprover");
        service.approve(approvalRequest(902L, "approve", "收货方同意"), 202L);

        assertThat(instanceMapper.instances.get(0).getStatus()).isEqualTo("approved");
        assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.APPROVED);
    }

    @Test
    @DisplayName("驳回回草稿后重新提交会创建新的审批实例")
    void shouldCreateNewInstanceAfterRejectedBackToDraftResubmission()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("submitter");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        InvTransferApprovalInstance oldInstance = runningInstance("back_to_draft", "0");
        oldInstance.setStatus("rejected");
        instanceMapper.instances.add(oldInstance);
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .add(201L, "dz", user(101L, "fromManager"));
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(101L, 201L);
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "发货店长", "from_leader", "dz")),
                candidateMapper, instanceMapper, new FakeTaskMapper(), deptScopeMapper);

        InvTransferApprovalInstance newInstance = service.createInstanceForSubmit(transfer());

        assertThat(instanceMapper.instances).hasSize(2);
        assertThat(newInstance.getStatus()).isEqualTo("running");
        assertThat(newInstance.getInstanceId()).isNotEqualTo(oldInstance.getInstanceId());
    }

    @Test
    @DisplayName("取消调拨关闭全部运行中实例并跳过未处理任务")
    void shouldCloseRunningInstancesAndSkipPendingTasksOnCancellation()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("submitter");
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        InvTransferApprovalInstance first = runningInstance(
                "back_to_draft", "0");
        InvTransferApprovalInstance second = runningInstance(
                "back_to_draft", "0");
        second.setInstanceId(501L);
        InvTransferApprovalInstance history = runningInstance(
                "back_to_draft", "0");
        history.setInstanceId(400L);
        history.setStatus("rejected");
        instanceMapper.instances.add(history);
        instanceMapper.instances.add(first);
        instanceMapper.instances.add(second);
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        taskMapper.tasks.add(trackTask(701L, 500L, 1, "店长",
                "approved", "101", "王店长", 101L, "王店长"));
        taskMapper.tasks.add(trackTask(702L, 500L, 2, "运营经理",
                "pending", "102", "李经理", null, null));
        taskMapper.tasks.add(trackTask(703L, 501L, 1, "店长",
                "pending", "103", "张店长", null, null));
        InvTransferApprovalTask unrelated = trackTask(704L, 600L, 1,
                "店长", "pending", "104", "其他店长", null, null);
        unrelated.setTransferId(301L);
        taskMapper.tasks.add(unrelated);
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "店长", "post", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper);

        service.cancelRunning(300L);

        assertThat(instanceMapper.instances)
                .filteredOn(instance -> instance.getTransferId().equals(300L))
                .extracting(InvTransferApprovalInstance::getStatus)
                .containsExactly("rejected", "closed", "closed");
        assertThat(taskMapper.tasks)
                .filteredOn(task -> task.getTransferId().equals(300L))
                .extracting(InvTransferApprovalTask::getStatus)
                .containsExactly("approved", "skipped", "skipped");
        assertThat(unrelated.getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("审批轨迹区分已通过当前等待节点并隐藏原始账号")
    void shouldBuildSafeCurrentApprovalTrack() throws Exception
    {
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        InvTransferApprovalInstance instance = runningInstance("back_to_draft", "0");
        instance.setCurrentNodeOrder(2);
        instance.setRuleSnapshot(snapshot("all_nodes", 0,
                node(1, "四级负责人", "post", "dz"),
                node(2, "三级负责人", "post", "yyjl"),
                node(3, "运营总监", "post", "yyzj")));
        instanceMapper.instances.add(instance);
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        taskMapper.tasks.add(trackTask(701L, 500L, 1, "四级负责人", "approved",
                "101", "13800138000", 101L, "13800138000"));
        taskMapper.tasks.add(trackTask(702L, 500L, 2, "三级负责人", "pending",
                "102,103", "13800138001,李四", null, null));
        taskMapper.tasks.add(trackTask(703L, 500L, 3, "运营总监", "pending",
                "104", "王五", null, null));
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .displayName(101L, "王店长")
                .displayName(102L, "张三")
                .displayName(103L, "李四")
                .displayName(104L, "王五");
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "四级负责人", "post", "dz")),
                candidateMapper, instanceMapper, taskMapper);
        InvTransferOrder order = submittedTransfer("sender", "TF300");

        InvTransferApprovalTrack track = service.selectTrack(order);

        assertThat(track.getState()).isEqualTo("in_progress");
        assertThat(track.getCurrentNode().getNodeName()).isEqualTo("三级负责人");
        assertThat(track.getCurrentNode().getCandidateDisplayNames()).containsExactly("张三", "李四");
        assertThat(track.getRounds().get(0).getNodes())
                .extracting(InvTransferApprovalTrack.Node::getState)
                .containsExactly("approved", "current", "waiting");
        assertThat(track.getRounds().get(0).getNodes().get(0).getActualApproverDisplayName())
                .isEqualTo("王店长");
        assertThat(track.getRounds().get(0).getNodes())
                .flatExtracting(InvTransferApprovalTrack.Node::getCandidateDisplayNames)
                .doesNotContain("13800138000", "13800138001");

        InvTransferApprovalSummary publicSummary = new InvTransferApprovalSummary();
        publicSummary.setTransferId(300L);
        publicSummary.setCandidateUserIds("101,102");
        publicSummary.setCandidateUserNames("13800138000,person@example.com");
        publicSummary.setCurrentCandidateDisplayNames(List.of("王店长", "张三"));
        String publicJson = new ObjectMapper().writeValueAsString(
                Map.of("track", track, "summary", publicSummary));
        assertThat(publicJson)
                .doesNotContain("candidateUserIds", "candidateUserNames", "approverId", "taskId",
                        "postCode", "ruleSnapshot", "13800138000", "person@example.com");
    }

    @Test
    @DisplayName("审批轨迹保留驳回后重新提交的多轮历史")
    void shouldBuildRejectedAndRunningApprovalRounds()
    {
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        InvTransferApprovalInstance rejected = runningInstance("back_to_draft", "0");
        rejected.setInstanceId(400L);
        rejected.setStatus("rejected");
        rejected.setCurrentNodeOrder(1);
        rejected.setRuleSnapshot(snapshot("all_nodes", 0, node(1, "店长", "post", "dz")));
        InvTransferApprovalInstance running = runningInstance("back_to_draft", "0");
        running.setInstanceId(500L);
        instanceMapper.instances.add(rejected);
        instanceMapper.instances.add(running);
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        taskMapper.tasks.add(trackTask(401L, 400L, 1, "店长", "rejected",
                "101", "oldAccount", 101L, "oldAccount"));
        taskMapper.tasks.add(trackTask(501L, 500L, 1, "店长", "pending",
                "102", "newAccount", null, null));
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper()
                .displayName(101L, "王店长")
                .displayName(102L, "张店长");
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "店长", "post", "dz")),
                candidateMapper, instanceMapper, taskMapper);

        InvTransferApprovalTrack track = service.selectTrack(submittedTransfer("sender", "TF300"));

        assertThat(track.getRounds()).extracting(InvTransferApprovalTrack.Round::getRoundNo)
                .containsExactly(1, 2);
        assertThat(track.getRounds()).extracting(InvTransferApprovalTrack.Round::getStatus)
                .containsExactly("rejected", "in_progress");
        assertThat(track.getRounds().get(0).getNodes().get(0).getState()).isEqualTo("rejected");
        assertThat(track.getRounds().get(1).getNodes().get(0).getState()).isEqualTo("current");
    }

    @Test
    @DisplayName("已取消调拨覆盖运行中实例并终止所有未完成节点")
    void shouldTerminateApprovalTrackWhenTransferCancelled()
    {
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        InvTransferApprovalInstance instance = runningInstance("back_to_draft", "0");
        instance.setRuleSnapshot(snapshot("all_nodes", 0,
                node(1, "店长", "post", "dz"), node(2, "运营经理", "post", "yyjl")));
        instanceMapper.instances.add(instance);
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        taskMapper.tasks.add(trackTask(501L, 500L, 1, "店长", "pending", "101", "manager", null, null));
        taskMapper.tasks.add(trackTask(502L, 500L, 2, "运营经理", "pending", "102", "leader", null, null));
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "店长", "post", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper);
        InvTransferOrder order = submittedTransfer("sender", "TF300");
        order.setStatus(InvStatusConstants.CANCELLED);

        InvTransferApprovalTrack track = service.selectTrack(order);

        assertThat(track.getState()).isEqualTo("cancelled");
        assertThat(track.getRounds().get(0).getNodes())
                .extracting(InvTransferApprovalTrack.Node::getState)
                .containsExactly("terminated", "terminated");
        assertThat(track.getCurrentNode()).isNull();
    }

    @Test
    @DisplayName("草稿保留已关闭历史审批时显示尚未提交")
    void shouldTreatClosedApprovalHistoryAsNotStartedForDraft()
    {
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        InvTransferApprovalInstance instance = runningInstance(
                "back_to_draft", "0");
        instance.setStatus("closed");
        instanceMapper.instances.add(instance);
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        taskMapper.tasks.add(trackTask(501L, 500L, 1, "店长", "cancelled",
                "101", "manager", null, null));
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "店长", "post", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper);
        InvTransferOrder order = submittedTransfer("sender", "TF300");
        order.setStatus(InvStatusConstants.DRAFT);

        InvTransferApprovalTrack track = service.selectTrack(order);

        assertThat(track.getState()).isEqualTo("not_started");
        assertThat(track.getSummaryText()).isEqualTo("尚未提交审批");
        assertThat(track.getRounds()).hasSize(1);
    }

    @Test
    @DisplayName("无人工任务的自动通过实例返回自动通过节点")
    void shouldBuildAutoApprovedTrackFromStatusLog()
    {
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        InvTransferApprovalInstance instance = runningInstance("back_to_draft", "0");
        instance.setStatus("approved");
        instanceMapper.instances.add(instance);
        FakeStatusLogMapper statusLogMapper = new FakeStatusLogMapper();
        InvTransferStatusLog log = new InvTransferStatusLog();
        log.setTransferId(300L);
        log.setAction("auto_approve");
        log.setReason("审批规则无有效候选人");
        log.setCreateTime(new Date(123456789L));
        statusLogMapper.logs.add(log);
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "店长", "post", "dz")),
                new FakeCandidateMapper(), instanceMapper, new FakeTaskMapper(),
                new FakeTransferOrderMapper(), statusLogMapper);
        InvTransferOrder order = submittedTransfer("sender", "TF300");
        order.setStatus(InvStatusConstants.APPROVED);

        InvTransferApprovalTrack track = service.selectTrack(order);

        assertThat(track.getState()).isEqualTo("auto_approved");
        assertThat(track.getRounds().get(0).getNodes()).singleElement().satisfies(node -> {
            assertThat(node.getState()).isEqualTo("auto_approved");
            assertThat(node.getDecision()).isEqualTo("auto_approve");
            assertThat(node.getComment()).isEqualTo("审批规则无有效候选人");
        });
    }

    @Test
    @DisplayName("运行实例缺少当前任务时返回部分轨迹")
    void shouldMarkTrackPartialWhenCurrentTaskMissing()
    {
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        instanceMapper.instances.add(runningInstance("back_to_draft", "0"));
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "店长", "post", "dz")),
                new FakeCandidateMapper(), instanceMapper, new FakeTaskMapper());

        InvTransferApprovalTrack track = service.selectTrack(submittedTransfer("sender", "TF300"));

        assertThat(track.getState()).isEqualTo("partial");
        assertThat(track.getTraceCompleteness()).isEqualTo("partial");
        assertThat(track.getSummaryText()).contains("数据异常");
    }

    @Test
    @DisplayName("非草稿调拨缺少审批实例时不得误报尚未提交")
    void shouldMarkTrackPartialWhenSubmittedTransferHasNoInstance()
    {
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "店长", "post", "dz")),
                new FakeCandidateMapper(), new FakeInstanceMapper(), new FakeTaskMapper());

        InvTransferApprovalTrack track = service.selectTrack(submittedTransfer("sender", "TF300"));

        assertThat(track.getState()).isEqualTo("partial");
        assertThat(track.getTraceCompleteness()).isEqualTo("partial");
        assertThat(track.getSummaryText()).contains("数据异常");
    }

    @Test
    @DisplayName("列表审批摘要用安全展示名替换手机号式账号")
    void shouldSanitizeApprovalSummaryCandidateNames()
    {
        InvTransferApprovalSummary summary = new InvTransferApprovalSummary();
        summary.setTransferId(300L);
        summary.setState("in_progress");
        summary.setRoundNo(1);
        summary.setCurrentNodeOrder(1);
        summary.setTotalNodeCount(2);
        summary.setCurrentNodeName("店长");
        summary.setCandidateUserIds("101,102");
        summary.setCandidateUserNames("13800138000,person@example.com");
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        taskMapper.summaries.add(summary);
        FakeCandidateMapper candidateMapper = new FakeCandidateMapper().displayName(101L, "王店长");
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "店长", "post", "dz")),
                candidateMapper, new FakeInstanceMapper(), taskMapper);

        Map<Long, InvTransferApprovalSummary> summaries = service.selectApprovalSummaries(List.of(300L));

        assertThat(summaries.get(300L).getCurrentCandidateDisplayNames())
                .containsExactly("王店长", "姓名未配置");
        assertThat(summaries.get(300L).getSummaryText())
                .isEqualTo("当前第 1/2 级，等待店长审批");
    }

    @Test
    @DisplayName("已审批单据仍有未完成任务时阻止发货")
    void shouldBlockDeliveryWhenApprovedInstanceIsIncomplete()
    {
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        InvTransferApprovalInstance instance = approvedDeliveryInstance();
        instanceMapper.instances.add(instance);
        taskMapper.tasks.add(deliveryTask("pending"));
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "店长", "post", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper);

        assertThatThrownBy(() -> service.assertApprovedForDelivery(approvedDeliveryTransfer()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("审批任务未完整通过");
    }

    @Test
    @DisplayName("审批实例与任务完整通过时允许发货")
    void shouldAllowDeliveryWhenApprovalIntegrityIsComplete()
    {
        FakeInstanceMapper instanceMapper = new FakeInstanceMapper();
        FakeTaskMapper taskMapper = new FakeTaskMapper();
        instanceMapper.instances.add(approvedDeliveryInstance());
        taskMapper.tasks.add(deliveryTask("approved"));
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "店长", "post", "dz")),
                new FakeCandidateMapper(), instanceMapper, taskMapper);

        service.assertApprovedForDelivery(approvedDeliveryTransfer());
    }

    @Test
    @DisplayName("无审批实例关联的已审批单据不得发货")
    void shouldBlockDeliveryWhenApprovalInstanceReferenceIsMissing()
    {
        InvTransferOrder transfer = approvedDeliveryTransfer();
        transfer.setApprovalInstanceId(null);
        InvTransferApprovalServiceImpl service = newService(
                rule(node(1, "店长", "post", "dz")),
                new FakeCandidateMapper(), new FakeInstanceMapper(), new FakeTaskMapper());

        assertThatThrownBy(() -> service.assertApprovedForDelivery(transfer))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未关联审批实例");
    }

    private static InvTransferOrder approvedDeliveryTransfer()
    {
        InvTransferOrder transfer = new InvTransferOrder();
        transfer.setTransferId(300L);
        transfer.setApprovalInstanceId(500L);
        transfer.setStatus(InvStatusConstants.APPROVED);
        return transfer;
    }

    private static InvTransferApprovalInstance approvedDeliveryInstance()
    {
        InvTransferApprovalInstance instance = new InvTransferApprovalInstance();
        instance.setInstanceId(500L);
        instance.setTransferId(300L);
        instance.setStatus("approved");
        instance.setApprovalMode("all_nodes");
        instance.setCurrentNodeOrder(1);
        instance.setRuleSnapshot("{\"approvalMode\":\"all_nodes\",\"nodes\":["
                + "{\"nodeOrder\":1,\"nodeName\":\"店长\",\"approvalMode\":\"any_one\",\"requiredCount\":1}]}");
        return instance;
    }

    private static InvTransferApprovalTask deliveryTask(String status)
    {
        InvTransferApprovalTask task = new InvTransferApprovalTask();
        task.setTaskId(600L);
        task.setInstanceId(500L);
        task.setTransferId(300L);
        task.setNodeOrder(1);
        task.setStatus(status);
        return task;
    }

    private static InvTransferApprovalServiceImpl newService(InvTransferApprovalRule rule,
            FakeCandidateMapper candidateMapper, FakeInstanceMapper instanceMapper, FakeTaskMapper taskMapper)
    {
        return newService(rule, candidateMapper, instanceMapper, taskMapper,
                new FakeTransferOrderMapper(), new FakeStatusLogMapper());
    }

    private static InvTransferApprovalServiceImpl newService(InvTransferApprovalRule rule,
            FakeCandidateMapper candidateMapper, FakeInstanceMapper instanceMapper, FakeTaskMapper taskMapper,
            FakeTransferOrderMapper orderMapper, FakeStatusLogMapper statusLogMapper)
    {
        return newService(rule, candidateMapper, instanceMapper, taskMapper, orderMapper, statusLogMapper,
                new FakeDeptScopeMapper());
    }

    private static InvTransferApprovalServiceImpl newService(InvTransferApprovalRule rule,
            FakeCandidateMapper candidateMapper, FakeInstanceMapper instanceMapper, FakeTaskMapper taskMapper,
            FakeDeptScopeMapper deptScopeMapper)
    {
        return newService(rule, candidateMapper, instanceMapper, taskMapper,
                new FakeTransferOrderMapper(), new FakeStatusLogMapper(), deptScopeMapper);
    }

    private static InvTransferApprovalServiceImpl newService(InvTransferApprovalRule rule,
            FakeCandidateMapper candidateMapper, FakeInstanceMapper instanceMapper, FakeTaskMapper taskMapper,
            FakeTransferOrderMapper orderMapper, FakeStatusLogMapper statusLogMapper, FakeDeptScopeMapper deptScopeMapper)
    {
        InvTransferApprovalServiceImpl service = new InvTransferApprovalServiceImpl();
        ReflectionTestUtils.setField(service, "ruleService", new FakeRuleService(rule));
        ReflectionTestUtils.setField(service, "candidateMapper", candidateMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        ReflectionTestUtils.setField(service, "instanceMapper", instanceMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "transferOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "statusLogMapper", statusLogMapper);
        ReflectionTestUtils.setField(service, "transferReservationService",
                org.mockito.Mockito.mock(
                        InvTransferReservationService.class));
        ReflectionTestUtils.setField(service, "transferRevisionService",
                org.mockito.Mockito.mock(
                        InvTransferRevisionService.class));
        return service;
    }

    private static InvTransferApprovalServiceImpl dynamicOnlyService(FakeCandidateMapper candidateMapper,
            FakeTaskMapper taskMapper, Long fixedUserId)
    {
        candidateMapper.add(202L, "fixed", user(fixedUserId, "fixedApprover"));
        return newService(
                rule(node(1, "四级负责人（店长/店助）", "level4_highest", null),
                        node(2, "三级负责人（店长上一级）", "level3_highest", null),
                        node(3, "固定审批", "to_leader", "fixed")),
                candidateMapper, new FakeInstanceMapper(), taskMapper,
                new FakeDeptScopeMapper().addUserShop(fixedUserId, 202L));
    }

    private static InvTransferApprovalServiceImpl fourLevelService(FakeCandidateMapper candidateMapper,
            FakeTaskMapper taskMapper)
    {
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
                .addUserShop(9L, 202L);
        InvTransferApprovalServiceImpl service = newService(
                fourLevelRule(), candidateMapper, new FakeInstanceMapper(), taskMapper,
                deptScopeMapper);
        TransferApprovalCandidateResolver resolver = new TransferApprovalCandidateResolver();
        ReflectionTestUtils.setField(resolver, "candidateMapper", candidateMapper);
        ReflectionTestUtils.setField(service, "candidateResolver", resolver);
        return service;
    }

    private static FakeCandidateMapper standardFourLevelCandidates()
    {
        return new FakeCandidateMapper()
                .managerSort(5)
                .addDirect("dz", leader(10L, "storeManager", 5))
                .addHigher(leader(20L, "level3Leader", 4))
                .add(202L, "yyzj", leader(30L, "operationsDirector", 1))
                .add(202L, "zjl", leader(40L, "generalManager", 1));
    }

    private static InvTransferApprovalRule fourLevelRule()
    {
        return rule(
                node(1, "四级负责人（店长/店助）", "level4_highest", null),
                node(2, "三级负责人（店长与高层之间）", "level3_highest", null),
                node(3, "运营总监", "operations_director", "yyzj"),
                node(4, "总经理", "general_manager", "zjl"));
    }

    private static FakeDeptScopeMapper approvalScope(Long userId, Long selectedShopDeptId)
    {
        return new FakeDeptScopeMapper().addUserShop(userId, selectedShopDeptId);
    }

    private static InvTransferApprovalRule rule(InvTransferApprovalNode... nodes)
    {
        InvTransferApprovalRule rule = new InvTransferApprovalRule();
        rule.setRuleId(100L);
        rule.setRuleName("跨店调拨审批");
        rule.setApprovalMode("all_nodes");
        rule.setRequiredCount(0);
        rule.setRejectAction("back_to_draft");
        rule.setAllowSelfApprove("0");
        List<InvTransferApprovalNode> nodeList = new ArrayList<>();
        Collections.addAll(nodeList, nodes);
        rule.setNodes(nodeList);
        return rule;
    }

    private static InvTransferApprovalNode node(Integer order, String name, String role, String postCode)
    {
        InvTransferApprovalNode node = new InvTransferApprovalNode();
        node.setNodeOrder(order);
        node.setNodeName(name);
        node.setNodeRole(role);
        node.setPostCode(postCode);
        node.setPostName("店长");
        node.setApprovalMode("any_one");
        node.setRequiredCount(1);
        return node;
    }

    private static InvTransferOrder transfer()
    {
        InvTransferOrder transfer = new InvTransferOrder();
        transfer.setTransferId(300L);
        transfer.setFromDeptId(201L);
        transfer.setToDeptId(202L);
        transfer.setTransferType("cross_store");
        transfer.setTotalQuantity(new BigDecimal("3"));
        transfer.setStatus("draft");
        return transfer;
    }

    private static InvTransferOrder submittedTransfer(String createBy, String orderNo)
    {
        InvTransferOrder transfer = transfer();
        transfer.setStatus(InvStatusConstants.SUBMITTED);
        transfer.setApprovalInstanceId(500L);
        transfer.setCreateBy(createBy);
        transfer.setOrderNo(orderNo);
        return transfer;
    }

    private static InvTransferApprovalInstance runningInstance(String rejectAction, String allowSelfApprove)
    {
        InvTransferApprovalInstance instance = new InvTransferApprovalInstance();
        instance.setInstanceId(500L);
        instance.setTransferId(300L);
        instance.setRuleId(100L);
        instance.setStatus("running");
        instance.setCurrentNodeOrder(1);
        instance.setApprovalMode("all_nodes");
        instance.setRequiredCount(0);
        instance.setRejectAction(rejectAction);
        instance.setAllowSelfApprove(allowSelfApprove);
        instance.setRuleSnapshot(snapshot("all_nodes", 0, node(1, "发货店长", "from_leader", "dz")));
        return instance;
    }

    private static InvTransferApprovalTask pendingTask(Long taskId, Integer nodeOrder, String postCode, String candidateUserIds)
    {
        InvTransferApprovalTask task = new InvTransferApprovalTask();
        task.setTaskId(taskId);
        task.setInstanceId(500L);
        task.setTransferId(300L);
        task.setNodeOrder(nodeOrder);
        task.setPostCode(postCode);
        task.setStatus("pending");
        task.setCandidateUserIds(candidateUserIds);
        return task;
    }

    private static InvTransferApprovalTask trackTask(Long taskId, Long instanceId, Integer nodeOrder,
            String nodeName, String status, String candidateUserIds, String candidateUserNames,
            Long approverId, String approverName)
    {
        InvTransferApprovalTask task = pendingTask(taskId, nodeOrder, "post", candidateUserIds);
        task.setInstanceId(instanceId);
        task.setNodeName(nodeName);
        task.setPostName("店长");
        task.setStatus(status);
        task.setCandidateUserNames(candidateUserNames);
        task.setApproverId(approverId);
        task.setApproverName(approverName);
        if (approverId != null)
        {
            task.setApproveTime(new Date(123456789L));
            task.setComment("同意");
        }
        return task;
    }

    private static InvTransferApprovalRequest approvalRequest(Long taskId, String action, String comment)
    {
        InvTransferApprovalRequest request = new InvTransferApprovalRequest();
        request.setTransferId(300L);
        request.setTaskId(taskId);
        request.setAction(action);
        request.setComment(comment);
        return request;
    }

    private static Map<String, Object> user(Long userId, String userName)
    {
        Map<String, Object> user = new HashMap<>();
        user.put("userId", userId);
        user.put("userName", userName);
        return user;
    }

    private static Map<String, Object> leader(Long userId, String userName, Integer postSort)
    {
        Map<String, Object> leader = user(userId, userName);
        leader.put("postSort", postSort);
        return leader;
    }

    @SuppressWarnings("unchecked")
    private static List<String> approvalWarnings(InvTransferApprovalInstance instance)
    {
        try
        {
            Object value = ReflectionTestUtils.getField(instance, "approvalWarnings");
            return value instanceof List ? (List<String>) value : Collections.emptyList();
        }
        catch (IllegalArgumentException exception)
        {
            return Collections.emptyList();
        }
    }

    private static String snapshot(String approvalMode, Integer requiredCount, InvTransferApprovalNode... nodes)
    {
        StringBuilder json = new StringBuilder();
        json.append("{\"ruleId\":100,\"ruleName\":\"快照规则\",\"approvalMode\":\"")
                .append(approvalMode)
                .append("\",\"requiredCount\":")
                .append(requiredCount == null ? 0 : requiredCount)
                .append(",\"rejectAction\":\"back_to_draft\",\"allowSelfApprove\":\"0\",\"nodes\":[");
        for (int i = 0; i < nodes.length; i++)
        {
            InvTransferApprovalNode node = nodes[i];
            if (i > 0)
            {
                json.append(',');
            }
            json.append("{\"nodeOrder\":").append(node.getNodeOrder())
                    .append(",\"nodeName\":\"").append(node.getNodeName())
                    .append("\",\"nodeRole\":\"").append(node.getNodeRole())
                    .append("\",\"postCode\":\"").append(node.getPostCode())
                    .append("\",\"postName\":\"").append(node.getPostName())
                    .append("\",\"approvalMode\":\"").append(node.getApprovalMode())
                    .append("\",\"requiredCount\":").append(node.getRequiredCount())
                    .append('}');
        }
        json.append("]}");
        return json.toString();
    }

    private static class FakeRuleService implements IInvTransferApprovalRuleService
    {
        private final InvTransferApprovalRule rule;

        private FakeRuleService(InvTransferApprovalRule rule)
        {
            this.rule = rule;
        }

        @Override
        public InvTransferApprovalRule matchRule(InvTransferOrder transfer)
        {
            return rule;
        }

        @Override
        public List<InvTransferApprovalRule> selectRuleList(InvTransferApprovalRule rule, Long selectedShopDeptId)
        {
            return Collections.emptyList();
        }

        @Override
        public InvTransferApprovalRule selectRuleById(Long ruleId, Long selectedShopDeptId)
        {
            return rule;
        }

        @Override
        public InvTransferApprovalRule saveRule(InvTransferApprovalRule rule, Long selectedShopDeptId)
        {
            return rule;
        }

        @Override
        public int deleteRuleById(Long ruleId, Integer expectedVersion, Long selectedShopDeptId)
        {
            return 1;
        }

        @Override
        public int saveNodes(Long ruleId, List<InvTransferApprovalNode> nodes, Long selectedShopDeptId)
        {
            return nodes.size();
        }

        @Override
        public int deleteNodesByRuleId(Long ruleId, Long selectedShopDeptId)
        {
            return 1;
        }

        @Override
        public InvTransferApprovalRule previewRule(InvTransferOrder transfer, Long selectedShopDeptId)
        {
            return rule;
        }

        @Override
        public com.erp.inventory.domain.vo.InvTransferApprovalRuleValidationResult validateRule(
                com.erp.inventory.domain.dto.InvTransferApprovalRuleValidationRequest request,
                Long selectedShopDeptId)
        {
            return new com.erp.inventory.domain.vo.InvTransferApprovalRuleValidationResult();
        }
    }

    private static class FakeCandidateMapper implements InvTransferApprovalCandidateMapper
    {
        private final Map<String, List<Map<String, Object>>> usersByDeptAndPost = new HashMap<>();
        private final Map<Long, List<String>> postCodesByUserId = new HashMap<>();
        private final List<String> calls = new ArrayList<>();
        private final Map<String, Integer> activePostSorts = new HashMap<>();
        private int activeTargetStoreCount = 1;
        private final Map<String, List<Map<String, Object>>> directUsers = new HashMap<>();
        private List<Map<String, Object>> higherUsers = new ArrayList<>();
        private final List<String> directCalls = new ArrayList<>();
        private List<String> lastExcludedPostCodes = new ArrayList<>();
        private Integer lastManagerPostSort;
        private Integer lastExecutiveBoundarySort;
        private final Map<Long, String> safeDisplayNames = new HashMap<>();

        private FakeCandidateMapper add(Long deptId, String postCode, Map<String, Object> user)
        {
            usersByDeptAndPost.computeIfAbsent(key(deptId, postCode), ignored -> new ArrayList<>()).add(user);
            return this;
        }

        private FakeCandidateMapper addUserPost(Long userId, String postCode)
        {
            postCodesByUserId.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(postCode);
            return this;
        }

        private FakeCandidateMapper managerSort(Integer postSort)
        {
            activePostSorts.put("dz", postSort);
            activePostSorts.put("yyzj", 1);
            activePostSorts.put("zjl", 1);
            return this;
        }

        private FakeCandidateMapper invalidTargetStore()
        {
            activeTargetStoreCount = 0;
            return this;
        }

        private FakeCandidateMapper displayName(Long userId, String displayName)
        {
            safeDisplayNames.put(userId, displayName);
            return this;
        }

        @SafeVarargs
        private final FakeCandidateMapper addDirect(String postCode, Map<String, Object>... users)
        {
            directUsers.put(postCode, new ArrayList<>(List.of(users)));
            return this;
        }

        @SafeVarargs
        private final FakeCandidateMapper addHigher(Map<String, Object>... users)
        {
            higherUsers = new ArrayList<>(List.of(users));
            return this;
        }

        @Override
        public Integer selectActivePostSortByCode(String postCode)
        {
            return activePostSorts.get(postCode);
        }

        @Override
        public int countActiveTargetStore(Long targetDeptId)
        {
            return activeTargetStoreCount;
        }

        @Override
        public List<Map<String, Object>> selectDirectStoreUsersByPostCode(Long targetDeptId, String postCode)
        {
            directCalls.add(targetDeptId + ":" + postCode);
            return directUsers.getOrDefault(postCode, Collections.emptyList());
        }

        @Override
        public List<Map<String, Object>> selectCoveredHigherPostUsers(Long targetDeptId, Integer managerPostSort,
                Integer executiveBoundarySort, List<String> excludedPostCodes)
        {
            lastManagerPostSort = managerPostSort;
            lastExecutiveBoundarySort = executiveBoundarySort;
            lastExcludedPostCodes = new ArrayList<>(excludedPostCodes);
            return higherUsers;
        }

        @Override
        public List<Map<String, Object>> selectCoveredUsersByPostCode(Long targetDeptId, String postCode)
        {
            return usersByDeptAndPost.getOrDefault(key(targetDeptId, postCode), Collections.emptyList());
        }

        @Override
        public List<Map<String, Object>> selectUsersByDeptAndPostCode(Long deptId, String postCode)
        {
            calls.add(key(deptId, postCode));
            return usersByDeptAndPost.getOrDefault(key(deptId, postCode), Collections.emptyList());
        }

        public List<String> selectPostCodesByUserId(Long userId)
        {
            return postCodesByUserId.getOrDefault(userId, Collections.emptyList());
        }

        @Override
        public List<Map<String, Object>> selectSafeDisplayNamesByUserIds(List<Long> userIds)
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Long userId : userIds)
            {
                if (!safeDisplayNames.containsKey(userId))
                {
                    continue;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("userId", userId);
                row.put("displayName", safeDisplayNames.get(userId));
                rows.add(row);
            }
            return rows;
        }

        private static String key(Long deptId, String postCode)
        {
            return deptId + ":" + postCode;
        }
    }

    private static class FakeDeptScopeMapper implements InvDeptScopeMapper
    {
        @Override public List<Long> selectUserAuthorizedInventoryDeptIds(Long userId) { return java.util.Collections.emptyList(); }
        @Override public List<Long> selectAllActiveInventoryDeptIds() { return java.util.Collections.emptyList(); }
        private final Set<String> userShops = new HashSet<>();
        private final Set<String> scopedDepts = new HashSet<>();
        private final Map<Long, List<Long>> ancestorDepts = new HashMap<>();

        private FakeDeptScopeMapper addUserShop(Long userId, Long deptId)
        {
            userShops.add(userId + ":" + deptId);
            return this;
        }

        private FakeDeptScopeMapper addScopedDept(Long scopeDeptId, Long targetDeptId)
        {
            scopedDepts.add(scopeDeptId + ":" + targetDeptId);
            return this;
        }

        private FakeDeptScopeMapper addAncestorDepts(Long deptId, Long... ancestors)
        {
            List<Long> deptIds = new ArrayList<>();
            Collections.addAll(deptIds, ancestors);
            ancestorDepts.put(deptId, deptIds);
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
            return ancestorDepts.getOrDefault(deptId, Collections.emptyList());
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
            return scopeDeptId != null && targetDeptId != null
                    && (scopeDeptId.equals(targetDeptId) || scopedDepts.contains(scopeDeptId + ":" + targetDeptId)) ? 1 : 0;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            return userShops.contains(userId + ":" + deptId) ? 1 : 0;
        }

        @Override
        public String selectDeptNameById(Long deptId)
        {
            return "门店" + deptId;
        }

        @Override
        public String selectDeptTypeById(Long deptId)
        {
            return "STORE";
        }
    }

    private static class FakeInstanceMapper implements InvTransferApprovalInstanceMapper
    {
        private final List<InvTransferApprovalInstance> instances = new ArrayList<>();
        private long nextId = 500L;

        @Override
        public int insertInstance(InvTransferApprovalInstance instance)
        {
            while (containsInstanceId(nextId))
            {
                nextId++;
            }
            instance.setInstanceId(nextId++);
            instances.add(instance);
            return 1;
        }

        private boolean containsInstanceId(Long instanceId)
        {
            return instances.stream().anyMatch(instance -> instanceId.equals(instance.getInstanceId()));
        }

        @Override
        public InvTransferApprovalInstance selectInstanceByIdForUpdate(Long instanceId)
        {
            return instances.stream()
                    .filter(instance -> instanceId.equals(instance.getInstanceId()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public InvTransferApprovalInstance selectRunningInstanceByTransferIdForUpdate(Long transferId)
        {
            return instances.stream()
                    .filter(instance -> transferId.equals(instance.getTransferId()))
                    .filter(instance -> "running".equals(instance.getStatus()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<InvTransferApprovalInstance> selectRunningInstancesByTransferIdForUpdate(
                Long transferId)
        {
            return instances.stream()
                    .filter(instance -> transferId.equals(
                            instance.getTransferId()))
                    .filter(instance -> "running".equals(
                            instance.getStatus()))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<InvTransferApprovalInstance> selectInstancesByTransferId(Long transferId)
        {
            return instances.stream()
                    .filter(instance -> transferId.equals(instance.getTransferId()))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public int closeRunningInstancesByTransferId(Long transferId,
                String updateBy, String reason)
        {
            int updated = 0;
            for (InvTransferApprovalInstance instance : instances)
            {
                if (transferId.equals(instance.getTransferId())
                        && "running".equals(instance.getStatus()))
                {
                    instance.setStatus("closed");
                    instance.setUpdateBy(updateBy);
                    instance.setRemark(reason);
                    updated++;
                }
            }
            return updated;
        }

        @Override
        public int updateInstance(InvTransferApprovalInstance instance)
        {
            return 1;
        }
    }

    private static class FakeTaskMapper implements InvTransferApprovalTaskMapper
    {
        private final List<InvTransferApprovalTask> tasks = new ArrayList<>();
        private final List<InvTransferApprovalSummary> summaries = new ArrayList<>();

        @Override
        public int insertTask(InvTransferApprovalTask task)
        {
            tasks.add(task);
            return 1;
        }

        @Override
        public InvTransferApprovalTask selectTaskByIdForUpdate(Long taskId)
        {
            return tasks.stream()
                    .filter(task -> taskId.equals(task.getTaskId()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<InvTransferApprovalTask> selectPendingTasksByInstanceAndNodeForUpdate(Long instanceId, Integer nodeOrder)
        {
            return tasks.stream()
                    .filter(task -> instanceId.equals(task.getInstanceId()))
                    .filter(task -> nodeOrder.equals(task.getNodeOrder()))
                    .filter(task -> "pending".equals(task.getStatus()))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<InvTransferApprovalTask> selectTasksByInstanceAndNode(Long instanceId, Integer nodeOrder)
        {
            return tasks.stream()
                    .filter(task -> instanceId.equals(task.getInstanceId()))
                    .filter(task -> nodeOrder.equals(task.getNodeOrder()))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<InvTransferApprovalTask> selectTasksByInstanceId(Long instanceId)
        {
            return tasks.stream()
                    .filter(task -> instanceId.equals(task.getInstanceId()))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<InvTransferApprovalTask> selectTasksByInstanceIds(List<Long> instanceIds)
        {
            return tasks.stream()
                    .filter(task -> instanceIds.contains(task.getInstanceId()))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<com.erp.inventory.domain.vo.InvTransferApprovalSummary> selectApprovalSummariesByTransferIds(
                List<Long> transferIds)
        {
            return summaries.stream()
                    .filter(summary -> transferIds.contains(summary.getTransferId()))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public int skipPendingTasksByTransferId(Long transferId,
                String updateBy, String reason)
        {
            int updated = 0;
            for (InvTransferApprovalTask task : tasks)
            {
                if (transferId.equals(task.getTransferId())
                        && "pending".equals(task.getStatus()))
                {
                    task.setStatus("skipped");
                    task.setUpdateBy(updateBy);
                    task.setRemark(reason);
                    updated++;
                }
            }
            return updated;
        }

        @Override
        public int updateTaskApproval(InvTransferApprovalTask task)
        {
            return 1;
        }
    }

    private static class FakeTransferOrderMapper implements InvTransferOrderMapper
    {
        private InvTransferOrder stored;

        @Override
        public InvTransferOrder selectInvTransferOrderById(Long transferId)
        {
            return stored;
        }

        @Override
        public InvTransferOrder selectInvTransferOrderByIdForUpdate(Long transferId)
        {
            return stored;
        }

        @Override
        public List<InvTransferOrder> selectInvTransferOrderList(InvTransferOrder order)
        {
            return Collections.singletonList(stored);
        }

        @Override
        public com.erp.inventory.domain.vo.InvTransferOpsSummaryVo selectOpsSummary(
                InvTransferOrder order)
        {
            return new com.erp.inventory.domain.vo.InvTransferOpsSummaryVo();
        }

        @Override
        public int insertInvTransferOrder(InvTransferOrder order)
        {
            stored = order;
            return 1;
        }

        @Override
        public int updateInvTransferOrder(InvTransferOrder order)
        {
            if (order.getStatus() != null) stored.setStatus(order.getStatus());
            if (order.getApprovedTime() != null) stored.setApprovedTime(order.getApprovedTime());
            if (order.getArchivedTime() != null) stored.setArchivedTime(order.getArchivedTime());
            if (order.getCloseReason() != null) stored.setCloseReason(order.getCloseReason());
            if (order.getUpdateBy() != null) stored.setUpdateBy(order.getUpdateBy());
            return 1;
        }

        @Override
        public int updateDraftIfVersionMatches(InvTransferOrder order)
        {
            return updateInvTransferOrder(order);
        }

        @Override
        public int finalizeNativeApprovalStart(Long transferId,
                Integer businessRound, Long expectedVersion, Long instanceId,
                String updateBy)
        {
            if (stored == null || stored.getApprovalInstanceId() != null)
            {
                return 0;
            }
            stored.setApprovalInstanceId(instanceId);
            stored.setUpdateBy(updateBy);
            return 1;
        }

        @Override
        public int deleteInvTransferOrderById(Long transferId)
        {
            stored = null;
            return 1;
        }

        @Override
        public InvTransferOrder selectByPurchaseId(Long purchaseId)
        {
            return stored;
        }

        @Override
        public InvTransferOrder selectBySourceBusinessTypeIdWarehouse(
                String sourceBusinessType, Long sourceBusinessId,
                Long fromWarehouseId)
        {
            return null;
        }


        @Override
        public InvTransferOrder selectBySourceBusinessTypeIdWarehouseForUpdate(
                String sourceBusinessType, Long sourceBusinessId,
                Long fromWarehouseId)
        {
            return null;
        }
    }

    private static class FakeStatusLogMapper implements InvTransferStatusLogMapper
    {
        private final List<InvTransferStatusLog> logs = new ArrayList<>();

        @Override
        public int insertLog(InvTransferStatusLog log)
        {
            logs.add(log);
            return 1;
        }

        @Override
        public List<InvTransferStatusLog> selectLogsByTransferId(Long transferId)
        {
            return logs.stream()
                    .filter(log -> transferId.equals(log.getTransferId()))
                    .collect(java.util.stream.Collectors.toList());
        }
    }
}
