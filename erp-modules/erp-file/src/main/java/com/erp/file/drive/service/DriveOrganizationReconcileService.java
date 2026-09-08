package com.erp.file.drive.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.vo.DriveOrganizationReconcileVo;
import com.erp.file.drive.domain.vo.DriveOrganizationBudgetViolationVo;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 将单组织事务隔离后汇总同步结果，一个异常组织不会阻断整批。 */
@Service
public class DriveOrganizationReconcileService
{
    private static final Logger log = LoggerFactory.getLogger(DriveOrganizationReconcileService.class);

    private final DriveOrganizationMapper organizationMapper;
    private final DriveOrganizationProvisioningService provisioningService;
    private final DriveAuthorizationService authorization;
    private final DriveOrganizationScopeService scopeService;
    private final DriveOrganizationBudgetService budgetService;

    public DriveOrganizationReconcileService(DriveOrganizationMapper organizationMapper,
            DriveOrganizationProvisioningService provisioningService,
            DriveAuthorizationService authorization,
            DriveOrganizationScopeService scopeService,
            DriveOrganizationBudgetService budgetService)
    {
        this.organizationMapper = organizationMapper;
        this.provisioningService = provisioningService;
        this.authorization = authorization;
        this.scopeService = scopeService;
        this.budgetService = budgetService;
    }

    public DriveOrganizationReconcileVo reconcile(DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        Set<Long> allowed = actor.admin() ? null : scopeService.manageableDeptIds(actor);
        return reconcile(actor.username(), allowed);
    }

    public DriveOrganizationReconcileVo reconcileAll(String updateBy)
    {
        return reconcile(updateBy, null);
    }

    private DriveOrganizationReconcileVo reconcile(String updateBy, Set<Long> allowed)
    {
        Set<Long> deptIds = new LinkedHashSet<>();
        for (DriveOrganization organization : safeList(organizationMapper.selectAllOrganizations()))
        {
            if (isAllowed(organization.getDeptId(), allowed)) deptIds.add(organization.getDeptId());
        }
        for (DriveOrganizationSpaceConfig config : safeList(organizationMapper.selectConfigs()))
        {
            if (isAllowed(config.getDeptId(), allowed)) deptIds.add(config.getDeptId());
        }

        int succeeded = 0;
        List<Long> failed = new ArrayList<>();
        for (Long deptId : deptIds)
        {
            try
            {
                provisioningService.syncOrganization(deptId, updateBy);
                succeeded++;
            }
            catch (RuntimeException ex)
            {
                failed.add(deptId);
                log.warn("组织盘同步失败, deptId={}", deptId, ex);
            }
        }
        List<DriveOrganizationBudgetViolationVo> visibleViolations = new ArrayList<>();
        Set<Long> blocked = new LinkedHashSet<>();
        for (DriveOrganizationBudgetViolationVo violation : budgetService.detectViolations())
        {
            List<Long> affected = violation.affectedDeptIds().stream()
                    .filter(deptId -> isAllowed(deptId, allowed)).toList();
            if (affected.isEmpty()) continue;
            blocked.addAll(affected);
            visibleViolations.add(new DriveOrganizationBudgetViolationVo(
                    violation.budgetDeptId(), violation.budgetDeptName(),
                    violation.budgetBytes(), violation.allocatedBytes(),
                    violation.exceededBytes(), affected,
                    violation.hierarchyInvalid(), violation.violationMessage()));
        }
        return new DriveOrganizationReconcileVo(deptIds.size(), succeeded,
                failed.size(), failed, visibleViolations.size(), blocked.size(),
                List.copyOf(blocked), visibleViolations);
    }

    private static boolean isAllowed(Long deptId, Set<Long> allowed)
    {
        return deptId != null && (allowed == null || allowed.contains(deptId));
    }

    private static <T> List<T> safeList(List<T> values)
    {
        return values == null ? List.of() : values;
    }
}
