package com.erp.system.service.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.system.api.domain.SysDictData;
import com.erp.system.api.domain.SysDictType;
import com.erp.system.api.domain.SysRole;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.SysPost;
import com.erp.system.exception.HrOnboardingValidationException;
import com.erp.system.mapper.HrOnboardingPositionConfigMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.service.IHrOnboardingPositionConfigService;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysDictTypeService;
import com.erp.system.service.ISysRoleService;

@Service
public class HrOnboardingPositionConfigServiceImpl implements IHrOnboardingPositionConfigService
{
    public static final String DICT_ROUTE_PREFIX = "hr.onboarding.dict_type.";
    public static final List<String> DICTIONARY_FIELDS = Collections.unmodifiableList(Arrays.asList(
            "employeeCategory", "sex", "idType", "maritalStatus", "ethnicity", "workCityLevel",
            "contractType", "socialType", "probationPeriod"));
    private static final Set<String> RULE_MODES = new LinkedHashSet<>(Arrays.asList(
            "REQUIRED", "OPTIONAL", "NOT_APPLICABLE"));
    private static final Set<String> DATA_SCOPE_STRATEGIES = new LinkedHashSet<>(Arrays.asList(
            "TARGET_STORE", "TARGET_DEPT", "NONE"));
    private static final Map<String, String> DATA_SCOPE_LABELS = Map.of(
            "TARGET_STORE", "目标门店",
            "TARGET_DEPT", "目标组织",
            "NONE", "不配置数据范围");

    private final HrOnboardingPositionConfigMapper mapper;
    private final SysPostMapper postMapper;
    private final ISysRoleService roleService;
    private final ISysConfigService systemConfigService;
    private final ISysDictTypeService dictTypeService;

    public HrOnboardingPositionConfigServiceImpl(HrOnboardingPositionConfigMapper mapper,
            SysPostMapper postMapper, ISysRoleService roleService, ISysConfigService systemConfigService,
            ISysDictTypeService dictTypeService)
    {
        this.mapper = mapper;
        this.postMapper = postMapper;
        this.roleService = roleService;
        this.systemConfigService = systemConfigService;
        this.dictTypeService = dictTypeService;
    }

    @Override
    public List<HrOnboardingPositionConfig> list(HrOnboardingPositionConfig query)
    {
        List<HrOnboardingPositionConfig> rows = mapper.selectList(query == null
                ? new HrOnboardingPositionConfig() : query);
        if (rows == null) return Collections.emptyList();
        rows.forEach(this::hydrateRoles);
        return rows;
    }

    @Override
    public HrOnboardingPositionConfig get(Long configId)
    {
        HrOnboardingPositionConfig row = mapper.selectById(configId);
        if (row == null) throw failure("POSITION_CONFIG_NOT_FOUND", "岗位入职配置不存在");
        hydrateRoles(row);
        return row;
    }

    @Override
    public HrOnboardingPositionConfig resolveActive(Long postId, String employeeCategory)
    {
        HrOnboardingPositionConfig row = mapper.selectByPair(postId, trim(employeeCategory));
        if (row == null || !"0".equals(row.getStatus())) return null;
        hydrateRoles(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrOnboardingPositionConfig create(HrOnboardingPositionConfig input, String operator)
    {
        requireInput(input);
        prepare(input);
        input.setStatus("0");
        validate(input);
        if (mapper.selectByPair(input.getPostId(), input.getEmployeeCategory()) != null)
            throw failure("POSITION_CONFIG_DUPLICATE", "该岗位和人员类别已存在配置");
        input.setVersion(0);
        input.setCreateBy(operator);
        input.setUpdateBy(operator);
        try
        {
            if (mapper.insert(input) != 1) throw failure("POSITION_CONFIG_WRITE_FAILED", "岗位入职配置保存失败");
        }
        catch (DataIntegrityViolationException failure)
        {
            if (!causedByPositionPairUniqueConstraint(failure)) throw failure;
            throw failure("POSITION_CONFIG_DUPLICATE", "该岗位和人员类别已存在配置");
        }
        replaceRoles(input.getConfigId(), input.getRoleIds(), operator);
        return get(input.getConfigId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrOnboardingPositionConfig update(Long configId, HrOnboardingPositionConfig input, String operator)
    {
        requireInput(input);
        HrOnboardingPositionConfig stored = mapper.selectByIdForUpdate(configId);
        if (stored == null) throw failure("POSITION_CONFIG_NOT_FOUND", "岗位入职配置不存在");
        if (input.getVersion() == null || !input.getVersion().equals(stored.getVersion()))
            throw versionConflict();
        input.setConfigId(configId);
        input.setStatus(resolveUpdateStatus(input.getStatus(), stored.getStatus()));
        prepare(input);
        validate(input);
        HrOnboardingPositionConfig samePair = mapper.selectByPair(input.getPostId(), input.getEmployeeCategory());
        if (samePair != null && !configId.equals(samePair.getConfigId()))
            throw failure("POSITION_CONFIG_DUPLICATE", "该岗位和人员类别已存在配置");
        input.setUpdateBy(operator);
        try
        {
            if (mapper.updateByVersion(input) != 1) throw versionConflict();
        }
        catch (DataIntegrityViolationException failure)
        {
            if (!causedByPositionPairUniqueConstraint(failure)) throw failure;
            throw failure("POSITION_CONFIG_DUPLICATE", "该岗位和人员类别已存在配置");
        }
        replaceRoles(configId, input.getRoleIds(), operator);
        return get(configId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long configId, Integer version, String operator)
    {
        HrOnboardingPositionConfig stored = mapper.selectByIdForUpdate(configId);
        if (stored == null) throw failure("POSITION_CONFIG_NOT_FOUND", "岗位入职配置不存在");
        if (version == null || !version.equals(stored.getVersion())) throw versionConflict();
        if (mapper.disableByVersion(configId, version, operator) != 1) throw versionConflict();
    }

    @Override
    public Map<String, Object> options()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        SysPost postQuery = new SysPost();
        postQuery.setStatus("0");
        result.put("posts", optionPosts(postMapper.selectPostList(postQuery)));
        SysRole roleQuery = new SysRole();
        roleQuery.setStatus("0");
        result.put("roles", optionRoles(roleService.selectRoleList(roleQuery)));
        Map<String, List<Map<String, Object>>> dictionaries = loadDictionaries(DICTIONARY_FIELDS);
        result.put("employeeCategories", dictionaries.get("employeeCategory"));
        result.put("dictionaryDefaults", dictionaries);
        result.put("missingDictionaryMappings", DICTIONARY_FIELDS.stream()
                .filter(fieldKey -> dictionaries.getOrDefault(fieldKey, Collections.emptyList()).isEmpty())
                .collect(Collectors.toList()));
        result.put("ruleModes", optionValues(RULE_MODES));
        result.put("dataScopeStrategies", DATA_SCOPE_STRATEGIES.stream()
                .map(value -> option(DATA_SCOPE_LABELS.getOrDefault(value, value), value))
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    public Map<String, List<Map<String, Object>>> loadDictionaries(List<String> fieldKeys)
    {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String fieldKey : fieldKeys) result.put(fieldKey, dictionaryOptions(fieldKey));
        return result;
    }

    private void validate(HrOnboardingPositionConfig input)
    {
        if (input.getPostId() == null || blank(input.getEmployeeCategory()))
            throw failure("POSITION_CONFIG_PAIR_REQUIRED", "岗位和人员类别不能为空");
        validateRule("contractType", input.getContractTypeMode(), input.getDefaultContractType());
        validateRule("socialType", input.getSocialTypeMode(), input.getDefaultSocialType());
        validateRule("probationPeriod", input.getProbationPeriodMode(), input.getDefaultProbationPeriod());
        if (!DATA_SCOPE_STRATEGIES.contains(input.getDataScopeStrategy()))
            throw failure("POSITION_CONFIG_DATA_SCOPE_INVALID", "数据范围策略无效");
        if (input.getAccountEnabled() == null)
            throw failure("POSITION_CONFIG_ACCOUNT_MODE_REQUIRED", "是否启用账号不能为空");
        if (!"0".equals(input.getStatus()) && !"1".equals(input.getStatus()))
            throw failure("POSITION_CONFIG_STATUS_INVALID", "配置状态无效");
        if (Boolean.TRUE.equals(input.getAccountEnabled()) && input.getRoleIds().isEmpty())
            throw failure("POSITION_CONFIG_ROLE_REQUIRED", "启用账号时至少选择一个角色");

        SysPost post = postMapper.selectPostById(input.getPostId());
        if (post == null || !"0".equals(post.getStatus()))
            throw failure("POSITION_CONFIG_POST_INACTIVE", "岗位不存在或已停用");
        for (Long roleId : input.getRoleIds())
        {
            roleService.checkRoleAllowed(new SysRole(roleId));
            roleService.checkRoleDataScope(roleId);
            SysRole role = roleService.selectRoleById(roleId);
            if (role == null || !"0".equals(role.getStatus()) || !"0".equals(role.getDelFlag()))
                throw failure("POSITION_CONFIG_ROLE_INACTIVE", "角色不存在、已停用或已删除");
        }
        validateDictionaryValues(input);
    }

    private void validateDictionaryValues(HrOnboardingPositionConfig input)
    {
        validateDictionaryValue("employeeCategory", input.getEmployeeCategory(), true);
        validateDictionaryValue("contractType", input.getDefaultContractType(),
                !"NOT_APPLICABLE".equals(input.getContractTypeMode()));
        validateDictionaryValue("socialType", input.getDefaultSocialType(),
                !"NOT_APPLICABLE".equals(input.getSocialTypeMode()));
        validateDictionaryValue("probationPeriod", input.getDefaultProbationPeriod(),
                !"NOT_APPLICABLE".equals(input.getProbationPeriodMode()));
    }

    private void validateDictionaryValue(String fieldKey, String value, boolean mappingRequired)
    {
        String dictType = trim(systemConfigService.selectConfigByKey(DICT_ROUTE_PREFIX + fieldKey));
        if (blank(dictType))
        {
            if (mappingRequired) throw failure("POSITION_CONFIG_DICTIONARY_MAPPING_MISSING",
                    "缺少字典映射: " + fieldKey);
            return;
        }
        List<SysDictData> values = activeDictionary(dictType);
        if (values.isEmpty() && mappingRequired)
            throw failure("POSITION_CONFIG_DICTIONARY_MAPPING_MISSING", "字典映射无有效选项: " + fieldKey);
        if (!blank(value) && values.stream().noneMatch(item -> value.equals(item.getDictValue())))
            throw failure("POSITION_CONFIG_DICTIONARY_VALUE_INVALID", "默认值不属于启用字典: " + fieldKey);
    }

    private void validateRule(String fieldKey, String mode, String defaultValue)
    {
        if (!RULE_MODES.contains(mode))
            throw failure("POSITION_CONFIG_RULE_MODE_INVALID", fieldKey + "规则模式无效");
        if ("REQUIRED".equals(mode) && blank(defaultValue))
            throw failure("POSITION_CONFIG_DEFAULT_REQUIRED", fieldKey + "为必填规则时默认值不能为空");
    }

    private void prepare(HrOnboardingPositionConfig input)
    {
        input.setEmployeeCategory(trim(input.getEmployeeCategory()));
        input.setDataScopeStrategy(trim(input.getDataScopeStrategy()));
        input.setContractTypeMode(trim(input.getContractTypeMode()));
        input.setDefaultContractType(trimToNull(input.getDefaultContractType()));
        input.setSocialTypeMode(trim(input.getSocialTypeMode()));
        input.setDefaultSocialType(trimToNull(input.getDefaultSocialType()));
        input.setProbationPeriodMode(trim(input.getProbationPeriodMode()));
        input.setDefaultProbationPeriod(trimToNull(input.getDefaultProbationPeriod()));
        input.setJobGrade(trimToNull(input.getJobGrade()));
        input.setRoleIds(input.getRoleIds().stream().filter(id -> id != null).distinct().collect(Collectors.toList()));
        if ("NOT_APPLICABLE".equals(input.getContractTypeMode())) input.setDefaultContractType(null);
        if ("NOT_APPLICABLE".equals(input.getSocialTypeMode())) input.setDefaultSocialType(null);
        if ("NOT_APPLICABLE".equals(input.getProbationPeriodMode())) input.setDefaultProbationPeriod(null);
    }

    private void replaceRoles(Long configId, List<Long> roleIds, String operator)
    {
        mapper.deleteRolesByConfigId(configId);
        for (Long roleId : roleIds) mapper.insertRole(configId, roleId, operator);
    }

    private void hydrateRoles(HrOnboardingPositionConfig row)
    {
        List<Long> roleIds = mapper.selectRoleIds(row.getConfigId());
        row.setRoleIds(roleIds == null ? Collections.emptyList() : roleIds);
    }

    private List<Map<String, Object>> dictionaryOptions(String fieldKey)
    {
        String dictType = trim(systemConfigService.selectConfigByKey(DICT_ROUTE_PREFIX + fieldKey));
        if (blank(dictType)) return Collections.emptyList();
        return activeDictionary(dictType).stream()
                .map(item -> option(item.getDictLabel(), item.getDictValue())).collect(Collectors.toList());
    }

    private List<SysDictData> activeDictionary(String dictType)
    {
        SysDictType type = dictTypeService.selectDictTypeByType(dictType);
        if (type == null || !"0".equals(type.getStatus())) return Collections.emptyList();
        List<SysDictData> values = dictTypeService.selectDictDataByType(dictType);
        if (values == null) return Collections.emptyList();
        return values.stream().filter(item -> item != null && "0".equals(item.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> optionPosts(List<SysPost> posts)
    {
        if (posts == null) return Collections.emptyList();
        return posts.stream().filter(post -> "0".equals(post.getStatus()))
                .map(post -> option(post.getPostName(), post.getPostId())).collect(Collectors.toList());
    }

    private List<Map<String, Object>> optionRoles(List<SysRole> roles)
    {
        if (roles == null) return Collections.emptyList();
        Comparator<SysRole> ordering = Comparator
                .comparing(SysRole::getRoleSort, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(SysRole::getRoleName, Comparator.nullsLast(String::compareTo))
                .thenComparing(SysRole::getRoleId, Comparator.nullsLast(Long::compareTo));
        return roles.stream().filter(role -> role != null && "0".equals(role.getStatus())
                        && "0".equals(role.getDelFlag()) && !isAdministrativeRole(role))
                .sorted(ordering)
                .map(role -> option(role.getRoleName(), role.getRoleId())).collect(Collectors.toList());
    }

    private boolean isAdministrativeRole(SysRole role)
    {
        return role.isAdmin() || "super-admin".equalsIgnoreCase(trim(role.getRoleKey()));
    }

    private boolean causedByPositionPairUniqueConstraint(DataIntegrityViolationException failure)
    {
        Throwable cause = failure.getCause();
        while (cause != null)
        {
            if (cause.getMessage() != null
                    && cause.getMessage().contains("uk_hr_onboarding_position_category")) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private List<Map<String, Object>> optionValues(Set<String> values)
    {
        return values.stream().map(value -> option(value, value)).collect(Collectors.toList());
    }

    private Map<String, Object> option(Object label, Object value)
    {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("label", label);
        option.put("value", value);
        return option;
    }

    private void requireInput(HrOnboardingPositionConfig input)
    {
        if (input == null) throw failure("POSITION_CONFIG_REQUIRED", "岗位入职配置不能为空");
    }

    private HrOnboardingValidationException versionConflict()
    {
        return failure("POSITION_CONFIG_VERSION_CONFLICT", "岗位入职配置已被其他人修改，请刷新后重试");
    }

    private String resolveUpdateStatus(String requestedStatus, String storedStatus)
    {
        String requested = trimToNull(requestedStatus);
        if (requested != null && !"0".equals(requested) && !"1".equals(requested))
            throw failure("POSITION_CONFIG_STATUS_INVALID", "配置状态无效");
        if ("0".equals(storedStatus) && "1".equals(requested))
            throw failure("POSITION_CONFIG_DISABLE_REQUIRED", "停用配置请使用专用停用操作");
        return requested == null ? storedStatus : requested;
    }

    private HrOnboardingValidationException failure(String code, String message)
    {
        return new HrOnboardingValidationException(code, message);
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String trimToNull(String value) { String result = trim(value); return blank(result) ? null : result; }
}
