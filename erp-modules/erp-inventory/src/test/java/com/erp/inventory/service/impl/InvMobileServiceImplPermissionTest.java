package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.TokenConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.domain.todo.TodoConstants;
import com.erp.common.core.domain.todo.TodoItem;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoSummary;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.exception.auth.NotLoginException;
import com.erp.common.core.exception.auth.NotPermissionException;
import com.erp.inventory.constant.InvTodoTypes;
import com.erp.inventory.domain.vo.MobileOption;
import com.erp.inventory.domain.vo.MobileTransferApprovalTodo;
import com.erp.inventory.domain.vo.MobileWorkbenchSummary;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvMobileMapper;
import com.erp.inventory.service.IInvTodoService;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

@DisplayName("手机端库存选项权限映射")
class InvMobileServiceImplPermissionTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("商品选项有对应权限后按当前组织相关目录 scope 查询")
    void productOptionsShouldRequireProductPermissionAndQueryInScope()
    {
        setLoginUser(7L, Set.of("inv:product:list"));
        FakeMobileMapper mobileMapper = new FakeMobileMapper(List.of(option(1L, "商品A")));
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        InvMobileServiceImpl service = service(mobileMapper, deptScopeMapper);

        List<MobileOption> result = service.selectOptions(" product ", "  SKU  ", 99, 88L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLabel()).isEqualTo("商品A");
        assertThat(mobileMapper.productCalls).isEqualTo(1);
        assertThat(mobileMapper.lastKeyword).isEqualTo("SKU");
        assertThat(mobileMapper.lastLimit).isEqualTo(50);
        assertThat(mobileMapper.lastParams).containsEntry("scopeDeptIds", List.of(88L, 288L));
        assertThat(deptScopeMapper.checkedUserId).isEqualTo(7L);
        assertThat(deptScopeMapper.checkedDeptId).isEqualTo(88L);
        assertThat(deptScopeMapper.relatedScopeChecks).isEqualTo(1);
    }

    @Test
    @DisplayName("选项类型缺少对应列表权限时拒绝且不查询数据")
    void optionTypesShouldRejectMissingPermissionBeforeQuery()
    {
        setLoginUser(7L, Set.of("inv:product:list"));
        FakeMobileMapper mobileMapper = new FakeMobileMapper(Collections.emptyList());
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        InvMobileServiceImpl service = service(mobileMapper, deptScopeMapper);

        assertThatThrownBy(() -> service.selectOptions("customer", null, null, 88L))
                .isInstanceOf(NotPermissionException.class)
                .hasMessage("inv:customer:list");
        assertThat(mobileMapper.totalCalls()).isZero();
        assertThat(deptScopeMapper.userScopeChecks).isZero();
    }

    @Test
    @DisplayName("未知选项类型必须先校验登录态")
    void unknownOptionTypeShouldRequireLoginBeforeRejecting()
    {
        withRequestOnly();
        InvMobileServiceImpl service = service(new FakeMobileMapper(Collections.emptyList()), new FakeDeptScopeMapper());

        assertThatThrownBy(() -> service.selectOptions("unknown", null, null, 88L))
                .isInstanceOf(NotLoginException.class)
                .hasMessage("无效的token");
    }

    @Test
    @DisplayName("未知选项类型登录后拒绝且不查询数据")
    void unknownOptionTypeShouldRejectLoggedInUserWithoutQuery()
    {
        setLoginUser(7L, Set.of("inv:product:list"));
        FakeMobileMapper mobileMapper = new FakeMobileMapper(Collections.emptyList());
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        InvMobileServiceImpl service = service(mobileMapper, deptScopeMapper);

        assertThatThrownBy(() -> service.selectOptions("unknown", null, null, 88L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不支持的手机端选项类型");
        assertThat(mobileMapper.totalCalls()).isZero();
        assertThat(deptScopeMapper.userScopeChecks).isZero();
    }

    @Test
    @DisplayName("仓库选项走库存权限并保持空结果")
    void warehouseOptionShouldRequireStockPermissionAndReturnEmpty()
    {
        setLoginUser(7L, Set.of("inv:stock:list"));
        FakeMobileMapper mobileMapper = new FakeMobileMapper(Collections.emptyList());
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        InvMobileServiceImpl service = service(mobileMapper, deptScopeMapper);

        List<MobileOption> result = service.selectOptions("warehouse", null, null, 88L);

        assertThat(result).isEmpty();
        assertThat(mobileMapper.totalCalls()).isZero();
        assertThat(deptScopeMapper.userScopeChecks).isZero();
    }

    @Test
    @DisplayName("调拨审批待办按当前登录候选人和组织 scope 查询")
    void transferApprovalTodosShouldQueryCurrentCandidateInSelectedScope()
    {
        setLoginUser(7L, Set.of("inv:transfer:approve"));
        MobileTransferApprovalTodo todo = new MobileTransferApprovalTodo();
        todo.setTransferId(91L);
        todo.setTaskId(901L);
        todo.setTotalAmount(new BigDecimal("88.00"));
        FakeMobileMapper mobileMapper = new FakeMobileMapper(Collections.emptyList(), List.of(todo));
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
        InvMobileServiceImpl service = service(mobileMapper, deptScopeMapper);

        List<MobileTransferApprovalTodo> result = service.selectPendingTransferApprovals(88L);

        assertThat(result).containsExactly(todo);
        assertThat(result.get(0).getTotalAmount()).isNull();
        assertThat(mobileMapper.transferApprovalCalls).isEqualTo(1);
        assertThat(mobileMapper.lastCandidateUserId).isEqualTo(7L);
        assertThat(mobileMapper.lastParams).containsEntry("scopeDeptIds", List.of(88L, 188L));
        assertThat(deptScopeMapper.checkedUserId).isEqualTo(7L);
        assertThat(deptScopeMapper.checkedDeptId).isEqualTo(88L);
    }

    @Test
    @DisplayName("成本权限允许移动审批待办查看总成本")
    void transferApprovalTodosShouldRetainAmountForCostViewer()
    {
        setLoginUser(7L, Set.of("inv:transfer:approve",
                InvTransferCostVisibilityPolicy.COST_VIEW_PERMISSION));
        MobileTransferApprovalTodo todo = new MobileTransferApprovalTodo();
        todo.setTotalAmount(new BigDecimal("88.00"));
        InvMobileServiceImpl service = service(new FakeMobileMapper(
                Collections.emptyList(), List.of(todo)),
                new FakeDeptScopeMapper());

        List<MobileTransferApprovalTodo> result =
                service.selectPendingTransferApprovals(88L);

        assertThat(result.get(0).getTotalAmount())
                .isEqualByComparingTo("88.00");
    }

    @Test
    @DisplayName("工作台兼容字段全部由 current_org 待办汇总的明确类型映射产生")
    void workbenchSummaryShouldMapEveryLegacyFieldFromCurrentOrgTodoSummary()
    {
        setLoginUser(7L, Set.of("inv:stock:list"));
        TodoSummary providerSummary = new TodoSummary();
        providerSummary.setTotal(101L);
        providerSummary.setApproval(17L);
        providerSummary.setExecution(61L);
        providerSummary.setRisk(23L);
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        typeCounts.put(InvTodoTypes.INV_PURCHASE_RECEIVE, 2L);
        typeCounts.put(InvTodoTypes.INV_PURCHASE_QC, 11L);
        typeCounts.put(InvTodoTypes.INV_TRANSFER_RECEIVE, 3L);
        typeCounts.put(InvTodoTypes.INV_DELIVERY_EXECUTE, 5L);
        typeCounts.put(InvTodoTypes.INV_TRANSFER_DELIVER, 7L);
        typeCounts.put(InvTodoTypes.INV_LOW_STOCK, 15L);
        typeCounts.put(InvTodoTypes.INV_OUT_OF_STOCK, 8L);
        typeCounts.put(InvTodoTypes.INV_SALES_NOTICE_CREATE, 44L);
        providerSummary.setTypeCounts(typeCounts);
        FakeTodoService todoService = new FakeTodoService(providerSummary);
        FakeMobileMapper mobileMapper = new FakeMobileMapper(Collections.emptyList());
        InvMobileServiceImpl service = service(mobileMapper, new FakeDeptScopeMapper(), todoService);

        MobileWorkbenchSummary result = service.selectWorkbenchSummary(88L);

        assertThat(todoService.summaryCalls).isEqualTo(1);
        assertThat(todoService.lastSelectedDeptId).isEqualTo(88L);
        assertThat(todoService.lastQuery.getScopeMode()).isEqualTo(TodoConstants.SCOPE_CURRENT_ORG);
        assertThat(result.getSelectedDeptId()).isEqualTo(88L);
        assertThat(result.getSelectedDeptType()).isEqualTo(InvBaseService.DEPT_TYPE_STORE);
        assertThat(result.getTodoCount()).isEqualTo(101L);
        assertThat(result.getPendingApprovalCount()).isEqualTo(17L);
        assertThat(result.getPurchaseReceiveCount()).isEqualTo(2L);
        assertThat(result.getPurchaseQcCount()).isEqualTo(11L);
        assertThat(result.getPurchasePendingCount()).isEqualTo(13L);
        assertThat(result.getTransferReceiveCount()).isEqualTo(3L);
        assertThat(result.getPendingReceiveCount()).isEqualTo(5L);
        assertThat(result.getDeliveryNoticeCount()).isEqualTo(5L);
        assertThat(result.getTransferDeliverCount()).isEqualTo(7L);
        assertThat(result.getPendingDeliverCount()).isEqualTo(12L);
        // The deployed lowStockCount field was the full warning count. Keep that
        // contract by adding the provider's mutually-exclusive low/out-of-stock facts.
        assertThat(result.getLowStockCount()).isEqualTo(23L);
        assertThat(mobileMapper.totalCalls()).isZero();
    }

    @Test
    @DisplayName("缺失的待办类型默认映射为零且不使用 execution 或 risk 分类近似")
    void workbenchSummaryShouldDefaultMissingTypesToZeroWithoutCategoryApproximation()
    {
        setLoginUser(7L, Set.of("inv:stock:list"));
        TodoSummary providerSummary = new TodoSummary();
        providerSummary.setTotal(91L);
        providerSummary.setApproval(19L);
        providerSummary.setExecution(37L);
        providerSummary.setRisk(35L);
        providerSummary.setTypeCounts(Collections.emptyMap());
        InvMobileServiceImpl service = service(new FakeMobileMapper(Collections.emptyList()),
                new FakeDeptScopeMapper(), new FakeTodoService(providerSummary));

        MobileWorkbenchSummary result = service.selectWorkbenchSummary(88L);

        assertThat(result.getTodoCount()).isEqualTo(91L);
        assertThat(result.getPendingApprovalCount()).isEqualTo(19L);
        assertThat(result.getPurchaseReceiveCount()).isZero();
        assertThat(result.getPurchaseQcCount()).isZero();
        assertThat(result.getPurchasePendingCount()).isZero();
        assertThat(result.getTransferReceiveCount()).isZero();
        assertThat(result.getPendingReceiveCount()).isZero();
        assertThat(result.getDeliveryNoticeCount()).isZero();
        assertThat(result.getTransferDeliverCount()).isZero();
        assertThat(result.getPendingDeliverCount()).isZero();
        assertThat(result.getLowStockCount()).isZero();
    }

    @Test
    @DisplayName("管理员查询旧调拨审批待办也必须传当前真实用户作为候选人")
    void adminTransferApprovalTodosShouldStillQueryCurrentCandidate()
    {
        setLoginUser(1L, Set.of("*:*:*"));
        FakeMobileMapper mobileMapper = new FakeMobileMapper(Collections.emptyList(), Collections.emptyList());
        InvMobileServiceImpl service = service(mobileMapper, new FakeDeptScopeMapper());

        service.selectPendingTransferApprovals(88L);

        assertThat(mobileMapper.lastCandidateUserId).isEqualTo(1L);
    }

    private static InvMobileServiceImpl service(FakeMobileMapper mobileMapper, FakeDeptScopeMapper deptScopeMapper)
    {
        return service(mobileMapper, deptScopeMapper, new FakeTodoService(new TodoSummary()));
    }

    private static InvMobileServiceImpl service(FakeMobileMapper mobileMapper, FakeDeptScopeMapper deptScopeMapper,
            IInvTodoService todoService)
    {
        InvMobileServiceImpl service = new InvMobileServiceImpl();
        ReflectionTestUtils.setField(service, "mobileMapper", mobileMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        ReflectionTestUtils.setField(service, "todoService", todoService);
        return service;
    }

    private static void setLoginUser(Long userId, Set<String> permissions)
    {
        withRequestOnly();
        SecurityContextHolder.setUserId(String.valueOf(userId));
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setUsername("mobile-options-user");
        loginUser.setRoles(Set.of("user"));
        loginUser.setPermissions(permissions);
        SysUser sysUser = new SysUser();
        sysUser.setUserId(userId);
        sysUser.setUserName("mobile-options-user");
        loginUser.setSysUser(sysUser);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static void withRequestOnly()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX + "unit-test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static MobileOption option(Long value, String label)
    {
        MobileOption option = new MobileOption();
        option.setValue(value);
        option.setLabel(label);
        return option;
    }

    private static class FakeMobileMapper implements InvMobileMapper
    {
        private final List<MobileOption> productResult;
        private final List<MobileTransferApprovalTodo> transferApprovalResult;
        private int productCalls;
        private int customerCalls;
        private int supplierCalls;
        private int transferApprovalCalls;
        private String lastKeyword;
        private Map<String, Object> lastParams;
        private int lastLimit;
        private Long lastCandidateUserId;

        private FakeMobileMapper(List<MobileOption> productResult)
        {
            this(productResult, Collections.emptyList());
        }

        private FakeMobileMapper(List<MobileOption> productResult, List<MobileTransferApprovalTodo> transferApprovalResult)
        {
            this.productResult = productResult;
            this.transferApprovalResult = transferApprovalResult;
        }

        @Override
        public List<MobileOption> selectProductOptions(String keyword, Map<String, Object> params, int limit)
        {
            productCalls++;
            lastKeyword = keyword;
            lastParams = params;
            lastLimit = limit;
            return productResult;
        }

        @Override
        public List<MobileOption> selectCustomerOptions(String keyword, Map<String, Object> params, int limit)
        {
            customerCalls++;
            return Collections.emptyList();
        }

        @Override
        public List<MobileOption> selectSupplierOptions(String keyword, Map<String, Object> params, int limit)
        {
            supplierCalls++;
            return Collections.emptyList();
        }

        @Override
        public List<MobileTransferApprovalTodo> selectPendingTransferApprovalTasks(Map<String, Object> params,
                Long candidateUserId)
        {
            transferApprovalCalls++;
            lastParams = params;
            lastCandidateUserId = candidateUserId;
            return transferApprovalResult;
        }

        private int totalCalls()
        {
            return productCalls + customerCalls + supplierCalls + transferApprovalCalls;
        }
    }

    private static class FakeTodoService implements IInvTodoService
    {
        private final TodoSummary result;
        private int summaryCalls;
        private TodoQuery lastQuery;
        private Long lastSelectedDeptId;

        private FakeTodoService(TodoSummary result)
        {
            this.result = result;
        }

        @Override
        public TodoSummary selectSummary(TodoQuery query, Long selectedDeptId)
        {
            summaryCalls++;
            lastQuery = query;
            lastSelectedDeptId = selectedDeptId;
            return result;
        }

        @Override
        public List<TodoItem> selectTodoList(TodoQuery query, Long selectedDeptId)
        {
            throw new AssertionError("Unexpected todo list query");
        }
    }

    private static class FakeDeptScopeMapper implements InvDeptScopeMapper
    {
        @Override public List<Long> selectUserAuthorizedInventoryDeptIds(Long userId) { return java.util.Collections.emptyList(); }
        @Override public List<Long> selectAllActiveInventoryDeptIds() { return java.util.Collections.emptyList(); }
        private int userScopeChecks;
        private int relatedScopeChecks;
        private Long checkedUserId;
        private Long checkedDeptId;

        @Override
        public List<Long> selectSubDeptIds(Long deptId)
        {
            return List.of(deptId, deptId + 100L);
        }

        @Override
        public List<Long> selectRelatedDeptIds(Long deptId)
        {
            relatedScopeChecks++;
            return List.of(deptId, deptId + 200L);
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
            return List.of(deptId);
        }

        @Override
        public Long selectRawBusinessRootDeptId(Long deptId)
        {
            return deptId;
        }

        @Override
        public List<Long> selectUserStoreScopeDeptIds(Long userId)
        {
            return java.util.Collections.emptyList();
        }

        @Override
        public List<Long> selectAllStoreDeptIds()
        {
            return java.util.Collections.emptyList();
        }

        @Override
        public int countDeptInScope(Long scopeDeptId, Long targetDeptId)
        {
            return scopeDeptId.equals(targetDeptId) ? 1 : 0;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            userScopeChecks++;
            checkedUserId = userId;
            checkedDeptId = deptId;
            return 1;
        }

        @Override
        public String selectDeptNameById(Long deptId)
        {
            return "测试门店";
        }

        @Override
        public String selectDeptTypeById(Long deptId)
        {
            return InvBaseService.DEPT_TYPE_STORE;
        }
    }
}
