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
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveCapacityConfig;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.vo.DriveCapacityOverviewVo;
import com.erp.file.drive.domain.vo.DriveUserQuotaVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveCapacityMapper;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import com.erp.file.drive.mapper.DriveUploadReservationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘全局容量池")
class DriveCapacityServiceTest
{
    private DriveCapacityMapper capacityMapper;
    private DriveOrganizationMapper organizationMapper;
    private DriveSpaceMapper spaceMapper;
    private DriveUploadReservationMapper reservationMapper;
    private DriveEffectiveQuotaService effectiveQuotaService;
    private DriveProperties properties;
    private DriveCapacityService service;

    @BeforeEach
    void setUp()
    {
        capacityMapper = mock(DriveCapacityMapper.class);
        organizationMapper = mock(DriveOrganizationMapper.class);
        spaceMapper = mock(DriveSpaceMapper.class);
        reservationMapper = mock(DriveUploadReservationMapper.class);
        effectiveQuotaService = mock(DriveEffectiveQuotaService.class);
        properties = new DriveProperties();
        properties.setCapacityReservationEnabled(true);
        service = new DriveCapacityService(capacityMapper, organizationMapper, spaceMapper,
                reservationMapper, effectiveQuotaService, new DriveAuthorizationService(),
                properties);
        when(spaceMapper.selectByType(DriveConstants.SPACE_COMPANY))
                .thenReturn(List.of(space(100L, 20L)));
        when(spaceMapper.selectByType(DriveConstants.SPACE_PERSONAL))
                .thenReturn(List.of(space(50L, 10L)));
        when(spaceMapper.selectByType(DriveConstants.SPACE_DEPARTMENT))
                .thenReturn(List.of(space(80L, 30L)));
        when(spaceMapper.selectTotalUsedBytes()).thenReturn(60L);
        when(effectiveQuotaService.listEffectiveUsers()).thenReturn(List.of(
                userQuota(1L, 60L, 10L), userQuota(2L, 70L, 0L)));
        DriveOrganizationSpaceConfig org = new DriveOrganizationSpaceConfig();
        org.setEnabled(true);
        org.setQuotaBytes(90L);
        org.setLifecycleStatus(DriveConstants.STATUS_ACTIVE);
        when(organizationMapper.selectConfigs()).thenReturn(List.of(org));
    }

    @Test
    @DisplayName("容量总览分开逻辑分配和物理实际使用")
    void shouldCalculateAllocatedPoolsAndActualUsage()
    {
        DriveCapacityConfig config = config(1_000L, 20,
                200L, 200L, 200L, DriveConstants.CAPACITY_WARN, 3);
        when(capacityMapper.selectConfig(1L)).thenReturn(config);

        DriveCapacityOverviewVo result = service.calculateOverview();

        assertThat(result.allocatableCapacityBytes()).isEqualTo(800L);
        assertThat(result.publicAllocatedBytes()).isEqualTo(100L);
        assertThat(result.personalAllocatedBytes()).isEqualTo(130L);
        assertThat(result.organizationAllocatedBytes()).isEqualTo(90L);
        assertThat(result.actualUsedBytes()).isEqualTo(60L);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("容量总览将未完成上传与已提交用量分开展示并安全合计")
    void shouldAccountPendingUploadReservations()
    {
        DriveCapacityConfig config = config(1_000L, 20,
                200L, 200L, 200L, DriveConstants.CAPACITY_WARN, 3);
        when(capacityMapper.selectConfig(1L)).thenReturn(config);
        when(reservationMapper.sumPendingBytes()).thenReturn(30L);
        when(reservationMapper.countStale(any(), any())).thenReturn(2);
        when(reservationMapper.countCleanupFailed()).thenReturn(1);

        DriveCapacityOverviewVo result = service.calculateOverview();

        assertThat(result.actualUsedBytes()).isEqualTo(60L);
        assertThat(result.pendingUploadBytes()).isEqualTo(30L);
        assertThat(result.capacityAccountedBytes()).isEqualTo(90L);
        assertThat(result.staleReservationCount()).isEqualTo(2);
        assertThat(result.cleanupFailedReservationCount()).isEqualTo(1);
        assertThat(result.warnings()).anyMatch(value -> value.contains("清理失败"));
    }

    @Test
    @DisplayName("实际使用量达到阈值时分级告警但不误判为池超分")
    void shouldWarnAtActualUsageThresholds()
    {
        when(spaceMapper.selectTotalUsedBytes()).thenReturn(700L);
        DriveCapacityConfig config = config(1_000L, 20,
                200L, 200L, 200L, DriveConstants.CAPACITY_WARN, 3);
        when(capacityMapper.selectConfig(1L)).thenReturn(config);

        DriveCapacityOverviewVo result = service.calculateOverview();

        assertThat(result.actualUsedBytes()).isEqualTo(700L);
        assertThat(result.warnings()).contains("实际容量使用率已达到 85%，请安排扩容或清理");
    }

    @Test
    @DisplayName("强制模式拒绝使个人已分配额度超出个人池的配置")
    void shouldBlockPoolSmallerThanCurrentAllocation()
    {
        DriveCapacityConfig current = config(1_000L, 0,
                200L, 200L, 200L, DriveConstants.CAPACITY_WARN, 4);
        when(capacityMapper.selectConfigForUpdate(1L)).thenReturn(current);

        assertThatThrownBy(() -> service.update(1_000L, 0,
                200L, 100L, 200L, DriveConstants.CAPACITY_BLOCK,
                4, "确认真实容量", manager()))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_CAPACITY_EXCEEDED);
        verify(capacityMapper, never()).updateConfig(any());
    }

    @Test
    @DisplayName("强制模式不允许未确认物理容量")
    void shouldRequirePhysicalCapacityForBlockMode()
    {
        DriveCapacityConfig current = config(null, 20,
                200L, 200L, 200L, DriveConstants.CAPACITY_WARN, 4);
        when(capacityMapper.selectConfigForUpdate(1L)).thenReturn(current);

        assertThatThrownBy(() -> service.update(null, 20,
                100L, 100L, 100L, DriveConstants.CAPACITY_BLOCK,
                4, "开启强制模式", manager()))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_POLICY_INVALID);
    }

    @Test
    @DisplayName("强制模式拒绝实际用量超过扣除保留后的可分配容量")
    void shouldBlockWhenActualUsageExceedsAllocatableCapacity()
    {
        when(spaceMapper.selectTotalUsedBytes()).thenReturn(850L);
        DriveCapacityConfig current = config(1_000L, 20,
                200L, 200L, 200L, DriveConstants.CAPACITY_WARN, 4);
        when(capacityMapper.selectConfigForUpdate(1L)).thenReturn(current);

        assertThatThrownBy(() -> service.update(1_000L, 20,
                200L, 200L, 200L, DriveConstants.CAPACITY_BLOCK,
                4, "确认真实容量", manager()))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_CAPACITY_EXCEEDED);
        verify(capacityMapper, never()).updateConfig(any());
    }

    @Test
    @DisplayName("强制模式在写对象前原子拒绝超出最后容量的预占")
    void shouldRejectUploadReservationBeyondRemainingCapacity()
    {
        DriveCapacityConfig current = config(1_000L, 20,
                200L, 200L, 200L, DriveConstants.CAPACITY_BLOCK, 4);
        when(capacityMapper.selectConfigForUpdate(1L)).thenReturn(current);
        when(spaceMapper.selectTotalUsedBytes()).thenReturn(750L);
        when(reservationMapper.sumPendingBytes()).thenReturn(40L);

        assertThatThrownBy(() -> service.requireUploadCapacityAndLock(11L))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_CAPACITY_EXCEEDED);
    }

    @Test
    @DisplayName("容量预占开关未开启时预览也不允许选择强制模式")
    void shouldRejectBlockPreviewWhileReservationFeatureIsDisabled()
    {
        properties.setCapacityReservationEnabled(false);

        assertThatThrownBy(() -> service.preview(1_000L, 20,
                200L, 200L, 200L, DriveConstants.CAPACITY_BLOCK, manager()))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_POLICY_INVALID);
    }

    @Test
    @DisplayName("升级期间强制模式拒绝绕过未开启的上传预占")
    void shouldRejectLegacyUploadWhenBlockModeAlreadyExists()
    {
        properties.setCapacityReservationEnabled(false);
        when(capacityMapper.selectConfig(1L)).thenReturn(config(1_000L, 20,
                200L, 200L, 200L, DriveConstants.CAPACITY_BLOCK, 4));

        assertThatThrownBy(service::requireLegacyUploadAllowedWithoutReservation)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_POLICY_INVALID);
    }

    private static DriveCapacityConfig config(Long physical, int reserve,
            long publicPool, long personalPool, long orgPool, String mode, int version)
    {
        DriveCapacityConfig value = new DriveCapacityConfig();
        value.setConfigId(1L);
        value.setPhysicalCapacityBytes(physical);
        value.setReservePercent(reserve);
        value.setPublicPoolBytes(publicPool);
        value.setPersonalPoolBytes(personalPool);
        value.setOrganizationPoolBytes(orgPool);
        value.setEnforcementMode(mode);
        value.setVersion(version);
        return value;
    }

    private static DriveSpace space(long quota, long used)
    {
        DriveSpace value = new DriveSpace();
        value.setQuotaBytes(quota);
        value.setUsedBytes(used);
        return value;
    }

    private static DriveUserQuotaVo userQuota(Long userId, long quota, long used)
    {
        return new DriveUserQuotaVo(userId, "u" + userId, "用户" + userId,
                8L, "财务部", List.of(), List.of(), quota, used,
                DriveConstants.QUOTA_SOURCE_GLOBAL, null, "全员默认", false, 0L);
    }

    private static DriveActor manager()
    {
        return new DriveActor(1L, 8L, "财务部", "manager", Set.of(
                DriveConstants.PERMISSION_ACCESS,
                DriveConstants.PERMISSION_QUOTA_MANAGE), false);
    }
}
