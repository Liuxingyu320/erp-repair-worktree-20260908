package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SignCandidateUserQuery;
import com.erp.system.api.domain.SignReadinessIssue;
import com.erp.system.mapper.SysUserMapper;

@DisplayName("签约候选员工准备度")
class SignCandidateReadinessTest
{
    @Test
    @DisplayName("完整标准档案返回空问题清单并保留签约字段")
    void shouldReturnReadyCandidateWithStandardCodes()
    {
        SignCandidateUser candidate = readyCandidate();
        candidate.setContractTerm("FIXED_TERM");
        candidate.setLegalEntityId(null);
        SysUserServiceImpl service = serviceReturning(candidate);

        SignCandidateUser result = service.selectSignCandidateUsers(new SignCandidateUserQuery()).get(0);

        assertThat(result.getContractType()).isEqualTo("LABOR_CONTRACT");
        assertThat(result.getContractTerm()).isEqualTo("FIXED_TERM");
        assertThat(result.getSocialType()).isEqualTo("SOCIAL_INSURED");
        assertThat(result.getLegalEntity()).isEqualTo("杭州名田食品有限公司");
        assertThat(result.getLegalEntityId()).isNull();
        assertThat(result.getLegalEntityCode()).isEqualTo("HZMT");
        assertThat(result.getEmployeeNo()).isEqualTo("E000020");
        assertThat(result.getEmployeeStatus()).isEqualTo("正式");
        assertThat(result.getAccountStatus()).isEqualTo("0");
        assertThat(result.getIdType()).isEqualTo("居民身份证");
        assertThat(result.getPostId()).isEqualTo(5L);
        assertThat(result.getPostCode()).isEqualTo("STORE_MANAGER");
        assertThat(result.getPostName()).isEqualTo("店长");
        assertThat(result.getProbationStartDate()).isEqualTo("2026-07-01");
        assertThat(result.getProbationEndDate()).isEqualTo("2026-09-30");
        assertThat(result.getBaseSalary()).isEqualByComparingTo("10000.00");
        assertThat(result.getPostSalary()).isEqualByComparingTo("5000.00");
        assertThat(result.getFieldAllowance()).isEqualByComparingTo("800.00");
        assertThat(result.getPerformanceSalary()).isEqualByComparingTo("1200.00");
        assertThat(result.getSalaryTotal()).isEqualByComparingTo("17000.00");
        assertThat(result.getSalaryVersion()).isEqualTo("2026-07");
        assertThat(result.getProfileFactsHash()).matches("[0-9a-f]{64}");
        assertThat(result.getReadinessIssues()).isEmpty();
    }

    @Test
    @DisplayName("缺失档案返回稳定问题代码和HR可读消息")
    void shouldExplainEveryMissingReadinessField()
    {
        SignCandidateUser candidate = new SignCandidateUser();
        candidate.setUserId(20L);
        SysUserServiceImpl service = serviceReturning(candidate);

        List<SignReadinessIssue> issues = service.selectSignCandidateUsers(new SignCandidateUserQuery())
                .get(0).getReadinessIssues();

        assertThat(issues).extracting(SignReadinessIssue::getCode)
                .containsExactly(
                        "MISSING_PHONE",
                        "INVALID_ID_CARD",
                        "MISSING_ADDRESS",
                        "MISSING_POST",
                        "MISSING_GRADE",
                        "MISSING_LEGAL_ENTITY",
                        "INVALID_CONTRACT_DATES",
                        "UNSUPPORTED_CONTRACT_TYPE",
                        "UNSUPPORTED_SOCIAL_TYPE");
        assertThat(issues).allSatisfy(issue -> {
            assertThat(issue.getField()).isNotBlank();
            assertThat(issue.getMessage()).isNotBlank();
        });
    }

    @Test
    @DisplayName("未知代码和倒置合同日期不能被静默接受")
    void shouldRejectUnknownCodesAndInvalidDateRange()
    {
        SignCandidateUser candidate = readyCandidate();
        candidate.setContractType("临时协议");
        candidate.setSocialType("商业保险");
        candidate.setContractStartDate(LocalDate.of(2029, 7, 1).toString());
        candidate.setContractEndDate(LocalDate.of(2026, 7, 1).toString());
        SysUserServiceImpl service = serviceReturning(candidate);

        List<SignReadinessIssue> issues = service.selectSignCandidateUsers(new SignCandidateUserQuery())
                .get(0).getReadinessIssues();

        assertThat(issues).extracting(SignReadinessIssue::getCode)
                .containsExactly("INVALID_CONTRACT_DATES", "UNSUPPORTED_CONTRACT_TYPE", "UNSUPPORTED_SOCIAL_TYPE");
        assertThat(issues).extracting(SignReadinessIssue::getMessage)
                .contains("合同开始日期不能晚于合同结束日期", "不支持的合同类型：临时协议", "不支持的社保类型：商业保险");
    }

    private SignCandidateUser readyCandidate()
    {
        SignCandidateUser candidate = new SignCandidateUser();
        candidate.setUserId(20L);
        candidate.setUserName("employee20");
        candidate.setNickName("张三");
        candidate.setPhonenumber("13800000000");
        candidate.setAccountStatus("0");
        candidate.setEmployeeNo("E000020");
        candidate.setEmployeeStatus("正式");
        candidate.setIdType("居民身份证");
        candidate.setIdNumber("330102199001011234");
        candidate.setCurrentAddress("杭州市西湖区测试路1号");
        candidate.setPostNames("店长");
        candidate.setPostId(5L);
        candidate.setPostCode("STORE_MANAGER");
        candidate.setPostName("店长");
        candidate.setJobGrade("P5");
        candidate.setLegalEntity("杭州名田食品有限公司");
        candidate.setLegalEntityCode("HZMT");
        candidate.setContractStartDate("2026-07-01");
        candidate.setContractEndDate("2029-06-30");
        candidate.setProbationStartDate("2026-07-01");
        candidate.setProbationEndDate("2026-09-30");
        candidate.setContractType("LABOR_CONTRACT");
        candidate.setSocialType("SOCIAL_INSURED");
        candidate.setBaseSalary(new BigDecimal("10000.00"));
        candidate.setPostSalary(new BigDecimal("5000.00"));
        candidate.setFieldAllowance(new BigDecimal("800.00"));
        candidate.setPerformanceSalary(new BigDecimal("1200.00"));
        candidate.setSalaryTotal(new BigDecimal("17000.00"));
        candidate.setSalaryVersion("2026-07");
        return candidate;
    }

    private SysUserServiceImpl serviceReturning(SignCandidateUser candidate)
    {
        SysUserMapper mapper = org.mockito.Mockito.mock(SysUserMapper.class);
        org.mockito.Mockito.when(mapper.selectSignCandidateUsers(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(candidate));
        SysUserServiceImpl service = new SysUserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        return service;
    }
}
