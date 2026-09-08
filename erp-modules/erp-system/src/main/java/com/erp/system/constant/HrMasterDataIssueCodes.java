package com.erp.system.constant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable, API-safe codes for read-only HR master-data readiness checks. */
public final class HrMasterDataIssueCodes
{
    public static final String ORG_PATH_INVALID = "ORG_PATH_INVALID";
    public static final String COMPANY_NODE_MISSING = "COMPANY_NODE_MISSING";
    public static final String STORE_MAPPING_MISSING = "STORE_MAPPING_MISSING";
    public static final String DEPT_LEADER_MISSING = "DEPT_LEADER_MISSING";
    public static final String DEPT_LEADER_UNRESOLVED = "DEPT_LEADER_UNRESOLVED";
    public static final String EMPLOYEE_PROFILE_MISSING = "EMPLOYEE_PROFILE_MISSING";
    public static final String POST_MISSING_OR_DISABLED = "POST_MISSING_OR_DISABLED";
    public static final String POSITION_CONFIG_MISSING = "POSITION_CONFIG_MISSING";
    public static final String POSITION_CONFIG_ROLE_MISSING = "POSITION_CONFIG_ROLE_MISSING";
    public static final String DICTIONARY_ROUTE_MISSING = "DICTIONARY_ROUTE_MISSING";
    /** Reserved for the later legal-entity master-data phase; not emitted until that feature is enabled. */
    public static final String LEGAL_ENTITY_MAPPING_MISSING = "LEGAL_ENTITY_MAPPING_MISSING";

    private static final Map<String, Definition> DEFINITIONS;

    static
    {
        Map<String, Definition> values = new LinkedHashMap<>();
        add(values, ORG_PATH_INVALID, "组织路径异常", "P0", "BLOCKING", "修复父子关系、祖级路径或停用节点");
        add(values, COMPANY_NODE_MISSING, "公司节点无法识别", "P0", "BLOCKING", "在集团节点下补齐有效公司层级");
        add(values, STORE_MAPPING_MISSING, "门店层级映射异常", "P1", "IMPORTANT", "调整门店节点类型或组织挂载位置");
        add(values, DEPT_LEADER_MISSING, "部门负责人缺失", "P0", "BLOCKING", "在负责员工管理的部门节点配置负责人");
        add(values, DEPT_LEADER_UNRESOLVED, "部门负责人身份待确认", "P0", "BLOCKING", "重新选择有效在职员工并确认负责人身份");
        add(values, EMPLOYEE_PROFILE_MISSING, "员工档案缺失", "P0", "BLOCKING", "初始化员工档案后补齐必填信息");
        add(values, POST_MISSING_OR_DISABLED, "岗位不存在或已停用", "P0", "BLOCKING", "修复员工岗位关系或启用有效岗位");
        add(values, POSITION_CONFIG_MISSING, "岗位入职配置缺失", "P1", "IMPORTANT", "按岗位和人员类别补齐入职配置");
        add(values, POSITION_CONFIG_ROLE_MISSING, "岗位默认角色缺失", "P1", "IMPORTANT", "为启用账号的岗位配置至少一个有效角色");
        add(values, DICTIONARY_ROUTE_MISSING, "字段字典路由缺失", "P1", "IMPORTANT", "配置有效字典类型并至少保留一个启用选项");
        add(values, LEGAL_ENTITY_MAPPING_MISSING, "法人主体映射缺失", "P0", "BLOCKING", "补齐组织与法人主体映射");
        DEFINITIONS = Collections.unmodifiableMap(values);
    }

    private HrMasterDataIssueCodes() { }

    public static Definition definition(String code)
    {
        Definition result = DEFINITIONS.get(code);
        if (result == null) throw new IllegalArgumentException("Unknown HR master-data issue code: " + code);
        return result;
    }

    public static List<Definition> definitions() { return List.copyOf(DEFINITIONS.values()); }

    private static void add(Map<String, Definition> values, String code, String label,
            String severity, String riskLevel, String defaultSuggestion)
    {
        values.put(code, new Definition(code, label, severity, riskLevel, defaultSuggestion));
    }

    public record Definition(String code, String label, String severity,
            String riskLevel, String defaultSuggestion) { }
}
