package com.erp.file.drive.service;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DrivePersonalQuotaPolicy;
import com.erp.file.drive.domain.DriveUserQuotaContext;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DrivePersonalQuotaPolicyMapper;
import com.erp.file.drive.domain.vo.DrivePostQuotaOptionVo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个人盘全员、岗位和用户策略的版本化管理入口。
 */
@Service
public class DrivePersonalQuotaPolicyService
{
    private static final int MAX_PRIORITY = 1000;

    private final DrivePersonalQuotaPolicyMapper mapper;
    private final DriveEffectiveQuotaService effectiveQuotaService;
    private final DriveCapacityService capacityService;
    private final DriveAuthorizationService authorization;
    private final DriveOrganizationScopeService scopeService;

    public DrivePersonalQuotaPolicyService(DrivePersonalQuotaPolicyMapper mapper,
            DriveEffectiveQuotaService effectiveQuotaService,
            DriveCapacityService capacityService,
            DriveAuthorizationService authorization,
            DriveOrganizationScopeService scopeService)
    {
        this.mapper = mapper;
        this.effectiveQuotaService = effectiveQuotaService;
        this.capacityService = capacityService;
        this.authorization = authorization;
        this.scopeService = scopeService;
    }

    public List<DrivePersonalQuotaPolicy> listPolicies(DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        List<DrivePersonalQuotaPolicy> policies = mapper.selectAllPolicies();
        if (policies == null || policies.isEmpty()) return List.of();
        if (actor != null && actor.admin()) return policies;

        Set<Long> manageableUserIds = new LinkedHashSet<>();
        Set<Long> manageableDeptIds = scopeService == null
                ? Set.of() : scopeService.manageableDeptIds(actor);
        List<DriveUserQuotaContext> contexts = mapper.selectActiveUserContexts();
        if (contexts != null && manageableDeptIds != null)
        {
            for (DriveUserQuotaContext context : contexts)
            {
                if (context != null && context.getUserId() != null
                        && manageableDeptIds.contains(context.getDeptId()))
                {
                    manageableUserIds.add(context.getUserId());
                }
            }
        }
        return policies.stream()
                .filter(policy -> policy != null
                        && (!DriveConstants.QUOTA_SUBJECT_USER.equals(policy.getSubjectType())
                                || manageableUserIds.contains(policy.getSubjectId())))
                .toList();
    }

    public List<DrivePostQuotaOptionVo> listPostOptions(DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        List<DrivePostQuotaOptionVo> posts = mapper.selectPostOptions();
        return posts == null ? List.of() : posts;
    }

    @Transactional
    public DrivePersonalQuotaPolicy savePolicy(String subjectType, Long subjectId,
            long quotaBytes, int priority, Date expireTime, Integer version,
            String reason, DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        capacityService.lockForAllocationChange();
        String normalizedType = validate(subjectType, subjectId, quotaBytes,
                priority, expireTime, reason);
        requireSubjectScope(normalizedType, subjectId, actor);
        ensureSubjectExists(normalizedType, subjectId);

        DrivePersonalQuotaPolicy existing = mapper.selectBySubject(normalizedType, subjectId);
        Date now = new Date();
        DrivePersonalQuotaPolicy value = new DrivePersonalQuotaPolicy();
        value.setSubjectType(normalizedType);
        value.setSubjectId(subjectId);
        value.setQuotaBytes(quotaBytes);
        value.setPriority(priority);
        value.setExpireTime(expireTime);
        value.setStatus(DriveConstants.STATUS_ACTIVE);
        value.setUpdateBy(username(actor));
        value.setUpdateTime(now);
        value.setRemark(reason.trim());

        if (existing == null)
        {
            if (version != null && version != 0)
            {
                throw concurrentModification();
            }
            value.setVersion(0);
            value.setCreateBy(username(actor));
            value.setCreateTime(now);
            try
            {
                if (mapper.insertPolicy(value) != 1) throw concurrentModification();
            }
            catch (DuplicateKeyException ex)
            {
                throw concurrentModification();
            }
        }
        else
        {
            if (version == null || !version.equals(existing.getVersion()))
            {
                throw concurrentModification();
            }
            value.setVersion(version);
            if (mapper.updatePolicy(value) != 1) throw concurrentModification();
        }

        capacityService.requireCurrentAllocationsWithinPools();
        Set<Long> affected = effectiveQuotaService.affectedUserIds(normalizedType, subjectId);
        effectiveQuotaService.reconcileExistingUsers(affected, username(actor));
        return requirePolicy(normalizedType, subjectId);
    }

    @Transactional
    public void deletePolicy(String subjectType, Long subjectId, Integer version, DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        capacityService.lockForAllocationChange();
        String normalizedType = normalizeType(subjectType);
        if (DriveConstants.QUOTA_SUBJECT_GLOBAL.equals(normalizedType))
        {
            throw invalid("全员默认额度不能删除");
        }
        if (subjectId == null || subjectId <= 0 || version == null || version < 0)
        {
            throw invalid("额度策略参数无效");
        }
        requireSubjectScope(normalizedType, subjectId, actor);
        DrivePersonalQuotaPolicy existing = mapper.selectBySubject(normalizedType, subjectId);
        if (existing == null) throw notFound();
        if (!version.equals(existing.getVersion())
                || mapper.deletePolicy(normalizedType, subjectId, version) != 1)
        {
            throw concurrentModification();
        }
        capacityService.requireCurrentAllocationsWithinPools();
        Set<Long> affected = effectiveQuotaService.affectedUserIds(normalizedType, subjectId);
        effectiveQuotaService.reconcileExistingUsers(affected, username(actor));
    }

    /** 将旧的单个人空间额度调整转换为用户例外策略。 */
    public DrivePersonalQuotaPolicy saveLegacyUserQuota(Long userId,
            long quotaBytes, String reason, DriveActor actor)
    {
        DrivePersonalQuotaPolicy existing = mapper.selectBySubject(
                DriveConstants.QUOTA_SUBJECT_USER, userId);
        return savePolicy(DriveConstants.QUOTA_SUBJECT_USER, userId, quotaBytes,
                existing == null || existing.getPriority() == null ? 0 : existing.getPriority(),
                null, existing == null ? 0 : existing.getVersion(), reason, actor);
    }

    private String validate(String subjectType, Long subjectId, long quotaBytes,
            int priority, Date expireTime, String reason)
    {
        String normalizedType = normalizeType(subjectType);
        if (quotaBytes <= 0 || priority < 0 || priority > MAX_PRIORITY)
        {
            throw invalid("额度或优先级无效");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500)
        {
            throw invalid("请填写 500 字以内的额度调整原因");
        }
        if (DriveConstants.QUOTA_SUBJECT_GLOBAL.equals(normalizedType))
        {
            if (subjectId == null || subjectId != 0L || priority != 0 || expireTime != null)
            {
                throw invalid("全员默认额度参数无效");
            }
        }
        else
        {
            if (subjectId == null || subjectId <= 0)
            {
                throw invalid("策略对象不能为空");
            }
            if (!DriveConstants.QUOTA_SUBJECT_USER.equals(normalizedType) && expireTime != null)
            {
                throw invalid("只有个人例外额度可以设置到期时间");
            }
            if (expireTime != null && !expireTime.after(new Date()))
            {
                throw invalid("个人例外额度的失效时间必须晚于当前时间");
            }
        }
        return normalizedType;
    }

    private void ensureSubjectExists(String subjectType, Long subjectId)
    {
        if (DriveConstants.QUOTA_SUBJECT_USER.equals(subjectType)
                && mapper.countActiveUser(subjectId) != 1)
        {
            throw invalid("用户不存在或已停用");
        }
        if (DriveConstants.QUOTA_SUBJECT_POST.equals(subjectType)
                && mapper.countActivePost(subjectId) != 1)
        {
            throw invalid("岗位不存在或已停用");
        }
    }

    private void requireSubjectScope(String subjectType, Long subjectId, DriveActor actor)
    {
        if (!DriveConstants.QUOTA_SUBJECT_USER.equals(subjectType)
                || actor == null || actor.admin()) return;
        Long deptId = null;
        List<DriveUserQuotaContext> contexts = mapper.selectActiveUserContexts();
        if (contexts != null)
        {
            for (DriveUserQuotaContext context : contexts)
            {
                if (context != null && subjectId.equals(context.getUserId()))
                {
                    deptId = context.getDeptId();
                    break;
                }
            }
        }
        if (deptId == null || scopeService == null || !scopeService.canManage(actor, deptId))
        {
            throw new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED,
                    "无权管理该用户的个人额度");
        }
    }

    private DrivePersonalQuotaPolicy requirePolicy(String subjectType, Long subjectId)
    {
        DrivePersonalQuotaPolicy value = mapper.selectBySubject(subjectType, subjectId);
        if (value == null) throw notFound();
        return value;
    }

    private static String normalizeType(String subjectType)
    {
        if (subjectType == null) throw invalid("策略类型不能为空");
        String value = subjectType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of(DriveConstants.QUOTA_SUBJECT_GLOBAL,
                DriveConstants.QUOTA_SUBJECT_POST,
                DriveConstants.QUOTA_SUBJECT_USER).contains(value))
        {
            throw invalid("不支持的额度策略类型");
        }
        return value;
    }

    private static String username(DriveActor actor)
    {
        return actor == null || actor.username() == null ? "" : actor.username();
    }

    private static DriveException invalid(String message)
    {
        return new DriveException(DriveErrorCodes.DRIVE_POLICY_INVALID, message);
    }

    private static DriveException notFound()
    {
        return new DriveException(DriveErrorCodes.DRIVE_POLICY_NOT_FOUND, "额度策略不存在");
    }

    private static DriveException concurrentModification()
    {
        return new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                "额度策略已被其他操作更新，请刷新后重试");
    }
}
