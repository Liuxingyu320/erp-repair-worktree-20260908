package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.mapper.InvDeptScopeMapper;

@DisplayName("库存基础组织契约")
class InvBaseServiceTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("仓库上下文必须选择仓库组织")
    void shouldRequireWarehouseContext()
    {
        SecurityContextHolder.setUserId("1");
        TestBaseService service = serviceWithDeptTypes(Map.of(10L, "STORE", 20L, "WAREHOUSE"));

        assertThat(service.requireWarehouse(20L)).isEqualTo(20L);
        assertThatThrownBy(() -> service.requireWarehouse(10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择仓库");
    }

    @Test
    @DisplayName("门店上下文必须选择门店组织")
    void shouldRequireStoreContext()
    {
        SecurityContextHolder.setUserId("1");
        TestBaseService service = serviceWithDeptTypes(Map.of(10L, "STORE", 20L, "WAREHOUSE"));

        assertThat(service.requireStore(10L)).isEqualTo(10L);
        assertThatThrownBy(() -> service.requireStore(20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择门店");
    }

    @Test
    @DisplayName("admin 不能绕过组织业务类型不变量")
    void shouldNotLetAdminBypassBusinessTypeInvariant()
    {
        SecurityContextHolder.setUserId("1");
        TestBaseService service = serviceWithDeptTypes(Map.of(10L, "STORE"));

        assertThatThrownBy(() -> service.requireWarehouse(10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请选择仓库");
    }

    @Test
    @DisplayName("库存写入只能写当前选择组织")
    void shouldRequireWritableInventoryDeptToMatchSelectedContext()
    {
        SecurityContextHolder.setUserId("1");
        TestBaseService service = serviceWithDeptTypes(Map.of(10L, "STORE", 20L, "WAREHOUSE"));

        service.assertWritable(20L, 20L);

        assertThatThrownBy(() -> service.assertWritable(10L, 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能调整当前组织库存");
    }

    @Test
    @DisplayName("非管理员使用完整授权库存组织集合而不是组织层级关系")
    void shouldResolveAuthorizedInventoryDepartmentsForCurrentUser()
    {
        SecurityContextHolder.setUserId("7");
        TestBaseService service = serviceWithDeptTypes(
                Map.of(10L, "STORE", 20L, "WAREHOUSE"));

        assertThat(service.authorizedInventoryDeptIds())
                .containsExactlyInAnyOrder(10L, 20L);
        service.assertAuthorized(20L,
                service.authorizedInventoryDeptIds());
        assertThatThrownBy(() -> service.assertAuthorized(99L,
                service.authorizedInventoryDeptIds()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权使用库存组织");
    }

    private TestBaseService serviceWithDeptTypes(Map<Long, String> deptTypes)
    {
        TestBaseService service = new TestBaseService();
        service.deptScopeMapper = new FakeDeptScopeMapper(deptTypes);
        return service;
    }

    private static class TestBaseService extends InvBaseService
    {
        Long requireWarehouse(Long deptId)
        {
            return requireWarehouseContext(deptId, "请选择仓库");
        }

        Long requireStore(Long deptId)
        {
            return requireStoreContext(deptId, "请选择门店");
        }

        void assertWritable(Long inventoryDeptId, Long selectedDeptId)
        {
            assertWritableInventoryDept(inventoryDeptId, selectedDeptId, "只能调整当前组织库存");
        }

        List<Long> authorizedInventoryDeptIds()
        {
            return resolveAuthorizedInventoryDeptIds();
        }

        void assertAuthorized(Long inventoryDeptId,
                List<Long> authorizedDeptIds)
        {
            assertAuthorizedInventoryDept(inventoryDeptId,
                    authorizedDeptIds, "无权使用库存组织");
        }
    }

    private static class FakeDeptScopeMapper implements InvDeptScopeMapper
    {
        private final Map<Long, String> deptTypes = new HashMap<>();

        FakeDeptScopeMapper(Map<Long, String> deptTypes)
        {
            this.deptTypes.putAll(deptTypes);
        }

        @Override
        public List<Long> selectUserAuthorizedInventoryDeptIds(Long userId)
        {
            return deptTypes.keySet().stream().sorted().toList();
        }

        @Override
        public List<Long> selectAllActiveInventoryDeptIds()
        {
            return deptTypes.keySet().stream().sorted().toList();
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
            return scopeDeptId != null && scopeDeptId.equals(targetDeptId) ? 1 : 0;
        }

        @Override
        public int countUserShopScope(Long userId, Long deptId)
        {
            return 1;
        }

        @Override
        public String selectDeptNameById(Long deptId)
        {
            return "测试组织";
        }

        @Override
        public String selectDeptTypeById(Long deptId)
        {
            return deptTypes.get(deptId);
        }
    }
}
