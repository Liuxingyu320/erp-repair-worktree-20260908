package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.constant.HttpStatus;
import com.erp.system.api.domain.SysDept;
import com.erp.system.domain.SysUserShop;
import com.erp.system.domain.vo.SysUserShopScopeVo;
import com.erp.system.domain.vo.SysUserShopScopePreviewVo;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysUserShopMapper;
import com.erp.system.service.ISysUserShopService;

@DisplayName("用户店铺授权服务")
class SysUserShopServiceImplTest
{
    @Test
    @DisplayName("超级管理员可见全部店铺和仓库节点")
    void adminShouldSeeAllShopAndWarehouseNodes()
    {
        List<SysDept> contextNodes = Arrays.asList(
                dept(100L, 0L, "深圳公司", "COMPANY"),
                shop(201L, 100L, "深圳南山店"),
                warehouse(301L, 100L, "深圳总仓"));
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("selectShopAuthTreeList".equals(method))
            {
                return contextNodes;
            }
            throw unexpected(method);
        });
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            throw unexpected(method);
        });
        SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

        List<SysDept> result = userShopService.selectAuthorizedShopTree(1L, true);

        assertThat(result).extracting(SysDept::getDeptId).containsExactly(100L);
        assertThat(result.get(0).getChildren()).extracting(SysDept::getDeptId).containsExactly(201L, 301L);
    }

    @Test
    @DisplayName("普通用户只可见已授权店铺")
    void normalUserShouldOnlySeeBoundShops()
    {
        List<Long> authorizedIds = Arrays.asList(202L, 203L);
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("selectShopDeptIdsByUserId".equals(method))
            {
                assertThat(args[0]).isEqualTo(2L);
                return authorizedIds;
            }
            throw unexpected(method);
        });
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("selectShopAuthTreeListByShopIds".equals(method))
            {
                assertThat(args[0]).isEqualTo(authorizedIds);
                return Arrays.asList(
                        dept(100L, 0L, "深圳公司", "COMPANY"),
                        shop(202L, 100L, "深圳福田店"),
                        shop(203L, 100L, "广州天河店"));
            }
            throw unexpected(method);
        });
        SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

        List<SysDept> result = userShopService.selectAuthorizedShopTree(2L, false);

        assertThat(result).extracting(SysDept::getDeptId).containsExactly(100L);
        assertThat(result.get(0).getChildren()).extracting(SysDept::getDeptId).containsExactly(202L, 203L);
    }

    @Test
    @DisplayName("授权上下文树SQL包含仓库组织")
    void authContextTreeSqlShouldIncludeWarehouseNodes() throws Exception
    {
        String xml = new String(new ClassPathResource("mapper/system/SysDeptMapper.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(xml).contains("d.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')");
        assertThat(xml).contains("scope.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')");
        assertThat(xml).contains("d.dept_type in ('STORE', 'WAREHOUSE')");
    }

    @Test
    @DisplayName("用户组织授权SQL保存和校验包含仓库组织")
    void userShopBindingSqlShouldIncludeWarehouseNodes() throws Exception
    {
        String deptXml = new String(new ClassPathResource("mapper/system/SysDeptMapper.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        String userShopXml = new String(new ClassPathResource("mapper/system/SysUserShopMapper.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(deptXml).contains("d.dept_type in ('STORE', 'WAREHOUSE')");
        assertThat(userShopXml).contains("scope_dept.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')");
        assertThat(userShopXml).contains("target_dept.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')");
        assertThat(userShopXml).contains("selectAllShopDeptIdsByUserId");
        assertThat(userShopXml).contains("lockUserForShopScope");
        assertThat(userShopXml.toLowerCase()).contains("for update");
        assertThat(userShopXml).contains("deleteUserShopByUserIdAndDeptIds");
        assertThat(userShopXml).contains("and 1 = 0");
    }

    @Test
    @DisplayName("用户组织授权SQL支持公司级继承下级业务组织")
    void userShopBindingSqlShouldInheritCompanyScope() throws Exception
    {
        String deptXml = new String(new ClassPathResource("mapper/system/SysDeptMapper.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        String userShopXml = new String(new ClassPathResource("mapper/system/SysUserShopMapper.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(deptXml)
                .as("authorization tree should accept company/group roots and expand descendants from sys_dept.ancestors")
                .contains(
                        "scope.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')",
                        "find_in_set(scope.dept_id, d.ancestors)",
                        "find_in_set(d.dept_id, scope.ancestors)");
        assertThat(userShopXml)
                .as("scope checks should match direct authorization or an authorized ancestor")
                .contains(
                        "scope_dept.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')",
                        "target_dept.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')",
                        "find_in_set(scope_dept.dept_id, target_dept.ancestors)")
                .doesNotContain("and us.dept_id = #{deptId}");
    }

    @Test
    @DisplayName("普通用户未配置店铺时可选店铺为空")
    void normalUserWithoutBindingsShouldSeeEmptyTree()
    {
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("selectShopDeptIdsByUserId".equals(method))
            {
                return Collections.emptyList();
            }
            throw unexpected(method);
        });
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            throw unexpected(method);
        });
        SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

        List<SysDept> result = userShopService.selectAuthorizedShopTree(2L, false);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("批量查询用户组织授权按用户分组返回")
    void batchUserShopScopesShouldGroupByUserId()
    {
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("selectUserShopsByUserIds".equals(method))
            {
                assertThat(args[0]).isEqualTo(Arrays.asList(2L, 3L));
                return Arrays.asList(userShop(2L, 202L), userShop(2L, 203L), userShop(3L, 301L));
            }
            throw unexpected(method);
        });
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            throw unexpected(method);
        });
        SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

        Map<Long, List<Long>> result = userShopService.selectShopDeptIdsByUserIds(new Long[] { 2L, 3L, 2L, null });

        assertThat(result).containsOnlyKeys(2L, 3L);
        assertThat(result.get(2L)).containsExactly(202L, 203L);
        assertThat(result.get(3L)).containsExactly(301L);
    }

    @Test
    @DisplayName("普通操作人查询用户授权时拆分可编辑和需保留店铺")
    void normalOperatorScopeQueryShouldSplitEditableAndPreservedShops()
    {
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("selectShopDeptIdsByUserId".equals(method))
            {
                assertThat(args[0]).isEqualTo(20L);
                return Arrays.asList(202L, 999L);
            }
            if ("countUserShopScope".equals(method))
            {
                assertThat(args[0]).isEqualTo(9L);
                return Long.valueOf(202L).equals(args[1]) ? 1 : 0;
            }
            throw unexpected(method);
        });
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            throw unexpected(method);
        });
        SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

        SysUserShopScopeVo result = userShopService.selectUserShopScope(20L, 9L, false);

        assertThat(result.getShopIds()).containsExactly(202L, 999L);
        assertThat(result.getEditableShopIds()).containsExactly(202L);
        assertThat(result.getPreservedShopIds()).containsExactly(999L);
        assertThat(result.getOutOfScopeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("管理员查询用户授权时全部店铺都可编辑")
    void adminScopeQueryShouldTreatAllShopsAsEditable()
    {
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("selectShopDeptIdsByUserId".equals(method))
            {
                assertThat(args[0]).isEqualTo(20L);
                return Arrays.asList(202L, 999L);
            }
            if ("countUserShopScope".equals(method))
            {
                throw new AssertionError("Admin scope query should not check per-shop binding");
            }
            throw unexpected(method);
        });
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            throw unexpected(method);
        });
        SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

        SysUserShopScopeVo result = userShopService.selectUserShopScope(20L, 1L, true);

        assertThat(result.getShopIds()).containsExactly(202L, 999L);
        assertThat(result.getEditableShopIds()).containsExactly(202L, 999L);
        assertThat(result.getPreservedShopIds()).isEmpty();
        assertThat(result.getOutOfScopeCount()).isZero();
    }

    @Test
    @DisplayName("授权预览区分直接授权和继承后的生效范围")
    void previewShouldCalculateDirectAndEffectiveDifferences()
    {
        List<SysDept> allNodes = Arrays.asList(
                scopedDept(100L, 0L, "深圳公司", "COMPANY", "0"),
                scopedDept(201L, 100L, "南山店", "STORE", "0,100"),
                scopedDept(202L, 100L, "福田店", "STORE", "0,100"));
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("countNormalShopByIds".equals(method))
            {
                assertThat(args[0]).isEqualTo(Collections.singletonList(100L));
                return 1;
            }
            if ("selectShopAuthTreeList".equals(method))
            {
                return allNodes;
            }
            throw unexpected(method);
        });
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("selectAllShopDeptIdsByUserId".equals(method))
            {
                assertThat(args[0]).isEqualTo(20L);
                return Collections.singletonList(201L);
            }
            throw unexpected(method);
        });

        SysUserShopScopePreviewVo result = newService(deptMapper, userShopMapper)
                .previewUserShops(20L, Collections.singletonList(100L), 1L, true);

        assertThat(result.getNormalizedShopIds()).containsExactly(100L);
        assertThat(result.getDirectAddedIds()).containsExactly(100L);
        assertThat(result.getDirectRemovedIds()).containsExactly(201L);
        assertThat(result.getEffectiveAddedCount()).isEqualTo(2);
        assertThat(result.getEffectiveRemovedCount()).isZero();
        assertThat(result.getScopeVersion()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("普通管理员预览和保存都保留当前管理范围外授权")
    void normalOperatorPreviewShouldPreserveOutOfBoundaryScope()
    {
        List<SysDept> editableNodes = Arrays.asList(
                scopedDept(100L, 0L, "深圳公司", "COMPANY", "0"),
                scopedDept(201L, 100L, "南山店", "STORE", "0,100"),
                scopedDept(202L, 100L, "福田店", "STORE", "0,100"));
        List<SysDept> allNodes = Arrays.asList(
                editableNodes.get(0), editableNodes.get(1), editableNodes.get(2),
                scopedDept(999L, 900L, "范围外门店", "STORE", "0,900"));
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("countNormalShopByIds".equals(method)) return 1;
            if ("selectShopAuthTreeListByShopIds".equals(method))
            {
                assertThat(args[0]).isEqualTo(Collections.singletonList(100L));
                return editableNodes;
            }
            if ("selectShopAuthTreeList".equals(method)) return allNodes;
            throw unexpected(method);
        });
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("selectAllShopDeptIdsByUserId".equals(method))
            {
                assertThat(args[0]).isEqualTo(20L);
                return Arrays.asList(201L, 999L);
            }
            if ("selectShopDeptIdsByUserId".equals(method))
            {
                assertThat(args[0]).isEqualTo(9L);
                return Collections.singletonList(100L);
            }
            throw unexpected(method);
        });

        SysUserShopScopePreviewVo result = newService(deptMapper, userShopMapper)
                .previewUserShops(20L, Collections.singletonList(202L), 9L, false);

        assertThat(result.getNormalizedShopIds()).containsExactly(202L);
        assertThat(result.getDirectAddedIds()).containsExactly(202L);
        assertThat(result.getDirectRemovedIds()).containsExactly(201L);
        assertThat(result.getPreservedShopIds()).containsExactly(999L);
        assertThat(result.getWarnings()).anyMatch(value -> value.contains("原样保留"));
    }

    @Test
    @DisplayName("锁定用户后发现版本变化时拒绝覆盖且不执行写入")
    void stalePreviewVersionShouldRejectWithoutWrites()
    {
        List<SysDept> allNodes = Arrays.asList(
                scopedDept(100L, 0L, "深圳公司", "COMPANY", "0"),
                scopedDept(201L, 100L, "南山店", "STORE", "0,100"),
                scopedDept(202L, 100L, "福田店", "STORE", "0,100"));
        AtomicReference<List<Long>> current = new AtomicReference<>(Collections.singletonList(201L));
        AtomicBoolean deleteCalled = new AtomicBoolean(false);
        AtomicBoolean insertCalled = new AtomicBoolean(false);
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("countNormalShopByIds".equals(method)) return 1;
            if ("selectShopAuthTreeList".equals(method)) return allNodes;
            throw unexpected(method);
        });
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("selectAllShopDeptIdsByUserId".equals(method)) return current.get();
            if ("lockUserForShopScope".equals(method)) return 20L;
            if ("deleteUserShopByUserId".equals(method))
            {
                deleteCalled.set(true);
                return 1;
            }
            if ("batchUserShop".equals(method))
            {
                insertCalled.set(true);
                return 1;
            }
            throw unexpected(method);
        });
        SysUserShopServiceImpl service = newService(deptMapper, userShopMapper);
        String previewVersion = service.previewUserShops(20L, Collections.singletonList(100L), 1L, true)
                .getScopeVersion();
        current.set(Collections.singletonList(202L));

        assertThatThrownBy(() -> service.savePreviewedUserShops(20L,
                Collections.singletonList(100L), previewVersion, "admin", 1L, true))
                .isInstanceOf(SysUserShopScopeConflictException.class);
        assertThat(deleteCalled).isFalse();
        assertThat(insertCalled).isFalse();
    }

    @Test
    @DisplayName("普通用户只能通过已授权店铺校验")
    void normalUserShopScopeShouldRequireBinding()
    {
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("countUserShopScope".equals(method))
            {
                return Long.valueOf(202L).equals(args[1]) ? 1 : 0;
            }
            throw unexpected(method);
        });
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            throw unexpected(method);
        });
        SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

        assertThatCode(() -> userShopService.checkUserShopScope(2L, 202L, false))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> userShopService.checkUserShopScope(2L, 203L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户无权选择该店铺")
                .satisfies(error -> assertThat(((ServiceException) error).getCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("普通操作人不能分配未绑定店铺或仓库")
    void normalOperatorShouldNotAssignUnboundShop()
    {
        AtomicBoolean deleteCalled = new AtomicBoolean(false);
        AtomicBoolean batchCalled = new AtomicBoolean(false);
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("countNormalShopByIds".equals(method))
            {
                assertThat(args[0]).isEqualTo(Arrays.asList(202L, 999L));
                return 2;
            }
            throw unexpected(method);
        });
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("countUserShopScope".equals(method))
            {
                assertThat(args[0]).isEqualTo(9L);
                return Long.valueOf(202L).equals(args[1]) ? 1 : 0;
            }
            if ("deleteUserShopByUserId".equals(method))
            {
                deleteCalled.set(true);
                return 1;
            }
            if ("batchUserShop".equals(method))
            {
                batchCalled.set(true);
                return 1;
            }
            throw unexpected(method);
        });
        SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

        assertThatThrownBy(() -> userShopService.saveUserShops(20L, new Long[] { 202L, 999L }, "operator", 9L,
                false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权分配该店铺或仓库")
                .satisfies(error -> assertThat(((ServiceException) error).getCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(deleteCalled).isFalse();
        assertThat(batchCalled).isFalse();
    }

    @Test
    @DisplayName("普通操作人空请求仅清除可编辑已有店铺或仓库")
    void normalOperatorEmptyRequestShouldOnlyClearEditableExistingShop()
    {
        AtomicBoolean fullDeleteCalled = new AtomicBoolean(false);
        AtomicBoolean deleteByIdsCalled = new AtomicBoolean(false);
        AtomicBoolean batchCalled = new AtomicBoolean(false);
        AtomicBoolean rawSelectCalled = new AtomicBoolean(false);
        AtomicBoolean filteredSelectCalled = new AtomicBoolean(false);
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            throw unexpected(method);
        });
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("selectAllShopDeptIdsByUserId".equals(method))
            {
                rawSelectCalled.set(true);
                assertThat(args[0]).isEqualTo(20L);
                return Arrays.asList(202L, 999L);
            }
            if ("selectShopDeptIdsByUserId".equals(method))
            {
                filteredSelectCalled.set(true);
                assertThat(args[0]).isEqualTo(20L);
                return Arrays.asList(202L, 999L);
            }
            if ("countUserShopScope".equals(method))
            {
                assertThat(args[0]).isEqualTo(9L);
                return Long.valueOf(202L).equals(args[1]) ? 1 : 0;
            }
            if ("deleteUserShopByUserId".equals(method))
            {
                fullDeleteCalled.set(true);
                return 1;
            }
            if ("deleteUserShopByUserIdAndDeptIds".equals(method))
            {
                deleteByIdsCalled.set(true);
                assertThat(args[0]).isEqualTo(20L);
                assertThat(args[1]).isEqualTo(Collections.singletonList(202L));
                return 1;
            }
            if ("batchUserShop".equals(method))
            {
                batchCalled.set(true);
                return 1;
            }
            throw unexpected(method);
        });
        SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

        assertThatCode(() -> userShopService.saveUserShops(20L, new Long[0], "operator", 9L, false))
                .doesNotThrowAnyException();
        assertThat(rawSelectCalled).isTrue();
        assertThat(filteredSelectCalled).isFalse();
        assertThat(deleteByIdsCalled).isTrue();
        assertThat(fullDeleteCalled).isFalse();
        assertThat(batchCalled).isFalse();
    }

    @Test
    @DisplayName("普通操作人子集请求保留超范围已有店铺或仓库")
    void normalOperatorSubsetRequestShouldPreserveOutOfScopeExistingShop()
    {
        AtomicBoolean fullDeleteCalled = new AtomicBoolean(false);
        AtomicBoolean deleteByIdsCalled = new AtomicBoolean(false);
        AtomicBoolean batchCalled = new AtomicBoolean(false);
        AtomicBoolean rawSelectCalled = new AtomicBoolean(false);
        AtomicBoolean filteredSelectCalled = new AtomicBoolean(false);
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("countNormalShopByIds".equals(method))
            {
                assertThat(args[0]).isEqualTo(Collections.singletonList(202L));
                return 1;
            }
            throw unexpected(method);
        });
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("selectAllShopDeptIdsByUserId".equals(method))
            {
                rawSelectCalled.set(true);
                assertThat(args[0]).isEqualTo(20L);
                return Arrays.asList(202L, 999L);
            }
            if ("selectShopDeptIdsByUserId".equals(method))
            {
                filteredSelectCalled.set(true);
                assertThat(args[0]).isEqualTo(20L);
                return Arrays.asList(202L, 999L);
            }
            if ("countUserShopScope".equals(method))
            {
                assertThat(args[0]).isEqualTo(9L);
                return Long.valueOf(202L).equals(args[1]) ? 1 : 0;
            }
            if ("deleteUserShopByUserId".equals(method))
            {
                fullDeleteCalled.set(true);
                return 1;
            }
            if ("deleteUserShopByUserIdAndDeptIds".equals(method))
            {
                deleteByIdsCalled.set(true);
                assertThat(args[0]).isEqualTo(20L);
                assertThat(args[1]).isEqualTo(Collections.singletonList(202L));
                return 1;
            }
            if ("batchUserShop".equals(method))
            {
                batchCalled.set(true);
                @SuppressWarnings("unchecked")
                List<SysUserShop> bindings = (List<SysUserShop>) args[0];
                assertThat(bindings).extracting(SysUserShop::getUserId).containsExactly(20L);
                assertThat(bindings).extracting(SysUserShop::getDeptId).containsExactly(202L);
                assertThat(bindings).extracting(SysUserShop::getCreateBy).containsExactly("operator");
                return 1;
            }
            throw unexpected(method);
        });
        SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

        assertThatCode(() -> userShopService.saveUserShops(20L, new Long[] { 202L }, "operator", 9L, false))
                .doesNotThrowAnyException();
        assertThat(rawSelectCalled).isTrue();
        assertThat(filteredSelectCalled).isFalse();
        assertThat(deleteByIdsCalled).isTrue();
        assertThat(fullDeleteCalled).isFalse();
        assertThat(batchCalled).isTrue();
    }

    @Test
    @DisplayName("管理员可以分配任意正常店铺或仓库")
    void adminShouldAssignAnyNormalShop()
    {
        AtomicBoolean batchCalled = new AtomicBoolean(false);
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("countNormalShopByIds".equals(method))
            {
                assertThat(args[0]).isEqualTo(Arrays.asList(202L, 999L));
                return 2;
            }
            throw unexpected(method);
        });
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("deleteUserShopByUserId".equals(method))
            {
                assertThat(args[0]).isEqualTo(20L);
                return 1;
            }
            if ("batchUserShop".equals(method))
            {
                List<?> bindings = (List<?>) args[0];
                assertThat(bindings).hasSize(2);
                batchCalled.set(true);
                return 1;
            }
            throw unexpected(method);
        });
        SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

        assertThatCode(() -> userShopService.saveUserShops(20L, new Long[] { 202L, 999L }, "admin", 1L, true))
                .doesNotThrowAnyException();
        assertThat(batchCalled).isTrue();
    }

    @Test
    @DisplayName("管理员可以把一级公司作为授权根节点")
    void adminShouldAssignNormalCompanyAsScopeRoot()
    {
        AtomicBoolean batchCalled = new AtomicBoolean(false);
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("countNormalShopByIds".equals(method))
            {
                assertThat(args[0]).isEqualTo(Collections.singletonList(100L));
                return 1;
            }
            throw unexpected(method);
        });
        SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
            if ("deleteUserShopByUserId".equals(method))
            {
                assertThat(args[0]).isEqualTo(20L);
                return 1;
            }
            if ("batchUserShop".equals(method))
            {
                @SuppressWarnings("unchecked")
                List<SysUserShop> bindings = (List<SysUserShop>) args[0];
                assertThat(bindings).extracting(SysUserShop::getDeptId).containsExactly(100L);
                batchCalled.set(true);
                return 1;
            }
            throw unexpected(method);
        });
        SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

        assertThatCode(() -> userShopService.saveUserShops(20L, new Long[] { 100L }, "admin", 1L, true))
                .doesNotThrowAnyException();
        assertThat(batchCalled).isTrue();
    }

    @Test
    @DisplayName("旧三参保存入口保持弃用状态")
    void legacySaveUserShopsOverloadShouldRemainDeprecated() throws Exception
    {
        assertThat(ISysUserShopService.class.getMethod("saveUserShops", Long.class, Long[].class, String.class))
                .matches(method -> method.isAnnotationPresent(Deprecated.class));
        assertThat(SysUserShopServiceImpl.class.getMethod("saveUserShops", Long.class, Long[].class, String.class))
                .matches(method -> method.isAnnotationPresent(Deprecated.class));
    }

    private static SysDept shop(Long deptId, Long parentId, String deptName)
    {
        return dept(deptId, parentId, deptName, "STORE");
    }

    private static SysDept warehouse(Long deptId, Long parentId, String deptName)
    {
        return dept(deptId, parentId, deptName, "WAREHOUSE");
    }

    private static SysDept dept(Long deptId, Long parentId, String deptName, String deptType)
    {
        SysDept dept = new SysDept();
        dept.setDeptId(deptId);
        dept.setParentId(parentId);
        dept.setDeptName(deptName);
        dept.setDeptType(deptType);
        dept.setStatus("0");
        return dept;
    }

    private static SysDept scopedDept(Long deptId, Long parentId, String deptName, String deptType,
            String ancestors)
    {
        SysDept dept = dept(deptId, parentId, deptName, deptType);
        dept.setAncestors(ancestors);
        return dept;
    }

    private static SysUserShop userShop(Long userId, Long deptId)
    {
        SysUserShop userShop = new SysUserShop();
        userShop.setUserId(userId);
        userShop.setDeptId(deptId);
        return userShop;
    }

    private static SysUserShopServiceImpl newService(SysDeptMapper deptMapper, SysUserShopMapper userShopMapper)
    {
        SysUserShopServiceImpl service = new SysUserShopServiceImpl();
        ReflectionTestUtils.setField(service, "deptMapper", deptMapper);
        ReflectionTestUtils.setField(service, "userShopMapper", userShopMapper);
        return service;
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, BiFunction<String, Object[], Object> handler)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class)
            {
                return method.invoke(thisProxy(type), args);
            }
            return handler.apply(method.getName(), args == null ? new Object[0] : args);
        });
    }

    private static Object thisProxy(Class<?> type)
    {
        return new Object()
        {
            @Override
            public String toString()
            {
                return type.getSimpleName() + "TestProxy";
            }
        };
    }

    private static AssertionError unexpected(String method)
    {
        return new AssertionError("Unexpected mapper call: " + method);
    }
}
