package com.erp.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.approval.candidate.ApprovalCandidateContext;
import com.erp.approval.candidate.ApprovalCandidateResolution;
import com.erp.approval.candidate.ApprovalDirectoryUser;
import com.erp.approval.candidate.PermissionHolderCandidateResolver;
import com.erp.approval.constant.ApprovalBusinessCodes;
import com.erp.approval.domain.ApprovalTemplate;
import com.erp.approval.domain.ApprovalVersionNode;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.support.ApprovalJsonSupport;
import com.erp.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("报销审批权限策略")
class ApprovalPermissionPolicyReimbursementTest
{
    private final ApprovalPermissionPolicy policy =
            new ApprovalPermissionPolicy();

    @Test
    @DisplayName("报销基础节点和财务节点使用相互独立的允许权限")
    void shouldAllowOnlyBaseAndFinancePermissionsForReimbursement()
    {
        assertThat(policy.requiredPermission(
                ApprovalBusinessCodes.OA_REIMBURSEMENT))
                .isEqualTo("oa:reimbursement:approve");
        assertThat(policy.candidatePermissions(
                ApprovalBusinessCodes.OA_REIMBURSEMENT))
                .containsExactlyInAnyOrder("oa:reimbursement:approve",
                        "oa:reimbursement:finance:approve");
        assertThat(policy.isCandidatePermissionAllowed(
                ApprovalBusinessCodes.OA_REIMBURSEMENT,
                "oa:reimbursement:approve")).isTrue();
        assertThat(policy.isCandidatePermissionAllowed(
                ApprovalBusinessCodes.OA_REIMBURSEMENT,
                "oa:reimbursement:finance:approve")).isTrue();
        assertThat(policy.isCandidatePermissionAllowed(
                ApprovalBusinessCodes.OA_REIMBURSEMENT,
                "oa:todo:approve")).isFalse();
        assertThat(policy.isCandidatePermissionAllowed(
                ApprovalBusinessCodes.OA_PURCHASE,
                "oa:reimbursement:finance:approve")).isFalse();
        assertThat(policy.permissionForCandidate(
                ApprovalBusinessCodes.OA_REIMBURSEMENT,
                "oa:reimbursement:finance:approve"))
                .isEqualTo("oa:reimbursement:finance:approve");
        assertThat(policy.permissionForCandidate(
                ApprovalBusinessCodes.OA_REIMBURSEMENT, "ORG_LEADER"))
                .isEqualTo("oa:reimbursement:approve");
    }

    @Test
    @DisplayName("财务责任人按专用权限及锚点组织解析")
    void shouldResolveFinanceHolderWithDedicatedPermission()
    {
        ApprovalCandidateDirectoryMapper directory =
                mock(ApprovalCandidateDirectoryMapper.class);
        ApprovalDirectoryUser finance = new ApprovalDirectoryUser();
        finance.setUserId(81L);
        finance.setUserName("finance");
        when(directory.selectPermissionHolders(20L,
                "oa:reimbursement:finance:approve"))
                .thenReturn(List.of(finance));
        PermissionHolderCandidateResolver resolver =
                new PermissionHolderCandidateResolver(directory, policy,
                        new ApprovalJsonSupport(new ObjectMapper()));

        ApprovalCandidateResolution resolution = resolver.resolve(
                context("OA_REIMBURSEMENT",
                        "oa:reimbursement:finance:approve"));

        assertThat(resolution.candidates()).hasSize(1);
        assertThat(resolution.candidates().get(0).userId()).isEqualTo(81L);
        assertThat(resolution.candidates().get(0).sourceCode())
                .isEqualTo("oa:reimbursement:finance:approve");
        verify(directory).selectPermissionHolders(20L,
                "oa:reimbursement:finance:approve");
    }

    @Test
    @DisplayName("其他业务不能借用报销财务权限")
    void shouldRejectFinancePermissionForOtherBusiness()
    {
        ApprovalCandidateDirectoryMapper directory =
                mock(ApprovalCandidateDirectoryMapper.class);
        PermissionHolderCandidateResolver resolver =
                new PermissionHolderCandidateResolver(directory, policy,
                        new ApprovalJsonSupport(new ObjectMapper()));

        assertThatThrownBy(() -> resolver.resolve(context(
                "OA_PURCHASE", "oa:reimbursement:finance:approve")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能绕过业务审批权限");
    }

    private ApprovalCandidateContext context(String businessCode,
            String permission)
    {
        ApprovalTemplate template = new ApprovalTemplate();
        template.setBusinessCode(businessCode);
        ApprovalVersionNode node = new ApprovalVersionNode();
        node.setStrategyType("BUSINESS_STRATEGY");
        node.setStrategyCode("PERMISSION_HOLDER");
        node.setStrategyConfig("{\"permissionKey\":\""
                + permission + "\"}");
        return new ApprovalCandidateContext(template, null, null, node,
                20L, 7L, "EXPENSE", Map.of());
    }
}
