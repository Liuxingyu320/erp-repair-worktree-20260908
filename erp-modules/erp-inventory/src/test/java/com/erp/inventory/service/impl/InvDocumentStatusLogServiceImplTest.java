package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvDocumentStatusLog;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvDocumentStatusLogMapper;
import com.erp.system.api.model.LoginUser;

@DisplayName("库存单据状态审计日志 Service")
class InvDocumentStatusLogServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
        System.clearProperty("erp.security.admin-role-bypass-enabled");
    }

    @Test
    @DisplayName("普通用户查询必须校验选择组织并追加范围过滤")
    void shouldValidateAndAppendScopeForNormalUser()
    {
        SecurityContextHolder.setUserId("200");
        FakeDocumentStatusLogMapper logMapper = new FakeDocumentStatusLogMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper(List.of(10L, 11L));
        InvDocumentStatusLogServiceImpl service = service(logMapper, deptScopeMapper);

        InvDocumentStatusLog query = new InvDocumentStatusLog();
        query.setDocumentType("sales_order");
        query.setDocumentId(99L);
        query.setShopDeptId(11L);
        query.getParams().put("beginOperateTime", "2026-06-01 00:00:00");
        query.getParams().put("endOperateTime", "2026-06-12 23:59:59");

        service.selectDocumentStatusLogList(query, 10L);

        assertThat(logMapper.lastQuery).isSameAs(query);
        assertThat(logMapper.lastQuery.getParams().get("scopeDeptIds")).isEqualTo(List.of(10L, 11L));
        assertThat(deptScopeMapper.countUserShopScopeCalls).containsExactly("200:10");
    }

    @Test
    @DisplayName("管理员带选择组织查询时只追加范围过滤不做用户门店校验")
    void shouldAppendScopeForAdminWithoutUserShopCheck()
    {
        SecurityContextHolder.setUserId("201");
        System.setProperty("erp.security.admin-role-bypass-enabled", "true");
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(new HashSet<>(Collections.singletonList("admin")));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        FakeDocumentStatusLogMapper logMapper = new FakeDocumentStatusLogMapper();
        FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper(List.of(10L, 11L));
        InvDocumentStatusLogServiceImpl service = service(logMapper, deptScopeMapper);

        service.selectDocumentStatusLogList(new InvDocumentStatusLog(), 10L);

        assertThat(logMapper.lastQuery.getParams().get("scopeDeptIds")).isEqualTo(List.of(10L, 11L));
        assertThat(deptScopeMapper.countUserShopScopeCalls).isEmpty();
    }

    @Test
    @DisplayName("管理员未选择组织时允许按查询条件直接审计全量日志")
    void shouldAllowAdminQueryWithoutSelectedShopScope()
    {
        SecurityContextHolder.setUserId("201");
        System.setProperty("erp.security.admin-role-bypass-enabled", "true");
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(new HashSet<>(Collections.singletonList("admin")));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        FakeDocumentStatusLogMapper logMapper = new FakeDocumentStatusLogMapper();
        InvDocumentStatusLogServiceImpl service = service(logMapper, new FakeDeptScopeMapper(List.of(10L)));

        service.selectDocumentStatusLogList(new InvDocumentStatusLog(), null);

        assertThat(logMapper.lastQuery.getParams()).doesNotContainKey("scopeDeptIds");
    }

    @Test
    @DisplayName("普通用户未选择组织时拒绝查询")
    void shouldRequireSelectedShopForNormalUser()
    {
        SecurityContextHolder.setUserId("200");
        InvDocumentStatusLogServiceImpl service = service(new FakeDocumentStatusLogMapper(),
                new FakeDeptScopeMapper(List.of(10L)));

        assertThatThrownBy(() -> service.selectDocumentStatusLogList(new InvDocumentStatusLog(), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请先选择店铺或仓库");
    }

    private static InvDocumentStatusLogServiceImpl service(FakeDocumentStatusLogMapper logMapper,
            FakeDeptScopeMapper deptScopeMapper)
    {
        InvDocumentStatusLogServiceImpl service = new InvDocumentStatusLogServiceImpl();
        ReflectionTestUtils.setField(service, "documentStatusLogMapper", logMapper);
        ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
        return service;
    }

    private static class FakeDocumentStatusLogMapper implements InvDocumentStatusLogMapper
    {
        private InvDocumentStatusLog lastQuery;

        @Override
        public List<InvDocumentStatusLog> selectInvDocumentStatusLogList(InvDocumentStatusLog documentStatusLog)
        {
            this.lastQuery = documentStatusLog;
            return Collections.emptyList();
        }
    }

    private static class FakeDeptScopeMapper implements InvDeptScopeMapper
    {
        @Override public List<Long> selectUserAuthorizedInventoryDeptIds(Long userId) { return java.util.Collections.emptyList(); }
        @Override public List<Long> selectAllActiveInventoryDeptIds() { return java.util.Collections.emptyList(); }
        private final List<Long> scopedDeptIds;
        private final List<String> countUserShopScopeCalls = new java.util.ArrayList<>();

        FakeDeptScopeMapper(List<Long> scopedDeptIds)
        {
            this.scopedDeptIds = scopedDeptIds;
        }

        @Override
        public List<Long> selectSubDeptIds(Long deptId)
        {
            return scopedDeptIds;
        }

        @Override
        public List<Long> selectRelatedDeptIds(Long deptId)
        {
            return scopedDeptIds;
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
            return scopedDeptIds.contains(targetDeptId) ? 1 : 0;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            countUserShopScopeCalls.add(userId + ":" + deptId);
            return 1;
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
}
