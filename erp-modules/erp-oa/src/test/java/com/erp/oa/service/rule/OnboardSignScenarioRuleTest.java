package com.erp.oa.service.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.mapper.OaSignPlanVersionMapper;

@DisplayName("入职签约场景规则")
class OnboardSignScenarioRuleTest
{
    private OaSignPlanVersionMapper versionMapper;
    private OnboardSignScenarioRule rule;

    @BeforeEach
    void setUp()
    {
        versionMapper = mock(OaSignPlanVersionMapper.class);
        rule = new OnboardSignScenarioRule(versionMapper, JsonMapper.builder().findAndAddModules().build());
    }

    @Test
    @DisplayName("唯一支持ONBOARD并从action来源严格生成固定去重键")
    void shouldSupportOnlyOnboardAndBuildStrictDedupeKey()
    {
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setScenario("ONBOARD");
        event.setEmployeeId(201L);
        event.setSourceBusinessId("501");
        event.setSourceEventVersion(1L);

        assertThat(rule.supports("ONBOARD")).isTrue();
        assertThat(rule.supports("RENEWAL")).isFalse();
        assertThat(rule.dedupeKey(event)).isEqualTo("ONBOARD:201:501:1");

        event.setSourceBusinessId("not-an-action-id");
        assertThatThrownBy(() -> rule.dedupeKey(event))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源业务编号");
    }

    @Test
    @DisplayName("Excel原值仅作建议展示且草稿冻结已匹配公司主数据")
    void shouldFreezeRecommendationsSeparatelyFromSelectedLegalEntity()
    {
        HrSignBusinessEvent event = event("LABOR_CONTRACT", "SOCIAL_INSURED", "9");
        event.setAttributes(Map.of(
                "recommendedCompany", "舟山茗汇文化传播有限公司",
                "recommendedLegalRepresentative", "杜翠香",
                "recommendedRegisteredAddress", "浙江省舟山市嵊泗县枸杞乡奇观村育才路9号203室-013工位",
                "historicalSupplement", true));
        OaSignPlanVersion version = version("A3", "LABOR_CONTRACT", "SOCIAL_INSURED",
                "7-9", "PUBLISHED", "ENABLED");
        when(versionMapper.selectPublishedMatchingCandidates("ONBOARD", 1171L, 301L))
                .thenReturn(List.of(version));

        OaSignPackage draft = rule.decide(event).getDraftPackage();

        assertThat(draft.getRecommendedCompanySnapshot())
                .isEqualTo("舟山茗汇文化传播有限公司");
        assertThat(draft.getRecommendedLegalRepresentativeSnapshot()).isEqualTo("杜翠香");
        assertThat(draft.getRecommendedRegisteredAddressSnapshot())
                .isEqualTo("浙江省舟山市嵊泗县枸杞乡奇观村育才路9号203室-013工位");
        assertThat(draft.getHistoricalSupplement()).isTrue();
        assertThat(draft.getLegalEntityIdSnapshot()).isEqualTo(301L);
        assertThat(draft.getLegalEntityNameSnapshot()).isEqualTo("上海示例有限公司");
    }

    @ParameterizedTest(name = "{0}: {1}+{2}+{3}")
    @CsvSource({
            "A1, LABOR_CONTRACT, SOCIAL_INSURED, 2",
            "A2, LABOR_CONTRACT, SOCIAL_INSURED, P5",
            "A3, LABOR_CONTRACT, SOCIAL_INSURED, 7-8",
            "A3, LABOR_CONTRACT, SOCIAL_INSURED, 9",
            "A4, LABOR_CONTRACT, SOCIAL_UNINSURED, P3",
            "A5, LABOR_CONTRACT, SOCIAL_UNINSURED, 5-6",
            "A6, LABOR_CONTRACT, SOCIAL_UNINSURED, P8",
            "B1, SERVICE_CONTRACT, SOCIAL_UNINSURED, 2-4",
            "B2, SERVICE_CONTRACT, SOCIAL_UNINSURED, 6",
            "B3, SERVICE_CONTRACT, SOCIAL_UNINSURED, P7"
    })
    @DisplayName("九类组合只匹配不可变版本ruleJson中的稳定路线")
    void shouldCreateDraftForAllNineStableRoutes(String routeCode, String contractType,
            String socialType, String jobGrade)
    {
        HrSignBusinessEvent event = event(contractType, socialType, jobGrade);
        OaSignPlanVersion version = version(routeCode, contractType, socialType,
                gradeBand(routeCode), "PUBLISHED", "ENABLED");
        when(versionMapper.selectPublishedMatchingCandidates("ONBOARD", 1171L, 301L))
                .thenReturn(List.of(version));

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getPlanVersionId()).isEqualTo(version.getVersionId());
        assertThat(decision.getRiskLevel()).isEqualTo("LOW");
        assertThat(decision.getReasonCodes()).isEmpty();
        OaSignPackage draft = decision.getDraftPackage();
        assertThat(draft.getEmployeeId()).isEqualTo(201L);
        assertThat(draft.getEmployeePhoneSnapshot()).isEqualTo("13800000000");
        assertThat(draft.getEmployeeIdCardSnapshot()).isEqualTo("310101199001011234");
        assertThat(draft.getEmployeeAddressSnapshot()).isEqualTo("上海市徐汇区");
        assertThat(draft.getShopDeptId()).isEqualTo(1171L);
        assertThat(draft.getPostNameSnapshot()).isEqualTo("销售顾问");
        assertThat(draft.getPostLevelSnapshot()).isEqualTo(jobGrade);
        assertThat(draft.getScenario()).isEqualTo("ONBOARD");
        assertThat(draft.getEmploymentType()).isEqualTo(contractType);
        assertThat(draft.getContractTermCodeSnapshot()).isEqualTo("FIXED_TERM");
        assertThat(draft.getSocialType()).isEqualTo(socialType);
        assertThat(draft.getSalaryVersion()).isEqualTo(
                "SOCIAL_INSURED".equals(socialType) ? "B" : "A");
        assertThat(draft.getSalaryTotal()).isEqualByComparingTo("8000.00");
        assertThat(draft.getStatus()).isEqualTo(OaSignPackageStatus.DRAFT);
    }

    @Test
    @DisplayName("无固定期限代码作为签约包不可变快照进入草稿")
    void shouldFreezeOpenEndedContractTermInDraft()
    {
        HrSignBusinessEvent event = event("LABOR_CONTRACT", "SOCIAL_INSURED", "3");
        event.getAfterSnapshot().setContractTermCode("OPEN_ENDED");
        OaSignPlanVersion version = version("A1", "LABOR_CONTRACT", "SOCIAL_INSURED",
                "2-4", "PUBLISHED", "ENABLED");
        when(versionMapper.selectPublishedMatchingCandidates("ONBOARD", 1171L, 301L))
                .thenReturn(List.of(version));

        OaSignPackage draft = rule.decide(event).getDraftPackage();

        assertThat(draft.getContractTermCodeSnapshot()).isEqualTo("OPEN_ENDED");
    }

    @ParameterizedTest(name = "{0} 透传劳务签约字段")
    @CsvSource({
            "B1, 2-4, 退休返聘, 雇主责任险",
            "B3, 7-8, 在校实习生, 商业意外保险"
    })
    @DisplayName("劳务路线从已发布方案版本透传人员和保险类型")
    void shouldCopyServiceFieldsFromPublishedPlanVersion(String routeCode, String band,
            String servicePersonType, String insuranceType)
    {
        HrSignBusinessEvent event = event("SERVICE_CONTRACT", "SOCIAL_UNINSURED", band);
        OaSignPlanVersion version = version(routeCode, "SERVICE_CONTRACT",
                "SOCIAL_UNINSURED", band, "PUBLISHED", "ENABLED");
        version.setRuleJson("{\"routeCode\":\"" + routeCode
                + "\",\"contractTypeCode\":\"SERVICE_CONTRACT\""
                + ",\"socialTypeCode\":\"SOCIAL_UNINSURED\""
                + ",\"jobGradeBand\":\"" + band
                + "\",\"servicePersonType\":\"  " + servicePersonType
                + "  \",\"insuranceType\":\"  " + insuranceType + "  \"}");
        when(versionMapper.selectPublishedMatchingCandidates("ONBOARD", 1171L, 301L))
                .thenReturn(List.of(version));

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getDraftPackage().getServicePersonType())
                .isEqualTo(servicePersonType);
        assertThat(decision.getDraftPackage().getInsuranceType())
                .isEqualTo(insuranceType);
    }

    @ParameterizedTest(name = "legacy {0}: {1}+{2}+{3}")
    @CsvSource({
            "A1, LABOR_CONTRACT, SOCIAL_INSURED, 2-4, 劳动合同, 有社保",
            "A2, LABOR_CONTRACT, SOCIAL_INSURED, 5-6, 劳动合同, 有社保",
            "A3, LABOR_CONTRACT, SOCIAL_INSURED, 7-8, 劳动合同, 有社保",
            "A4, LABOR_CONTRACT, SOCIAL_UNINSURED, 2-4, 劳动合同, 无社保",
            "A5, LABOR_CONTRACT, SOCIAL_UNINSURED, 5-6, 劳动合同, 无社保",
            "A6, LABOR_CONTRACT, SOCIAL_UNINSURED, 7-8, 劳动合同, 无社保",
            "B1, SERVICE_CONTRACT, SOCIAL_UNINSURED, 2-4, 劳务合同, 无社保",
            "B2, SERVICE_CONTRACT, SOCIAL_UNINSURED, 5-6, 劳务合同, 无社保",
            "B3, SERVICE_CONTRACT, SOCIAL_UNINSURED, 7-8, 劳务合同, 无社保"
    })
    @DisplayName("既有中文legacy ruleJson严格推导九类稳定路线")
    void shouldCreateDraftForAllNineLegacyRules(String routeCode, String contractType,
            String socialType, String postLevel, String employmentType, String legacySocialType)
    {
        HrSignBusinessEvent event = event(contractType, socialType, postLevel);
        OaSignPlanVersion version = version(routeCode, contractType, socialType,
                postLevel, "PUBLISHED", "ENABLED");
        String salaryRule = "LABOR_CONTRACT".equals(contractType)
                ? ",\"salaryVersion\":\"" + ("SOCIAL_INSURED".equals(socialType) ? "B" : "A")
                        + "\"" : "";
        version.setRuleJson("{\"employmentType\":\"" + employmentType
                + "\",\"socialType\":\"" + legacySocialType
                + "\",\"postLevel\":\"" + postLevel + "\"" + salaryRule + "}");
        when(versionMapper.selectPublishedMatchingCandidates("ONBOARD", 1171L, 301L))
                .thenReturn(List.of(version));

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getPlanVersionId()).isEqualTo(version.getVersionId());
    }

    @Test
    @DisplayName("发布服务规范化后的社保代码仍可作为兼容字段参与匹配")
    void shouldAcceptCanonicalSocialCodeInPublishedLegacyField()
    {
        HrSignBusinessEvent event = event("LABOR_CONTRACT", "SOCIAL_UNINSURED", "4");
        OaSignPlanVersion version = version("A4", "LABOR_CONTRACT", "SOCIAL_UNINSURED",
                "2-4", "PUBLISHED", "ENABLED");
        version.setRuleJson("{\"contractTypeCode\":\"LABOR_CONTRACT\""
                + ",\"employmentType\":\"劳动合同\""
                + ",\"socialTypeCode\":\"SOCIAL_UNINSURED\""
                + ",\"socialType\":\"SOCIAL_UNINSURED\""
                + ",\"jobGradeBand\":\"2-4\",\"postLevel\":\"2-4\""
                + ",\"salaryVersion\":\"A\",\"routeCode\":\"A4\"}");
        when(versionMapper.selectPublishedMatchingCandidates("ONBOARD", 1171L, 301L))
                .thenReturn(List.of(version));

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getPlanVersionId()).isEqualTo(version.getVersionId());
    }

    @Test
    @DisplayName("新旧字段语义一致时接受显式稳定字段")
    void shouldAcceptCompatibleExplicitAndLegacyFields()
    {
        OaSignPlanVersion version = version("A1", "LABOR_CONTRACT", "SOCIAL_INSURED",
                "2-4", "PUBLISHED", "ENABLED");
        version.setRuleJson("{\"routeCode\":\" a1 \","
                + "\"contractTypeCode\":\" labor_contract \","
                + "\"socialTypeCode\":\" social_insured \","
                + "\"jobGradeBand\":\"2-4\","
                + "\"employmentType\":\"劳动合同\","
                + "\"socialType\":\"有社保\",\"postLevel\":\"P3\","
                + "\"salaryVersion\":\"B\"}");
        when(versionMapper.selectPublishedMatchingCandidates("ONBOARD", 1171L, 301L))
                .thenReturn(List.of(version));

        assertThat(rule.decide(event("LABOR_CONTRACT", "SOCIAL_INSURED", "P3")).getAction())
                .isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"routeCode\":\"A1\",\"contractTypeCode\":\"LABOR_CONTRACT\",\"socialTypeCode\":\"SOCIAL_INSURED\",\"jobGradeBand\":\"2-4\",\"employmentType\":\"劳务合同\"}",
            "{\"routeCode\":\"A1\",\"contractTypeCode\":\"LABOR_CONTRACT\",\"socialTypeCode\":\"SOCIAL_INSURED\",\"jobGradeBand\":\"2-4\",\"socialType\":\"无社保\"}",
            "{\"routeCode\":\"A1\",\"contractTypeCode\":\"LABOR_CONTRACT\",\"socialTypeCode\":\"SOCIAL_INSURED\",\"jobGradeBand\":\"2-4\",\"postLevel\":\"5-6\"}",
            "{\"routeCode\":\"A2\",\"contractTypeCode\":\"LABOR_CONTRACT\",\"socialTypeCode\":\"SOCIAL_INSURED\",\"jobGradeBand\":\"2-4\"}",
            "{\"routeCode\":\"A1\",\"contractTypeCode\":\"LABOR_CONTRACT\",\"socialTypeCode\":\"SOCIAL_INSURED\",\"jobGradeBand\":\"2-4\",\"servicePersonType\":{}}",
            "{\"routeCode\":\"A1\",\"contractTypeCode\":\"LABOR_CONTRACT\",\"socialTypeCode\":\"SOCIAL_INSURED\",\"jobGradeBand\":\"2-4\",\"salaryVersion\":\"A\"}"
    })
    @DisplayName("新旧字段或显式路线冲突时整条规则无效")
    void shouldInvalidateConflictingRuleFields(String ruleJson)
    {
        OaSignPlanVersion version = version("A1", "LABOR_CONTRACT", "SOCIAL_INSURED",
                "2-4", "PUBLISHED", "ENABLED");
        version.setRuleJson(ruleJson);
        when(versionMapper.selectPublishedMatchingCandidates("ONBOARD", 1171L, 301L))
                .thenReturn(List.of(version));

        OaSignDraftDecision decision = rule.decide(
                event("LABOR_CONTRACT", "SOCIAL_INSURED", "P3"));

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).containsExactly("PLAN_RULE_INVALID");
    }

    @Test
    @DisplayName("缺失基础字段返回NEEDS_DATA和全部可读原因而不查询方案")
    void shouldReturnReadableReasonsForMissingData()
    {
        HrSignBusinessEvent event = event("LABOR_CONTRACT", "SOCIAL_INSURED", "P3");
        HrEmployeeSigningSnapshot snapshot = event.getAfterSnapshot();
        snapshot.setPhone(null);
        snapshot.setIdType(null);
        snapshot.setIdNumber(null);
        snapshot.setCurrentAddress(null);
        snapshot.setShopDeptId(null);
        snapshot.setDeptId(null);
        snapshot.setPostId(null);
        snapshot.setPostCode(null);
        snapshot.setPostName(null);
        snapshot.setJobGradeCode(null);
        snapshot.setLegalEntityId(null);
        snapshot.setLegalEntityCode(null);
        snapshot.setLegalEntityName(null);
        snapshot.setContractTypeCode(null);
        snapshot.setContractTermCode(null);
        snapshot.setSocialTypeCode(null);
        snapshot.setEntryDate(null);
        snapshot.setContractStartDate(null);
        snapshot.setContractEndDate(null);
        snapshot.setSalaryVersion(null);
        snapshot.setBaseSalary(null);

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains(
                "MISSING_PHONE", "MISSING_IDENTITY", "MISSING_CURRENT_ADDRESS",
                "MISSING_ORGANIZATION", "MISSING_POST", "MISSING_JOB_GRADE",
                "MISSING_CONTRACT_TYPE",
                "MISSING_CONTRACT_TERM", "MISSING_SOCIAL_TYPE", "MISSING_ENTRY_DATE",
                "MISSING_CONTRACT_DATES", "MISSING_SALARY");
        verify(versionMapper, never()).selectPublishedMatchingCandidates(
                org.mockito.ArgumentMatchers.anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("日期与薪资关系非法返回原因不抛基础设施异常")
    void shouldReturnReasonsForInvalidDatesAndSalary()
    {
        HrSignBusinessEvent event = event("LABOR_CONTRACT", "SOCIAL_INSURED", "P3");
        HrEmployeeSigningSnapshot snapshot = event.getAfterSnapshot();
        snapshot.setContractEndDate(snapshot.getContractStartDate().minusDays(1));
        snapshot.setProbationEndDate(snapshot.getProbationStartDate().minusDays(1));
        snapshot.setSalaryTotal(new BigDecimal("9999.00"));

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains(
                "INVALID_CONTRACT_DATES", "INVALID_PROBATION_DATES", "INVALID_SALARY_TOTAL");
    }

    @Test
    @DisplayName("合同开始和结束同日也不是有效合同期间")
    void shouldRejectSameDayContractPeriod()
    {
        HrSignBusinessEvent event = event("LABOR_CONTRACT", "SOCIAL_INSURED", "P3");
        HrEmployeeSigningSnapshot snapshot = event.getAfterSnapshot();
        snapshot.setContractEndDate(snapshot.getContractStartDate());
        snapshot.setProbationEndDate(snapshot.getContractStartDate());

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains("INVALID_CONTRACT_DATES");
    }

    @ParameterizedTest
    @CsvSource({
            "SERVICE_CONTRACT, SOCIAL_INSURED",
            "INTERNSHIP_AGREEMENT, SOCIAL_UNINSURED",
            "OUTSOURCING_CONTRACT, SOCIAL_UNINSURED",
            "LABOR_CONTRACT, DISPATCHED",
            "LABOR_CONTRACT, PENDING_CONFIRMATION"
    })
    @DisplayName("不支持组合进入人工原因且绝不猜方案")
    void shouldRejectUnsupportedCombinations(String contractType, String socialType)
    {
        OaSignDraftDecision decision = rule.decide(event(contractType, socialType, "P3"));

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains("UNSUPPORTED_ONBOARD_COMBINATION");
        verify(versionMapper, never()).selectPublishedMatchingCandidates(
                org.mockito.ArgumentMatchers.anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("未知职级不猜区间")
    void shouldRejectUnknownJobGrade()
    {
        OaSignDraftDecision decision = rule.decide(
                event("LABOR_CONTRACT", "SOCIAL_INSURED", "MANAGER"));

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains("UNKNOWN_JOB_GRADE");
    }

    @Test
    @DisplayName("真实方案候选0和N分别返回PLAN_NOT_FOUND与PLAN_CONFLICT")
    void shouldHandleZeroAndMultiplePlanCandidates()
    {
        HrSignBusinessEvent event = event("LABOR_CONTRACT", "SOCIAL_INSURED", "P3");
        when(versionMapper.selectPublishedMatchingCandidates("ONBOARD", 1171L, 301L))
                .thenReturn(List.of());
        assertThat(rule.decide(event).getReasonCodes()).contains("PLAN_NOT_FOUND");

        when(versionMapper.selectPublishedMatchingCandidates("ONBOARD", 1171L, 301L))
                .thenReturn(List.of(
                        version("A1", "LABOR_CONTRACT", "SOCIAL_INSURED", "2-4",
                                "PUBLISHED", "ENABLED"),
                        version("A1", "LABOR_CONTRACT", "SOCIAL_INSURED", "2-4",
                                "PUBLISHED", "ENABLED")));
        OaSignDraftDecision conflict = rule.decide(event);
        assertThat(conflict.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(conflict.getReasonCodes()).contains("PLAN_CONFLICT");
    }

    @Test
    @DisplayName("只使用PUBLISHED ENABLED且ruleJson大小写空白归一化")
    void shouldOnlyUsePublishedEnabledAndNormalizeRuleJson()
    {
        OaSignPlanVersion draft = version("A1", "LABOR_CONTRACT", "SOCIAL_INSURED", "2-4",
                "DRAFT", "ENABLED");
        OaSignPlanVersion disabled = version("A1", "LABOR_CONTRACT", "SOCIAL_INSURED", "2-4",
                "PUBLISHED", "DISABLED");
        OaSignPlanVersion published = version(" a1 ", " labor_contract ",
                " social_insured ", " 2-4 ", "PUBLISHED", "ENABLED");
        published.setVersionId(909L);
        when(versionMapper.selectPublishedMatchingCandidates("ONBOARD", 1171L, 301L))
                .thenReturn(List.of(draft, disabled, published));

        OaSignDraftDecision decision = rule.decide(
                event("LABOR_CONTRACT", "SOCIAL_INSURED", "P3"));

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getPlanVersionId()).isEqualTo(909L);
    }

    @Test
    @DisplayName("候选查询按复合索引字段限定不可变发布版本且不按方案名猜")
    void shouldQueryOnlyImmutablePublishedCandidatesWithIndexedScope() throws Exception
    {
        String xml = Files.readString(repoFile(
                "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPlanVersionMapper.xml"),
                StandardCharsets.UTF_8);
        assertThat(xml).contains(
                "selectPublishedMatchingCandidates", "scenario = #{scenario}",
                "shop_dept_id = 0",
                "publish_status = 'PUBLISHED'", "matching_status = 'ENABLED'")
                .doesNotContain("legal_entity_id = #{legalEntityId}",
                        "plan_name like", "plan_name = #{routeCode}");
    }

    private HrSignBusinessEvent event(String contractType, String socialType, String jobGrade)
    {
        HrEmployeeSigningSnapshot snapshot = new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(201L);
        snapshot.setEmployeeNo("E0201");
        snapshot.setEmployeeName("张三");
        snapshot.setPhone("13800000000");
        snapshot.setIdType("CN_ID_CARD");
        snapshot.setIdNumber("310101199001011234");
        snapshot.setCurrentAddress("上海市徐汇区");
        snapshot.setEmployeeStatus("试用");
        snapshot.setEmployeeCategory("FULL_TIME");
        snapshot.setShopDeptId(1171L);
        snapshot.setShopDeptName("徐汇门店");
        snapshot.setDeptId(1171L);
        snapshot.setDeptName("徐汇门店");
        snapshot.setLegalEntityId(301L);
        snapshot.setLegalEntityCode("LE-SH");
        snapshot.setLegalEntityName("上海示例有限公司");
        snapshot.setPostId(401L);
        snapshot.setPostCode("SALES");
        snapshot.setPostName("销售顾问");
        snapshot.setJobGradeCode(jobGrade);
        snapshot.setJobGradeName(jobGrade);
        snapshot.setWorkLocation("上海");
        snapshot.setContractTypeCode(contractType);
        snapshot.setContractTermCode("FIXED_TERM");
        snapshot.setSocialTypeCode(socialType);
        snapshot.setEntryDate(LocalDate.of(2026, 7, 15));
        snapshot.setContractStartDate(LocalDate.of(2026, 7, 15));
        snapshot.setContractEndDate(LocalDate.of(2029, 7, 14));
        snapshot.setProbationStartDate(LocalDate.of(2026, 7, 15));
        snapshot.setProbationEndDate(LocalDate.of(2026, 10, 14));
        snapshot.setBaseSalary(new BigDecimal("5000.00"));
        snapshot.setPostSalary(new BigDecimal("2000.00"));
        snapshot.setFieldAllowance(new BigDecimal("300.00"));
        snapshot.setPerformanceSalary(new BigDecimal("700.00"));
        snapshot.setSalaryTotal(new BigDecimal("8000.00"));
        snapshot.setSalaryVersion("2026-V1");

        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId("event-501");
        event.setScenario("ONBOARD");
        event.setEmployeeId(201L);
        event.setSourceType("HR_LIFECYCLE_ACTION");
        event.setSourceBusinessId("501");
        event.setSourceEventVersion(1L);
        event.setAfterSnapshot(snapshot);
        return event;
    }

    private OaSignPlanVersion version(String routeCode, String contractType, String socialType,
            String band, String publishStatus, String matchingStatus)
    {
        String salaryRule = "LABOR_CONTRACT".equalsIgnoreCase(contractType.trim())
                ? ",\"salaryVersion\":\""
                        + ("SOCIAL_INSURED".equalsIgnoreCase(socialType.trim()) ? "B" : "A")
                        + "\"" : "";
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId((long) (Math.abs(routeCode.hashCode()) + 1));
        version.setPlanId(77L);
        version.setPlanName("名称不能参与路线匹配");
        version.setScenario("ONBOARD");
        version.setShopDeptId(0L);
        version.setLegalEntityId(301L);
        version.setLegalEntityName("上海示例有限公司");
        version.setRuleJson("{\"routeCode\":\"" + routeCode
                + "\",\"contractTypeCode\":\"" + contractType
                + "\",\"socialTypeCode\":\"" + socialType
                + "\",\"jobGradeBand\":\"" + band + "\"" + salaryRule + "}");
        version.setPublishStatus(publishStatus);
        version.setMatchingStatus(matchingStatus);
        return version;
    }

    private String gradeBand(String routeCode)
    {
        return switch (routeCode.substring(1))
        {
            case "1", "4" -> "2-4";
            case "2", "5" -> "5-6";
            case "3", "6" -> "7-8";
            default -> switch (routeCode)
            {
                case "B1" -> "2-4";
                case "B2" -> "5-6";
                case "B3" -> "7-8";
                default -> throw new IllegalArgumentException(routeCode);
            };
        };
    }

    private static Path repoFile(String relativePath)
    {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path path = base.resolve(relativePath);
        if (!Files.exists(path))
        {
            path = base.resolve("../..").resolve(relativePath).normalize();
        }
        return path;
    }
}
