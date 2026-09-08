package com.erp.oa.constant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

@DisplayName("员工签约模板类型")
class OaSignTemplateTypeTest
{
    @Test
    @DisplayName("只允许系统内置的入离调转签署文件类型")
    void shouldExposeOnlyKnownSigningTemplateTypes()
    {
        assertThat(OaSignTemplateType.options())
                .extracting(OaSignTemplateType.Option::getCode)
                .contains(
                        "ONBOARD_LABOR_CONTRACT",
                        "ONBOARD_SERVICE_CONTRACT",
                        "ONBOARD_SERVICE_RECEIPT",
                        "ONBOARD_COMMITMENT",
                        "ONBOARD_POST_DUTY",
                        "ONBOARD_HANDBOOK_RECEIPT",
                        "ONBOARD_HANDBOOK",
                        "ONBOARD_SALARY_CONFIRM",
                        "ONBOARD_APPLICATION_FORM",
                        "ONBOARD_BACKGROUND_CHECK",
                        "ONBOARD_ARCHIVE_CATALOG",
                        "ONBOARD_OFFER_NOTICE",
                        "ONBOARD_CONFIDENTIAL_NONCOMPETE",
                        "REGULARIZE_CONFIRMATION",
                        "REGULARIZE_POST_DUTY",
                        "REGULARIZE_SALARY_CONFIRM",
                        "OFFBOARD_LEAVE_CERTIFICATE");
    }

    @Test
    @DisplayName("转正三类材料必须属于regularize且默认要员工确认")
    void shouldExposeRegularizationTemplateTypes()
    {
        assertThat(OaSignTemplateType.options())
                .filteredOn(option -> "regularize".equals(option.getScenario()))
                .extracting(OaSignTemplateType.Option::getCode)
                .containsExactly(
                        "REGULARIZE_CONFIRMATION",
                        "REGULARIZE_POST_DUTY",
                        "REGULARIZE_SALARY_CONFIRM");

        assertThat(OaSignTemplateType.options())
                .filteredOn(option -> "regularize".equals(option.getScenario()))
                .allSatisfy(option -> {
                    assertThat(option.getFileFormat()).isEqualTo("docx");
                    assertThat(option.isEmployeeSignRequired()).isTrue();
                    assertThat(option.getRequiredPlaceholders())
                            .contains("actualRegularizationDate", "signDate");
                });
    }

    @Test
    @DisplayName("转正确认岗位职责和薪资材料覆盖各自必需快照")
    void shouldRequireRegularizationDocumentPlaceholders()
    {
        assertThat(OaSignTemplateType.requiredPlaceholders("REGULARIZE_CONFIRMATION"))
                .containsExactly(
                        "employeeName", "employeeIdCard", "actualRegularizationDate",
                        "postName", "postLevel", "signDate");
        assertThat(OaSignTemplateType.requiredPlaceholders("REGULARIZE_POST_DUTY"))
                .containsExactly(
                        "employeeName", "postName", "postLevel",
                        "actualRegularizationDate", "signDate");
        assertThat(OaSignTemplateType.requiredPlaceholders("REGULARIZE_SALARY_CONFIRM"))
                .containsExactly(
                        "employeeName", "employeeIdCard", "actualRegularizationDate",
                        "baseSalary", "postSalary", "fieldAllowance", "performanceSalary",
                        "salaryTotal", "salaryVersion", "signDate");
    }

    @Test
    @DisplayName("续签劳动劳务和薪酬材料使用独立renewal类型")
    void shouldExposeRenewalTemplateTypes()
    {
        assertThat(OaSignTemplateType.options())
                .filteredOn(option -> "renewal".equals(option.getScenario()))
                .extracting(OaSignTemplateType.Option::getCode)
                .containsExactly(
                        OaSignTemplateType.RENEWAL_LABOR_CONTRACT,
                        OaSignTemplateType.RENEWAL_SERVICE_CONTRACT,
                        OaSignTemplateType.RENEWAL_SALARY_CONFIRM);

        assertThat(OaSignTemplateType.options())
                .filteredOn(option -> "renewal".equals(option.getScenario()))
                .allSatisfy(option -> {
                    assertThat(option.getFileFormat()).isEqualTo("docx");
                    assertThat(option.isEmployeeVisible()).isTrue();
                    assertThat(option.isReadConfirmationRequired()).isTrue();
                    assertThat(option.isEmployeeSignRequired()).isTrue();
                });
    }

    @Test
    @DisplayName("续签材料只引用当前签约包能够冻结和渲染的字段")
    void shouldRequireRenewalDocumentPlaceholders()
    {
        assertThat(OaSignTemplateType.requiredPlaceholders(
                OaSignTemplateType.RENEWAL_LABOR_CONTRACT))
                .containsExactly(
                        "employeeName", "employeeIdCard", "employeePhone", "employeeAddress",
                        "companyName", "previousContractEndDate", "previousEmploymentType",
                        "previousRenewalCount", "renewalCount", "contractStartDate", "contractEndDate",
                        "postName", "baseSalary", "signDate");
        assertThat(OaSignTemplateType.requiredPlaceholders(
                OaSignTemplateType.RENEWAL_SERVICE_CONTRACT))
                .containsExactly(
                        "employeeName", "employeeIdCard", "employeePhone", "employeeAddress",
                        "companyName", "previousContractEndDate", "previousEmploymentType",
                        "previousRenewalCount", "renewalCount", "servicePersonType",
                        "contractStartDate", "contractEndDate", "postName", "baseSalary", "signDate");
        assertThat(OaSignTemplateType.requiredPlaceholders(
                OaSignTemplateType.RENEWAL_SALARY_CONFIRM))
                .containsExactly(
                        "employeeName", "employeeIdCard", "companyName",
                        "previousRenewalCount", "renewalCount", "contractStartDate", "contractEndDate",
                        "baseSalary", "postSalary", "fieldAllowance", "performanceSalary",
                        "salaryTotal", "salaryVersion", "signDate");
    }

    @Test
    @DisplayName("劳动合同类型必须校验员工和合同期限占位符")
    void shouldRequireLaborContractPlaceholders()
    {
        assertThat(OaSignTemplateType.requiredPlaceholders("ONBOARD_LABOR_CONTRACT"))
                .containsExactly(
                        "employeeName",
                        "employeeIdCard",
                        "employeePhone",
                        "employeeAddress",
                        "companyName",
                        "companyAddress",
                        "companyLegalRepresentative",
                        "contractTermSelection",
                        "contractTermFixedMark",
                        "contractTermOpenEndedMark",
                        "contractStartDate",
                        "contractEndDate",
                        "probationStartDate",
                        "probationEndDate",
                        "postName",
                        "baseSalary",
                        "signDate");
    }

    @Test
    @DisplayName("入离调转签署文件按实际文件内容校验最小占位符")
    void shouldRequireDocumentSpecificPlaceholders()
    {
        assertThat(OaSignTemplateType.requiredPlaceholders("ONBOARD_COMMITMENT"))
                .containsExactly("employeeName", "employeeIdCard", "signDate");

        assertThat(OaSignTemplateType.requiredPlaceholders("ONBOARD_POST_DUTY"))
                .containsExactly("employeeName", "postName", "postLevel", "signDate");

        assertThat(OaSignTemplateType.requiredPlaceholders("ONBOARD_HANDBOOK_RECEIPT"))
                .containsExactly("employeeName", "employeeIdCard", "signDate");

        assertThat(OaSignTemplateType.requiredPlaceholders("ONBOARD_SERVICE_RECEIPT"))
                .containsExactly(
                        "employeeName",
                        "employeeIdCard",
                        "servicePersonType",
                        "baseSalary",
                        "postSalary",
                        "fieldAllowance",
                        "performanceSalary",
                        "salaryTotal",
                        "insuranceType",
                        "signDate");
    }

    @Test
    @DisplayName("六类离职材料使用统一offboard场景和固定员工确认策略")
    void shouldExposeOffboardingTemplateTypes()
    {
        assertThat(OaSignTemplateType.options())
                .filteredOn(option -> "offboard".equals(option.getScenario()))
                .extracting(OaSignTemplateType.Option::getCode)
                .containsExactly(
                        "OFFBOARD_CONFIRMATION",
                        "OFFBOARD_HANDOVER",
                        "OFFBOARD_SETTLEMENT",
                        "OFFBOARD_CONFIDENTIALITY_NONCOMPETE",
                        "OFFBOARD_TERMINATION_NOTICE",
                        "OFFBOARD_LEAVE_CERTIFICATE");
        assertThat(OaSignTemplateType.options())
                .filteredOn(option -> "offboard".equals(option.getScenario()))
                .filteredOn(OaSignTemplateType.Option::isEmployeeSignRequired)
                .extracting(OaSignTemplateType.Option::getCode)
                .containsExactly(
                        "OFFBOARD_CONFIRMATION",
                        "OFFBOARD_HANDOVER",
                        "OFFBOARD_SETTLEMENT",
                        "OFFBOARD_CONFIDENTIALITY_NONCOMPETE");
    }

    @Test
    @DisplayName("离职材料占位符精确绑定冻结离职快照")
    void shouldRequireOffboardingDocumentPlaceholders()
    {
        assertThat(OaSignTemplateType.requiredPlaceholders("OFFBOARD_CONFIRMATION"))
                .containsExactly("employeeName", "employeeIdCard", "entryDate",
                        "leaveDate", "offboardingType", "leaveReason", "signDate");
        assertThat(OaSignTemplateType.requiredPlaceholders("OFFBOARD_HANDOVER"))
                .containsExactly("employeeName", "leaveDate", "postName",
                        "assetHandoverStatus", "signDate");
        assertThat(OaSignTemplateType.requiredPlaceholders("OFFBOARD_SETTLEMENT"))
                .containsExactly("employeeName", "employeeIdCard", "leaveDate",
                        "salarySettlementStatus", "compensationAmount",
                        "compensationNote", "signDate");
        assertThat(OaSignTemplateType.requiredPlaceholders(
                "OFFBOARD_CONFIDENTIALITY_NONCOMPETE"))
                .containsExactly("employeeName", "employeeIdCard", "leaveDate",
                        "nonCompeteDecision", "signDate");
        assertThat(OaSignTemplateType.requiredPlaceholders("OFFBOARD_TERMINATION_NOTICE"))
                .containsExactly("employeeName", "employeeIdCard", "entryDate",
                        "leaveDate", "offboardingType", "leaveReason", "companyName",
                        "signDate");
        assertThat(OaSignTemplateType.requiredPlaceholders("OFFBOARD_LEAVE_CERTIFICATE"))
                .containsExactly("employeeName", "employeeIdCard", "entryDate",
                        "leaveDate", "postName", "companyName", "signDate");
    }

    @Test
    @DisplayName("员工手册正文要求员工阅读确认但不要求签署或占位符")
    void shouldSupportHandbookReadonlyAttachment()
    {
        OaSignTemplateType.Option option = OaSignTemplateType.require("ONBOARD_HANDBOOK");

        assertThat(option.getLabel()).isEqualTo("员工手册");
        assertThat(option.isEmployeeVisible()).isTrue();
        assertThat(option.isReadConfirmationRequired()).isTrue();
        assertThat(option.isEmployeeSignRequired()).isFalse();
        assertThat(option.getRequiredPlaceholders()).isEmpty();
    }

    @Test
    @DisplayName("薪酬结构确认书必须校验薪资结构占位符")
    void shouldRequireSalaryPlaceholders()
    {
        assertThat(OaSignTemplateType.requiredPlaceholders("ONBOARD_SALARY_CONFIRM"))
                .containsExactly(
                        "employeeName",
                        "employeeIdCard",
                        "baseSalary",
                        "postSalary",
                        "fieldAllowance",
                        "performanceSalary",
                        "salaryTotal",
                        "signDate");
    }

    @Test
    @DisplayName("录用通知书和保密竞业协议应覆盖真实入职模板")
    void shouldSupportOfferAndConfidentialAgreementTemplates()
    {
        assertThat(OaSignTemplateType.requiredPlaceholders("ONBOARD_OFFER_NOTICE"))
                .containsExactly(
                        "employeeName",
                        "employeeIdCard",
                        "employeePhone",
                        "employeeAddress",
                        "employeeDeptName",
                        "postName",
                        "entryDate",
                        "probationStartDate",
                        "probationEndDate",
                        "baseSalary",
                        "postSalary",
                        "fieldAllowance",
                        "performanceSalary",
                        "salaryTotal",
                        "signDate");

        OaSignTemplateType.Option offer = OaSignTemplateType.require("ONBOARD_OFFER_NOTICE");
        assertThat(offer.isEmployeeVisible()).isTrue();
        assertThat(offer.isReadConfirmationRequired()).isTrue();
        assertThat(offer.isEmployeeSignRequired()).isTrue();

        assertThat(OaSignTemplateType.requiredPlaceholders("ONBOARD_CONFIDENTIAL_NONCOMPETE"))
                .containsExactly(
                        "employeeName",
                        "employeeIdCard",
                        "employeePhone",
                        "employeeAddress",
                        "postName",
                        "postLevel",
                        "contractStartDate",
                        "signDate");
    }

    @Test
    @DisplayName("应聘背调和档案目录固定为HR内部材料")
    void shouldKeepInternalOnboardingMaterialsAwayFromEmployees()
    {
        assertThat(OaSignTemplateType.options())
                .filteredOn(option -> option.getCode().equals("ONBOARD_APPLICATION_FORM")
                        || option.getCode().equals("ONBOARD_BACKGROUND_CHECK")
                        || option.getCode().equals("ONBOARD_ARCHIVE_CATALOG"))
                .allSatisfy(option -> {
                    assertThat(option.isEmployeeVisible()).isFalse();
                    assertThat(option.isReadConfirmationRequired()).isFalse();
                    assertThat(option.isEmployeeSignRequired()).isFalse();
                });
    }

    @Test
    @DisplayName("未知模板类型拒绝上传")
    void shouldRejectUnknownTemplateType()
    {
        assertThatThrownBy(() -> OaSignTemplateType.require("FREE_UPLOAD"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未知模板类型不能上传");
    }
}
