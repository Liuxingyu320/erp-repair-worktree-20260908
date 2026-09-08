package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.DriveOrganizationTypeRule;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("云盘动态组织盘建盘")
class DriveOrganizationProvisioningServiceTest
{
    private DriveOrganizationMapper organizationMapper;
    private DriveSpaceMapper spaceMapper;
    private DriveProperties properties;
    private DriveOrganizationBudgetService budgetService;
    private DriveCapacityService capacityService;
    private DriveOrganizationProvisioningService service;

    @BeforeEach
    void setUp()
    {
        organizationMapper = mock(DriveOrganizationMapper.class);
        spaceMapper = mock(DriveSpaceMapper.class);
        properties = new DriveProperties();
        properties.setOrganizationSyncEnabled(true);
        budgetService = mock(DriveOrganizationBudgetService.class);
        capacityService = mock(DriveCapacityService.class);
        service = new DriveOrganizationProvisioningService(
                organizationMapper, spaceMapper, properties, budgetService, capacityService);
    }

    @Test
    @DisplayName("已启用组织配置首次访问幂等创建组织盘")
    void shouldProvisionEnabledOrganizationSpaceIdempotently()
    {
        DriveOrganization org = organization(8L, "财务部", "COMPANY", "0", "0", 2L);
        DriveOrganizationSpaceConfig config = config(8L, true, 5_000L,
                DriveConstants.STATUS_ACTIVE, DriveConstants.CONFIG_SOURCE_MANUAL);
        DriveSpace stored = space(8L, "财务部组织盘", 5_000L,
                DriveConstants.STATUS_ACTIVE, DriveConstants.QUOTA_SOURCE_ORG_OVERRIDE, 0);
        when(spaceMapper.selectByKey("DEPARTMENT:8")).thenReturn(null, stored);
        when(organizationMapper.selectOrganization(8L)).thenReturn(org);
        when(organizationMapper.selectConfig(8L)).thenReturn(config);

        DriveSpace result = service.syncOrganization(8L, "manager");

        assertThat(result).isSameAs(stored);
        ArgumentCaptor<DriveSpace> candidate = ArgumentCaptor.forClass(DriveSpace.class);
        verify(spaceMapper).insertIgnore(candidate.capture());
        assertThat(candidate.getValue().getSpaceName()).isEqualTo("财务部组织盘");
        assertThat(candidate.getValue().getQuotaSourceType())
                .isEqualTo(DriveConstants.QUOTA_SOURCE_ORG_OVERRIDE);
        verify(spaceMapper, never()).updateEffectiveQuota(
                any(), anyLong(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("组织改名同步名称，组织停用只转只读不删空间")
    void shouldRenameAndMakeDisabledOrganizationReadOnly()
    {
        DriveSpace existing = space(8L, "旧名称组织盘", 5_000L,
                DriveConstants.STATUS_ACTIVE, DriveConstants.QUOTA_SOURCE_ORG_OVERRIDE, 2);
        DriveSpace updated = space(8L, "新名称组织盘", 5_000L,
                DriveConstants.STATUS_READ_ONLY, DriveConstants.QUOTA_SOURCE_ORG_OVERRIDE, 3);
        when(spaceMapper.selectByKey("DEPARTMENT:8")).thenReturn(existing, updated);
        when(organizationMapper.selectOrganization(8L)).thenReturn(
                organization(8L, "新名称", "COMPANY", "1", "0", 2L));

        DriveSpace result = service.syncOrganization(8L, "system");

        assertThat(result.getStatus()).isEqualTo(DriveConstants.STATUS_READ_ONLY);
        verify(spaceMapper).updateOrganizationMetadata(8L, "新名称组织盘",
                DriveConstants.STATUS_READ_ONLY, "system");
        verify(spaceMapper, never()).insertIgnore(any());
    }

    @Test
    @DisplayName("组织类型自动规则只在有有效直属成员时建立默认配置")
    void shouldApplyAutomaticTypeRuleOnlyWithRequiredMember()
    {
        DriveOrganization org = organization(9L, "南店", "STORE", "0", "0", 1L);
        DriveOrganizationTypeRule rule = new DriveOrganizationTypeRule();
        rule.setDeptType("STORE");
        rule.setAutoEnable(true);
        rule.setRequireActiveMember(true);
        rule.setDefaultQuotaBytes(10_000L);
        rule.setStatus(DriveConstants.STATUS_ACTIVE);
        DriveOrganizationSpaceConfig generated = config(9L, true, 10_000L,
                DriveConstants.STATUS_ACTIVE, DriveConstants.CONFIG_SOURCE_TYPE_DEFAULT);
        DriveSpace stored = space(9L, "南店组织盘", 10_000L,
                DriveConstants.STATUS_ACTIVE, DriveConstants.QUOTA_SOURCE_ORG_TYPE, 0);
        when(spaceMapper.selectByKey("DEPARTMENT:9")).thenReturn(null, stored);
        when(organizationMapper.selectOrganization(9L)).thenReturn(org);
        when(organizationMapper.selectConfig(9L)).thenReturn(null, null, generated);
        when(organizationMapper.selectTypeRule("STORE")).thenReturn(rule);

        service.syncOrganization(9L, "system");

        verify(organizationMapper).insertConfig(any());
        verify(budgetService).validateTreeBudgets(any());
        verify(capacityService).requireCurrentAllocationsWithinPools();
        verify(spaceMapper).insertIgnore(any());
    }

    private static DriveOrganization organization(Long id, String name, String type,
            String status, String delFlag, Long members)
    {
        DriveOrganization value = new DriveOrganization();
        value.setDeptId(id);
        value.setDeptName(name);
        value.setDeptType(type);
        value.setStatus(status);
        value.setDelFlag(delFlag);
        value.setActiveMemberCount(members);
        return value;
    }

    private static DriveOrganizationSpaceConfig config(Long deptId, boolean enabled,
            long quota, String lifecycle, String source)
    {
        DriveOrganizationSpaceConfig value = new DriveOrganizationSpaceConfig();
        value.setDeptId(deptId);
        value.setEnabled(enabled);
        value.setQuotaBytes(quota);
        value.setLifecycleStatus(lifecycle);
        value.setMemberWriteMode(DriveConstants.ORG_WRITE_PERMISSION_ONLY);
        value.setConfigSource(source);
        value.setVersion(0);
        return value;
    }

    private static DriveSpace space(Long deptId, String name, long quota,
            String status, String source, int version)
    {
        DriveSpace value = new DriveSpace();
        value.setSpaceId(100L + deptId);
        value.setSpaceKey("DEPARTMENT:" + deptId);
        value.setSpaceType(DriveConstants.SPACE_DEPARTMENT);
        value.setDeptId(deptId);
        value.setSpaceName(name);
        value.setQuotaBytes(quota);
        value.setUsedBytes(0L);
        value.setQuotaSourceType(source);
        value.setQuotaSourceId(deptId);
        value.setStatus(status);
        value.setVersion(version);
        return value;
    }
}
