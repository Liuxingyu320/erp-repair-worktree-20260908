package com.erp.oa.service.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.vo.OaSignDraftDecision;
import com.erp.oa.mapper.OaSignPlanVersionMapper;

@DisplayName("转正签约场景规则")
class RegularizeSignScenarioRuleTest
{
    private OaSignPlanVersionMapper versionMapper;
    private ObjectMapper objectMapper;
    private RegularizeSignScenarioRule rule;

    @BeforeEach
    void setUp()
    {
        versionMapper = mock(OaSignPlanVersionMapper.class);
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        rule = new RegularizeSignScenarioRule(versionMapper, objectMapper);
    }

    @Test
    @DisplayName("注册独立REGULARIZE只读规则组件")
    void shouldRegisterRegularizeScenarioRule() throws Exception
    {
        Path source = repoFile(
                "erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/RegularizeSignScenarioRule.java");
        assertThat(source).exists();
        assertThat(Files.readString(source, StandardCharsets.UTF_8)).contains(
                "class RegularizeSignScenarioRule implements OaSignScenarioRule",
                "private static final String SCENARIO = \"REGULARIZE\"",
                "@Component");
    }

    @Test
    @DisplayName("typed action来源生成固定去重键且拒绝伪造action引用")
    void shouldBuildDedupeKeyFromTypedSourceAction()
    {
        HrSignBusinessEvent valid = validEvent();
        assertThat(rule.supports(" regularize ")).isTrue();
        assertThat(rule.dedupeKey(valid)).isEqualTo("REGULARIZE:201:701:1");

        valid.getAttributes().put("sourceActionId", 702L);
        assertThatThrownBy(() -> rule.dedupeKey(valid))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("SOURCE_ACTION_ID_MISMATCH");
    }

    @Test
    @DisplayName("人工转正事件必须携带发生时间和正数操作人")
    void shouldRequireOccurredTimeAndPositiveHumanOperator()
    {
        HrSignBusinessEvent missingTime = validEvent();
        missingTime.setOccurredTime(null);
        assertThat(rule.decide(missingTime).getReasonCodes())
                .contains("MISSING_OCCURRED_TIME");
        assertThatThrownBy(() -> rule.dedupeKey(missingTime))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("MISSING_OCCURRED_TIME");

        HrSignBusinessEvent missingOperator = validEvent();
        missingOperator.setOperatorUserId(null);
        assertThat(rule.decide(missingOperator).getReasonCodes())
                .contains("INVALID_OPERATOR_USER_ID");

        HrSignBusinessEvent zeroOperator = validEvent();
        zeroOperator.setOperatorUserId(0L);
        assertThat(rule.decide(zeroOperator).getReasonCodes())
                .contains("INVALID_OPERATOR_USER_ID");
        assertThatThrownBy(() -> rule.dedupeKey(zeroOperator))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("INVALID_OPERATOR_USER_ID");
    }

    @Test
    @DisplayName("事件头损坏早退时可观察薪资变化仍保留复核风险")
    void shouldKeepObservableSalaryRiskWhenEnvelopeValidationFails()
    {
        HrSignBusinessEvent event = validEvent();
        event.setEventId(null);
        event.getAfterSnapshot().setSalaryVersion("2026-V2");

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getRiskLevel()).isEqualTo("REVIEW_REQUIRED");
        assertThat(decision.getReasonCodes())
                .contains("MISSING_EVENT_ID", "SALARY_CHANGED");
        verifyNoInteractions(versionMapper);
    }

    @Test
    @DisplayName("薪资字段本身损坏早退时仍以null安全比较保留复核风险")
    void shouldKeepObservableSalaryRiskWhenSnapshotValidationFails()
    {
        HrSignBusinessEvent event = validEvent();
        event.getAfterSnapshot().setBaseSalary(null);

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getRiskLevel()).isEqualTo("REVIEW_REQUIRED");
        assertThat(decision.getReasonCodes())
                .contains("INVALID_AFTER_SALARY", "SALARY_CHANGED");
        verifyNoInteractions(versionMapper);
    }

    @Test
    @DisplayName("来源action数值拒绝小数指数和Long溢出而不截断")
    void shouldRejectFractionalOrOverflowingSourceActionNumbers()
    {
        HrSignBusinessEvent fractional = validEvent();
        fractional.getAttributes().put("sourceActionId", new BigDecimal("701.5"));
        assertThatThrownBy(() -> rule.dedupeKey(fractional))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("SOURCE_ACTION_ID_MISMATCH");

        HrSignBusinessEvent overflow = validEvent();
        overflow.getAttributes().put("sourceActionVersion",
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        assertThatThrownBy(() -> rule.dedupeKey(overflow))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("SOURCE_ACTION_VERSION_MISMATCH");

        HrSignBusinessEvent exponent = validEvent();
        exponent.getAttributes().put("sourceActionId", "7.01e2");
        assertThatThrownBy(() -> rule.dedupeKey(exponent))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("SOURCE_ACTION_ID_MISMATCH");
    }

    @Test
    @DisplayName("唯一匹配方案且存在员工确认模板时创建完整可读草稿")
    void shouldCreateReadableDraftFromUniquePublishedPlan()
    {
        OaSignPlanVersion plan = version("{}", "PUBLISHED", "ENABLED", 901L);
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(plan));
        when(versionMapper.selectTemplatesByVersionId(901L))
                .thenReturn(List.of(template(901L, "Y", "REGULARIZE_CONFIRMATION")));

        OaSignDraftDecision decision = rule.decide(validEvent());

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getPlanVersionId()).isEqualTo(901L);
        assertThat(decision.getRiskLevel()).isEqualTo("LOW");
        assertThat(decision.getReasonCodes()).doesNotContain("SALARY_CHANGED");
        OaSignPackage draft = decision.getDraftPackage();
        assertThat(draft.getScenario()).isEqualTo("REGULARIZE");
        assertThat(draft.getPlanVersionId()).isEqualTo(901L);
        assertThat(draft.getSourcePlanId()).isEqualTo(77L);
        assertThat(draft.getStatus()).isEqualTo(OaSignPackageStatus.DRAFT);
        assertThat(draft.getEmployeeId()).isEqualTo(201L);
        assertThat(draft.getPostNameSnapshot()).isEqualTo("销售组长");
        assertThat(draft.getPostLevelSnapshot()).isEqualTo("P4");
        assertThat(draft.getActualRegularizationDate()).isEqualTo("2026-07-10");
        assertThat(draft.getSalaryTotal()).isEqualByComparingTo("8000.00");
        assertThat(draft.getRemark()).contains(
                "状态:试用→正式", "实际转正日:未填写→2026-07-10",
                "岗位:销售顾问", "销售组长", "职级:P3→P4",
                "基本工资:5000.00→5000.00", "岗位工资:2000.00→2000.00",
                "外勤补贴:300.00→300.00", "绩效工资:700.00→700.00",
                "薪资合计:8000.00→8000.00", "薪资版本:2026-V1→2026-V1",
                "组织:徐汇门店/销售部→徐汇门店/销售部");
        assertThat(draft.getRemark()).hasSizeLessThanOrEqualTo(500);
    }

    @Test
    @DisplayName("任一数值薪资或薪资版本变化均显式标记REVIEW_REQUIRED")
    void shouldRequireReviewForAnySalaryChange()
    {
        HrSignBusinessEvent event = validEvent();
        event.getAfterSnapshot().setBaseSalary(new BigDecimal("6000.00"));
        event.getAfterSnapshot().setSalaryTotal(new BigDecimal("9000.00"));
        event.getAfterSnapshot().setSalaryVersion("2026-V2");
        OaSignPlanVersion plan = version(
                "{\"salaryChanged\":true}", "PUBLISHED", "ENABLED", 901L);
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(plan));
        when(versionMapper.selectTemplatesByVersionId(901L))
                .thenReturn(List.of(template(901L, "Y", "REGULARIZE_SALARY_CONFIRM")));

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getRiskLevel()).isEqualTo("REVIEW_REQUIRED");
        assertThat(decision.getReasonCodes()).contains("SALARY_CHANGED");
        assertThat(decision.getDraftPackage().getRemark()).contains(
                "基本工资:5000.00→6000.00", "薪资合计:8000.00→9000.00",
                "薪资版本:2026-V1→2026-V2");
    }

    @Test
    @DisplayName("薪资变化即使方案缺失也保留REVIEW_REQUIRED风险")
    void shouldKeepSalaryReviewRiskWhenTrustedDecisionNeedsData()
    {
        HrSignBusinessEvent event = validEvent();
        event.getAfterSnapshot().setBaseSalary(new BigDecimal("6000.00"));
        event.getAfterSnapshot().setSalaryTotal(new BigDecimal("9000.00"));
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of());

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getRiskLevel()).isEqualTo("REVIEW_REQUIRED");
        assertThat(decision.getReasonCodes()).contains("PLAN_NOT_FOUND", "SALARY_CHANGED");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unrelatedSnapshotMutations")
    @DisplayName("除状态日期岗位职级薪资外的快照变化一律NEEDS_DATA")
    void shouldRejectEveryUnrelatedSnapshotMutation(String label,
            Consumer<HrEmployeeSigningSnapshot> mutation)
    {
        HrSignBusinessEvent event = validEvent();
        mutation.accept(event.getAfterSnapshot());

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).anyMatch(code ->
                code.contains("SNAPSHOT_CHANGED") || code.contains("ORGANIZATION_CHANGED"));
    }

    @Test
    @DisplayName("状态转移和实际转正日期必须符合转正业务语义")
    void shouldValidateStatusTransitionAndEffectiveDate()
    {
        HrSignBusinessEvent event = validEvent();
        event.getBeforeSnapshot().setEmployeeStatus("正式");
        assertThat(rule.decide(event).getReasonCodes()).contains("INVALID_STATUS_TRANSITION");

        event = validEvent();
        event.getAfterSnapshot().setActualRegularizationDate(LocalDate.of(2026, 4, 30));
        assertThat(rule.decide(event).getReasonCodes())
                .contains("INVALID_REGULARIZATION_DATE");
    }

    @Test
    @DisplayName("实际转正日可等于事件上海业务日但不得晚于事件业务日")
    void shouldRejectRegularizationDateAfterEventShanghaiBusinessDay()
    {
        HrSignBusinessEvent sameDay = validEvent();
        sameDay.setOccurredTime(Date.from(Instant.parse("2026-07-11T16:00:00Z")));
        sameDay.getAfterSnapshot().setActualRegularizationDate(LocalDate.of(2026, 7, 12));
        OaSignPlanVersion plan = version("{}", "PUBLISHED", "ENABLED", 901L);
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(plan));
        when(versionMapper.selectTemplatesByVersionId(901L))
                .thenReturn(List.of(template(901L, "Y", "REGULARIZE_CONFIRMATION")));
        assertThat(rule.decide(sameDay).getAction())
                .isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);

        HrSignBusinessEvent nextDay = validEvent();
        nextDay.setOccurredTime(Date.from(Instant.parse("2026-07-11T16:00:00Z")));
        nextDay.getAfterSnapshot().setActualRegularizationDate(LocalDate.of(2026, 7, 13));
        assertThat(rule.decide(nextDay).getReasonCodes())
                .contains("INVALID_REGULARIZATION_DATE");
    }

    @Test
    @DisplayName("试用期必须完整落在合同期间内否则不生成草稿")
    void shouldRejectProbationDatesOutsideContractInBothSnapshots()
    {
        HrSignBusinessEvent event = validEvent();
        LocalDate corruptEnd = LocalDate.of(2030, 1, 1);
        event.getBeforeSnapshot().setProbationEndDate(corruptEnd);
        event.getAfterSnapshot().setProbationEndDate(corruptEnd);

        OaSignDraftDecision decision = rule.decide(event);

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes())
                .contains("INVALID_BEFORE_DATES", "INVALID_AFTER_DATES");
    }

    @Test
    @DisplayName("方案严格按场景门店法律主体及显式变化谓词0/1/N匹配")
    void shouldMatchExactlyOnePlanWithExplicitChangePredicates()
    {
        OaSignPlanVersion notMatched = version(
                "{\"postChanged\":false}", "PUBLISHED", "ENABLED", 900L);
        OaSignPlanVersion matched = version(
                "{\"postChanged\":true,\"gradeChanged\":true,"
                        + "\"salaryChanged\":false,\"organizationChanged\":false}",
                "PUBLISHED", "ENABLED", 901L);
        matched.setPlanName("名称包含低风险也不得参与匹配");
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(notMatched, matched));
        when(versionMapper.selectTemplatesByVersionId(901L))
                .thenReturn(List.of(template(901L, "Y", "REGULARIZE_POST_DUTY")));

        OaSignDraftDecision decision = rule.decide(validEvent());

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getPlanVersionId()).isEqualTo(901L);
        verify(versionMapper).selectTemplatesByVersionId(901L);
    }

    @Test
    @DisplayName("发布兼容条件与转正后快照精确匹配并只选择唯一方案")
    void shouldMatchPublishedCompatibilityFieldsAgainstAfterSnapshot()
    {
        OaSignPlanVersion wrongPost = version(
                "{\"postName\":\"门店经理\",\"employmentType\":\"劳动合同\","
                        + "\"socialType\":\"有社保\",\"postLevel\":\"P4\","
                        + "\"salaryVersion\":\"2026-V1\"}",
                "PUBLISHED", "ENABLED", 900L);
        OaSignPlanVersion matched = version(
                "{\"postName\":\"销售组长\",\"employmentType\":\"劳动合同\","
                        + "\"socialType\":\"有社保\",\"postLevel\":\"p4\","
                        + "\"salaryVersion\":\"2026-v1\"}",
                "PUBLISHED", "ENABLED", 901L);
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(wrongPost, matched));
        when(versionMapper.selectTemplatesByVersionId(901L))
                .thenReturn(List.of(template(901L, "Y", "REGULARIZE_CONFIRMATION")));

        OaSignDraftDecision decision = rule.decide(validEvent());

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getPlanVersionId()).isEqualTo(901L);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("compatibilityMismatches")
    @DisplayName("任一发布兼容条件不符都不能匹配方案")
    void shouldRejectAnyCompatibilityFieldMismatch(String label, String ruleJson)
    {
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(version(ruleJson, "PUBLISHED", "ENABLED", 901L)));

        OaSignDraftDecision decision = rule.decide(validEvent());

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains("PLAN_NOT_FOUND");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "servicePersonType", "insuranceType" })
    @DisplayName("typed快照无法核验的历史兼容条件按损坏规则处理")
    void shouldRejectUnsupportedLegacyCompatibilityFields(String field)
    {
        String ruleJson = "{\"" + field + "\":\"任意值\"}";
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(version(ruleJson, "PUBLISHED", "ENABLED", 901L)));

        OaSignDraftDecision decision = rule.decide(validEvent());

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NEEDS_DATA);
        assertThat(decision.getReasonCodes()).contains("PLAN_RULE_INVALID");
    }

    @Test
    @DisplayName("零方案多方案和损坏规则都进入NEEDS_DATA而不猜planName")
    void shouldReturnNeedsDataForZeroManyOrInvalidPlans()
    {
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of());
        assertThat(rule.decide(validEvent()).getReasonCodes()).contains("PLAN_NOT_FOUND");

        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(
                        version("{}", "PUBLISHED", "ENABLED", 901L),
                        version("{}", "PUBLISHED", "ENABLED", 902L)));
        assertThat(rule.decide(validEvent()).getReasonCodes()).contains("PLAN_CONFLICT");

        OaSignPlanVersion invalid = version(
                "{\"salaryChanged\":\"yes\"}", "PUBLISHED", "ENABLED", 903L);
        invalid.setPlanName("转正确认书-薪资变化");
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(invalid));
        assertThat(rule.decide(validEvent()).getReasonCodes()).contains("PLAN_RULE_INVALID");

        OaSignPlanVersion misspelled = version(
                "{\"salaryChange\":true}", "PUBLISHED", "ENABLED", 904L);
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(misspelled));
        assertThat(rule.decide(validEvent()).getReasonCodes()).contains("PLAN_RULE_INVALID");

        OaSignPlanVersion compatible = version(
                "{\"postName\":\"销售组长\",\"employmentType\":\"LABOR_CONTRACT\","
                        + "\"socialType\":\"SOCIAL_INSURED\",\"servicePersonType\":null,"
                        + "\"insuranceType\":null,\"postLevel\":\"P4\","
                        + "\"salaryVersion\":\"2026-V1\"}",
                "PUBLISHED", "ENABLED", 905L);
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(compatible));
        when(versionMapper.selectTemplatesByVersionId(905L))
                .thenReturn(List.of(template(905L, "Y", "REGULARIZE_CONFIRMATION")));
        assertThat(rule.decide(validEvent()).getAction())
                .isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
    }

    @Test
    @DisplayName("唯一方案的模板快照缺失空集或损坏均NEEDS_DATA而非NO_ACTION")
    void shouldTreatMissingEmptyOrCorruptTemplatesAsNeedsData()
    {
        OaSignPlanVersion plan = version("{}", "PUBLISHED", "ENABLED", 901L);
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(plan));

        when(versionMapper.selectTemplatesByVersionId(901L)).thenReturn(null);
        assertThat(rule.decide(validEvent()).getReasonCodes())
                .contains("PLAN_TEMPLATE_SNAPSHOT_MISSING");
        when(versionMapper.selectTemplatesByVersionId(901L)).thenReturn(List.of());
        assertThat(rule.decide(validEvent()).getReasonCodes())
                .contains("PLAN_TEMPLATE_SNAPSHOT_MISSING");

        OaSignPlanVersionTemplate corrupt = template(
                901L, "N", "REGULARIZE_CONFIRMATION");
        corrupt.setSourceFileHash(null);
        when(versionMapper.selectTemplatesByVersionId(901L)).thenReturn(List.of(corrupt));
        assertThat(rule.decide(validEvent()).getReasonCodes())
                .contains("PLAN_TEMPLATE_INVALID");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTemplatePlacements")
    @DisplayName("损坏的员工签名或企业章定位不能当成已发布模板")
    void shouldRejectInvalidTemplatePlacementSnapshots(String label,
            String signRequired, String signaturePosition, String companySealPosition)
    {
        OaSignPlanVersion plan = version("{}", "PUBLISHED", "ENABLED", 901L);
        OaSignPlanVersionTemplate invalid = template(
                901L, signRequired, "REGULARIZE_CONFIRMATION");
        invalid.setSignaturePositionJson(signaturePosition);
        invalid.setCompanySealPositionJson(companySealPosition);
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(plan));
        when(versionMapper.selectTemplatesByVersionId(901L)).thenReturn(List.of(invalid));

        assertThat(rule.decide(validEvent()).getReasonCodes())
                .contains("PLAN_TEMPLATE_INVALID");
    }

    @Test
    @DisplayName("模板全N才显式NO_ACTION且保留可读原因")
    void shouldReturnNoActionOnlyWhenAllValidTemplatesNeedNoEmployeeConfirmation()
    {
        OaSignPlanVersion plan = version("{}", "PUBLISHED", "ENABLED", 901L);
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(plan));
        OaSignPlanVersionTemplate sealedArchive = template(
                901L, "N", "REGULARIZE_CONFIRMATION");
        sealedArchive.setCompanySealPositionJson(placedPosition());
        when(versionMapper.selectTemplatesByVersionId(901L)).thenReturn(List.of(
                sealedArchive, template(901L, "N", "REGULARIZE_POST_DUTY")));

        OaSignDraftDecision decision = rule.decide(validEvent());

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.NO_ACTION);
        assertThat(decision.getPlanVersionId()).isEqualTo(901L);
        assertThat(decision.getReasonCodes()).contains(
                "NO_EMPLOYEE_CONFIRMATION_DOCUMENT", "方案未包含需要员工确认的文件");
        assertThat(decision.getDraftPackage()).isNull();
    }

    @Test
    @DisplayName("模板至少一个Y即创建草稿且规则本身不进入发送态")
    void shouldCreateDraftWhenAnyValidTemplateNeedsEmployeeConfirmation()
    {
        OaSignPlanVersion plan = version("{}", "PUBLISHED", "ENABLED", 901L);
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(plan));
        when(versionMapper.selectTemplatesByVersionId(901L)).thenReturn(List.of(
                template(901L, "N", "REGULARIZE_CONFIRMATION"),
                template(901L, "Y", "REGULARIZE_POST_DUTY")));

        OaSignDraftDecision decision = rule.decide(validEvent());

        assertThat(decision.getAction()).isEqualTo(OaSignDraftDecision.Action.CREATE_DRAFT);
        assertThat(decision.getDraftPackage().getStatus()).isEqualTo(OaSignPackageStatus.DRAFT);
        assertThat(decision.getDraftPackage().getConfirmStatus()).isNull();
    }

    @Test
    @DisplayName("未发布停用或候选维度不一致的版本不能参与匹配")
    void shouldRejectIneligibleCandidateVersions()
    {
        OaSignPlanVersion disabled = version("{}", "PUBLISHED", "DISABLED", 901L);
        OaSignPlanVersion wrongShop = version("{}", "PUBLISHED", "ENABLED", 902L);
        wrongShop.setShopDeptId(999L);
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(disabled, wrongShop));

        assertThat(rule.decide(validEvent()).getReasonCodes()).contains("PLAN_NOT_FOUND");
    }

    @Test
    @DisplayName("转正方案不能用入职或未知类型模板冒充")
    void shouldRejectTemplatesOutsideRegularizationCatalog()
    {
        OaSignPlanVersion plan = version("{}", "PUBLISHED", "ENABLED", 901L);
        when(versionMapper.selectPublishedMatchingCandidates("REGULARIZE", 1171L, 301L))
                .thenReturn(List.of(plan));
        when(versionMapper.selectTemplatesByVersionId(901L)).thenReturn(List.of(
                template(901L, "Y", "ONBOARD_POST_DUTY")));
        assertThat(rule.decide(validEvent()).getReasonCodes())
                .contains("PLAN_TEMPLATE_INVALID");

        when(versionMapper.selectTemplatesByVersionId(901L)).thenReturn(List.of(
                template(901L, "Y", "CUSTOM_REGULARIZE_DOCUMENT")));
        assertThat(rule.decide(validEvent()).getReasonCodes())
                .contains("PLAN_TEMPLATE_INVALID");
    }

    private HrSignBusinessEvent validEvent()
    {
        HrEmployeeSigningSnapshot before = beforeSnapshot();
        HrEmployeeSigningSnapshot after = objectMapper.convertValue(
                before, HrEmployeeSigningSnapshot.class);
        after.setEmployeeStatus("正式");
        after.setActualRegularizationDate(LocalDate.of(2026, 7, 10));
        after.setPostId(402L);
        after.setPostCode("SALES_LEAD");
        after.setPostName("销售组长");
        after.setJobGradeCode("P4");
        after.setJobGradeName("P4");

        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId("event-701");
        event.setScenario("REGULARIZE");
        event.setEmployeeId(201L);
        event.setSourceType("HR_LIFECYCLE_ACTION");
        event.setSourceBusinessId("701");
        event.setSourceEventVersion(1L);
        event.setOccurredTime(Date.from(Instant.parse("2026-07-12T02:03:04Z")));
        event.setOperatorUserId(88L);
        event.setBeforeSnapshot(before);
        event.setAfterSnapshot(after);
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("actionType", "REGULARIZATION_CONFIRMED");
        attributes.put("sourceActionId", 701L);
        attributes.put("sourceActionVersion", 1L);
        event.setAttributes(attributes);
        return event;
    }

    private HrEmployeeSigningSnapshot beforeSnapshot()
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
        snapshot.setDeptId(1172L);
        snapshot.setDeptName("销售部");
        snapshot.setLegalEntityId(301L);
        snapshot.setLegalEntityCode("LE-SH");
        snapshot.setLegalEntityName("上海示例有限公司");
        snapshot.setPostId(401L);
        snapshot.setPostCode("SALES");
        snapshot.setPostName("销售顾问");
        snapshot.setJobGradeCode("P3");
        snapshot.setJobGradeName("P3");
        snapshot.setDirectSupervisorId(501L);
        snapshot.setDirectSupervisorName("主管甲");
        snapshot.setDepartmentSupervisorId(502L);
        snapshot.setDepartmentSupervisorName("经理乙");
        snapshot.setWorkLocation("上海");
        snapshot.setWorkCityLevel("一线");
        snapshot.setContractTypeCode("LABOR_CONTRACT");
        snapshot.setContractTermCode("FIXED_TERM");
        snapshot.setSocialTypeCode("SOCIAL_INSURED");
        snapshot.setRenewalCount(0);
        snapshot.setEntryDate(LocalDate.of(2026, 5, 1));
        snapshot.setContractStartDate(LocalDate.of(2026, 5, 1));
        snapshot.setContractEndDate(LocalDate.of(2029, 4, 30));
        snapshot.setProbationStartDate(LocalDate.of(2026, 5, 1));
        snapshot.setProbationEndDate(LocalDate.of(2026, 7, 31));
        snapshot.setBaseSalary(new BigDecimal("5000.00"));
        snapshot.setPostSalary(new BigDecimal("2000.00"));
        snapshot.setFieldAllowance(new BigDecimal("300.00"));
        snapshot.setPerformanceSalary(new BigDecimal("700.00"));
        snapshot.setSalaryTotal(new BigDecimal("8000.00"));
        snapshot.setSalaryVersion("2026-V1");
        return snapshot;
    }

    private OaSignPlanVersion version(String ruleJson, String publishStatus,
            String matchingStatus, Long versionId)
    {
        OaSignPlanVersion version = new OaSignPlanVersion();
        version.setVersionId(versionId);
        version.setPlanId(77L);
        version.setPlanName("方案名称不参与匹配");
        version.setScenario("REGULARIZE");
        version.setShopDeptId(0L);
        version.setLegalEntityId(301L);
        version.setLegalEntityName("上海示例有限公司");
        version.setRuleJson(ruleJson);
        version.setPublishStatus(publishStatus);
        version.setMatchingStatus(matchingStatus);
        return version;
    }

    private OaSignPlanVersionTemplate template(Long versionId, String signRequired,
            String type)
    {
        OaSignPlanVersionTemplate template = new OaSignPlanVersionTemplate();
        template.setId(Math.abs((long) type.hashCode()) + 1L);
        template.setPlanVersionId(versionId);
        template.setTemplateId(Math.abs((long) type.hashCode()) + 100L);
        template.setTemplateVersion("V1");
        template.setTemplateType(type);
        template.setTemplateName(type + "模板");
        template.setSourceFileUrl("/profile/regularize/" + type + ".docx");
        template.setSourceFileHash("a".repeat(64));
        template.setSortOrder(1);
        template.setEmployeeSignRequired(signRequired);
        if ("Y".equals(signRequired))
        {
            template.setSignaturePositionJson("{\"mode\":\"APPENDED_CONFIRMATION_PAGE\"}");
        }
        return template;
    }

    private static Stream<Arguments> invalidTemplatePlacements()
    {
        return Stream.of(
                Arguments.of("签名JSON损坏", "Y", "{", null),
                Arguments.of("签名定位缺高度", "Y",
                        "{\"mode\":\"PLACED\",\"pageNumber\":1,\"x\":0,\"y\":0,\"width\":80}", null),
                Arguments.of("签名mode不受支持", "Y", "{\"mode\":\"AUTO\"}", null),
                Arguments.of("企业章不得使用追加确认页", "N", null,
                        "{\"mode\":\"APPENDED_CONFIRMATION_PAGE\"}"),
                Arguments.of("企业章尺寸必须大于0", "N", null,
                        "{\"mode\":\"PLACED\",\"pageNumber\":1,\"x\":0,\"y\":0,"
                                + "\"width\":0,\"height\":40}"),
                Arguments.of("整版缺少任何签署策略", "N", null, null));
    }

    private static Stream<Arguments> compatibilityMismatches()
    {
        return Stream.of(
                Arguments.of("岗位名称", "{\"postName\":\"门店经理\"}"),
                Arguments.of("合同类型", "{\"employmentType\":\"劳务合同\"}"),
                Arguments.of("社保类型", "{\"socialType\":\"无社保\"}"),
                Arguments.of("职级", "{\"postLevel\":\"P9\"}"),
                Arguments.of("薪资版本", "{\"salaryVersion\":\"2026-V9\"}"));
    }

    private static String placedPosition()
    {
        return "{\"mode\":\"PLACED\",\"pageNumber\":1,\"x\":0,\"y\":0,"
                + "\"width\":80,\"height\":40}";
    }

    private static Stream<Arguments> unrelatedSnapshotMutations()
    {
        return Stream.of(
                Arguments.of("员工姓名", (Consumer<HrEmployeeSigningSnapshot>)
                        snapshot -> snapshot.setEmployeeName("李四")),
                Arguments.of("手机号", (Consumer<HrEmployeeSigningSnapshot>)
                        snapshot -> snapshot.setPhone("13900000000")),
                Arguments.of("门店", (Consumer<HrEmployeeSigningSnapshot>)
                        snapshot -> snapshot.setShopDeptId(999L)),
                Arguments.of("部门", (Consumer<HrEmployeeSigningSnapshot>)
                        snapshot -> snapshot.setDeptName("其他部门")),
                Arguments.of("上级", (Consumer<HrEmployeeSigningSnapshot>)
                        snapshot -> snapshot.setDirectSupervisorId(999L)),
                Arguments.of("地点", (Consumer<HrEmployeeSigningSnapshot>)
                        snapshot -> snapshot.setWorkLocation("北京")),
                Arguments.of("合同", (Consumer<HrEmployeeSigningSnapshot>)
                        snapshot -> snapshot.setContractTypeCode("SERVICE_CONTRACT")),
                Arguments.of("社保", (Consumer<HrEmployeeSigningSnapshot>)
                        snapshot -> snapshot.setSocialTypeCode("SOCIAL_UNINSURED")),
                Arguments.of("入职日期", (Consumer<HrEmployeeSigningSnapshot>)
                        snapshot -> snapshot.setEntryDate(LocalDate.of(2026, 4, 1))),
                Arguments.of("合同日期", (Consumer<HrEmployeeSigningSnapshot>)
                        snapshot -> snapshot.setContractEndDate(LocalDate.of(2030, 4, 30))),
                Arguments.of("试用日期", (Consumer<HrEmployeeSigningSnapshot>)
                        snapshot -> snapshot.setProbationEndDate(LocalDate.of(2026, 8, 1))),
                Arguments.of("离职日期", (Consumer<HrEmployeeSigningSnapshot>)
                        snapshot -> snapshot.setLeaveDate(LocalDate.of(2026, 8, 1))));
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
