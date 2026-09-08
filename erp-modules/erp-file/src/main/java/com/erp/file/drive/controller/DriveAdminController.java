package com.erp.file.drive.controller;

import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.dto.DriveCapacityUpdateRequest;
import com.erp.file.drive.domain.dto.DriveOrganizationBatchRequest;
import com.erp.file.drive.domain.dto.DriveOrganizationConfigRequest;
import com.erp.file.drive.domain.dto.DriveOrganizationTypeRuleRequest;
import com.erp.file.drive.domain.dto.DrivePersonalQuotaPolicyRequest;
import com.erp.file.drive.domain.dto.DrivePolicyDeleteRequest;
import com.erp.file.drive.domain.dto.DriveQuotaImpactRequest;
import com.erp.file.drive.domain.vo.DriveUserQuotaVo;
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
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 云盘容量、岗位/个人额度和组织盘配置的单一管理入口。 */
@RestController
@RequestMapping("/drive/admin")
public class DriveAdminController
{
    private final DriveFeatureGuard featureGuard;
    private final DriveActorResolver actorResolver;
    private final DriveCapacityService capacityService;
    private final DrivePersonalQuotaPolicyService policyService;
    private final DriveEffectiveQuotaService effectiveQuotaService;
    private final DriveOrganizationConfigService organizationConfigService;
    private final DriveOrganizationTypeRuleService organizationTypeRuleService;
    private final DriveOrganizationScopeService organizationScopeService;
    private final DriveOrganizationReconcileService reconcileService;
    private final DriveQuotaImpactService impactService;
    private final DriveOperationLogService operationLogService;

    public DriveAdminController(DriveFeatureGuard featureGuard,
            DriveActorResolver actorResolver, DriveCapacityService capacityService,
            DrivePersonalQuotaPolicyService policyService,
            DriveEffectiveQuotaService effectiveQuotaService,
            DriveOrganizationConfigService organizationConfigService,
            DriveOrganizationTypeRuleService organizationTypeRuleService,
            DriveOrganizationScopeService organizationScopeService,
            DriveOrganizationReconcileService reconcileService,
            DriveQuotaImpactService impactService,
            DriveOperationLogService operationLogService)
    {
        this.featureGuard = featureGuard;
        this.actorResolver = actorResolver;
        this.capacityService = capacityService;
        this.policyService = policyService;
        this.effectiveQuotaService = effectiveQuotaService;
        this.organizationConfigService = organizationConfigService;
        this.organizationTypeRuleService = organizationTypeRuleService;
        this.organizationScopeService = organizationScopeService;
        this.reconcileService = reconcileService;
        this.impactService = impactService;
        this.operationLogService = operationLogService;
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @GetMapping({"/quota/overview", "/quota/capacity"})
    public AjaxResult capacity()
    {
        DriveActor actor = actor();
        return AjaxResult.success(capacityService.overview(actor));
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @GetMapping("/status")
    public AjaxResult status()
    {
        actor();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("quotaPolicyEnabled", featureGuard.isQuotaPolicyEnabled());
        status.put("organizationSyncEnabled", featureGuard.isOrganizationSyncEnabled());
        status.put("organizationSyncCron", featureGuard.organizationSyncCron());
        status.put("capacityReservationEnabled",
                featureGuard.isCapacityReservationEnabled());
        status.put("uploadReservationTimeoutMinutes",
                featureGuard.uploadReservationTimeoutMinutes());
        status.put("uploadReservationCleanupCron",
                featureGuard.uploadReservationCleanupCron());
        status.put("uploadReservationCleanupBatchSize",
                featureGuard.uploadReservationCleanupBatchSize());
        status.put("uploadReservationCleanupEnabled",
                featureGuard.isCapacityReservationEnabled());
        return AjaxResult.success(status);
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @PutMapping("/quota/capacity")
    public AjaxResult updateCapacity(@Valid @RequestBody DriveCapacityUpdateRequest request)
    {
        DriveActor actor = actor();
        DriveQuotaImpactRequest impact = capacityImpact(request);
        impactService.verify(impact, request.getImpactHash(), actor);
        Object result = capacityService.update(request.getPhysicalCapacityBytes(),
                request.getReservePercent(), request.getPublicPoolBytes(),
                request.getPersonalPoolBytes(), request.getOrganizationPoolBytes(),
                request.getEnforcementMode(), request.getVersion(), request.getReason(), actor);
        operationLogService.success(DriveConstants.ACTION_CAPACITY_UPDATE, actor,
                null, "version=" + request.getVersion(), "capacity configuration updated");
        return AjaxResult.success(result);
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @GetMapping("/quota/personal-policies")
    public AjaxResult policies()
    {
        DriveActor actor = actor();
        return AjaxResult.success(policyService.listPolicies(actor));
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @GetMapping("/quota/posts")
    public AjaxResult posts()
    {
        DriveActor actor = actor();
        return AjaxResult.success(policyService.listPostOptions(actor));
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @PutMapping("/quota/personal-policies/{subjectType}/{subjectId}")
    public AjaxResult savePolicy(@PathVariable String subjectType,
            @PathVariable Long subjectId,
            @Valid @RequestBody DrivePersonalQuotaPolicyRequest request)
    {
        DriveActor actor = actor();
        DriveQuotaImpactRequest impact = policyImpact(subjectType, subjectId, request, false);
        impactService.verify(impact, request.getImpactHash(), actor);
        Object result = policyService.savePolicy(subjectType, subjectId,
                request.getQuotaBytes(), request.getPriority(), request.getExpireTime(),
                request.getVersion(), request.getReason(), actor);
        operationLogService.success(DriveConstants.ACTION_QUOTA_POLICY_UPDATE, actor,
                null, "subject=" + subjectType + ':' + subjectId,
                "quotaBytes=" + request.getQuotaBytes());
        return AjaxResult.success(result);
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @DeleteMapping("/quota/personal-policies/{subjectType}/{subjectId}")
    public AjaxResult deletePolicy(@PathVariable String subjectType,
            @PathVariable Long subjectId,
            @Valid @RequestBody DrivePolicyDeleteRequest request)
    {
        DriveActor actor = actor();
        DriveQuotaImpactRequest impact = new DriveQuotaImpactRequest();
        impact.setChangeType(DriveConstants.IMPACT_PERSONAL_POLICY);
        impact.setSubjectType(subjectType);
        impact.setSubjectId(subjectId);
        impact.setDeletePolicy(true);
        impact.setVersion(request.getVersion());
        impactService.verify(impact, request.getImpactHash(), actor);
        policyService.deletePolicy(subjectType, subjectId, request.getVersion(), actor);
        operationLogService.success(DriveConstants.ACTION_QUOTA_POLICY_DELETE, actor,
                null, "subject=" + subjectType + ':' + subjectId,
                "deleted, reason=" + request.getReason().trim());
        return AjaxResult.success();
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @GetMapping("/quota/users")
    public AjaxResult users(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Boolean overQuota,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize)
    {
        DriveActor actor = actor();
        String search = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        Set<Long> manageable = actor.admin() ? Set.of()
                : organizationScopeService.manageableDeptIds(actor);
        List<DriveUserQuotaVo> filtered = effectiveQuotaService.listEffectiveUsers().stream()
                .filter(value -> actor.admin() || (value.deptId() != null
                        && manageable.contains(value.deptId())))
                .filter(value -> deptId == null || deptId.equals(value.deptId()))
                .filter(value -> overQuota == null || overQuota == value.overQuota())
                .filter(value -> search.isEmpty() || contains(value.username(), search)
                        || contains(value.displayName(), search)
                        || contains(value.deptName(), search)
                        || value.postNames().stream().anyMatch(name -> contains(name, search)))
                .toList();
        int size = Math.max(1, Math.min(pageSize, 100));
        int page = Math.max(1, pageNum);
        int from = (int) Math.min(filtered.size(), (long) (page - 1) * size);
        int to = Math.min(filtered.size(), from + size);
        return AjaxResult.success(filtered.subList(from, to)).put("total", filtered.size())
                .put("pageNum", page).put("pageSize", size);
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @GetMapping("/organizations")
    public AjaxResult organizations()
    {
        DriveActor actor = actor();
        return AjaxResult.success(organizationConfigService.list(actor));
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @GetMapping("/organizations/type-rules")
    public AjaxResult organizationTypeRules()
    {
        DriveActor actor = actor();
        return AjaxResult.success(organizationTypeRuleService.list(actor));
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @PutMapping("/organizations/type-rules/{deptType}")
    public AjaxResult saveOrganizationTypeRule(@PathVariable String deptType,
            @Valid @RequestBody DriveOrganizationTypeRuleRequest request)
    {
        DriveActor actor = actor();
        DriveQuotaImpactRequest impact = organizationTypeRuleImpact(deptType, request);
        impactService.verify(impact, request.getImpactHash(), actor);
        Object result = organizationTypeRuleService.update(deptType,
                request.getAutoEnable(), request.getRequireActiveMember(),
                request.getDefaultQuotaBytes(), request.getStatus(), request.getVersion(),
                request.getReason(), actor);
        operationLogService.success(DriveConstants.ACTION_ORG_TYPE_RULE_UPDATE, actor,
                null, "deptType=" + deptType,
                "autoEnable=" + request.getAutoEnable()
                        + ", defaultQuotaBytes=" + request.getDefaultQuotaBytes());
        return AjaxResult.success(result);
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @PostMapping("/organizations/batch")
    public AjaxResult saveOrganizationBatch(
            @Valid @RequestBody DriveOrganizationBatchRequest request)
    {
        DriveActor actor = actor();
        DriveQuotaImpactRequest impact = organizationBatchImpact(request);
        impactService.verify(impact, request.getImpactHash(), actor);
        Object result = organizationConfigService.saveBatch(request.getTargets(),
                request.getEnabled(), request.getQuotaBytes(),
                request.getMemberWriteMode(), request.getLifecycleStatus(),
                request.getReason(), actor);
        operationLogService.success(DriveConstants.ACTION_ORG_CONFIG_BATCH, actor,
                null, "count=" + request.getTargets().size(),
                "quotaBytes=" + request.getQuotaBytes());
        return AjaxResult.success(result);
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @PutMapping("/organizations/{deptId}")
    public AjaxResult saveOrganization(@PathVariable Long deptId,
            @Valid @RequestBody DriveOrganizationConfigRequest request)
    {
        DriveActor actor = actor();
        DriveQuotaImpactRequest impact = organizationImpact(deptId, request);
        impactService.verify(impact, request.getImpactHash(), actor);
        Object result = organizationConfigService.save(deptId, request.getEnabled(),
                request.getQuotaBytes(), request.getTreeBudgetBytes(),
                request.getMemberWriteMode(), request.getLifecycleStatus(),
                request.getVersion(), request.getReason(), actor);
        operationLogService.success(DriveConstants.ACTION_ORG_CONFIG_UPDATE, actor,
                null, "deptId=" + deptId, "quotaBytes=" + request.getQuotaBytes());
        return AjaxResult.success(result);
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @PostMapping("/quota/impact")
    public AjaxResult impact(@Valid @RequestBody DriveQuotaImpactRequest request)
    {
        return AjaxResult.success(impactService.preview(request, actor()));
    }

    @RequiresPermissions({DriveConstants.PERMISSION_ACCESS, DriveConstants.PERMISSION_QUOTA_MANAGE})
    @PostMapping("/organizations/reconcile")
    public AjaxResult reconcile()
    {
        DriveActor actor = actor();
        featureGuard.requireOrganizationSyncEnabled();
        Object result = reconcileService.reconcile(actor);
        operationLogService.success(DriveConstants.ACTION_ORG_RECONCILE, actor,
                null, null, "organization reconciliation completed");
        return AjaxResult.success(result);
    }

    private DriveActor actor()
    {
        featureGuard.requireEnabled();
        return actorResolver.resolve();
    }

    private static DriveQuotaImpactRequest capacityImpact(DriveCapacityUpdateRequest request)
    {
        DriveQuotaImpactRequest value = new DriveQuotaImpactRequest();
        value.setChangeType(DriveConstants.IMPACT_CAPACITY);
        value.setPhysicalCapacityBytes(request.getPhysicalCapacityBytes());
        value.setReservePercent(request.getReservePercent());
        value.setPublicPoolBytes(request.getPublicPoolBytes());
        value.setPersonalPoolBytes(request.getPersonalPoolBytes());
        value.setOrganizationPoolBytes(request.getOrganizationPoolBytes());
        value.setEnforcementMode(request.getEnforcementMode());
        value.setVersion(request.getVersion());
        return value;
    }

    private static DriveQuotaImpactRequest policyImpact(String subjectType, Long subjectId,
            DrivePersonalQuotaPolicyRequest request, boolean delete)
    {
        DriveQuotaImpactRequest value = new DriveQuotaImpactRequest();
        value.setChangeType(DriveConstants.IMPACT_PERSONAL_POLICY);
        value.setSubjectType(subjectType);
        value.setSubjectId(subjectId);
        value.setDeletePolicy(delete);
        value.setQuotaBytes(request.getQuotaBytes());
        value.setPriority(request.getPriority());
        value.setExpireTime(request.getExpireTime());
        value.setVersion(request.getVersion());
        return value;
    }

    private static DriveQuotaImpactRequest organizationImpact(Long deptId,
            DriveOrganizationConfigRequest request)
    {
        DriveQuotaImpactRequest value = new DriveQuotaImpactRequest();
        value.setChangeType(DriveConstants.IMPACT_ORGANIZATION);
        value.setSubjectId(deptId);
        value.setEnabled(request.getEnabled());
        value.setQuotaBytes(request.getQuotaBytes());
        value.setTreeBudgetBytes(request.getTreeBudgetBytes());
        value.setMemberWriteMode(request.getMemberWriteMode());
        value.setLifecycleStatus(request.getLifecycleStatus());
        value.setVersion(request.getVersion());
        return value;
    }

    private static DriveQuotaImpactRequest organizationTypeRuleImpact(String deptType,
            DriveOrganizationTypeRuleRequest request)
    {
        DriveQuotaImpactRequest value = new DriveQuotaImpactRequest();
        value.setChangeType(DriveConstants.IMPACT_ORGANIZATION_TYPE_RULE);
        value.setSubjectType(deptType);
        value.setEnabled(request.getAutoEnable());
        value.setRequireActiveMember(request.getRequireActiveMember());
        value.setQuotaBytes(request.getDefaultQuotaBytes());
        value.setRuleStatus(request.getStatus());
        value.setVersion(request.getVersion());
        return value;
    }

    private static DriveQuotaImpactRequest organizationBatchImpact(
            DriveOrganizationBatchRequest request)
    {
        DriveQuotaImpactRequest value = new DriveQuotaImpactRequest();
        value.setChangeType(DriveConstants.IMPACT_ORGANIZATION_BATCH);
        value.setTargets(request.getTargets());
        value.setEnabled(request.getEnabled());
        value.setQuotaBytes(request.getQuotaBytes());
        value.setMemberWriteMode(request.getMemberWriteMode());
        value.setLifecycleStatus(request.getLifecycleStatus());
        return value;
    }

    private static boolean contains(String value, String search)
    {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }
}
