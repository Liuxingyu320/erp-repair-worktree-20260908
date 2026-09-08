package com.erp.file.drive.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.Map;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveCapacityConfig;
import com.erp.file.drive.domain.dto.DriveCapacityUpdateRequest;
import com.erp.file.drive.domain.dto.DriveQuotaImpactRequest;
import com.erp.file.drive.service.DriveActorResolver;
import com.erp.file.drive.service.DriveCapacityService;
import com.erp.file.drive.service.DriveEffectiveQuotaService;
import com.erp.file.drive.service.DriveFeatureGuard;
import com.erp.file.drive.service.DriveOperationLogService;
import com.erp.file.drive.service.DriveOrganizationConfigService;
import com.erp.file.drive.service.DriveOrganizationReconcileService;
import com.erp.file.drive.service.DriveOrganizationScopeService;
import com.erp.file.drive.service.DriveOrganizationTypeRuleService;
import com.erp.file.drive.service.DrivePersonalQuotaPolicyService;
import com.erp.file.drive.service.DriveQuotaImpactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.bind.annotation.RequestMapping;

@DisplayName("云盘额度管理接口")
class DriveAdminControllerTest
{
    private DriveProperties properties;
    private DriveActorResolver actorResolver;
    private DriveCapacityService capacityService;
    private DriveQuotaImpactService impactService;
    private DriveOperationLogService operationLogService;
    private DriveOrganizationReconcileService reconcileService;
    private DriveAdminController controller;

    @BeforeEach
    void setUp()
    {
        properties = mock(DriveProperties.class);
        actorResolver = mock(DriveActorResolver.class);
        capacityService = mock(DriveCapacityService.class);
        impactService = mock(DriveQuotaImpactService.class);
        operationLogService = mock(DriveOperationLogService.class);
        reconcileService = mock(DriveOrganizationReconcileService.class);
        controller = new DriveAdminController(new DriveFeatureGuard(properties), actorResolver,
                capacityService, mock(DrivePersonalQuotaPolicyService.class),
                mock(DriveEffectiveQuotaService.class),
                mock(DriveOrganizationConfigService.class),
                mock(DriveOrganizationTypeRuleService.class),
                mock(DriveOrganizationScopeService.class),
                reconcileService, impactService,
                operationLogService);
    }

    @Test
    @DisplayName("管理入口使用稳定前缀且同时要求访问与额度权限")
    void shouldDeclareStablePrefixAndPermissions() throws Exception
    {
        assertThat(DriveAdminController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/drive/admin");
        Method impact = DriveAdminController.class.getMethod(
                "impact", DriveQuotaImpactRequest.class);
        assertThat(impact.getAnnotation(RequiresPermissions.class).value()).containsExactly(
                DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE);
        Method capacity = DriveAdminController.class.getMethod("capacity");
        assertThat(capacity.getAnnotation(RequiresPermissions.class).value()).containsExactly(
                DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE);
    }

    @Test
    @DisplayName("云盘关闭时在解析用户和读取容量前保守失败")
    void shouldFailClosedBeforeAdminReads()
    {
        when(properties.isEnabled()).thenReturn(false);

        assertThatThrownBy(controller::capacity)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_DISABLED);
        verifyNoInteractions(actorResolver, capacityService);
    }

    @Test
    @DisplayName("容量保存必须先校验影响快照再执行版本化修改")
    void shouldVerifyImpactBeforeCapacityMutation()
    {
        DriveActor actor = admin();
        DriveCapacityUpdateRequest request = capacityRequest();
        when(properties.isEnabled()).thenReturn(true);
        when(actorResolver.resolve()).thenReturn(actor);
        when(capacityService.update(1_000L, 20, 200L, 300L, 300L,
                DriveConstants.CAPACITY_WARN, 2, "确认生产容量", actor))
                .thenReturn(new com.erp.file.drive.domain.vo.DriveCapacityOverviewVo(
                        1_000L, 20, 800L, 200L, 300L, 300L,
                        100L, 200L, 200L, 100L,
                        DriveConstants.CAPACITY_WARN, 3, java.util.List.of()));

        controller.updateCapacity(request);

        InOrder order = inOrder(impactService, capacityService);
        order.verify(impactService).verify(
                org.mockito.ArgumentMatchers.any(DriveQuotaImpactRequest.class),
                org.mockito.ArgumentMatchers.eq("hash"),
                org.mockito.ArgumentMatchers.eq(actor));
        order.verify(capacityService).update(1_000L, 20, 200L, 300L, 300L,
                DriveConstants.CAPACITY_WARN, 2, "确认生产容量", actor);
    }

    @Test
    @DisplayName("组织同步开关关闭时立即对账明确失败而不返回虚假成功")
    void shouldRejectManualReconcileWhileOrganizationSyncIsDisabled()
    {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isOrganizationSyncEnabled()).thenReturn(false);
        when(actorResolver.resolve()).thenReturn(admin());

        assertThatThrownBy(controller::reconcile)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_DISABLED);
        verifyNoInteractions(reconcileService);
    }

    @Test
    @DisplayName("管理状态明确返回上传预占与过期清理参数")
    void shouldExposeReservationRuntimeStatus()
    {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isCapacityReservationEnabled()).thenReturn(true);
        when(properties.getUploadReservationTimeoutMinutes()).thenReturn(120);
        when(properties.getUploadReservationCleanupCron()).thenReturn("0 */5 * * * *");
        when(properties.getUploadReservationCleanupBatchSize()).thenReturn(100);
        when(actorResolver.resolve()).thenReturn(admin());

        Object data = controller.status().get("data");

        assertThat(data).isInstanceOf(Map.class);
        Map<?, ?> status = (Map<?, ?>) data;
        assertThat(status.get("capacityReservationEnabled")).isEqualTo(true);
        assertThat(status.get("uploadReservationTimeoutMinutes")).isEqualTo(120);
        assertThat(status.get("uploadReservationCleanupCron"))
                .isEqualTo("0 */5 * * * *");
        assertThat(status.get("uploadReservationCleanupBatchSize")).isEqualTo(100);
        assertThat(status.get("uploadReservationCleanupEnabled")).isEqualTo(true);
    }

    private static DriveCapacityUpdateRequest capacityRequest()
    {
        DriveCapacityUpdateRequest value = new DriveCapacityUpdateRequest();
        value.setPhysicalCapacityBytes(1_000L);
        value.setReservePercent(20);
        value.setPublicPoolBytes(200L);
        value.setPersonalPoolBytes(300L);
        value.setOrganizationPoolBytes(300L);
        value.setEnforcementMode(DriveConstants.CAPACITY_WARN);
        value.setVersion(2);
        value.setReason("确认生产容量");
        value.setImpactHash("hash");
        return value;
    }

    private static DriveActor admin()
    {
        return new DriveActor(1L, null, null, "admin", Set.of(), true);
    }
}
