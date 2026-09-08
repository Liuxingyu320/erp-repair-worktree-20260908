package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveRoleScope;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘组织数据范围")
class DriveOrganizationScopeServiceTest
{
    private DriveOrganizationMapper mapper;
    private DriveOrganizationScopeService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(DriveOrganizationMapper.class);
        service = new DriveOrganizationScopeService(mapper);
        when(mapper.selectAllOrganizations()).thenReturn(List.of(
                org(1L, "0", "0", "0"),
                org(8L, "0,1", "0", "0"),
                org(9L, "0,1,8", "0", "0"),
                org(18L, "0,1,80", "0", "0"),
                org(20L, "0,1,8", "1", "0"),
                org(21L, "0,1,8", "0", "2")));
    }

    @Test
    @DisplayName("本组织及下级范围使用分隔后的 ancestors 匹配防止越权")
    void shouldResolveDepartmentAndChildrenWithoutSubstringCollision()
    {
        DriveActor actor = manager(8L);
        when(mapper.selectRoleScopes(30L)).thenReturn(List.of(scope("4", null)));

        assertThat(service.manageableDeptIds(actor)).containsExactly(8L, 9L);
        assertThat(service.manageableDeptIds(actor)).doesNotContain(18L, 20L, 21L);
    }

    @Test
    @DisplayName("普通成员始终只能读取实时直属组织")
    void shouldAlwaysIncludeOnlyDirectDepartmentForOrdinaryMember()
    {
        DriveActor actor = new DriveActor(40L, 8L, "财务部", "alice",
                Set.of(DriveConstants.PERMISSION_ACCESS), false);

        assertThat(service.readableDeptIds(actor)).containsExactly(8L);
        assertThat(service.manageableDeptIds(actor)).isEmpty();
    }

    @Test
    @DisplayName("自定义范围只接受角色关联表的明确组织")
    void shouldUseOnlyExplicitCustomDepartments()
    {
        DriveActor actor = manager(8L);
        when(mapper.selectRoleScopes(30L)).thenReturn(List.of(
                scope("2", 9L), scope("2", 20L)));

        assertThat(service.manageableDeptIds(actor)).containsExactly(9L);
    }

    @Test
    @DisplayName("系统管理员可管理全部有效组织但不包含停用和删除项")
    void shouldGiveAdminAllActiveOrganizations()
    {
        DriveActor admin = new DriveActor(1L, null, null, "admin", Set.of(), true);

        assertThat(service.manageableDeptIds(admin)).containsExactly(1L, 8L, 9L, 18L);
    }

    private static DriveOrganization org(Long id, String ancestors,
            String status, String delFlag)
    {
        DriveOrganization value = new DriveOrganization();
        value.setDeptId(id);
        value.setAncestors(ancestors);
        value.setStatus(status);
        value.setDelFlag(delFlag);
        return value;
    }

    private static DriveRoleScope scope(String dataScope, Long deptId)
    {
        DriveRoleScope value = new DriveRoleScope();
        value.setRoleId(1L);
        value.setDataScope(dataScope);
        value.setDeptId(deptId);
        return value;
    }

    private static DriveActor manager(Long deptId)
    {
        return new DriveActor(30L, deptId, "部门", "manager", Set.of(
                DriveConstants.PERMISSION_ACCESS,
                DriveConstants.PERMISSION_DEPARTMENT_MANAGE), false);
    }
}
