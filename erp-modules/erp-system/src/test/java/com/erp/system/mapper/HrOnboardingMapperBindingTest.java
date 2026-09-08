package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.vo.HrOnboardingQuery;

@DisplayName("HR入职持久层契约")
class HrOnboardingMapperBindingTest
{
    private static final String ONBOARDING_MAPPER = "com.erp.system.mapper.HrOnboardingMapper";
    private static final String OPERATION_LOG_MAPPER = "com.erp.system.mapper.HrOnboardingOperationLogMapper";
    private static final String ONBOARDING_RESOURCE = "mapper/system/HrOnboardingMapper.xml";
    private static final String POSITION_CONFIG_MAPPER =
            "com.erp.system.mapper.HrOnboardingPositionConfigMapper";
    private static final String POSITION_CONFIG_RESOURCE =
            "mapper/system/HrOnboardingPositionConfigMapper.xml";

    @Test
    @DisplayName("入职单Mapper方法与XML语句一一绑定")
    void shouldBindOnboardingMapperMethodsToXmlStatements() throws Exception
    {
        Document document = parseResource(ONBOARDING_RESOURCE);
        Configuration configuration = mapperConfiguration(ONBOARDING_RESOURCE);

        assertThat(document.getDocumentElement().getAttribute("namespace")).isEqualTo(ONBOARDING_MAPPER);
        assertThat(statementIds(document)).containsExactlyInAnyOrderElementsOf(methodNames(ONBOARDING_MAPPER));
        assertStatementsBound(configuration, ONBOARDING_MAPPER);
    }

    @Test
    @DisplayName("岗位入职配置Mapper方法与XML语句一一绑定")
    void shouldBindPositionConfigMapperMethodsAndOptimisticRoleSql() throws Exception
    {
        Document document = parseResource(POSITION_CONFIG_RESOURCE);
        Configuration configuration = mapperConfiguration(POSITION_CONFIG_RESOURCE);

        assertThat(document.getDocumentElement().getAttribute("namespace")).isEqualTo(POSITION_CONFIG_MAPPER);
        assertThat(statementIds(document)).containsExactlyInAnyOrderElementsOf(methodNames(POSITION_CONFIG_MAPPER));
        assertStatementsBound(configuration, POSITION_CONFIG_MAPPER);
        com.erp.system.domain.HrOnboardingPositionConfig config =
                new com.erp.system.domain.HrOnboardingPositionConfig();
        config.setConfigId(1L);
        config.setPostId(2L);
        config.setEmployeeCategory("FORMAL");
        config.setDataScopeStrategy("TARGET_STORE");
        config.setContractTypeMode("REQUIRED");
        config.setSocialTypeMode("OPTIONAL");
        config.setProbationPeriodMode("NOT_APPLICABLE");
        config.setAccountEnabled(true);
        config.setVersion(3);
        BoundSql update = configuration.getMappedStatement(POSITION_CONFIG_MAPPER + ".updateByVersion")
                .getBoundSql(config);
        assertThat(normalizeSql(update.getSql())).contains(
                "account_enabled = '1'", "status = ?", "version = version + 1", "and version = ?");
        assertThat(parameterProperties(update)).endsWith("configId", "version");
    }

    @Test
    @DisplayName("状态更新Mapper显式声明取消操作人用户ID参数")
    void shouldDeclareCancellationOperatorUserIdParameter() throws Exception
    {
        Method method = Arrays.stream(HrOnboardingMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("updateOnboardingStatusByVersion"))
                .findFirst()
                .orElseThrow();

        assertThat(Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(Param.class))
                .map(Param::value))
                .containsExactly("onboardingId", "fromStatus", "toStatus", "version", "operatorUserId",
                        "operator", "cancelReason");
    }

    @Test
    @DisplayName("列表查询无筛选时生成稳定SQL且没有参数映射")
    void shouldBuildUnfilteredListBoundSql() throws Exception
    {
        HrOnboardingQuery query = new HrOnboardingQuery();
        query.getParams().put("dataScope", "");

        BoundSql boundSql = boundSql("selectOnboardingList", query);

        assertThat(normalizeSql(boundSql.getSql())).isEqualTo(
                "select o.*, owner.nick_name as owner_name from hr_onboarding o "
                        + "join sys_dept d on d.dept_id = o.target_dept_id "
                        + "left join sys_user owner on owner.user_id = o.owner_user_id "
                        + "order by o.expected_entry_date asc, o.onboarding_id desc");
        assertThat(parameterProperties(boundSql)).isEmpty();
    }

    @Test
    @DisplayName("批量冲突SQL有界、数据范围安全且兼容MySQL5.7")
    void shouldBuildScopedBatchConflictSql() throws Exception
    {
        HrOnboarding first=new HrOnboarding();first.setPhoneNumber("13800138000");
        HrOnboarding second=new HrOnboarding();second.setIdNumber("350203199001011234");
        HrOnboardingQuery query=new HrOnboardingQuery();query.getParams().put("dataScope","and d.dept_id in (10)");
        Map<String,Object> params=new HashMap<>();params.put("query",query);
        params.put("identities",Arrays.asList(first,second));

        BoundSql sql=mapperConfiguration(ONBOARDING_RESOURCE)
                .getMappedStatement(ONBOARDING_MAPPER+".selectScopedConflictCandidatesBatch").getBoundSql(params);

        String normalized=normalizeSql(sql.getSql()).toLowerCase();
        assertThat(normalized).contains("select distinct o.*","o.phone_number=?","o.id_number=?",
                "and d.dept_id in (10)","order by o.onboarding_id desc")
                .doesNotContain("window ","row_number(","returning ","nulls last");
        assertThat(parameterProperties(sql)).containsExactly("__frch_identity_0.phoneNumber",
                "__frch_identity_1.idNumber");
    }

    @Test
    @DisplayName("员工完整度批量关联只在数据范围内按最新顺序返回")
    void shouldBuildScopedLinkedEmployeeBatchWithoutCrossScopeAntiJoin() throws Exception
    {
        HrOnboardingQuery query=new HrOnboardingQuery();query.getParams().put("dataScope","and d.dept_id = 10");
        Map<String,Object> params=new HashMap<>();params.put("query",query);params.put("userIds",List.of(7L,8L));

        BoundSql bound=boundSql("selectScopedByLinkedUserIds",params);
        String sql=normalizeSql(bound.getSql());

        assertThat(sql).contains("join sys_dept d on d.dept_id = o.target_dept_id",
                "o.linked_user_id in ( ? , ? )","and d.dept_id = 10",
                "order by o.linked_user_id asc, o.onboarding_id desc")
                .doesNotContain("newer.onboarding_id","${query.params.dataScope}");
    }

    @Test
    @DisplayName("列表查询全筛选时生成全部谓词与有序参数映射")
    void shouldBuildFullyFilteredListBoundSql() throws Exception
    {
        HrOnboardingQuery query = new HrOnboardingQuery();
        query.setOnboardingId(1L);
        query.setKeyword("张");
        query.setStatus("DRAFT");
        query.setTargetDeptId(2L);
        query.setTargetStoreId(3L);
        query.setEmployeeCategory("FULL_TIME");
        query.setOwnerUserId(4L);
        query.setExpectedEntryDateFrom(new Date(1_000L));
        query.setExpectedEntryDateTo(new Date(2_000L));
        query.getParams().put("dataScope", "and d.dept_id in (10, 20)");

        BoundSql boundSql = boundSql("selectOnboardingList", query);

        assertThat(normalizeSql(boundSql.getSql())).isEqualTo(
                "select o.*, owner.nick_name as owner_name from hr_onboarding o "
                        + "join sys_dept d on d.dept_id = o.target_dept_id "
                        + "left join sys_user owner on owner.user_id = o.owner_user_id "
                        + "where o.onboarding_id = ? and (o.employee_name like concat('%', ?, '%') "
                        + "or o.phone_number like concat('%', ?, '%') or o.position_name like concat('%', ?, '%') "
                        + "or d.dept_name like concat('%', ?, '%') or o.company_name like concat('%', ?, '%') "
                        + "or o.dept_level1_name like concat('%', ?, '%') "
                        + "or o.dept_level2_name like concat('%', ?, '%') "
                        + "or o.dept_level3_name like concat('%', ?, '%') or o.store_name like concat('%', ?, '%')) "
                        + "and o.status = ? and o.target_dept_id = ? and o.target_store_id = ? "
                        + "and o.employee_category = ? and o.owner_user_id = ? and o.expected_entry_date >= ? "
                        + "and o.expected_entry_date <= ? and d.dept_id in (10, 20) "
                        + "order by o.expected_entry_date asc, o.onboarding_id desc");
        assertThat(parameterProperties(boundSql)).containsExactly("onboardingId", "keyword", "keyword", "keyword",
                "keyword", "keyword", "keyword", "keyword", "keyword", "keyword", "status", "targetDeptId",
                "targetStoreId", "employeeCategory", "ownerUserId", "expectedEntryDateFrom", "expectedEntryDateTo");
    }

    @Test
    @DisplayName("汇总聚合查询应用数据范围且不读取明细敏感列")
    void shouldBuildScopedAggregateSummarySqlWithoutFullRows() throws Exception
    {
        HrOnboardingQuery query = new HrOnboardingQuery();
        query.setSummaryDate(new Date(1_000L));
        query.getParams().put("dataScope", "and d.dept_id = 10");

        BoundSql boundSql = boundSql("selectOnboardingSummary", query);
        String sql = normalizeSql(boundSql.getSql());

        assertThat(sql).contains("coalesce(sum(case when", "join sys_dept d", "where d.dept_id = 10")
                .doesNotContain("select o.*", "id_number", "bank_account", "registered_residence",
                        "current_address", "emergency_contact_phone");
        assertThat(parameterProperties(boundSql)).contains("summaryDate");
    }

    @Test
    @DisplayName("用户选项查询仅投影标识和昵称并使用稳定排序与数据范围")
    void shouldBuildScopedUserOptionSqlWithoutPiiAndWithStableOrdering() throws Exception
    {
        SysUser query = new SysUser();
        query.setStatus("0");
        query.getParams().put("dataScope", "and d.dept_id = 10");

        BoundSql boundSql = boundSql("selectScopedUserOptions", query);
        String sql = normalizeSql(boundSql.getSql());
        String projection = sql.substring(sql.indexOf("select ") + 7, sql.indexOf(" from "));

        assertThat(splitTopLevelCsv(projection)).containsExactly("u.user_id", "u.nick_name");
        assertThat(sql).contains("join sys_dept d on d.dept_id = u.dept_id",
                        "u.status = ?", "u.del_flag = '0'", "d.dept_id = 10")
                .endsWith("order by u.nick_name asc, u.user_id asc")
                .doesNotContain("phone", "email", "profile", "id_number", "address", "bank");
        assertThat(parameterProperties(boundSql)).containsExactly("status");
    }

    @Test
    @DisplayName("今日任务查询只选择列表安全列并应用数据范围")
    void shouldBuildScopedTodayTaskSqlWithSafeColumnBoundary() throws Exception
    {
        HrOnboardingQuery query = new HrOnboardingQuery();
        query.setSummaryDate(new Date(1_000L));
        query.getParams().put("dataScope", "and d.dept_id = 10");

        BoundSql boundSql = boundSql("selectTodayOnboardingTasks", query);
        String sql = normalizeSql(boundSql.getSql());

        assertThat(sql).contains("o.onboarding_id", "phone_number_masked", "readiness_missing_count",
                        "mark_ready_allowed", "case when", "derived_blocking_count",
                        "owner.nick_name as owner_name", "join sys_dept d",
                        "and d.dept_id = 10", "o.id_number", "o.registered_residence",
                        "o.current_address", "o.department_supervisor")
                .doesNotContain("select o.*", "o.employee_name, o.phone_number,", ", o.id_number,",
                        ", o.bank_account,", ", o.registered_residence,", ", o.current_address,",
                        ", o.emergency_contact_phone,", "o.birth_date", "o.marital_status", "o.ethnicity",
                        "o.emergency_contact", "o.work_city_level", "o.legal_entity");
        String outerProjection = sql.substring(0, sql.indexOf("from ("));
        assertThat(outerProjection).contains("task.phone_number_masked", "task.readiness_missing_count")
                .doesNotContain("phone_number,", "id_number", "bank_account", "registered_residence",
                        "current_address", "emergency_contact");
        assertThat(parameterProperties(boundSql)).contains("summaryDate");
    }

    @Test
    @DisplayName("确认入职持久化从身份证自动解析的出生日期")
    void shouldPersistDerivedBirthDateWhenConfirming() throws Exception
    {
        BoundSql boundSql = boundSql("confirmOnboardingByVersion", new HrOnboarding());
        String sql = normalizeSql(boundSql.getSql());

        assertThat(sql).contains("actual_entry_date = ?", "birth_date = ?", "linked_user_id = ?");
        assertThat(parameterProperties(boundSql)).containsSubsequence(
                "employeeNo", "actualEntryDate", "birthDate", "linkedUserId");
    }

    @Test
    @DisplayName("冲突查询空条件时强制返回空集")
    void shouldBuildEmptyConflictBoundSql() throws Exception
    {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("phone", "");
        parameters.put("idNumber", null);
        parameters.put("employeeNo", "");

        BoundSql boundSql = boundSql("selectConflictCandidates", parameters);

        assertThat(normalizeSql(boundSql.getSql())).isEqualTo(
                "select o.* from hr_onboarding o where 1 = 0 order by o.onboarding_id desc");
        assertThat(parameterProperties(boundSql)).isEmpty();
    }

    @Test
    @DisplayName("冲突查询非空条件时生成OR谓词与有序参数映射")
    void shouldBuildPopulatedConflictBoundSql() throws Exception
    {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("phone", "13800000000");
        parameters.put("idNumber", "110101199001010000");
        parameters.put("employeeNo", "E001");

        BoundSql boundSql = boundSql("selectConflictCandidates", parameters);

        assertThat(normalizeSql(boundSql.getSql())).isEqualTo(
                "select o.* from hr_onboarding o where o.phone_number = ? or o.id_number = ? "
                        + "or o.employee_no = ? order by o.onboarding_id desc");
        assertThat(parameterProperties(boundSql)).containsExactly("phone", "idNumber", "employeeNo");
    }

    @Test
    @DisplayName("导入冲突查询不生成NULL自排除谓词")
    void shouldBuildImportConflictSqlWithoutNullSelfExclusion() throws Exception
    {
        HrOnboardingQuery query = new HrOnboardingQuery();
        query.setTargetDeptId(10L);
        query.getParams().put("dataScope", "and d.dept_id = 10");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        parameters.put("phone", "13800000000");
        parameters.put("idNumber", null);
        parameters.put("employeeNo", null);

        BoundSql boundSql = boundSql("selectScopedConflictCandidates", parameters);
        String sql = normalizeSql(boundSql.getSql());

        assertThat(sql).contains("o.phone_number = ?", "d.dept_id = 10")
                .doesNotContain("o.onboarding_id <>");
        assertThat(parameterProperties(boundSql)).containsExactly("phone");
    }

    @Test
    @DisplayName("已持久化入职单冲突预览保留自排除谓词")
    void shouldBuildPersistedConflictSqlWithSelfExclusion() throws Exception
    {
        HrOnboardingQuery query = new HrOnboardingQuery();
        query.setOnboardingId(99L);
        query.getParams().put("dataScope", "");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        parameters.put("phone", "13800000000");
        parameters.put("idNumber", null);
        parameters.put("employeeNo", null);

        BoundSql boundSql = boundSql("selectScopedConflictCandidates", parameters);

        assertThat(normalizeSql(boundSql.getSql())).contains("o.onboarding_id <> ?", "o.phone_number = ?");
        assertThat(parameterProperties(boundSql)).containsExactly("query.onboardingId", "phone");
    }

    @Test
    @DisplayName("状态更新到READY时只更新通用审计字段")
    void shouldBuildReadyStatusBoundSql() throws Exception
    {
        BoundSql boundSql = statusBoundSql("DRAFT", "READY");

        assertThat(normalizeSql(boundSql.getSql())).isEqualTo(
                "update hr_onboarding set status = ?, update_by = ?, update_time = sysdate(), "
                        + "version = version + 1 where onboarding_id = ? and status = ? and version = ?");
        assertThat(parameterProperties(boundSql)).containsExactly("toStatus", "operator", "onboardingId",
                "fromStatus", "version");
    }

    @Test
    @DisplayName("取消状态更新同时持久化操作人用户ID、名称、原因与时间")
    void shouldBuildCancelledStatusBoundSql() throws Exception
    {
        BoundSql boundSql = statusBoundSql("DRAFT", "CANCELLED");

        assertThat(normalizeSql(boundSql.getSql())).isEqualTo(
                "update hr_onboarding set status = ?, cancel_reason = ?, cancelled_by_user_id = ?, "
                        + "cancelled_by = ?, cancelled_time = sysdate(), update_by = ?, update_time = sysdate(), "
                        + "version = version + 1 where onboarding_id = ? and status = ? and version = ?");
        assertThat(parameterProperties(boundSql)).containsExactly("toStatus", "cancelReason", "operatorUserId",
                "operator", "operator", "onboardingId", "fromStatus", "version");
    }

    @Test
    @DisplayName("从取消恢复草稿时清空全部取消审计字段")
    void shouldBuildRestoreDraftStatusBoundSql() throws Exception
    {
        BoundSql boundSql = statusBoundSql("CANCELLED", "DRAFT");

        assertThat(normalizeSql(boundSql.getSql())).isEqualTo(
                "update hr_onboarding set status = ?, cancel_reason = null, cancelled_by_user_id = null, "
                        + "cancelled_by = null, cancelled_time = null, update_by = ?, update_time = sysdate(), "
                        + "version = version + 1 where onboarding_id = ? and status = ? and version = ?");
        assertThat(parameterProperties(boundSql)).containsExactly("toStatus", "operator", "onboardingId",
                "fromStatus", "version");
    }

    @Test
    @DisplayName("通用版本更新只允许修改草稿可编辑字段")
    void shouldRestrictVersionUpdateToDraftEditableFields() throws Exception
    {
        BoundSql boundSql = boundSql("updateOnboardingByVersion", new HrOnboarding());
        String sql = normalizeSql(boundSql.getSql());

        assertThat(sql)
                .contains("employee_name = ?", "phone_number = ?", "target_dept_id = ?", "target_post_id = ?",
                        "expected_entry_date = ?", "update_by = ?", "update_time = sysdate()",
                        "version = version + 1", "and status = 'draft'")
                .doesNotContain("set status = ?", ", status = ?", "employee_no =", "actual_entry_date =", "source_type =",
                        "preferred_conflict_action =", "preferred_bind_user_id =", "linked_user_id =",
                        "account_configuration_status =", "account_risk_code =", "cancel_reason =",
                        "cancelled_by_user_id =", "cancelled_by =", "cancelled_time =", "confirmed_by =",
                        "confirmed_time =", "confirm_idempotency_key =");
        assertThat(parameterProperties(boundSql)).endsWith("updateBy", "remark", "onboardingId", "version");
    }

    @Test
    @DisplayName("新增入职单的列和值及参数映射数量一致")
    void shouldKeepInsertColumnsAndValuesAligned() throws Exception
    {
        BoundSql boundSql = boundSql("insertOnboarding", new HrOnboarding());
        String sql = normalizeSql(boundSql.getSql());
        int valuesIndex = sql.indexOf(") values (");
        List<String> columns = splitTopLevelCsv(sql.substring(sql.indexOf('(') + 1, valuesIndex));
        List<String> values = splitTopLevelCsv(sql.substring(valuesIndex + ") values (".length(), sql.length() - 1));

        assertThat(values).hasSameSizeAs(columns);
        assertThat(parameterProperties(boundSql)).hasSameSizeAs(columns);
    }

    @Test
    @DisplayName("操作日志Mapper方法与XML语句绑定")
    void shouldBindOperationLogMapperMethodsToXmlStatements() throws Exception
    {
        String resource = "mapper/system/HrOnboardingOperationLogMapper.xml";
        Document document = parseResource(resource);
        Configuration configuration = mapperConfiguration(resource);

        assertThat(document.getDocumentElement().getAttribute("namespace")).isEqualTo(OPERATION_LOG_MAPPER);
        assertThat(methodNames(OPERATION_LOG_MAPPER)).containsExactlyInAnyOrder("insertOperationLog",
                "selectByOnboardingId");
        assertThat(statementIds(document)).containsExactlyInAnyOrderElementsOf(methodNames(OPERATION_LOG_MAPPER));
        assertStatementsBound(configuration, OPERATION_LOG_MAPPER);
    }

    @Test
    @DisplayName("列表与详情VO仅暴露脱敏敏感字段")
    void shouldExposeOnlyMaskedSensitiveFieldsFromViewObjects() throws Exception
    {
        assertMaskedSensitiveBoundary("com.erp.system.domain.vo.HrOnboardingListVo");
        assertMaskedSensitiveBoundary("com.erp.system.domain.vo.HrOnboardingDetailVo");
    }

    private static BoundSql statusBoundSql(String fromStatus, String toStatus) throws Exception
    {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("onboardingId", 1L);
        parameters.put("fromStatus", fromStatus);
        parameters.put("toStatus", toStatus);
        parameters.put("version", 2);
        parameters.put("operatorUserId", 9L);
        parameters.put("operator", "tester");
        parameters.put("cancelReason", "duplicate");
        return boundSql("updateOnboardingStatusByVersion", parameters);
    }

    private static BoundSql boundSql(String statementId, Object parameters) throws Exception
    {
        MappedStatement statement = mapperConfiguration(ONBOARDING_RESOURCE)
                .getMappedStatement(ONBOARDING_MAPPER + "." + statementId);
        return statement.getBoundSql(parameters);
    }

    private static Configuration mapperConfiguration(String resource) throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static String normalizeSql(String sql)
    {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static List<String> parameterProperties(BoundSql boundSql)
    {
        return boundSql.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty())
                .collect(Collectors.toList());
    }

    private static List<String> splitTopLevelCsv(String text)
    {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++)
        {
            char current = text.charAt(i);
            if (current == '(')
            {
                depth++;
            }
            else if (current == ')')
            {
                depth--;
            }
            else if (current == ',' && depth == 0)
            {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(text.substring(start).trim());
        return parts;
    }

    private static void assertMaskedSensitiveBoundary(String className) throws Exception
    {
        Set<String> fields = new java.util.HashSet<String>();
        Class<?> type = Class.forName(className);
        while (type != null && type != Object.class)
        {
            fields.addAll(Arrays.stream(type.getDeclaredFields()).map(Field::getName).collect(Collectors.toSet()));
            type = type.getSuperclass();
        }

        assertThat(fields).doesNotContain("phoneNumber", "idNumber", "bankAccount", "registeredResidence",
                "currentAddress", "emergencyContactPhone");
        assertThat(fields).contains("phoneNumberMasked");
    }

    private static Set<String> methodNames(String className) throws Exception
    {
        return Arrays.stream(Class.forName(className).getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
    }

    private static Set<String> statementIds(Document document)
    {
        Set<String> ids = new java.util.HashSet<String>();
        for (String tag : Arrays.asList("select", "insert", "update", "delete"))
        {
            NodeList nodes = document.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++)
            {
                ids.add(((Element) nodes.item(i)).getAttribute("id"));
            }
        }
        return ids;
    }

    private static Document parseResource(String resource) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory.newDocumentBuilder().parse(Resources.getResourceAsStream(resource));
    }

    private static void assertStatementsBound(Configuration configuration, String mapperClassName) throws Exception
    {
        for (String methodName : methodNames(mapperClassName))
        {
            assertThat(configuration.hasStatement(mapperClassName + "." + methodName))
                    .as("MyBatis statement for %s.%s", mapperClassName, methodName)
                    .isTrue();
        }
    }
}
