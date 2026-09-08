package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTemplate;

@DisplayName("签约包发送前检查")
class OaSignPackagePreflightValidatorTest
{
    @Test
    @DisplayName("exact-v7冻结要求完整且唯一的员工可见入职文档集合")
    void shouldFailClosedForIncompleteHiddenOrDuplicateExactV7DocumentSet()
    {
        OaSignPackage signPackage = baseOnboardingPackage();
        List<OaSignTemplate> complete = exactV7Templates(false);
        assertThatCode(() -> OaSignPackagePreflightValidator.validate(signPackage, complete))
                .doesNotThrowAnyException();
        assertThatCode(() -> OaSignPackagePreflightValidator.validate(
                signPackage, exactV7Templates(true))).doesNotThrowAnyException();

        List<OaSignTemplate> missingSalary = complete.stream()
                .filter(value -> !OaSignTemplateType.ONBOARD_SALARY_CONFIRM.equals(
                        value.getTemplateType())).toList();
        OaSignTemplate hiddenSalary = complete.stream()
                .filter(value -> OaSignTemplateType.ONBOARD_SALARY_CONFIRM.equals(
                        value.getTemplateType())).findFirst().orElseThrow();
        hiddenSalary.setEmployeeVisible("N");
        List<OaSignTemplate> duplicateSalary = new ArrayList<>(exactV7Templates(false));
        duplicateSalary.add(exactV7SalaryTemplate());
        List<OaSignTemplate> unreviewedCombination = new ArrayList<>(
                exactV7Templates(false));
        unreviewedCombination.add(template(
                OaSignTemplateType.ONBOARD_POST_DUTY, "岗位职责说明书", ""));

        for (List<OaSignTemplate> invalid : List.of(
                missingSalary, complete, duplicateSalary, unreviewedCombination))
        {
            assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(
                    signPackage, invalid))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("审定的员工可见文档集合");
        }
    }

    @Test
    @DisplayName("缺失字段应一次列出字段及受影响文件")
    void shouldReportMissingFieldsWithImpactedDocuments()
    {
        OaSignPackage signPackage = baseOnboardingPackage();
        signPackage.setEmployeeAddressSnapshot(null);
        OaSignTemplate offer = template(OaSignTemplateType.ONBOARD_OFFER_NOTICE,
                "录用通知书", "employeeName,employeeAddress,performanceSalary,signDate");

        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(signPackage, List.of(offer)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("联系住址（影响：录用通知书）")
                .hasMessageContaining("绩效工资（影响：录用通知书）");
    }

    @Test
    @DisplayName("劳动与劳务材料不能混在同一入职包")
    void shouldRejectMixedEmploymentContracts()
    {
        OaSignPackage signPackage = baseOnboardingPackage();

        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(signPackage, List.of(
                template(OaSignTemplateType.ONBOARD_LABOR_CONTRACT, "劳动合同", "employeeName"),
                template(OaSignTemplateType.ONBOARD_SERVICE_CONTRACT, "劳务合同", "employeeName"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能同时包含劳动合同和劳务合同材料");
    }

    @Test
    @DisplayName("模板类型大小写和空格不能绕过劳动劳务混包检查")
    void shouldNormalizeTemplateTypesBeforeCombinationValidation()
    {
        OaSignPackage signPackage = baseOnboardingPackage();

        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(signPackage, List.of(
                template(" onboard_labor_contract ", "劳动合同", "employeeName"),
                template("onboard_service_contract", "劳务合同", "employeeName"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能同时包含劳动合同和劳务合同材料");
    }

    @Test
    @DisplayName("劳务包不得混入劳动关系专用材料")
    void shouldRejectLaborOnlyDocumentsInServicePackage()
    {
        OaSignPackage signPackage = baseOnboardingPackage();
        signPackage.setEmploymentType("劳务合同");
        signPackage.setSocialType("SOCIAL_UNINSURED");

        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(signPackage, List.of(
                template(OaSignTemplateType.ONBOARD_SERVICE_CONTRACT, "劳务合同", "employeeName"),
                template(OaSignTemplateType.ONBOARD_SERVICE_RECEIPT, "劳务合同签收单", "employeeName"),
                template(OaSignTemplateType.ONBOARD_HANDBOOK, "员工手册", ""))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能发送劳动关系专用");
    }

    @Test
    @DisplayName("劳务包不得混入岗位职责等劳动关系专用材料")
    void shouldRejectOtherLaborOnlyDocumentsInServicePackage()
    {
        for (String templateType : List.of(
                OaSignTemplateType.ONBOARD_POST_DUTY,
                OaSignTemplateType.ONBOARD_SALARY_CONFIRM))
        {
            OaSignPackage signPackage = baseOnboardingPackage();
            signPackage.setEmploymentType("劳务合同");
            signPackage.setSocialType("无社保");

            assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(signPackage, List.of(
                    template(OaSignTemplateType.ONBOARD_SERVICE_CONTRACT, "劳务合同", "employeeName"),
                    template(OaSignTemplateType.ONBOARD_SERVICE_RECEIPT, "劳务合同签收单", "employeeName"),
                    template(templateType, "劳动关系专用材料", ""))))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("不能发送劳动关系专用");
        }
    }

    @Test
    @DisplayName("劳务包允许通用入职承诺书，7级及以上允许保密与竞业协议")
    void shouldAcceptCommitmentAndConfidentialAgreementInServicePackage()
    {
        OaSignPackage signPackage = baseOnboardingPackage();
        signPackage.setEmploymentType("劳务合同");
        signPackage.setSocialType("无社保");
        signPackage.setPostLevelSnapshot("7");

        assertThatCode(() -> OaSignPackagePreflightValidator.validate(signPackage, List.of(
                template(OaSignTemplateType.ONBOARD_SERVICE_CONTRACT, "劳务合同", "employeeName"),
                template(OaSignTemplateType.ONBOARD_SERVICE_RECEIPT, "劳务合同签收单", "employeeName"),
                template(OaSignTemplateType.ONBOARD_COMMITMENT, "入职承诺书", "employeeName"),
                template(OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE,
                        "保密与竞业限制协议", "employeeName"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("薪资明细完整时合计必须一致")
    void shouldRejectMismatchedSalaryTotal()
    {
        OaSignPackage signPackage = baseOnboardingPackage();
        signPackage.setBaseSalary(new BigDecimal("3000"));
        signPackage.setPostSalary(new BigDecimal("1000"));
        signPackage.setFieldAllowance(new BigDecimal("500"));
        signPackage.setPerformanceSalary(new BigDecimal("500"));
        signPackage.setSalaryTotal(new BigDecimal("6000"));

        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(signPackage,
                List.of(template(OaSignTemplateType.ONBOARD_COMMITMENT, "入职承诺书", "employeeName"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("薪资合计应等于");
    }

    @Test
    @DisplayName("资料、日期和薪资一致时允许生成")
    void shouldAcceptCompleteConsistentPackage()
    {
        OaSignPackage signPackage = baseOnboardingPackage();
        signPackage.setBaseSalary(new BigDecimal("3000"));
        signPackage.setPostSalary(new BigDecimal("1000"));
        signPackage.setFieldAllowance(new BigDecimal("500"));
        signPackage.setPerformanceSalary(new BigDecimal("500"));
        signPackage.setSalaryTotal(new BigDecimal("5000"));

        assertThatCode(() -> OaSignPackagePreflightValidator.validate(signPackage,
                List.of(template(OaSignTemplateType.ONBOARD_COMMITMENT, "入职承诺书",
                        "employeeName,employeeIdCard,signDate"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("附件勾选占位符由系统派生不要求HR补录")
    void shouldAcceptSystemDerivedAttachmentChecklistPlaceholders()
    {
        OaSignPackage signPackage = baseOnboardingPackage();
        OaSignTemplate labor = template(OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                "劳动合同",
                "employeeName,attachmentDormitoryMark,attachmentDutyMark,"
                        + "attachmentHandbookMark,attachmentSalaryMark,signDate");

        assertThatCode(() -> OaSignPackagePreflightValidator.validate(
                signPackage, List.of(labor))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("劳动合同必须冻结受支持的合同期限类型")
    void shouldRequireSupportedContractTermForLaborContract()
    {
        OaSignTemplate labor = template(OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                "劳动合同", "contractTermSelection,contractTermFixedMark,contractTermOpenEndedMark");
        OaSignPackage signPackage = baseOnboardingPackage();

        signPackage.setContractTermCodeSnapshot(null);
        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(
                signPackage, List.of(labor)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("合同期限类型")
                .hasMessageContaining("劳动合同");

        signPackage.setContractTermCodeSnapshot("UNSUPPORTED");
        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(
                signPackage, List.of(labor)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("仅支持固定期限或无固定期限");

        signPackage.setContractTermCodeSnapshot("OPEN_ENDED");
        assertThatCode(() -> OaSignPackagePreflightValidator.validate(
                signPackage, List.of(labor))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("入职发送前检查接受系统规范用工类型编码")
    void shouldAcceptCanonicalOnboardingEmploymentCode()
    {
        OaSignPackage signPackage = baseOnboardingPackage();
        signPackage.setEmploymentType("LABOR_CONTRACT");

        assertThatCode(() -> OaSignPackagePreflightValidator.validate(signPackage,
                List.of(template(OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        "劳动合同", "employeeName"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("入职发送前检查拒绝九宫格之外的社保组合")
    void shouldRejectUnsupportedOnboardingSocialCombination()
    {
        OaSignPackage unknownSocial = baseOnboardingPackage();
        unknownSocial.setSocialType("DISPATCHED");
        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(unknownSocial,
                List.of(template(OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        "劳动合同", "employeeName"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("社保类型暂不支持");

        OaSignPackage insuredService = baseOnboardingPackage();
        insuredService.setEmploymentType("SERVICE_CONTRACT");
        insuredService.setSocialType("SOCIAL_INSURED");
        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(insuredService,
                List.of(
                        template(OaSignTemplateType.ONBOARD_SERVICE_CONTRACT,
                                "劳务合同", "employeeName"),
                        template(OaSignTemplateType.ONBOARD_SERVICE_RECEIPT,
                                "劳务合同签收单", "employeeName"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("劳务合同仅支持无社保");
    }

    @Test
    @DisplayName("有社保必须匹配B版薪酬确认书且不允许手工切A版")
    void shouldForceInsuredPackageToSalaryConfirmationVersionB()
    {
        OaSignPackage signPackage = baseOnboardingPackage();
        signPackage.setSalaryVersion("A");
        OaSignTemplate salaryA = template(OaSignTemplateType.ONBOARD_SALARY_CONFIRM,
                "薪酬结构确认书（A版）", "employeeName");
        salaryA.setSalaryVersion("A");

        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(
                signPackage, List.of(salaryA)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前社保类型")
                .hasMessageContaining("B版")
                .hasMessageContaining("不允许手工切换");

        signPackage.setSalaryVersion("B");
        OaSignTemplate salaryB = template(OaSignTemplateType.ONBOARD_SALARY_CONFIRM,
                "薪酬结构确认书（B版）", "employeeName");
        salaryB.setSalaryVersion("B");
        assertThatCode(() -> OaSignPackagePreflightValidator.validate(
                signPackage, List.of(salaryB))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("人工选择的薪酬版本必须与实际模板版本一致")
    void shouldRequireSelectedSalaryVersionToMatchTemplateVersion()
    {
        OaSignPackage signPackage = baseOnboardingPackage();
        signPackage.setSocialType("无社保");
        signPackage.setSalaryVersion("B");
        OaSignTemplate salaryA = template(OaSignTemplateType.ONBOARD_SALARY_CONFIRM,
                "薪酬结构确认书（A版）", "employeeName");
        salaryA.setSalaryVersion("A");

        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(
                signPackage, List.of(salaryA)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前社保类型")
                .hasMessageContaining("A版")
                .hasMessageContaining("不允许手工切换");

        signPackage.setSalaryVersion("A");
        assertThatCode(() -> OaSignPackagePreflightValidator.validate(
                signPackage, List.of(salaryA))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("发送前拒绝与签约包场景不一致的模板")
    void shouldRejectCrossScenarioTemplateBeforeSend()
    {
        OaSignPackage signPackage = baseRenewalPackage();

        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(signPackage,
                List.of(template(OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        "入职劳动合同", "employeeName"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("续签签约包")
                .hasMessageContaining("续签场景模板");
    }

    @Test
    @DisplayName("续签包必须且只能包含一种续签合同")
    void shouldRequireExactlyOneRenewalContractTemplate()
    {
        OaSignPackage signPackage = baseRenewalPackage();

        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(signPackage,
                List.of(template(OaSignTemplateType.RENEWAL_SALARY_CONFIRM,
                        "续签薪酬确认书", "employeeName"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须包含续签劳动合同或续签劳务协议");

        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(signPackage, List.of(
                template(OaSignTemplateType.RENEWAL_LABOR_CONTRACT,
                        "续签劳动合同", "employeeName"),
                template(OaSignTemplateType.RENEWAL_SERVICE_CONTRACT,
                        "续签劳务协议", "employeeName"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能同时包含续签劳动合同和续签劳务协议");
    }

    @Test
    @DisplayName("续签合同类型必须与冻结用工类型一致")
    void shouldRejectRenewalTemplateForWrongEmploymentType()
    {
        OaSignPackage signPackage = baseRenewalPackage();
        signPackage.setEmploymentType("SERVICE_CONTRACT");

        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(signPackage,
                List.of(template(OaSignTemplateType.RENEWAL_LABOR_CONTRACT,
                        "续签劳动合同", "employeeName"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用工类型为劳务合同")
                .hasMessageContaining("续签劳动合同");
    }

    @Test
    @DisplayName("资料完整的劳动合同续签包允许生成")
    void shouldAcceptCompleteLaborRenewalPackage()
    {
        OaSignPackage signPackage = baseRenewalPackage();
        OaSignTemplate contract = template(OaSignTemplateType.RENEWAL_LABOR_CONTRACT,
                "续签劳动合同",
                "employeeName,employeeIdCard,employeePhone,employeeAddress,companyName,"
                        + "previousContractEndDate,previousEmploymentType,previousRenewalCount,"
                        + "renewalCount,contractStartDate,contractEndDate,postName,baseSalary,signDate");

        assertThatCode(() -> OaSignPackagePreflightValidator.validate(
                signPackage, List.of(contract))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("续签包拒绝合同重叠、续签次数跳号或合同类型变化，公司留待首次签名后确定")
    void shouldRejectUnsafeRenewalSnapshotChanges()
    {
        OaSignTemplate contract = template(OaSignTemplateType.RENEWAL_LABOR_CONTRACT,
                "续签劳动合同", "employeeName");

        OaSignPackage overlap = baseRenewalPackage();
        overlap.setPreviousContractEndDate(overlap.getContractStartDate());
        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(overlap, List.of(contract)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("新合同开始日期必须晚于原合同结束日期");

        OaSignPackage skippedCount = baseRenewalPackage();
        skippedCount.setRenewalCount(4);
        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(skippedCount, List.of(contract)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("续签次数必须在原次数基础上加1");

        OaSignPackage changedEntity = baseRenewalPackage();
        changedEntity.setPreviousLegalEntityIdSnapshot(999L);
        assertThatCode(() -> OaSignPackagePreflightValidator.validate(changedEntity, List.of(contract)))
                .doesNotThrowAnyException();

        OaSignPackage changedType = baseRenewalPackage();
        changedType.setPreviousEmploymentType("SERVICE_CONTRACT");
        assertThatThrownBy(() -> OaSignPackagePreflightValidator.validate(changedType, List.of(contract)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("续签前后合同类型不一致");
    }

    @Test
    @DisplayName("首次发送允许公司占位，最终合同在员工签名后补入法律主体")
    void shouldAllowDeferredLegalEntityForCompanyDocument()
    {
        OaSignPackage signPackage = baseOnboardingPackage();
        signPackage.setScenario("offboard");
        signPackage.setShopDeptName("徐汇门店");
        signPackage.setEntryDate("2023-01-01");
        signPackage.setLeaveDate("2026-07-31");
        OaSignTemplate certificate = template(OaSignTemplateType.OFFBOARD_LEAVE_CERTIFICATE,
                "离职证明", "employeeName,companyName,signDate");

        assertThatCode(() -> OaSignPackagePreflightValidator.validate(
                signPackage, List.of(certificate))).doesNotThrowAnyException();

        signPackage.setLegalEntityNameSnapshot("上海示例餐饮有限公司");
        assertThatCode(() -> OaSignPackagePreflightValidator.validate(
                signPackage, List.of(certificate))).doesNotThrowAnyException();
    }

    private OaSignPackage baseOnboardingPackage()
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setScenario("onboard");
        signPackage.setEmploymentType("劳动合同");
        signPackage.setContractTermCodeSnapshot("FIXED_TERM");
        signPackage.setSocialType("SOCIAL_INSURED");
        signPackage.setSalaryVersion("B");
        signPackage.setEmployeeNameSnapshot("张三");
        signPackage.setEmployeeIdCardSnapshot("TEST-ID-****-0000");
        signPackage.setEmployeePhoneSnapshot("13800000000");
        signPackage.setEmployeeAddressSnapshot("测试路1号");
        signPackage.setDeptNameSnapshot("运营部");
        signPackage.setPostNameSnapshot("店员");
        signPackage.setPostLevelSnapshot("3");
        signPackage.setEntryDate("2026-07-15");
        signPackage.setContractStartDate("2026-07-15");
        signPackage.setContractEndDate("2029-07-14");
        signPackage.setProbationStartDate("2026-07-15");
        signPackage.setProbationEndDate("2026-10-14");
        signPackage.setRecommendedCompanySnapshot("上海示例餐饮有限公司");
        signPackage.setRecommendedLegalRepresentativeSnapshot("李经理");
        signPackage.setRecommendedRegisteredAddressSnapshot("上海市测试路1号");
        return signPackage;
    }

    private OaSignPackage baseRenewalPackage()
    {
        OaSignPackage signPackage = baseOnboardingPackage();
        signPackage.setScenario("renewal");
        signPackage.setEmploymentType("LABOR_CONTRACT");
        signPackage.setPreviousEmploymentType("LABOR_CONTRACT");
        signPackage.setPreviousContractEndDate("2026-07-14");
        signPackage.setLegalEntityIdSnapshot(301L);
        signPackage.setLegalEntityCodeSnapshot("LE-SH-301");
        signPackage.setLegalEntityNameSnapshot("上海示例餐饮有限公司");
        signPackage.setLegalEntityCreditCodeSnapshot("91310000TEST301");
        signPackage.setLegalEntityAddressSnapshot("上海市测试路1号");
        signPackage.setLegalRepresentativeSnapshot("李经理");
        signPackage.setPreviousLegalEntityIdSnapshot(301L);
        signPackage.setPreviousRenewalCount(2);
        signPackage.setRenewalCount(3);
        signPackage.setProbationStartDate(null);
        signPackage.setProbationEndDate(null);
        signPackage.setBaseSalary(new BigDecimal("3000"));
        signPackage.setPostSalary(new BigDecimal("1000"));
        signPackage.setFieldAllowance(new BigDecimal("500"));
        signPackage.setPerformanceSalary(new BigDecimal("500"));
        signPackage.setSalaryTotal(new BigDecimal("5000"));
        signPackage.setSalaryVersion("A");
        return signPackage;
    }

    private OaSignTemplate template(String type, String name, String requiredPlaceholders)
    {
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(type);
        template.setTemplateName(name);
        template.setRequiredPlaceholders(requiredPlaceholders);
        return template;
    }

    private List<OaSignTemplate> exactV7Templates(boolean confidential)
    {
        List<OaSignTemplate> values = new ArrayList<>();
        values.add(template(OaSignTemplateType.ONBOARD_COMMITMENT, "入职承诺书", ""));
        OaSignTemplate labor = template(
                OaSignTemplateType.ONBOARD_LABOR_CONTRACT, "劳动合同", "");
        labor.setTemplateVersion("20260721-v7");
        labor.setFileHash(
                "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558");
        values.add(labor);
        values.add(template(OaSignTemplateType.ONBOARD_HANDBOOK_RECEIPT,
                "员工手册签收确认书", ""));
        values.add(exactV7SalaryTemplate());
        if (confidential)
        {
            values.add(template(OaSignTemplateType.ONBOARD_CONFIDENTIAL_NONCOMPETE,
                    "保密与竞业限制协议", ""));
        }
        values.forEach(value -> value.setEmployeeVisible("Y"));
        return List.copyOf(values);
    }

    private OaSignTemplate exactV7SalaryTemplate()
    {
        OaSignTemplate salary = template(
                OaSignTemplateType.ONBOARD_SALARY_CONFIRM, "薪酬结构确认书", "");
        salary.setEmployeeVisible("Y");
        salary.setSalaryVersion("B");
        return salary;
    }
}
