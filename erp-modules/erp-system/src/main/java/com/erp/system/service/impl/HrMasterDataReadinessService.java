package com.erp.system.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysDictData;
import com.erp.system.api.domain.SysDictType;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.constant.HrMasterDataIssueCodes;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.SysUserPost;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.domain.vo.HrMasterDataIssueQuery;
import com.erp.system.domain.vo.HrMasterDataIssueVo;
import com.erp.system.domain.vo.HrMasterDataSummaryVo;
import com.erp.system.mapper.HrOnboardingPositionConfigMapper;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysDictTypeService;
import com.erp.system.service.ISysRoleService;

/**
 * Read-only orchestration for HR master-data readiness. This service owns
 * scoped snapshot loading, filtering and summaries; the evaluator owns rules.
 */
@Service
public class HrMasterDataReadinessService
{
    public static final String READINESS_ENABLED_KEY =
            "hr.master-data.readiness.enabled";
    public static final String ENFORCEMENT_ENABLED_KEY =
            "hr.master-data.enforce-on-enable";
    private static final String DICT_ROUTE_PREFIX =
            HrOnboardingPositionConfigServiceImpl.DICT_ROUTE_PREFIX;

    private final HrEmployeeAccessService employeeAccess;
    private final SysDeptMapper deptMapper;
    private final SysPostMapper postMapper;
    private final SysUserPostMapper userPostMapper;
    private final HrOnboardingPositionConfigMapper positionConfigMapper;
    private final ISysConfigService configService;
    private final ISysDictTypeService dictTypeService;
    private final ISysRoleService roleService;
    private final HrMasterDataReadinessEvaluator evaluator;

    public HrMasterDataReadinessService(HrEmployeeAccessService employeeAccess,
            SysDeptMapper deptMapper, SysPostMapper postMapper,
            SysUserPostMapper userPostMapper,
            HrOnboardingPositionConfigMapper positionConfigMapper,
            ISysConfigService configService,
            ISysDictTypeService dictTypeService,
            ISysRoleService roleService)
    {
        this.employeeAccess = employeeAccess;
        this.deptMapper = deptMapper;
        this.postMapper = postMapper;
        this.userPostMapper = userPostMapper;
        this.positionConfigMapper = positionConfigMapper;
        this.configService = configService;
        this.dictTypeService = dictTypeService;
        this.roleService = roleService;
        this.evaluator = new HrMasterDataReadinessEvaluator();
    }

    @Transactional(readOnly = true)
    public List<HrMasterDataIssueVo> issues(HrMasterDataIssueQuery query)
    {
        if (!readinessEnabled())
        {
            return Collections.emptyList();
        }
        HrMasterDataScanResult scan = scan();
        return filter(scan.issues(),
                query == null ? new HrMasterDataIssueQuery() : query);
    }

    @Transactional(readOnly = true)
    public HrMasterDataSummaryVo summary(HrMasterDataIssueQuery query)
    {
        boolean enabled = readinessEnabled();
        HrMasterDataScanResult scan = enabled ? scan()
                : new HrMasterDataScanResult();
        List<HrMasterDataIssueVo> values = enabled
                ? filter(scan.issues(), query == null
                        ? new HrMasterDataIssueQuery() : query)
                : Collections.emptyList();
        HrMasterDataSummaryVo result = new HrMasterDataSummaryVo();
        result.setReadinessEnabled(enabled);
        result.setEnforcementEnabled(enforcementEnabled());
        result.setObservationMode(!result.isEnforcementEnabled());
        result.setGeneratedAt(LocalDateTime.now());
        result.setTotalIssueCount(values.size());
        result.setBlockingIssueCount(values.stream()
                .filter(value -> "P0".equals(value.getSeverity())).count());
        result.setImportantIssueCount(values.stream()
                .filter(value -> "P1".equals(value.getSeverity())).count());
        result.setNormalIssueCount(values.stream()
                .filter(value -> "P2".equals(value.getSeverity())).count());
        result.setAffectedEmployeeCount(
                scan.affectedEmployeeIds(values).size());
        result.setAffectedOccurrenceCount(values.stream()
                .mapToLong(HrMasterDataIssueVo::getAffectedEmployeeCount)
                .sum());
        Map<String, Long> byCode = new LinkedHashMap<>();
        values.forEach(value -> byCode.merge(value.getIssueCode(), 1L,
                Long::sum));
        result.setIssueCodeCounts(byCode);
        return result;
    }

    public List<HrMasterDataIssueCodes.Definition> definitions()
    {
        return HrMasterDataIssueCodes.definitions();
    }

    private HrMasterDataScanResult scan()
    {
        List<SysDept> scopedDepartments = safe(
                employeeAccess.listScopedDepartments(new SysDept()));
        List<SysDept> allDepartments = safe(
                deptMapper.selectDeptList(new SysDept()));
        List<SysUser> employees = safe(
                employeeAccess.listActiveScoped(new HrEmployeeQuery()));
        List<SysPost> posts = safe(postMapper.selectPostAll());
        List<Long> userIds = employees.stream().filter(Objects::nonNull)
                .map(SysUser::getUserId).filter(Objects::nonNull).distinct()
                .toList();
        List<SysUserPost> userPosts = userIds.isEmpty()
                ? Collections.emptyList()
                : safe(userPostMapper.selectByUserIds(userIds));
        List<HrOnboardingPositionConfig> configs = safe(positionConfigMapper
                .selectList(new HrOnboardingPositionConfig()));
        Map<Long, List<Long>> roleIdsByConfig = new LinkedHashMap<>();
        for (HrOnboardingPositionConfig config : configs)
        {
            if (config != null && config.getConfigId() != null
                    && "0".equals(config.getStatus())
                    && Boolean.TRUE.equals(config.getAccountEnabled()))
            {
                roleIdsByConfig.put(config.getConfigId(), safe(
                        positionConfigMapper.selectRoleIds(
                                config.getConfigId())));
            }
        }
        return evaluator.evaluate(new HrMasterDataReadinessEvaluator.Snapshot(
                scopedDepartments, allDepartments, employees, posts, userPosts,
                configs, roleIdsByConfig, activeRoleIds(),
                dictionaryRoutes()));
    }

    private Map<String, HrMasterDataReadinessEvaluator.DictionaryRouteSnapshot>
            dictionaryRoutes()
    {
        Map<String, HrMasterDataReadinessEvaluator.DictionaryRouteSnapshot>
                result = new LinkedHashMap<>();
        for (String fieldKey :
                HrOnboardingPositionConfigServiceImpl.DICTIONARY_FIELDS)
        {
            String configKey = DICT_ROUTE_PREFIX + fieldKey;
            String dictType = trim(configService.selectConfigByKey(configKey));
            SysDictType type = blank(dictType) ? null
                    : dictTypeService.selectDictTypeByType(dictType);
            boolean typeActive = type != null && "0".equals(type.getStatus());
            List<String> values = typeActive
                    ? activeDictionaryData(dictType).stream()
                            .map(SysDictData::getDictValue).map(this::trim)
                            .filter(value -> !blank(value)).distinct().toList()
                    : Collections.emptyList();
            result.put(fieldKey,
                    new HrMasterDataReadinessEvaluator.DictionaryRouteSnapshot(
                            fieldKey, configKey, dictType, typeActive, values));
        }
        return result;
    }

    private Set<Long> activeRoleIds()
    {
        SysRole query = new SysRole();
        query.setStatus("0");
        return safe(roleService.selectRoleList(query)).stream()
                .filter(value -> value != null && value.getRoleId() != null
                        && "0".equals(value.getStatus())
                        && "0".equals(value.getDelFlag()))
                .map(SysRole::getRoleId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private List<SysDictData> activeDictionaryData(String dictType)
    {
        return safe(dictTypeService.selectDictDataByType(dictType)).stream()
                .filter(value -> value != null
                        && "0".equals(value.getStatus()))
                .toList();
    }

    private List<HrMasterDataIssueVo> filter(
            List<HrMasterDataIssueVo> values,
            HrMasterDataIssueQuery query)
    {
        String code = upper(query.getIssueCode());
        if (!blank(code))
        {
            try
            {
                HrMasterDataIssueCodes.definition(code);
            }
            catch (IllegalArgumentException invalid)
            {
                throw new ServiceException("主数据问题代码无效");
            }
        }
        String severity = upper(query.getSeverity());
        if (!blank(severity)
                && !Set.of("P0", "P1", "P2").contains(severity))
        {
            throw new ServiceException("主数据问题优先级无效");
        }
        String resourceType = upper(query.getResourceType());
        String keyword = trim(query.getKeyword());
        String lowered = keyword == null ? null
                : keyword.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> blank(code)
                        || code.equals(value.getIssueCode()))
                .filter(value -> blank(severity)
                        || severity.equals(value.getSeverity()))
                .filter(value -> blank(resourceType)
                        || resourceType.equals(value.getResourceType()))
                .filter(value -> query.getDeptId() == null
                        || Objects.equals(query.getDeptId(), value.getDeptId()))
                .filter(value -> !Boolean.TRUE.equals(query.getAffectedOnly())
                        || value.getAffectedEmployeeCount() > 0)
                .filter(value -> lowered == null
                        || searchable(value).toLowerCase(Locale.ROOT)
                                .contains(lowered))
                .sorted(Comparator.comparingInt(
                        (HrMasterDataIssueVo value) -> severityRank(
                                value.getSeverity()))
                        .thenComparing(
                                HrMasterDataIssueVo::getAffectedEmployeeCount,
                                Comparator.reverseOrder())
                        .thenComparing(HrMasterDataIssueVo::getIssueCode)
                        .thenComparing(value ->
                                nullSafe(value.getResourceName()))
                        .thenComparing(value ->
                                nullSafe(value.getResourceId())))
                .toList();
    }

    private boolean readinessEnabled()
    {
        return flag(READINESS_ENABLED_KEY, true);
    }

    private boolean enforcementEnabled()
    {
        return flag(ENFORCEMENT_ENABLED_KEY, false);
    }

    private boolean flag(String key, boolean fallback)
    {
        String value = trim(configService.selectConfigByKey(key));
        if (blank(value))
        {
            return fallback;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (Set.of("true", "1", "yes", "on").contains(normalized))
        {
            return true;
        }
        if (Set.of("false", "0", "no", "off").contains(normalized))
        {
            return false;
        }
        return fallback;
    }

    private int severityRank(String severity)
    {
        return "P0".equals(severity) ? 0 : "P1".equals(severity) ? 1 : 2;
    }

    private String searchable(HrMasterDataIssueVo value)
    {
        return String.join(" ", nullSafe(value.getIssueCode()),
                nullSafe(value.getIssueName()),
                nullSafe(value.getResourceName()),
                nullSafe(value.getDeptName()),
                nullSafe(value.getEmployeeCategory()),
                nullSafe(value.getDetail()));
    }

    private String upper(String value)
    {
        String result = trim(value);
        return result == null ? null : result.toUpperCase(Locale.ROOT);
    }

    private String trim(String value)
    {
        return value == null ? null : value.trim();
    }

    private boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private String nullSafe(String value)
    {
        return value == null ? "" : value;
    }

    private <T> List<T> safe(List<T> values)
    {
        return values == null ? Collections.emptyList() : values;
    }
}
