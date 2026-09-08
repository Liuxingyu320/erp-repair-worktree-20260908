package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.vo.DriveSpaceVo;
import com.erp.file.drive.domain.vo.DriveOrganizationBudgetViolationVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("云盘可见空间与额度服务")
class DriveSpaceServiceTest
{
    private DriveSpaceMapper mapper;
    private DriveQuotaService quotaService;
    private DriveProperties properties;
    private DriveOrganizationBudgetService budgetService;
    private DriveOrganizationProvisioningService provisioningService;
    private DriveOrganizationScopeService scopeService;
    private DriveSpaceService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(DriveSpaceMapper.class);
        properties = new DriveProperties();
        budgetService = mock(DriveOrganizationBudgetService.class);
        provisioningService = mock(DriveOrganizationProvisioningService.class);
        scopeService = mock(DriveOrganizationScopeService.class);
        DriveAuthorizationService authorization = new DriveAuthorizationService();
        quotaService = new DriveQuotaService(mapper);
        service = new DriveSpaceService(mapper, properties, authorization, quotaService,
                mock(DriveEffectiveQuotaService.class),
                provisioningService, scopeService,
                mock(DrivePersonalQuotaPolicyService.class),
                mock(DriveOrganizationConfigService.class),
                mock(DriveCapacityService.class), budgetService);
    }

    @Test
    @DisplayName("首次访问幂等创建个人和部门空间并按个人、公司、部门排序")
    void shouldEnsureVisibleSpacesInStableOrder()
    {
        DriveActor actor = actor(20L, 8L, "财务部", Set.of(DriveConstants.PERMISSION_ACCESS));
        DriveSpace personal = space(1L, DriveConstants.SPACE_PERSONAL, "我的文件", 20L, null, 0L);
        DriveSpace company = space(2L, DriveConstants.SPACE_COMPANY, "公司公共盘", null, null, 0L);
        DriveSpace department = space(3L, DriveConstants.SPACE_DEPARTMENT, "旧部门名", null, 8L, 0L);
        when(mapper.selectByKey("PERSONAL:20")).thenReturn(null, personal);
        when(mapper.selectByKey(DriveConstants.COMPANY_SPACE_KEY)).thenReturn(company);
        when(mapper.selectByKey("DEPARTMENT:8")).thenReturn(null, department);

        List<DriveSpaceVo> spaces = service.listVisibleSpaces(actor);

        assertThat(spaces).extracting(DriveSpaceVo::spaceType).containsExactly(
                DriveConstants.SPACE_PERSONAL,
                DriveConstants.SPACE_COMPANY,
                DriveConstants.SPACE_DEPARTMENT);
        assertThat(spaces).extracting(DriveSpaceVo::spaceName).containsExactly(
                "我的文件", "公司公共盘", "财务部部门盘");
        verify(mapper, times(2)).selectByKey("PERSONAL:20");
        verify(mapper, times(2)).selectByKey("DEPARTMENT:8");

        ArgumentCaptor<DriveSpace> inserted = ArgumentCaptor.forClass(DriveSpace.class);
        verify(mapper, times(2)).insertIgnore(inserted.capture());
        assertThat(inserted.getAllValues()).extracting(DriveSpace::getSpaceKey)
                .containsExactly("PERSONAL:20", "DEPARTMENT:8");
        assertThat(inserted.getAllValues().get(0).getSpaceName()).isEqualTo("我的文件");
        assertThat(inserted.getAllValues().get(1).getSpaceName()).isEqualTo("财务部部门盘");
    }

    @Test
    @DisplayName("无部门用户不查询也不创建部门空间")
    void shouldOmitDepartmentSpaceWithoutLiveDepartment()
    {
        DriveActor actor = actor(20L, null, null, Set.of(DriveConstants.PERMISSION_ACCESS));
        DriveSpace personal = space(1L, DriveConstants.SPACE_PERSONAL, "我的文件", 20L, null, 0L);
        DriveSpace company = space(2L, DriveConstants.SPACE_COMPANY, "公司公共盘", null, null, 0L);
        when(mapper.selectByKey("PERSONAL:20")).thenReturn(personal, personal);
        when(mapper.selectByKey(DriveConstants.COMPANY_SPACE_KEY)).thenReturn(company);

        assertThat(service.listVisibleSpaces(actor)).hasSize(2);

        verify(mapper, never()).selectByKey(argThat(key -> key != null && key.startsWith("DEPARTMENT:")));
    }

    @Test
    @DisplayName("公司空间未初始化时返回稳定未找到错误")
    void shouldRequireSeededCompanySpace()
    {
        DriveActor actor = actor(20L, null, null, Set.of(DriveConstants.PERMISSION_ACCESS));
        DriveSpace personal = space(1L, DriveConstants.SPACE_PERSONAL, "我的文件", 20L, null, 0L);
        when(mapper.selectByKey("PERSONAL:20")).thenReturn(personal, personal);
        when(mapper.selectByKey(DriveConstants.COMPANY_SPACE_KEY)).thenReturn(null);

        assertThatThrownBy(() -> service.listVisibleSpaces(actor))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_SPACE_NOT_FOUND);
    }

    @Test
    @DisplayName("并发额度预占失败时返回额度不足")
    void shouldRejectQuotaReservationWhenConditionalUpdateMisses()
    {
        when(mapper.reserveQuota(9L, 100L)).thenReturn(0);

        assertThatThrownBy(() -> quotaService.reserve(9L, 100L))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_QUOTA_EXCEEDED);
    }

    @Test
    @DisplayName("清理额度不足以精确扣减时拒绝完成而不是归零")
    void shouldRejectQuotaReleaseUnderflow()
    {
        when(mapper.releaseQuota(9L, 100L)).thenReturn(0);
        when(mapper.selectById(9L)).thenReturn(
                space(9L, DriveConstants.SPACE_COMPANY,
                        "公司公共盘", null, null, 50L));

        assertThatThrownBy(() -> quotaService.release(9L, 100L))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("调整额度必须拥有专门权限且不能低于当前用量")
    void shouldRequireQuotaPermissionAndCurrentUsageFloor()
    {
        DriveActor ordinary = actor(20L, 8L, "财务部", Set.of(DriveConstants.PERMISSION_ACCESS));
        assertThatThrownBy(() -> service.updateQuota(9L, 100L, 0, ordinary))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_ACCESS_DENIED);
        verify(mapper, never()).updateQuota(any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(), anyString());

        DriveActor manager = quotaManager();
        when(mapper.selectById(9L)).thenReturn(
                space(9L, DriveConstants.SPACE_COMPANY, "公司公共盘", null, null, 101L));
        assertThatThrownBy(() -> service.updateQuota(9L, 100L, 0, manager))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_QUOTA_EXCEEDED);
    }

    @Test
    @DisplayName("上传竞态使最新用量超出新额度时分类为额度不足")
    void shouldClassifyRacingUsageAsQuotaExceeded()
    {
        DriveSpace before = space(9L, DriveConstants.SPACE_COMPANY, "公司公共盘", null, null, 50L);
        DriveSpace raced = space(9L, DriveConstants.SPACE_COMPANY, "公司公共盘", null, null, 120L);
        when(mapper.selectById(9L)).thenReturn(before, raced);
        when(mapper.updateQuota(9L, 100L, 3, "manager")).thenReturn(0);

        assertThatThrownBy(() -> service.updateQuota(9L, 100L, 3, quotaManager()))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_QUOTA_EXCEEDED);
        verify(mapper, times(2)).selectById(9L);
    }

    @Test
    @DisplayName("条件更新失败但用量未超限时分类为并发修改")
    void shouldClassifyStaleVersionAsConcurrentModification()
    {
        DriveSpace current = space(9L, DriveConstants.SPACE_COMPANY, "公司公共盘", null, null, 50L);
        when(mapper.selectById(9L)).thenReturn(current, current);
        when(mapper.updateQuota(9L, 100L, 3, "manager")).thenReturn(0);

        assertThatThrownBy(() -> service.updateQuota(9L, 100L, 3, quotaManager()))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION);
    }

    @Test
    @DisplayName("组织预算漂移时列表明示只读原因但保留清理权限")
    void shouldExposeBudgetWriteBlockWithoutBlockingCleanup()
    {
        properties.setOrganizationSyncEnabled(true);
        DriveActor actor = actor(20L, 8L, "财务部", Set.of(
                DriveConstants.PERMISSION_ACCESS,
                DriveConstants.PERMISSION_DEPARTMENT_MANAGE));
        DriveSpace personal = space(1L, DriveConstants.SPACE_PERSONAL,
                "我的文件", 20L, null, 0L);
        DriveSpace company = space(2L, DriveConstants.SPACE_COMPANY,
                "公司公共盘", null, null, 0L);
        DriveSpace department = space(3L, DriveConstants.SPACE_DEPARTMENT,
                "财务部组织盘", null, 8L, 0L);
        DriveOrganizationBudgetViolationVo violation =
                new DriveOrganizationBudgetViolationVo(1L, "集团", 100L,
                        120L, 20L, List.of(8L));
        when(mapper.selectByKey("PERSONAL:20")).thenReturn(personal, personal);
        when(mapper.selectByKey(DriveConstants.COMPANY_SPACE_KEY)).thenReturn(company);
        when(scopeService.readableDeptIds(actor)).thenReturn(Set.of(8L));
        when(mapper.selectByDeptIds(List.of(8L))).thenReturn(List.of(department));
        when(budgetService.violationsByDept()).thenReturn(Map.of(8L, violation));

        DriveSpaceVo organization = service.listVisibleSpaces(actor).stream()
                .filter(value -> DriveConstants.SPACE_DEPARTMENT.equals(value.spaceType()))
                .findFirst().orElseThrow();

        assertThat(organization.canWrite()).isFalse();
        assertThat(organization.canCleanup()).isTrue();
        assertThat(organization.writeBlockedCode())
                .isEqualTo(DriveErrorCodes.DRIVE_ORG_BUDGET_EXCEEDED);
        assertThat(organization.writeBlockedMessage()).contains("集团", "20");
    }

    @Test
    @DisplayName("组织预算违规阻断新写入但不阻断删除清理")
    void shouldBlockOrganizationWritesButAllowCleanup()
    {
        properties.setOrganizationSyncEnabled(true);
        DriveActor actor = actor(20L, 8L, "财务部", Set.of(
                DriveConstants.PERMISSION_ACCESS,
                DriveConstants.PERMISSION_DEPARTMENT_MANAGE));
        DriveSpace department = space(3L, DriveConstants.SPACE_DEPARTMENT,
                "财务部组织盘", null, 8L, 0L);
        when(mapper.selectById(3L)).thenReturn(department);
        org.mockito.Mockito.doThrow(new DriveException(
                DriveErrorCodes.DRIVE_ORG_BUDGET_EXCEEDED, "budget"))
                .when(budgetService).requireWriteAllowed(8L);

        assertThatThrownBy(() -> service.requireWritableSpace(3L, actor))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_ORG_BUDGET_EXCEEDED);
        assertThat(service.requireCleanupSpace(3L, actor)).isSameAs(department);
    }

    private static DriveActor quotaManager()
    {
        return actor(40L, 8L, "财务部", Set.of(
                DriveConstants.PERMISSION_ACCESS,
                DriveConstants.PERMISSION_QUOTA_MANAGE));
    }

    private static DriveActor actor(
            Long userId, Long deptId, String deptName, Set<String> permissions)
    {
        return new DriveActor(userId, deptId, deptName, "manager", permissions, false);
    }

    private static DriveSpace space(Long id, String type, String name,
            Long ownerId, Long deptId, Long usedBytes)
    {
        DriveSpace space = new DriveSpace();
        space.setSpaceId(id);
        space.setSpaceKey(type + ":" + id);
        space.setSpaceType(type);
        space.setSpaceName(name);
        space.setOwnerUserId(ownerId);
        space.setDeptId(deptId);
        space.setQuotaBytes(1000L);
        space.setUsedBytes(usedBytes);
        space.setStatus(DriveConstants.STATUS_ACTIVE);
        space.setVersion(0);
        return space;
    }
}
