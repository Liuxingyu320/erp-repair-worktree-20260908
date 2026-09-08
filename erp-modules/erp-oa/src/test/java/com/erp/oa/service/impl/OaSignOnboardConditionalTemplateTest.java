package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.OaSignTemplate;

@DisplayName("入职签约条件模板")
class OaSignOnboardConditionalTemplateTest
{
    @Test
    @DisplayName("2-10仅匹配合同开始时年16至18周岁且非在校员工")
    void shouldMatchMinorDeclarationOnlyForMinorNonStudent()
    {
        OaSignTemplate template = template(
                OaSignTemplateType.ONBOARD_MINOR_NONSTUDENT_DECLARATION, null);
        OaSignPackage signPackage = packageAt("330102200907181234", "2026-07-18");
        signPackage.setStudentStatusSnapshot("NON_STUDENT");

        assertThat(OaSignTemplateApplicabilityPolicy.shouldIncludeTemplate(template, signPackage))
                .isTrue();

        signPackage.setStudentStatusSnapshot("STUDENT");
        assertThat(OaSignTemplateApplicabilityPolicy.shouldIncludeTemplate(template, signPackage))
                .isFalse();

        signPackage = packageAt("330102200807181234", "2026-07-18");
        signPackage.setStudentStatusSnapshot("NON_STUDENT");
        assertThat(OaSignTemplateApplicabilityPolicy.shouldIncludeTemplate(template, signPackage))
                .isFalse();
    }

    @Test
    @DisplayName("2-9对劳动和劳务包都只按7级及以上匹配")
    void shouldMatchConfidentialTemplateByGradeOnly()
    {
        OaSignTemplate template = template(
                OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE, "7级及以上");
        OaSignPackage signPackage = packageAt("330102199001011234", "2026-07-18");
        signPackage.setEmploymentType("SERVICE_CONTRACT");
        signPackage.setPostLevelSnapshot("9");

        assertThat(OaSignTemplateApplicabilityPolicy.shouldIncludeTemplate(template, signPackage))
                .isTrue();

        signPackage.setEmploymentType("LABOR_CONTRACT");
        signPackage.setPostLevelSnapshot("6");
        assertThat(OaSignTemplateApplicabilityPolicy.shouldIncludeTemplate(template, signPackage))
                .isFalse();
    }

    @Test
    @DisplayName("已发布方案版本从冻结匹配条件恢复职级范围")
    void shouldReadPostLevelScopeFromFrozenMatchCondition()
    {
        assertThat(OaSignTemplateApplicabilityPolicy.matchConditionText(
                "{\"postLevelScope\":\"7级及以上\"}", "postLevelScope"))
                .isEqualTo("7级及以上");
        assertThat(OaSignTemplateApplicabilityPolicy.matchConditionText(
                "not-json", "postLevelScope"))
                .isNull();
    }

    @Test
    @DisplayName("冻结方案模板恢复文件、交付和匹配条件")
    void shouldRestorePublishedTemplateSnapshot()
    {
        OaSignPlanVersionTemplate snapshot = new OaSignPlanVersionTemplate();
        snapshot.setTemplateId(21L);
        snapshot.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
        snapshot.setTemplateName("劳动合同");
        snapshot.setTemplateVersion("v3");
        snapshot.setSourceFileUrl("/immutable/labor-v3.docx");
        snapshot.setSourceFileHash("source-hash");
        snapshot.setEmployeeVisible("Y");
        snapshot.setReadConfirmationRequired("Y");
        snapshot.setEmployeeSignRequired("Y");
        snapshot.setCompanySealRequired("Y");
        snapshot.setMatchConditionJson(
                "{\"employmentType\":\"LABOR_CONTRACT\",\"postLevelScope\":\"7级及以上\"}");
        snapshot.setSortOrder(3);

        OaSignTemplate restored =
                OaSignTemplateApplicabilityPolicy.fromVersionSnapshot(snapshot);

        assertThat(restored.getTemplateId()).isEqualTo(21L);
        assertThat(restored.getFileUrl()).isEqualTo("/immutable/labor-v3.docx");
        assertThat(restored.getEmployeeVisible()).isEqualTo("Y");
        assertThat(restored.getEmploymentType()).isEqualTo("LABOR_CONTRACT");
        assertThat(restored.getPostLevelScope()).isEqualTo("7级及以上");
        assertThat(restored.getStatus()).isEqualTo("0");
    }

    private OaSignTemplate template(String type, String scope)
    {
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(type);
        template.setPostLevelScope(scope);
        return template;
    }

    private OaSignPackage packageAt(String idCard, String contractStartDate)
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setEmployeeIdCardSnapshot(idCard);
        signPackage.setContractStartDate(contractStartDate);
        return signPackage;
    }
}
