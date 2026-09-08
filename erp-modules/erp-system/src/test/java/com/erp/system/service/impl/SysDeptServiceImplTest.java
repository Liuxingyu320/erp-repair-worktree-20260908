package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.constant.HttpStatus;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.dto.SysSortChangeItem;
import com.erp.system.domain.dto.SysSortChangeRequest;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.service.ISysUserShopService;

@DisplayName("部门业务组织服务")
class SysDeptServiceImplTest
{
    @Test
    @DisplayName("管理员业务仓库列表返回全部启用仓库")
    void adminWarehouseListShouldDelegateToWarehouseQuery()
    {
        List<SysDept> warehouses = Collections.singletonList(warehouse(104L, 100L, "仓库"));
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("selectWarehouseList".equals(method))
            {
                return warehouses;
            }
            throw unexpected(method);
        });
        SysDeptServiceImpl deptService = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(deptService, "deptMapper", deptMapper);

        List<SysDept> result = deptService.selectWarehouseList(1L, true);

        assertThat(result).containsExactlyElementsOf(warehouses);
    }

    @Test
    @DisplayName("普通用户业务仓库列表返回全部启用仓库")
    void normalUserWarehouseListShouldReturnAllEnabledWarehouses()
    {
        List<SysDept> warehouses = Arrays.asList(
                warehouse(301L, 100L, "授权仓库"),
                warehouse(302L, 100L, "公共仓库"));
        ISysUserShopService userShopService = mapper(ISysUserShopService.class, (method, args) -> {
            throw unexpected(method);
        });
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("selectWarehouseList".equals(method))
            {
                return warehouses;
            }
            throw unexpected(method);
        });
        SysDeptServiceImpl deptService = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(deptService, "deptMapper", deptMapper);
        ReflectionTestUtils.setField(deptService, "userShopService", userShopService);

        List<SysDept> result = deptService.selectWarehouseList(2L, false);

        assertThat(result).extracting(SysDept::getDeptId).containsExactly(301L, 302L);
    }

    @Test
    @DisplayName("普通用户无仓库授权时业务仓库列表仍返回启用仓库")
    void normalUserWithoutWarehouseScopeShouldStillSeeEnabledWarehouseList()
    {
        List<SysDept> warehouses = Collections.singletonList(warehouse(104L, 100L, "仓库"));
        ISysUserShopService userShopService = mapper(ISysUserShopService.class, (method, args) -> {
            throw unexpected(method);
        });
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("selectWarehouseList".equals(method))
            {
                return warehouses;
            }
            throw unexpected(method);
        });
        SysDeptServiceImpl deptService = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(deptService, "deptMapper", deptMapper);
        ReflectionTestUtils.setField(deptService, "userShopService", userShopService);

        List<SysDept> result = deptService.selectWarehouseList(3L, false);

        assertThat(result).containsExactlyElementsOf(warehouses);
    }

    @Test
    @DisplayName("发货仓库按销售门店范围过滤")
    void deliverySourceWarehouseListShouldOnlyReturnWarehousesInsideScope()
    {
        SysDept scopedWarehouse = warehouse(301L, 201L, "门店发货仓");
        scopedWarehouse.setAncestors("0,100,201");
        SysDept outsideWarehouse = warehouse(302L, 202L, "其他门店仓");
        outsideWarehouse.setAncestors("0,100,202");
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("selectWarehouseList".equals(method))
            {
                return Arrays.asList(scopedWarehouse, outsideWarehouse);
            }
            throw unexpected(method);
        });
        SysDeptServiceImpl deptService = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(deptService, "deptMapper", deptMapper);

        List<SysDept> result = deptService.selectWarehouseList("deliverySource", 201L, 9L, false);

        assertThat(result).extracting(SysDept::getDeptId).containsExactly(301L);
    }

    @Test
    @DisplayName("门店要货只返回同一业务根仓库且不要求直接管理仓库")
    void replenishmentSourceWarehouseListShouldUseBusinessRootQuery()
    {
        SysDept mainWarehouse = warehouse(301L, 100L, "主仓库");
        SysDept backupWarehouse = warehouse(302L, 100L, "备用仓库");
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("selectReplenishmentSourceWarehouseList".equals(method))
            {
                assertThat(args).containsExactly(201L);
                return Arrays.asList(mainWarehouse, backupWarehouse);
            }
            throw unexpected(method);
        });
        ISysUserShopService userShopService = mapper(ISysUserShopService.class, (method, args) -> {
            if ("checkUserShopScope".equals(method))
            {
                assertThat(args).containsExactly(9L, 201L, false);
                return null;
            }
            throw unexpected(method);
        });
        SysDeptServiceImpl deptService = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(deptService, "deptMapper", deptMapper);
        ReflectionTestUtils.setField(deptService, "userShopService", userShopService);

        List<SysDept> result = deptService.selectWarehouseList("replenishmentSource", 201L, 9L, false);

        assertThat(result).extracting(SysDept::getDeptId).containsExactly(301L, 302L);
    }

    @Test
    @DisplayName("补货来源缺少目标门店时返回空列表")
    void replenishmentSourceWithoutStoreShouldReturnEmpty()
    {
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        ISysUserShopService userShopService = mock(
                ISysUserShopService.class);
        SysDeptServiceImpl service = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(service, "deptMapper", deptMapper);
        ReflectionTestUtils.setField(service, "userShopService",
                userShopService);

        assertThat(service.selectWarehouseList("replenishmentSource", null,
                9L, false)).isEmpty();
        verifyNoInteractions(deptMapper, userShopService);
    }

    @Test
    @DisplayName("返仓目标候选保持用户仓库授权隔离")
    void returnTargetShouldOnlyExposeAuthorizedWarehouses()
    {
        SysDept first = warehouse(301L, 100L, "授权仓库");
        SysDept second = warehouse(302L, 100L, "未授权仓库");
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        when(deptMapper.selectWarehouseList()).thenReturn(
                List.of(first, second));
        ISysUserShopService userShopService = mock(
                ISysUserShopService.class);
        org.mockito.Mockito.doNothing().when(userShopService)
                .checkUserShopScope(9L, 201L, false);
        org.mockito.Mockito.doNothing().when(userShopService)
                .checkUserShopScope(9L, 301L, false);
        org.mockito.Mockito.doThrow(new ServiceException("无权访问"))
                .when(userShopService)
                .checkUserShopScope(9L, 302L, false);
        SysDeptServiceImpl service = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(service, "deptMapper", deptMapper);
        ReflectionTestUtils.setField(service, "userShopService",
                userShopService);

        List<SysDept> result = service.selectWarehouseList("returnTarget",
                201L, 9L, false);

        assertThat(result).extracting(SysDept::getDeptId)
                .containsExactly(301L);
    }

    @Test
    @DisplayName("非空未知用途明确拒绝而空用途保留旧列表")
    void unknownPurposeShouldFailButEmptyPurposeShouldRemainCompatible()
    {
        SysDept warehouse = warehouse(301L, 100L, "主仓库");
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        when(deptMapper.selectWarehouseList()).thenReturn(List.of(warehouse));
        SysDeptServiceImpl service = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(service, "deptMapper", deptMapper);

        assertThat(service.selectWarehouseList("", 201L, 9L, false))
                .containsExactly(warehouse);
        assertThatThrownBy(() -> service.selectWarehouseList(
                "unexpectedPurpose", 201L, 9L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不支持的仓库选择用途")
                .satisfies(error -> assertThat(
                        ((ServiceException) error).getCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.selectWarehouseList(
                " ", 201L, 9L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不支持的仓库选择用途");
    }

    @Test
    @DisplayName("当前仓库目的只返回当前仓库")
    void currentWarehouseListShouldOnlyReturnSelectedWarehouse()
    {
        SysDept currentWarehouse = warehouse(301L, 100L, "当前仓库");
        SysDept otherWarehouse = warehouse(302L, 100L, "其他仓库");
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("selectWarehouseList".equals(method))
            {
                return Arrays.asList(currentWarehouse, otherWarehouse);
            }
            throw unexpected(method);
        });
        SysDeptServiceImpl deptService = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(deptService, "deptMapper", deptMapper);
        ReflectionTestUtils.setField(deptService, "userShopService", mapper(ISysUserShopService.class, (method, args) -> {
            if ("checkUserShopScope".equals(method))
            {
                return null;
            }
            throw unexpected(method);
        }));

        List<SysDept> result = deptService.selectWarehouseList("currentWarehouse", 301L, 9L, false);

        assertThat(result).extracting(SysDept::getDeptId).containsExactly(301L);
    }

    @Test
    @DisplayName("仓库可见门店列表按当前仓库同一业务根返回门店")
    void visibleStoreListShouldReturnStoresInsideSelectedWarehouseScope()
    {
        SysDept storeA = shop(401L, 201L, "同公司门店A");
        SysDept storeB = shop(402L, 202L, "同公司门店B");
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("selectVisibleStoreListByScopeDeptId".equals(method))
            {
                assertThat(args[0]).isEqualTo(301L);
                return Arrays.asList(storeA, storeB);
            }
            throw unexpected(method);
        });
        ISysUserShopService userShopService = mapper(ISysUserShopService.class, (method, args) -> {
            if ("checkUserShopScope".equals(method))
            {
                assertThat(args).containsExactly(9L, 301L, false);
                return null;
            }
            throw unexpected(method);
        });
        SysDeptServiceImpl deptService = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(deptService, "deptMapper", deptMapper);
        ReflectionTestUtils.setField(deptService, "userShopService", userShopService);

        List<SysDept> result = deptService.selectVisibleStoreList(301L, 9L, false);

        assertThat(result).extracting(SysDept::getDeptId).containsExactly(401L, 402L);
    }

    @Test
    @DisplayName("业务仓库列表SQL只返回启用仓库")
    void warehouseAndVisibleStoreListSqlShouldOnlyReturnNormalBusinessDepts() throws Exception
    {
        String xml = new String(new ClassPathResource("mapper/system/SysDeptMapper.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(xml).contains("<select id=\"selectWarehouseList\"");
        assertThat(xml).contains(
                "<select id=\"selectReplenishmentSourceWarehouseList\"",
                "current_dept.dept_type = 'STORE'",
                "scope_root.del_flag = '0'",
                "scope_root.status = '0'",
                "substring_index(substring_index(d.ancestors, ',', 2), ',', -1)",
                ") = scope_root.dept_id");
        assertThat(xml).contains("d.del_flag = '0'");
        assertThat(xml).contains("d.status = '0'");
        assertThat(xml).contains("d.dept_type = 'WAREHOUSE'");
        assertThat(xml).contains("<select id=\"selectVisibleStoreListByScopeDeptId\"");
        assertThat(xml).contains("d.dept_type = 'STORE'");
        assertThat(xml).contains("inner join sys_dept current_dept on current_dept.dept_id = #{scopeDeptId}");
        assertThat(xml).contains("inner join sys_dept scope_root on scope_root.dept_id = cast(");
        assertThat(xml).contains("find_in_set(scope_root.dept_id, d.ancestors)");
    }

    @Test
    @DisplayName("新增部门前校验父部门数据范围")
    void insertDeptShouldCheckParentDeptScopeBeforeWriting()
    {
        List<String> events = new ArrayList<>();
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("selectDeptList".equals(method))
            {
                SysDept query = (SysDept) args[0];
                events.add("scope:" + query.getDeptId());
                return Collections.emptyList();
            }
            if ("selectDeptById".equals(method))
            {
                events.add("read-parent:" + args[0]);
                return dept((Long) args[0], 0L, "公司", "COMPANY");
            }
            if ("insertDept".equals(method))
            {
                events.add("write:insertDept");
                return 1;
            }
            throw unexpected(method);
        });
        SysDeptServiceImpl deptService = proxiedDeptService(deptMapper);

        assertThatThrownBy(() -> deptService.insertDept(dept(null, 100L, "越权门店", "STORE")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("没有权限访问部门数据")
                .satisfies(error -> assertThat(((ServiceException) error).getCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(events).contains("scope:100");
        assertThat(events).doesNotContain("write:insertDept");
    }

    @Test
    @DisplayName("保存部门排序前校验每个目标部门数据范围")
    void updateDeptSortShouldCheckEachDeptScopeBeforeWriting()
    {
        List<String> events = new ArrayList<>();
        SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
            if ("selectDeptList".equals(method))
            {
                SysDept query = (SysDept) args[0];
                events.add("scope:" + query.getDeptId());
                if (Long.valueOf(202L).equals(query.getDeptId()))
                {
                    return Collections.emptyList();
                }
                return Collections.singletonList(query);
            }
            if ("selectDeptById".equals(method))
            {
                events.add("read:" + args[0]);
                return dept((Long) args[0], 100L, "部门", "STORE");
            }
            if ("updateDeptSort".equals(method))
            {
                events.add("write:sort");
                return 1;
            }
            throw unexpected(method);
        });
        SysDeptServiceImpl deptService = proxiedDeptService(deptMapper);

        assertThatThrownBy(() -> deptService.updateDeptSort(sortRequest(
                sortChange(101L, 5, 1), sortChange(202L, 6, 2))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("没有权限访问部门数据")
                .satisfies(error -> assertThat(((ServiceException) error).getCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(events).containsExactly("scope:101", "scope:202");
        assertThat(events).doesNotContain("write:sort");
    }

    private static SysSortChangeRequest sortRequest(SysSortChangeItem... changes)
    {
        SysSortChangeRequest request = new SysSortChangeRequest();
        request.setChanges(Arrays.asList(changes));
        return request;
    }

    private static SysSortChangeItem sortChange(Long id, int expected, int next)
    {
        SysSortChangeItem change = new SysSortChangeItem();
        change.setId(id);
        change.setExpectedOrderNum(expected);
        change.setNewOrderNum(next);
        return change;
    }

    @Test
    @DisplayName("负责人稳定ID校验成功后由员工主数据同步姓名快照")
    void normalizeLeaderIdentityShouldSyncEmployeeNameSnapshot()
    {
        HrEmployeeAccessService employeeAccess=mock(HrEmployeeAccessService.class);
        SysUser leader=employee(9L,"负责人甲","0","E009",200L,"运营中心");
        when(employeeAccess.findActiveScoped(any(HrEmployeeQuery.class))).thenReturn(leader);
        SysDeptServiceImpl service=new SysDeptServiceImpl();
        ReflectionTestUtils.setField(service,"employeeAccess",employeeAccess);
        SysDept department=dept(200L,100L,"运营中心","COMPANY");
        department.setLeaderUserId(9L);
        department.setLeader("客户端伪造姓名");

        service.normalizeLeaderIdentity(department);

        assertThat(department.getLeaderUserId()).isEqualTo(9L);
        assertThat(department.getLeader()).isEqualTo("负责人甲");
    }

    @Test
    @DisplayName("启用公司或部门必须选择有效负责人账号")
    void activeGovernedDepartmentShouldRequireStableLeaderIdentity()
    {
        HrEmployeeAccessService employeeAccess=mock(HrEmployeeAccessService.class);
        SysDeptServiceImpl service=new SysDeptServiceImpl();
        ReflectionTestUtils.setField(service,"employeeAccess",employeeAccess);
        SysDept department=dept(200L,100L,"运营中心","COMPANY");
        department.setLeader("历史负责人");

        assertThatThrownBy(()->service.normalizeLeaderIdentity(department))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须选择有效负责人");
        verifyNoInteractions(employeeAccess);
    }

    @Test
    @DisplayName("未携带负责人字段的局部更新保留原负责人")
    void partialUpdateWithoutLeaderIdentityShouldPreserveExistingLeader()
    {
        SysDept parent=dept(100L,0L,"集团","GROUP");
        parent.setAncestors("0");
        SysDept existing=dept(200L,100L,"运营中心","COMPANY");
        existing.setAncestors("0,100");
        existing.setLeaderUserId(9L);
        existing.setLeader("负责人甲");
        SysDept proposed=dept(200L,100L,"运营中心","COMPANY");
        proposed.setPhone("13800138000");
        assertThat(proposed.isLeaderUserIdSpecified()).isFalse();

        SysDeptMapper deptMapper=mock(SysDeptMapper.class);
        when(deptMapper.selectDeptById(100L)).thenReturn(parent);
        when(deptMapper.selectDeptById(200L)).thenReturn(existing);
        when(deptMapper.selectChildrenDeptById(200L)).thenReturn(Collections.emptyList());
        when(deptMapper.updateDept(any(SysDept.class))).thenReturn(1);
        HrEmployeeAccessService employeeAccess=mock(HrEmployeeAccessService.class);
        SysDeptServiceImpl service=new SysDeptServiceImpl();
        ReflectionTestUtils.setField(service,"deptMapper",deptMapper);
        ReflectionTestUtils.setField(service,"employeeAccess",employeeAccess);

        assertThat(service.updateDept(proposed)).isEqualTo(1);

        ArgumentCaptor<SysDept> captor=ArgumentCaptor.forClass(SysDept.class);
        verify(deptMapper).updateDept(captor.capture());
        assertThat(captor.getValue().getLeaderUserId()).isEqualTo(9L);
        assertThat(captor.getValue().getLeader()).isEqualTo("负责人甲");
        verifyNoInteractions(employeeAccess);
    }

    @Test
    @DisplayName("可选负责人组织显式传空ID时清空负责人")
    void optionalDepartmentExplicitNullLeaderIdentityShouldClearLeader()
    {
        SysDept existing=dept(300L,100L,"一号门店","STORE");
        existing.setLeaderUserId(9L);
        existing.setLeader("负责人甲");
        SysDept proposed=dept(300L,null,"一号门店","STORE");
        proposed.setLeaderUserId(null);
        assertThat(proposed.isLeaderUserIdSpecified()).isTrue();

        SysDeptMapper deptMapper=mock(SysDeptMapper.class);
        when(deptMapper.selectDeptById(null)).thenReturn(null);
        when(deptMapper.selectDeptById(300L)).thenReturn(existing);
        when(deptMapper.updateDept(any(SysDept.class))).thenReturn(1);
        SysDeptServiceImpl service=new SysDeptServiceImpl();
        ReflectionTestUtils.setField(service,"deptMapper",deptMapper);

        assertThat(service.updateDept(proposed)).isEqualTo(1);

        ArgumentCaptor<SysDept> captor=ArgumentCaptor.forClass(SysDept.class);
        verify(deptMapper).updateDept(captor.capture());
        assertThat(captor.getValue().getLeaderUserId()).isNull();
        assertThat(captor.getValue().getLeader()).isNull();
    }

    @Test
    @DisplayName("部门JSON可区分未携带与显式传空负责人ID")
    void departmentJsonShouldTrackExplicitLeaderIdentityIntent() throws Exception
    {
        ObjectMapper objectMapper=new ObjectMapper();

        SysDept omitted=objectMapper.readValue("{\"deptId\":200}",SysDept.class);
        SysDept explicitNull=objectMapper.readValue("{\"deptId\":200,\"leaderUserId\":null}",SysDept.class);

        assertThat(omitted.isLeaderUserIdSpecified()).isFalse();
        assertThat(explicitNull.isLeaderUserIdSpecified()).isTrue();
        assertThat(objectMapper.writeValueAsString(explicitNull)).doesNotContain("leaderUserIdSpecified");
    }

    @Test
    @DisplayName("超级管理员、停用员工和越权员工均不能成为负责人")
    void invalidEmployeeShouldNotBecomeLeader()
    {
        HrEmployeeAccessService employeeAccess=mock(HrEmployeeAccessService.class);
        SysDeptServiceImpl service=new SysDeptServiceImpl();
        ReflectionTestUtils.setField(service,"employeeAccess",employeeAccess);
        SysDept department=dept(200L,100L,"运营中心","COMPANY");
        department.setLeaderUserId(1L);
        assertThatThrownBy(()->service.normalizeLeaderIdentity(department))
                .isInstanceOf(ServiceException.class).hasMessageContaining("不能设置为负责人");
        verifyNoInteractions(employeeAccess);

        department.setLeaderUserId(9L);
        when(employeeAccess.findActiveScoped(any(HrEmployeeQuery.class)))
                .thenReturn(employee(9L,"停用员工","1","E009",200L,"运营中心"));
        assertThatThrownBy(()->service.normalizeLeaderIdentity(department))
                .isInstanceOf(ServiceException.class).hasMessageContaining("不存在、已离职、已停用或无权访问");

        when(employeeAccess.findActiveScoped(any(HrEmployeeQuery.class)))
                .thenThrow(new ServiceException("无权访问该员工或记录不存在"));
        assertThatThrownBy(()->service.normalizeLeaderIdentity(department))
                .isInstanceOf(ServiceException.class).hasMessageContaining("不存在、已离职、已停用或无权访问");
    }

    @Test
    @DisplayName("负责人候选只返回当前数据范围内启用在职员工的安全字段")
    void leaderOptionsShouldFilterDisabledEmployeesAndExposeSafeFields()
    {
        HrEmployeeAccessService employeeAccess=mock(HrEmployeeAccessService.class);
        when(employeeAccess.listActiveScoped(any(HrEmployeeQuery.class))).thenAnswer(invocation->{
            HrEmployeeQuery query=invocation.getArgument(0);
            assertThat(query.getKeyword()).isEqualTo("负责");
            return List.of(
                    employee(9L,"负责人甲","0","E009",200L,"运营中心"),
                    employee(10L,"停用负责人","1","E010",201L,"财务中心"));
        });
        SysDeptServiceImpl service=new SysDeptServiceImpl();
        ReflectionTestUtils.setField(service,"employeeAccess",employeeAccess);

        var options=service.selectLeaderOptions(" 负责 ");

        assertThat(options).singleElement().satisfies(option->{
            assertThat(option.getUserId()).isEqualTo(9L);
            assertThat(option.getEmployeeName()).isEqualTo("负责人甲");
            assertThat(option.getEmployeeNo()).isEqualTo("E009");
            assertThat(option.getDeptId()).isEqualTo(200L);
            assertThat(option.getDeptName()).isEqualTo("运营中心");
        });
    }

    private static SysDeptServiceImpl proxiedDeptService(SysDeptMapper deptMapper)
    {
        SysDeptServiceImpl deptService = new SysDeptServiceImpl();
        ReflectionTestUtils.setField(deptService, "deptMapper", deptMapper);
        ProxyFactory proxyFactory = new ProxyFactory(deptService);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.setExposeProxy(true);
        return (SysDeptServiceImpl) proxyFactory.getProxy();
    }

    private static SysDept warehouse(Long deptId, Long parentId, String deptName)
    {
        return dept(deptId, parentId, deptName, "WAREHOUSE");
    }

    private static SysDept shop(Long deptId, Long parentId, String deptName)
    {
        return dept(deptId, parentId, deptName, "STORE");
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

    private static SysUser employee(Long userId,String name,String status,String employeeNo,
            Long deptId,String deptName)
    {
        SysUser user=new SysUser();user.setUserId(userId);user.setNickName(name);user.setStatus(status);
        user.setEmployeeNo(employeeNo);user.setDeptId(deptId);
        SysDept dept=dept(deptId,100L,deptName,"COMPANY");user.setDept(dept);
        return user;
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
