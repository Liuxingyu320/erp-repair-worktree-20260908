package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.dto.DriveOrganizationBatchTarget;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘组织额度与组织树预算")
class DriveOrganizationConfigServiceTest
{
    private DriveOrganizationMapper organizationMapper;
    private DriveOrganizationProvisioningService provisioningService;
    private DriveCapacityService capacityService;
    private DriveOrganizationConfigService service;

    @BeforeEach
    void setUp()
    {
        organizationMapper = mock(DriveOrganizationMapper.class);
        provisioningService = mock(DriveOrganizationProvisioningService.class);
        capacityService = mock(DriveCapacityService.class);
        service = new DriveOrganizationConfigService(organizationMapper,
                mock(DriveSpaceMapper.class), mock(DriveOrganizationScopeService.class),
                provisioningService, new DriveOrganizationBudgetService(organizationMapper),
                capacityService, new DriveAuthorizationService());
    }

    @Test
    @DisplayName("新建组织配置同一事务校验容量并立即建盘")
    void shouldSaveConfigValidateCapacityAndProvisionSpace()
    {
        DriveOrganization org = organization(8L, "财务部", "0,1");
        DriveOrganizationSpaceConfig saved = config(8L, 5_000L, null, true, 0);
        when(organizationMapper.selectOrganization(8L)).thenReturn(org);
        when(organizationMapper.selectConfigForUpdate(8L)).thenReturn(null);
        when(organizationMapper.selectAllOrganizations()).thenReturn(List.of(
                organization(1L, "集团", "0"), org));
        when(organizationMapper.selectConfigs()).thenReturn(List.of(), List.of(saved));
        when(organizationMapper.insertConfig(any())).thenReturn(1);
        when(organizationMapper.selectConfig(8L)).thenReturn(saved);

        DriveOrganizationSpaceConfig result = service.save(8L, true, 5_000L,
                null, DriveConstants.ORG_WRITE_ALL_DIRECT_MEMBERS,
                DriveConstants.STATUS_ACTIVE, 0, "门店协作资料", admin());

        assertThat(result).isSameAs(saved);
        verify(capacityService).requireCurrentAllocationsWithinPools();
        verify(provisioningService).syncOrganization(8L, "admin");
    }

    @Test
    @DisplayName("下级调高额度超出上级组织树预算时返回稳定错误")
    void shouldRejectQuotaExceedingAncestorTreeBudget()
    {
        DriveOrganization company = organization(1L, "华东公司", "0");
        DriveOrganization store = organization(2L, "南店", "0,1");
        DriveOrganizationSpaceConfig companyConfig = config(1L, 40L, 100L, true, 0);
        DriveOrganizationSpaceConfig storeConfig = config(2L, 50L, null, true, 3);
        when(organizationMapper.selectOrganization(2L)).thenReturn(store);
        when(organizationMapper.selectConfigForUpdate(2L)).thenReturn(storeConfig);
        when(organizationMapper.selectAllOrganizations()).thenReturn(List.of(company, store));
        when(organizationMapper.selectConfigs()).thenReturn(List.of(companyConfig, storeConfig));

        assertThatThrownBy(() -> service.save(2L, true, 70L, null,
                DriveConstants.ORG_WRITE_PERMISSION_ONLY,
                DriveConstants.STATUS_ACTIVE, 3, "增加门店空间", admin()))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_ORG_BUDGET_EXCEEDED);
        verify(organizationMapper, never()).updateConfig(any());
        verify(capacityService, never()).requireCurrentAllocationsWithinPools();
    }

    @Test
    @DisplayName("组织配置使用独立乐观锁版本防止并发覆盖")
    void shouldRejectStaleConfigVersion()
    {
        DriveOrganization org = organization(8L, "财务部", "0,1");
        DriveOrganizationSpaceConfig current = config(8L, 5_000L, null, true, 4);
        when(organizationMapper.selectOrganization(8L)).thenReturn(org);
        when(organizationMapper.selectConfigForUpdate(8L)).thenReturn(current);

        assertThatThrownBy(() -> service.save(8L, true, 6_000L, null,
                DriveConstants.ORG_WRITE_PERMISSION_ONLY,
                DriveConstants.STATUS_ACTIVE, 3, "并发调整", admin()))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION);
    }

    @Test
    @DisplayName("批量配置先校验整体预算和容量再一次性写入")
    void shouldSaveSelectedOrganizationsAsOneValidatedBatch()
    {
        DriveOrganization first = organization(8L, "财务部", "0,1");
        DriveOrganization second = organization(9L, "人事部", "0,1");
        DriveOrganizationSpaceConfig firstSaved = config(8L, 100L, null, true, 1);
        DriveOrganizationSpaceConfig secondSaved = config(9L, 100L, null, true, 1);
        when(organizationMapper.selectAllOrganizations()).thenReturn(List.of(
                organization(1L, "集团", "0"), first, second));
        when(organizationMapper.selectConfigs()).thenReturn(List.of(),
                List.of(firstSaved, secondSaved));
        when(organizationMapper.selectConfigForUpdate(8L)).thenReturn(null);
        when(organizationMapper.selectConfigForUpdate(9L)).thenReturn(null);
        when(organizationMapper.insertConfig(any())).thenReturn(1);
        when(organizationMapper.selectConfig(8L)).thenReturn(firstSaved);
        when(organizationMapper.selectConfig(9L)).thenReturn(secondSaved);

        List<DriveOrganizationSpaceConfig> result = service.saveBatch(List.of(
                target(8L, 0), target(9L, 0)), true, 100L,
                DriveConstants.ORG_WRITE_PERMISSION_ONLY,
                DriveConstants.STATUS_ACTIVE, "统一部门额度", admin());

        assertThat(result).containsExactly(firstSaved, secondSaved);
        verify(capacityService).requireOrganizationAllocationWithinPool(200L);
        verify(organizationMapper, org.mockito.Mockito.times(2)).insertConfig(any());
        verify(provisioningService).syncOrganization(8L, "admin");
        verify(provisioningService).syncOrganization(9L, "admin");
    }

    private static DriveOrganization organization(Long deptId, String name, String ancestors)
    {
        DriveOrganization value = new DriveOrganization();
        value.setDeptId(deptId);
        value.setDeptName(name);
        value.setAncestors(ancestors);
        value.setDeptType("COMPANY");
        value.setStatus("0");
        value.setDelFlag("0");
        value.setActiveMemberCount(1L);
        return value;
    }

    private static DriveOrganizationSpaceConfig config(Long deptId, long quota,
            Long treeBudget, boolean enabled, int version)
    {
        DriveOrganizationSpaceConfig value = new DriveOrganizationSpaceConfig();
        value.setDeptId(deptId);
        value.setQuotaBytes(quota);
        value.setTreeBudgetBytes(treeBudget);
        value.setEnabled(enabled);
        value.setMemberWriteMode(DriveConstants.ORG_WRITE_PERMISSION_ONLY);
        value.setLifecycleStatus(DriveConstants.STATUS_ACTIVE);
        value.setConfigSource(DriveConstants.CONFIG_SOURCE_MANUAL);
        value.setVersion(version);
        return value;
    }

    private static DriveActor admin()
    {
        return new DriveActor(1L, null, null, "admin", Set.of(), true);
    }

    private static DriveOrganizationBatchTarget target(Long deptId, int version)
    {
        DriveOrganizationBatchTarget value = new DriveOrganizationBatchTarget();
        value.setDeptId(deptId);
        value.setVersion(version);
        return value;
    }
}
