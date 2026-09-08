package com.erp.file.drive.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveEffectiveQuota;
import com.erp.file.drive.domain.DrivePersonalQuotaPolicy;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.DriveUserQuotaContext;
import com.erp.file.drive.domain.vo.DriveUserQuotaVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DrivePersonalQuotaPolicyMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import org.springframework.stereotype.Service;

/**
 * 按个人例外、岗位、全员默认的固定优先级解析并物化个人盘额度。
 */
@Service
public class DriveEffectiveQuotaService
{
    private static final int RECONCILE_ATTEMPTS = 3;

    private final DrivePersonalQuotaPolicyMapper policyMapper;
    private final DriveSpaceMapper spaceMapper;
    private final DriveProperties properties;

    public DriveEffectiveQuotaService(DrivePersonalQuotaPolicyMapper policyMapper,
            DriveSpaceMapper spaceMapper, DriveProperties properties)
    {
        this.policyMapper = policyMapper;
        this.spaceMapper = spaceMapper;
        this.properties = properties;
    }

    public DriveEffectiveQuota resolve(Long userId)
    {
        if (userId == null)
        {
            throw invalidPolicy("用户不能为空");
        }
        if (!properties.isQuotaPolicyEnabled())
        {
            return fallback();
        }

        Date now = new Date();
        DrivePersonalQuotaPolicy userPolicy = policyMapper.selectActiveUserPolicy(userId, now);
        if (valid(userPolicy, now))
        {
            return effective(userPolicy, "个人例外额度");
        }

        List<DrivePersonalQuotaPolicy> postPolicies = policyMapper.selectActivePostPolicies(userId, now);
        if (postPolicies != null)
        {
            DrivePersonalQuotaPolicy post = postPolicies.stream()
                    .filter(policy -> valid(policy, now))
                    .min(postComparator())
                    .orElse(null);
            if (post != null)
            {
                return effective(post, "岗位：" + safeName(post.getSubjectName(), post.getSubjectId()));
            }
        }

        DrivePersonalQuotaPolicy global = policyMapper.selectGlobalPolicy();
        return valid(global, now) ? effective(global, "全员默认") : fallback();
    }

    public DriveSpace reconcilePersonalSpace(DriveSpace original, Long userId, String updateBy)
    {
        if (original == null || !DriveConstants.SPACE_PERSONAL.equals(original.getSpaceType())
                || !properties.isQuotaPolicyEnabled())
        {
            return original;
        }
        DriveEffectiveQuota effective = resolve(userId);
        DriveSpace current = original;
        for (int attempt = 0; attempt < RECONCILE_ATTEMPTS; attempt++)
        {
            if (matches(current, effective))
            {
                return current;
            }
            int version = current.getVersion() == null ? 0 : current.getVersion();
            if (spaceMapper.updateEffectiveQuota(current.getSpaceId(), effective.quotaBytes(),
                    effective.sourceType(), effective.sourceId(), version, safeUpdateBy(updateBy)) == 1)
            {
                DriveSpace updated = spaceMapper.selectById(current.getSpaceId());
                return updated == null ? current : updated;
            }
            current = spaceMapper.selectById(current.getSpaceId());
            if (current == null)
            {
                throw new DriveException(DriveErrorCodes.DRIVE_SPACE_NOT_FOUND, "云盘空间不存在");
            }
        }
        throw new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                "空间额度正在变化，请稍后重试");
    }

    public List<DriveUserQuotaVo> listEffectiveUsers()
    {
        Date now = new Date();
        List<DriveUserQuotaContext> contexts = safeList(policyMapper.selectActiveUserContexts());
        // 管理面始终展示已配置策略，便于在运行开关开启前完成容量校验。
        List<DrivePersonalQuotaPolicy> policies = safeList(policyMapper.selectAllPolicies());
        Map<Long, DriveSpace> spaces = safeList(spaceMapper.selectByType(DriveConstants.SPACE_PERSONAL))
                .stream()
                .filter(space -> space.getOwnerUserId() != null)
                .collect(Collectors.toMap(DriveSpace::getOwnerUserId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));

        Map<Long, List<DriveUserQuotaContext>> grouped = contexts.stream()
                .filter(context -> context.getUserId() != null)
                .collect(Collectors.groupingBy(DriveUserQuotaContext::getUserId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<String, DrivePersonalQuotaPolicy> policyIndex = policyIndex(policies);

        List<DriveUserQuotaVo> result = new ArrayList<>(grouped.size());
        for (Map.Entry<Long, List<DriveUserQuotaContext>> entry : grouped.entrySet())
        {
            List<DriveUserQuotaContext> rows = entry.getValue();
            DriveUserQuotaContext first = rows.get(0);
            DriveEffectiveQuota effective = resolveFromSnapshot(entry.getKey(), rows, policyIndex, now);
            DriveSpace space = spaces.get(entry.getKey());
            long used = space == null || space.getUsedBytes() == null ? 0L : space.getUsedBytes();
            long over = Math.max(0L, used - effective.quotaBytes());
            Set<Long> postIds = new LinkedHashSet<>();
            Set<String> postNames = new LinkedHashSet<>();
            for (DriveUserQuotaContext row : rows)
            {
                if (row.getPostId() != null) postIds.add(row.getPostId());
                if (row.getPostName() != null && !row.getPostName().isBlank()) postNames.add(row.getPostName());
            }
            result.add(new DriveUserQuotaVo(entry.getKey(), first.getUserName(),
                    safeName(first.getNickName(), entry.getKey()), first.getDeptId(),
                    first.getDeptName(), List.copyOf(postIds), List.copyOf(postNames),
                    effective.quotaBytes(), used, effective.sourceType(), effective.sourceId(),
                    effective.sourceLabel(), over > 0, over));
        }
        return result;
    }

    public Set<Long> affectedUserIds(String subjectType, Long subjectId)
    {
        List<DriveUserQuotaContext> contexts = safeList(policyMapper.selectActiveUserContexts());
        Set<Long> result = new LinkedHashSet<>();
        for (DriveUserQuotaContext context : contexts)
        {
            if (context.getUserId() == null) continue;
            if (DriveConstants.QUOTA_SUBJECT_GLOBAL.equals(subjectType)
                    || (DriveConstants.QUOTA_SUBJECT_USER.equals(subjectType)
                        && Objects.equals(subjectId, context.getUserId()))
                    || (DriveConstants.QUOTA_SUBJECT_POST.equals(subjectType)
                        && Objects.equals(subjectId, context.getPostId())))
            {
                result.add(context.getUserId());
            }
        }
        return result;
    }

    public void reconcileExistingUsers(Set<Long> userIds, String updateBy)
    {
        if (!properties.isQuotaPolicyEnabled() || userIds == null || userIds.isEmpty()) return;
        for (DriveSpace space : safeList(spaceMapper.selectByType(DriveConstants.SPACE_PERSONAL)))
        {
            if (space.getOwnerUserId() != null && userIds.contains(space.getOwnerUserId()))
            {
                reconcilePersonalSpace(space, space.getOwnerUserId(), updateBy);
            }
        }
    }

    private DriveEffectiveQuota resolveFromSnapshot(Long userId,
            List<DriveUserQuotaContext> contexts,
            Map<String, DrivePersonalQuotaPolicy> policies, Date now)
    {
        DrivePersonalQuotaPolicy user = policies.get(key(DriveConstants.QUOTA_SUBJECT_USER, userId));
        if (valid(user, now)) return effective(user, "个人例外额度");

        DrivePersonalQuotaPolicy bestPost = contexts.stream()
                .map(DriveUserQuotaContext::getPostId)
                .filter(Objects::nonNull)
                .map(postId -> policies.get(key(DriveConstants.QUOTA_SUBJECT_POST, postId)))
                .filter(policy -> valid(policy, now))
                .min(postComparator())
                .orElse(null);
        if (bestPost != null)
        {
            return effective(bestPost,
                    "岗位：" + safeName(bestPost.getSubjectName(), bestPost.getSubjectId()));
        }
        DrivePersonalQuotaPolicy global = policies.get(key(DriveConstants.QUOTA_SUBJECT_GLOBAL, 0L));
        return valid(global, now) ? effective(global, "全员默认") : fallback();
    }

    private Map<String, DrivePersonalQuotaPolicy> policyIndex(
            List<DrivePersonalQuotaPolicy> policies)
    {
        Map<String, DrivePersonalQuotaPolicy> result = new HashMap<>();
        for (DrivePersonalQuotaPolicy policy : policies)
        {
            if (policy.getSubjectType() != null && policy.getSubjectId() != null)
            {
                result.put(key(policy.getSubjectType(), policy.getSubjectId()), policy);
            }
        }
        return result;
    }

    private DriveEffectiveQuota fallback()
    {
        return new DriveEffectiveQuota(properties.getPersonalQuota(),
                DriveConstants.QUOTA_SOURCE_GLOBAL, null, "全员默认");
    }

    private static DriveEffectiveQuota effective(DrivePersonalQuotaPolicy policy, String label)
    {
        return new DriveEffectiveQuota(policy.getQuotaBytes(), policy.getSubjectType(),
                DriveConstants.QUOTA_SUBJECT_GLOBAL.equals(policy.getSubjectType())
                        ? null : policy.getSubjectId(), label);
    }

    private static boolean valid(DrivePersonalQuotaPolicy policy, Date now)
    {
        return policy != null && policy.getQuotaBytes() != null && policy.getQuotaBytes() > 0
                && DriveConstants.STATUS_ACTIVE.equals(policy.getStatus())
                && (policy.getExpireTime() == null || policy.getExpireTime().after(now));
    }

    private static Comparator<DrivePersonalQuotaPolicy> postComparator()
    {
        return Comparator
                .comparing((DrivePersonalQuotaPolicy policy) -> value(policy.getPriority())).reversed()
                .thenComparing(policy -> value(policy.getQuotaBytes()), Comparator.reverseOrder())
                .thenComparing(policy -> value(policy.getPolicyId()));
    }

    private static long value(Number value)
    {
        return value == null ? 0L : value.longValue();
    }

    private static boolean matches(DriveSpace space, DriveEffectiveQuota effective)
    {
        return space != null && Objects.equals(space.getQuotaBytes(), effective.quotaBytes())
                && Objects.equals(space.getQuotaSourceType(), effective.sourceType())
                && Objects.equals(space.getQuotaSourceId(), effective.sourceId());
    }

    private static String key(String subjectType, Long subjectId)
    {
        return subjectType + ":" + subjectId;
    }

    private static String safeName(String value, Object fallback)
    {
        return value == null || value.isBlank() ? String.valueOf(fallback) : value;
    }

    private static String safeUpdateBy(String value)
    {
        return value == null ? "" : value;
    }

    private static <T> List<T> safeList(List<T> values)
    {
        return values == null ? List.of() : values;
    }

    private static DriveException invalidPolicy(String message)
    {
        return new DriveException(DriveErrorCodes.DRIVE_POLICY_INVALID, message);
    }
}
