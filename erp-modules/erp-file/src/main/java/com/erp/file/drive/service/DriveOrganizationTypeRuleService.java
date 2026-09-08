package com.erp.file.drive.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.DriveOrganizationTypeRule;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理按组织类型自动建盘的默认规则。 */
@Service
public class DriveOrganizationTypeRuleService
{
    private static final Set<String> TYPES = Set.of("GROUP", "COMPANY", "STORE", "WAREHOUSE");

    private final DriveOrganizationMapper organizationMapper;
    private final DriveOrganizationBudgetService budgetService;
    private final DriveCapacityService capacityService;
    private final DriveAuthorizationService authorization;

    public DriveOrganizationTypeRuleService(DriveOrganizationMapper organizationMapper,
            DriveOrganizationBudgetService budgetService,
            DriveCapacityService capacityService,
            DriveAuthorizationService authorization)
    {
        this.organizationMapper = organizationMapper;
        this.budgetService = budgetService;
        this.capacityService = capacityService;
        this.authorization = authorization;
    }

    public List<DriveOrganizationTypeRule> list(DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        return safeList(organizationMapper.selectTypeRules());
    }

    @Transactional
    public DriveOrganizationTypeRule update(String deptType, boolean autoEnable,
            boolean requireActiveMember, long defaultQuotaBytes, String status,
            Integer version, String reason, DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        capacityService.lockForAllocationChange();
        String type = normalizeType(deptType);
        String normalizedStatus = normalizeStatus(status);
        validate(defaultQuotaBytes, version, reason);

        DriveOrganizationTypeRule existing = organizationMapper.selectTypeRule(type);
        if (existing == null)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_ORG_CONFIG_NOT_FOUND,
                    "组织类型规则不存在");
        }
        if (!version.equals(existing.getVersion())) throw concurrentModification();

        DriveOrganizationTypeRule proposed = copy(type, autoEnable,
                requireActiveMember, defaultQuotaBytes, normalizedStatus,
                version, reason, actor == null ? null : actor.username());
        Projection projection = project(proposed);
        budgetService.validate(projection.configs(), projection.organizations());
        capacityService.requireOrganizationAllocationWithinPool(
                allocated(projection.configs()));
        if (organizationMapper.updateTypeRule(proposed) != 1)
        {
            throw concurrentModification();
        }
        DriveOrganizationTypeRule saved = organizationMapper.selectTypeRule(type);
        if (saved == null) throw concurrentModification();
        return saved;
    }

    public Projection project(DriveOrganizationTypeRule proposed)
    {
        List<DriveOrganization> organizations = safeList(
                organizationMapper.selectAllOrganizations());
        Map<Long, DriveOrganizationSpaceConfig> configs = safeList(
                organizationMapper.selectConfigs()).stream()
                .filter(value -> value.getDeptId() != null)
                .collect(Collectors.toMap(DriveOrganizationSpaceConfig::getDeptId,
                        Function.identity(), (left, right) -> right, HashMap::new));
        int additions = 0;
        if (proposed != null && Boolean.TRUE.equals(proposed.getAutoEnable())
                && DriveConstants.STATUS_ACTIVE.equals(proposed.getStatus()))
        {
            for (DriveOrganization organization : organizations)
            {
                if (!eligible(organization, proposed)
                        || configs.containsKey(organization.getDeptId())) continue;
                configs.put(organization.getDeptId(), automaticConfig(
                        organization.getDeptId(), proposed.getDefaultQuotaBytes()));
                additions++;
            }
        }
        return new Projection(new ArrayList<>(configs.values()), organizations, additions);
    }

    public static long allocated(List<DriveOrganizationSpaceConfig> configs)
    {
        long total = 0L;
        for (DriveOrganizationSpaceConfig config : safeList(configs))
        {
            if (Boolean.TRUE.equals(config.getEnabled())
                    && !DriveConstants.STATUS_ARCHIVED.equals(config.getLifecycleStatus()))
            {
                try
                {
                    total = Math.addExact(total, Math.max(0L,
                            config.getQuotaBytes() == null ? 0L : config.getQuotaBytes()));
                }
                catch (ArithmeticException ex)
                {
                    throw invalid("组织额度合计超出系统可支持范围");
                }
            }
        }
        return total;
    }

    private static boolean eligible(DriveOrganization organization,
            DriveOrganizationTypeRule rule)
    {
        return organization != null && organization.getDeptId() != null
                && rule.getDeptType().equalsIgnoreCase(organization.getDeptType())
                && "0".equals(organization.getStatus())
                && "0".equals(organization.getDelFlag())
                && (!Boolean.TRUE.equals(rule.getRequireActiveMember())
                    || (organization.getActiveMemberCount() != null
                        && organization.getActiveMemberCount() > 0));
    }

    private static DriveOrganizationSpaceConfig automaticConfig(Long deptId, Long quotaBytes)
    {
        DriveOrganizationSpaceConfig value = new DriveOrganizationSpaceConfig();
        value.setDeptId(deptId);
        value.setEnabled(true);
        value.setQuotaBytes(quotaBytes);
        value.setMemberWriteMode(DriveConstants.ORG_WRITE_PERMISSION_ONLY);
        value.setLifecycleStatus(DriveConstants.STATUS_ACTIVE);
        value.setConfigSource(DriveConstants.CONFIG_SOURCE_TYPE_DEFAULT);
        value.setVersion(0);
        return value;
    }

    private static DriveOrganizationTypeRule copy(String type, boolean autoEnable,
            boolean requireActiveMember, long quotaBytes, String status,
            int version, String reason, String username)
    {
        DriveOrganizationTypeRule value = new DriveOrganizationTypeRule();
        value.setDeptType(type);
        value.setAutoEnable(autoEnable);
        value.setRequireActiveMember(requireActiveMember);
        value.setDefaultQuotaBytes(quotaBytes);
        value.setStatus(status);
        value.setVersion(version);
        value.setUpdateBy(username == null ? "" : username);
        value.setUpdateTime(new Date());
        value.setRemark(reason.trim());
        return value;
    }

    private static String normalizeType(String value)
    {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw invalid("组织类型无效");
        return type;
    }

    private static String normalizeStatus(String value)
    {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(DriveConstants.STATUS_ACTIVE, DriveConstants.STATUS_DISABLED).contains(status))
        {
            throw invalid("组织类型规则状态无效");
        }
        return status;
    }

    private static void validate(long quotaBytes, Integer version, String reason)
    {
        if (quotaBytes <= 0 || version == null || version < 0)
        {
            throw invalid("默认额度或版本无效");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500)
        {
            throw invalid("请填写 500 字以内的调整原因");
        }
    }

    private static <T> List<T> safeList(List<T> values)
    {
        return values == null ? List.of() : values;
    }

    private static DriveException invalid(String message)
    {
        return new DriveException(DriveErrorCodes.DRIVE_POLICY_INVALID, message);
    }

    private static DriveException concurrentModification()
    {
        return new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                "组织类型规则已被其他操作更新，请刷新后重试");
    }

    public record Projection(List<DriveOrganizationSpaceConfig> configs,
            List<DriveOrganization> organizations, int additions)
    {
    }
}
