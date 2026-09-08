package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaOnboardSalaryVersionPolicy;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.domain.dto.OaSignOnboardImportRowUpdateRequest;
import com.erp.system.api.constant.SigningProfileCodes;
import com.erp.system.api.domain.SignCandidateUser;

@DisplayName("入职签约导入行策略")
class OaSignOnboardRowPolicyTest
{
    private final OaSignOnboardRowPolicy policy = new OaSignOnboardRowPolicy();

    @Test
    @DisplayName("HR编辑值按字段规范化并只更新显式提供的事实")
    void appliesAndNormalizesExplicitHrEdits()
    {
        OaSignOnboardContractSnapshot snapshot = validSnapshot();
        snapshot.setCurrentAddress("原地址");
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setMatchedLegalEntityId(8L);
        row.setRecommendedSealId(18L);
        row.setSealRecommendationMode("AUTO_UNIQUE");

        OaSignOnboardImportRowUpdateRequest request = new OaSignOnboardImportRowUpdateRequest();
        request.setCurrentAddress("  浙江省杭州市西湖区  ");
        request.setContractTypeCode(" labor_contract ");
        request.setSocialTypeCode(" social_uninsured ");
        request.setContractTermCode(" open_ended ");
        request.setEmployeePost("  店长  ");
        request.setJobGradeCode(" 7级 ");
        request.setWorkLocation("  杭州  ");
        request.setCityLevel(" 二线 ");
        request.setServicePersonType(" consultant ");
        request.setInsuranceType(" commercial ");
        request.setStudentStatus(" non_student ");
        request.setSchoolName("  浙江大学  ");
        request.setRetirementStatus(" not_retired ");
        request.setIncomeStartYearMonth(" 2026-07 ");
        request.setWarningConfirmed(true);
        request.setWarningReason("  超出参考区间，已复核  ");
        request.setHistoricalSupplement(true);
        request.setHistoricalReason("  补录历史合同  ");
        request.setNoExternalContractConfirmed(true);
        request.setLegalEntityId(9L);

        policy.applyUpdate(request, snapshot, row);

        assertThat(snapshot.getCurrentAddress()).isEqualTo("浙江省杭州市西湖区");
        assertThat(snapshot.getAddressSource()).isEqualTo("HR");
        assertThat(snapshot.getContractTypeCode()).isEqualTo("LABOR_CONTRACT");
        assertThat(snapshot.getSocialTypeCode()).isEqualTo("SOCIAL_UNINSURED");
        assertThat(snapshot.getContractTermCode()).isEqualTo("OPEN_ENDED");
        assertThat(snapshot.getEmployeePost()).isEqualTo("店长");
        assertThat(snapshot.getJobGradeCode()).isEqualTo("7");
        assertThat(snapshot.getWorkLocation()).isEqualTo("杭州");
        assertThat(snapshot.getCityLevel()).isEqualTo("二线");
        assertThat(snapshot.getServicePersonType()).isEqualTo("CONSULTANT");
        assertThat(snapshot.getInsuranceType()).isEqualTo("COMMERCIAL");
        assertThat(snapshot.getStudentStatus()).isEqualTo("NON_STUDENT");
        assertThat(snapshot.getSchoolName()).isEqualTo("浙江大学");
        assertThat(snapshot.getRetirementStatus()).isEqualTo("NOT_RETIRED");
        assertThat(snapshot.getIncomeStartYearMonth()).isEqualTo("2026-07");
        assertThat(row.getMatchedLegalEntityId()).isEqualTo(9L);
        assertThat(row.getCompanyMatchMode()).isEqualTo("HR_CONFIRMED");
        assertThat(row.getRecommendedSealId()).isNull();
        assertThat(row.getSealRecommendationMode()).isNull();
        assertThat(row.getWarningReason()).isEqualTo("超出参考区间，已复核");
        assertThat(row.getHistoricalReason()).isEqualTo("补录历史合同");
        assertThat(row.getNoExternalContractConfirmed()).isTrue();
    }

    @Test
    @DisplayName("公司印章编号和确认理由非法时失败关闭")
    void rejectsInvalidMasterIdsAndMissingReasons()
    {
        OaSignOnboardContractSnapshot snapshot = validSnapshot();
        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        OaSignOnboardImportRowUpdateRequest invalidCompany =
                new OaSignOnboardImportRowUpdateRequest();
        invalidCompany.setLegalEntityId(0L);
        assertThatThrownBy(() -> policy.applyUpdate(invalidCompany, snapshot, row))
                .isInstanceOf(ServiceException.class)
                .hasMessage("公司主数据编号无效");

        OaSignOnboardImportRowUpdateRequest orphanSeal =
                new OaSignOnboardImportRowUpdateRequest();
        orphanSeal.setSealId(19L);
        assertThatThrownBy(() -> policy.applyUpdate(orphanSeal, snapshot, row))
                .isInstanceOf(ServiceException.class)
                .hasMessage("请先确认公司再选择印章");

        OaSignOnboardImportRowUpdateRequest warning =
                new OaSignOnboardImportRowUpdateRequest();
        warning.setWarningConfirmed(true);
        assertThatThrownBy(() -> policy.applyUpdate(warning, snapshot, row))
                .isInstanceOf(ServiceException.class)
                .hasMessage("确认薪资警告时必须填写原因");

        row.setWarningConfirmed(false);
        OaSignOnboardImportRowUpdateRequest historical =
                new OaSignOnboardImportRowUpdateRequest();
        historical.setHistoricalSupplement(true);
        assertThatThrownBy(() -> policy.applyUpdate(historical, snapshot, row))
                .isInstanceOf(ServiceException.class)
                .hasMessage("历史补签必须填写原因");
    }

    @Test
    @DisplayName("可编辑合同事实通过校验并派生唯一薪资版本")
    void validatesCompleteEditableFactsAndDerivesSalaryVersion()
    {
        OaSignOnboardContractSnapshot snapshot = validSnapshot();
        Set<String> errors = new LinkedHashSet<>();

        policy.validateEditable(snapshot, errors);

        assertThat(errors).isEmpty();
        assertThat(snapshot.getSalaryVersion())
                .isEqualTo(OaOnboardSalaryVersionPolicy.VERSION_B);
    }

    @Test
    @DisplayName("非法合同日期薪资和字典值产生稳定错误码")
    void invalidEditableFactsProduceStableErrors()
    {
        OaSignOnboardContractSnapshot snapshot = validSnapshot();
        snapshot.setContractTypeCode("UNKNOWN");
        snapshot.setSocialTypeCode("UNKNOWN");
        snapshot.setContractTermCode("UNKNOWN");
        snapshot.setJobGradeCode("10");
        snapshot.setContractEndDate(snapshot.getContractStartDate());
        snapshot.setProbationStartDate(snapshot.getContractStartDate().minusDays(1));
        snapshot.setProbationEndDate(snapshot.getContractEndDate().plusDays(1));
        snapshot.setSalaryTotal(new BigDecimal("9999"));
        snapshot.setStudentStatus("UNKNOWN");
        snapshot.setRetirementStatus("UNKNOWN");
        snapshot.setIncomeStartYearMonth("2026-13");
        Set<String> errors = new LinkedHashSet<>();

        policy.validateEditable(snapshot, errors);

        assertThat(errors).containsExactly(
                "INVALID_CONTRACT_TYPE",
                "INVALID_SOCIAL_TYPE",
                "SALARY_VERSION_NOT_DERIVABLE",
                "INVALID_CONTRACT_TERM",
                "INVALID_JOB_GRADE",
                "INVALID_CONTRACT_DATES",
                "INVALID_PROBATION_DATES",
                "INVALID_SALARY_TOTAL",
                "INVALID_STUDENT_STATUS",
                "INVALID_RETIREMENT_STATUS",
                "INVALID_INCOME_START_YEAR_MONTH");
    }

    @Test
    @DisplayName("劳务合同与未成年人只请求员工可维护的缺失事实")
    void derivesEmployeeMaintainableMissingFields()
    {
        OaSignOnboardContractSnapshot service = validSnapshot();
        service.setContractTypeCode(SigningProfileCodes.SERVICE_CONTRACT);
        service.setSocialTypeCode(SigningProfileCodes.SOCIAL_UNINSURED);
        service.setCurrentAddress(null);
        service.setStudentStatus(null);
        service.setSchoolName(null);
        service.setRetirementStatus(null);
        assertThat(policy.employeeMissingFields(service)).containsExactly(
                "currentAddress", "studentStatus", "schoolName", "retirementStatus");

        OaSignOnboardContractSnapshot minor = validSnapshot();
        minor.setIdNumber("330102200907181234");
        minor.setContractStartDate(LocalDate.of(2026, 7, 18));
        minor.setStudentStatus("NON_STUDENT");
        minor.setIncomeStartYearMonth(null);
        assertThat(policy.employeeMissingFields(minor))
                .containsExactly("incomeStartYearMonth");

        minor.setStudentStatus(null);
        assertThat(policy.employeeMissingFields(minor)).containsExactly(
                "studentStatus", "schoolName", "incomeStartYearMonth");
    }

    @Test
    @DisplayName("身份与档案版本比对规范化空白且自动匹配排除离职员工")
    void matchesAuthoritativeIdentityAndProfileVersion()
    {
        OaSignOnboardContractSnapshot snapshot = validSnapshot();
        snapshot.setEmployeeName("李曼");
        snapshot.setPhone("13800001111");
        snapshot.setIdNumber("33010219900101123X");
        snapshot.setProfileFactsHash(" hash-v1 ");
        SignCandidateUser candidate = candidate();
        candidate.setNickName(" 李曼 ");
        candidate.setPhonenumber(" 13800001111 ");
        candidate.setIdNumber("330102 19900101123x");
        candidate.setProfileFactsHash("hash-v1");

        assertThat(policy.identityFactsMatch(snapshot, candidate)).isTrue();
        assertThat(policy.profileFactsMatch(snapshot, candidate)).isTrue();

        OaSignOnboardImportBatch autoMatch = new OaSignOnboardImportBatch();
        autoMatch.setSelectedCount(0);
        candidate.setEmployeeStatus("离职");
        assertThat(policy.identityFactsMatch(autoMatch, snapshot, candidate)).isFalse();

        autoMatch.setSelectedCount(1);
        assertThat(policy.identityFactsMatch(autoMatch, snapshot, candidate)).isTrue();
        candidate.setProfileFactsHash("hash-v2");
        assertThat(policy.profileFactsMatch(snapshot, candidate)).isFalse();
    }

    @Test
    @DisplayName("匹配后身份事实只取系统权威值且不接受Excel残留")
    void appliesAuthoritativeSystemIdentity()
    {
        OaSignOnboardContractSnapshot snapshot = validSnapshot();
        snapshot.setEmployeeName("Excel姓名");
        snapshot.setPhone("13000000000");
        snapshot.setIdNumber("330102201001011234");
        SignCandidateUser candidate = candidate();
        candidate.setNickName(" ");
        candidate.setUserName("  system-user  ");
        candidate.setPhonenumber(" 13900001111 ");
        candidate.setIdNumber("330102 19900101123x");

        policy.applyAuthoritativeIdentity(snapshot, candidate);

        assertThat(snapshot.getEmployeeName()).isEqualTo("system-user");
        assertThat(snapshot.getPhone()).isEqualTo("13900001111");
        assertThat(snapshot.getIdNumber()).isEqualTo("33010219900101123X");
    }

    @Test
    @DisplayName("个人事实并发保护只识别员工资料白名单字段")
    void detectsOnlyEmployeeOwnedPersonalFactEdits()
    {
        assertThat(policy.editsPersonalFacts(null)).isFalse();
        OaSignOnboardImportRowUpdateRequest contract =
                new OaSignOnboardImportRowUpdateRequest();
        contract.setContractTypeCode(SigningProfileCodes.LABOR_CONTRACT);
        assertThat(policy.editsPersonalFacts(contract)).isFalse();

        OaSignOnboardImportRowUpdateRequest address =
                new OaSignOnboardImportRowUpdateRequest();
        address.setCurrentAddress("新地址");
        assertThat(policy.editsPersonalFacts(address)).isTrue();
    }

    private OaSignOnboardContractSnapshot validSnapshot()
    {
        OaSignOnboardContractSnapshot snapshot = new OaSignOnboardContractSnapshot();
        snapshot.setEmployeeName("员工");
        snapshot.setPhone("13800001111");
        snapshot.setIdNumber("330102199001011234");
        snapshot.setCurrentAddress("浙江省杭州市");
        snapshot.setContractTypeCode(SigningProfileCodes.LABOR_CONTRACT);
        snapshot.setSocialTypeCode(SigningProfileCodes.SOCIAL_INSURED);
        snapshot.setContractTermCode(SigningProfileCodes.FIXED_TERM);
        snapshot.setJobGradeCode("5");
        snapshot.setContractStartDate(LocalDate.of(2026, 8, 1));
        snapshot.setContractEndDate(LocalDate.of(2027, 7, 31));
        snapshot.setSalaryTotal(new BigDecimal("10000"));
        snapshot.setBaseSalary(new BigDecimal("6000"));
        snapshot.setPostSalary(new BigDecimal("2000"));
        snapshot.setFieldAllowance(new BigDecimal("500"));
        snapshot.setPerformanceSalary(new BigDecimal("1500"));
        return snapshot;
    }

    private SignCandidateUser candidate()
    {
        SignCandidateUser candidate = new SignCandidateUser();
        candidate.setUserId(88L);
        candidate.setUserName("li.man");
        candidate.setNickName("李曼");
        candidate.setPhonenumber("13800001111");
        candidate.setIdNumber("330102199001011234");
        candidate.setProfileFactsHash("hash-v1");
        candidate.setEmployeeStatus("在职");
        return candidate;
    }
}
