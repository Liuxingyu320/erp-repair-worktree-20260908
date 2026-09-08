package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.vo.DriveOrganizationReconcileVo;
import com.erp.file.drive.domain.vo.DriveOrganizationBudgetViolationVo;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘组织盘批量对账")
class DriveOrganizationReconcileServiceTest
{
    @Test
    @DisplayName("单个组织失败不阻断后续组织且会返回失败摘要")
    void shouldIsolateOneOrganizationFailure()
    {
        DriveOrganizationMapper mapper = mock(DriveOrganizationMapper.class);
        DriveOrganizationProvisioningService provisioning =
                mock(DriveOrganizationProvisioningService.class);
        DriveOrganization one = organization(1L);
        DriveOrganization two = organization(2L);
        DriveOrganizationSpaceConfig orphan = new DriveOrganizationSpaceConfig();
        orphan.setDeptId(3L);
        when(mapper.selectAllOrganizations()).thenReturn(List.of(one, two));
        when(mapper.selectConfigs()).thenReturn(List.of(orphan));
        doThrow(new IllegalStateException("broken organization"))
                .when(provisioning).syncOrganization(2L, "system");
        DriveOrganizationReconcileService service = new DriveOrganizationReconcileService(
                mapper, provisioning, new DriveAuthorizationService(),
                mock(DriveOrganizationScopeService.class),
                mock(DriveOrganizationBudgetService.class));

        DriveOrganizationReconcileVo result = service.reconcileAll("system");

        assertThat(result.scanned()).isEqualTo(3);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failedDeptIds()).containsExactly(2L);
        verify(provisioning).syncOrganization(3L, "system");
    }

    @Test
    @DisplayName("手动对账只处理管理员数据范围内的组织")
    void shouldReconcileOnlyManageableOrganizations()
    {
        DriveOrganizationMapper mapper = mock(DriveOrganizationMapper.class);
        DriveOrganizationProvisioningService provisioning =
                mock(DriveOrganizationProvisioningService.class);
        DriveOrganizationScopeService scope = mock(DriveOrganizationScopeService.class);
        when(mapper.selectAllOrganizations()).thenReturn(
                List.of(organization(1L), organization(2L)));
        when(mapper.selectConfigs()).thenReturn(List.of());
        DriveActor actor = new DriveActor(10L, 1L, "一部", "manager",
                Set.of(DriveConstants.PERMISSION_ACCESS,
                        DriveConstants.PERMISSION_QUOTA_MANAGE), false);
        when(scope.manageableDeptIds(actor)).thenReturn(Set.of(1L));
        DriveOrganizationReconcileService service = new DriveOrganizationReconcileService(
                mapper, provisioning, new DriveAuthorizationService(), scope,
                mock(DriveOrganizationBudgetService.class));

        DriveOrganizationReconcileVo result = service.reconcile(actor);

        assertThat(result.scanned()).isEqualTo(1);
        verify(provisioning).syncOrganization(1L, "manager");
        verify(provisioning, never()).syncOrganization(2L, "manager");
    }

    @Test
    @DisplayName("对账将技术失败与组织预算阻断分开返回")
    void shouldReportBudgetDriftSeparatelyFromSyncFailures()
    {
        DriveOrganizationMapper mapper = mock(DriveOrganizationMapper.class);
        DriveOrganizationProvisioningService provisioning =
                mock(DriveOrganizationProvisioningService.class);
        DriveOrganizationBudgetService budget = mock(DriveOrganizationBudgetService.class);
        when(mapper.selectAllOrganizations()).thenReturn(
                List.of(organization(1L), organization(2L)));
        when(mapper.selectConfigs()).thenReturn(List.of());
        DriveOrganizationBudgetViolationVo violation =
                new DriveOrganizationBudgetViolationVo(1L, "集团", 100L,
                        130L, 30L, List.of(1L, 2L));
        when(budget.detectViolations()).thenReturn(List.of(violation));
        DriveOrganizationReconcileService service = new DriveOrganizationReconcileService(
                mapper, provisioning, new DriveAuthorizationService(),
                mock(DriveOrganizationScopeService.class), budget);

        DriveOrganizationReconcileVo result = service.reconcileAll("system");

        assertThat(result.failed()).isZero();
        assertThat(result.budgetViolationCount()).isEqualTo(1);
        assertThat(result.budgetBlockedCount()).isEqualTo(2);
        assertThat(result.budgetBlockedDeptIds()).containsExactly(1L, 2L);
        assertThat(result.budgetViolations()).containsExactly(violation);
    }

    private static DriveOrganization organization(Long deptId)
    {
        DriveOrganization value = new DriveOrganization();
        value.setDeptId(deptId);
        return value;
    }
}
