package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.dto.HrRegularizationRequest;

@DisplayName("HR转正业务策略")
class HrRegularizationPolicyTest
{
    private static final LocalDate BUSINESS_TODAY =
            LocalDate.of(2026, 7, 31);

    private final HrRegularizationPolicy policy =
            new HrRegularizationPolicy();

    @Test
    @DisplayName("请求文本和职级代码在校验前统一规范化")
    void normalizesRequest()
    {
        HrRegularizationRequest request = validRequest();
        request.setRequestId("  regularize-1  ");
        request.setPostCode("  SALESLEAD  ");
        request.setPostName("  销售组长  ");
        request.setJobGradeCode("  p4  ");
        request.setJobGradeName("  p4  ");
        request.setSalaryVersion("  2026-V2  ");

        policy.normalizeAndValidate(9L, request);

        assertThat(request.getRequestId()).isEqualTo("regularize-1");
        assertThat(request.getPostCode()).isEqualTo("SALESLEAD");
        assertThat(request.getPostName()).isEqualTo("销售组长");
        assertThat(request.getJobGradeCode()).isEqualTo("P4");
        assertThat(request.getJobGradeName()).isEqualTo("P4");
        assertThat(request.getSalaryVersion()).isEqualTo("2026-V2");
    }

    @Test
    @DisplayName("员工标识、单一职级和薪资约束均失败关闭")
    void rejectsMalformedRequestAndSalary()
    {
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                0L, validRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("员工ID不能为空");

        HrRegularizationRequest mismatchedGrade = validRequest();
        mismatchedGrade.setJobGradeName("P5");
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                9L, mismatchedGrade))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("职级代码和名称必须一致");

        HrRegularizationRequest preciseSalary = validRequest();
        preciseSalary.setPerformanceSalary(new BigDecimal("700.001"));
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                9L, preciseSalary))
                .isInstanceOf(ServiceException.class)
                .hasMessage("绩效工资小数不能超过2位");

        HrRegularizationRequest wrongTotal = validRequest();
        wrongTotal.setSalaryTotal(new BigDecimal("9000.01"));
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                9L, wrongTotal))
                .isInstanceOf(ServiceException.class)
                .hasMessage("薪资合计必须等于各薪资项之和");
    }

    @Test
    @DisplayName("实际转正日期受上海业务当天和员工冻结日期约束")
    void validatesEffectiveDateAgainstFrozenFacts()
    {
        HrEmployeeSigningSnapshot snapshot = probationSnapshot();
        assertThatCode(() -> policy.validateEffectiveDate(
                snapshot, BUSINESS_TODAY, BUSINESS_TODAY))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> policy.validateEffectiveDate(
                snapshot, BUSINESS_TODAY.plusDays(1), BUSINESS_TODAY))
                .isInstanceOf(ServiceException.class)
                .hasMessage("实际转正日期不能晚于上海业务当天");

        assertThatThrownBy(() -> policy.validateEffectiveDate(
                snapshot, snapshot.getEntryDate().minusDays(1),
                BUSINESS_TODAY))
                .isInstanceOf(ServiceException.class)
                .hasMessage("实际转正日期不能早于入职日期");

        snapshot.setEntryDate(LocalDate.of(2026, 4, 1));
        snapshot.setProbationStartDate(LocalDate.of(2026, 5, 1));
        assertThatThrownBy(() -> policy.validateEffectiveDate(
                snapshot, LocalDate.of(2026, 4, 30), BUSINESS_TODAY))
                .isInstanceOf(ServiceException.class)
                .hasMessage("实际转正日期不能早于试用期开始日期");

        snapshot.setContractEndDate(LocalDate.of(2026, 7, 20));
        assertThatThrownBy(() -> policy.validateEffectiveDate(
                snapshot, LocalDate.of(2026, 7, 21), BUSINESS_TODAY))
                .isInstanceOf(ServiceException.class)
                .hasMessage("实际转正日期不能晚于合同结束日期");
    }

    @Test
    @DisplayName("岗位必须与启用的权威主数据一致")
    void validatesCanonicalPost()
    {
        HrRegularizationRequest request = validRequest();
        assertThatCode(() -> policy.validateCanonicalPost(
                canonicalPost(), request)).doesNotThrowAnyException();

        SysPost disabled = canonicalPost();
        disabled.setStatus("1");
        assertThatThrownBy(() -> policy.validateCanonicalPost(
                disabled, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("岗位已停用");

        SysPost renamed = canonicalPost();
        renamed.setPostName("另一个岗位");
        assertThatThrownBy(() -> policy.validateCanonicalPost(
                renamed, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("岗位代码或名称与系统主数据不一致");
    }

    @Test
    @DisplayName("转正快照只应用请求和权威岗位中的稳定事实")
    void appliesRegularizationSnapshot()
    {
        HrEmployeeSigningSnapshot snapshot = probationSnapshot();

        policy.apply(snapshot, validRequest(), canonicalPost());

        assertThat(snapshot.getEmployeeStatus()).isEqualTo("正式");
        assertThat(snapshot.getActualRegularizationDate())
                .isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(snapshot.getPostId()).isEqualTo(402L);
        assertThat(snapshot.getPostCode()).isEqualTo("SALESLEAD");
        assertThat(snapshot.getPositionNo())
                .isEqualTo("SALESLEAD-E000009");
        assertThat(snapshot.getJobGradeCode()).isEqualTo("P4");
        assertThat(snapshot.getJobGradeName()).isEqualTo("P4");
        assertThat(snapshot.getSalaryTotal())
                .isEqualByComparingTo("9000.00");
        assertThat(snapshot.getSalaryVersion()).isEqualTo("2026-V2");
    }

    @Test
    @DisplayName("薪资风险按金额数值和版本共同判断")
    void detectsSalaryChange()
    {
        HrEmployeeSigningSnapshot before = probationSnapshot();
        HrEmployeeSigningSnapshot same = probationSnapshot();
        same.setSalaryTotal(new BigDecimal("8000.0"));

        assertThat(policy.salaryChanged(before, same)).isFalse();

        same.setSalaryVersion("2026-V9");
        assertThat(policy.salaryChanged(before, same)).isTrue();
    }

    @Test
    @DisplayName("幂等重放同时核对冻结payload和当前转正终态")
    void matchesFrozenPayloadAndCurrentState()
    {
        HrRegularizationRequest request = validRequest();
        HrEmployeeSigningSnapshot expected = probationSnapshot();
        policy.apply(expected, request, canonicalPost());
        HrEmployeeSigningSnapshot current = probationSnapshot();
        policy.apply(current, request, canonicalPost());

        assertThat(policy.matchesRequest(expected, request)).isTrue();
        assertThat(policy.sameState(current, expected)).isTrue();

        request.setPostName("另一个岗位");
        assertThat(policy.matchesRequest(expected, request)).isFalse();
        request.setPostName("销售组长");
        current.setEmployeeStatus("试用");
        assertThat(policy.sameState(current, expected)).isFalse();
    }

    private HrRegularizationRequest validRequest()
    {
        HrRegularizationRequest request =
                new HrRegularizationRequest();
        request.setRequestId("regularize-1");
        request.setActualRegularizationDate(
                LocalDate.of(2026, 7, 10));
        request.setPostId(402L);
        request.setPostCode("SALESLEAD");
        request.setPostName("销售组长");
        request.setJobGradeCode("P4");
        request.setJobGradeName("P4");
        request.setBaseSalary(new BigDecimal("6000.00"));
        request.setPostSalary(new BigDecimal("2000.00"));
        request.setFieldAllowance(new BigDecimal("300.00"));
        request.setPerformanceSalary(new BigDecimal("700.00"));
        request.setSalaryTotal(new BigDecimal("9000.00"));
        request.setSalaryVersion("2026-V2");
        return request;
    }

    private HrEmployeeSigningSnapshot probationSnapshot()
    {
        HrEmployeeSigningSnapshot snapshot =
                new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(9L);
        snapshot.setEmployeeNo("E000009");
        snapshot.setEmployeeName("员工甲");
        snapshot.setEmployeeStatus("试用");
        snapshot.setEntryDate(LocalDate.of(2026, 5, 1));
        snapshot.setProbationStartDate(LocalDate.of(2026, 5, 1));
        snapshot.setContractEndDate(LocalDate.of(2029, 4, 30));
        snapshot.setPostId(401L);
        snapshot.setPostCode("SALES");
        snapshot.setPostName("销售顾问");
        snapshot.setJobGradeCode("P3");
        snapshot.setJobGradeName("P3");
        snapshot.setBaseSalary(new BigDecimal("5000.00"));
        snapshot.setPostSalary(new BigDecimal("2000.00"));
        snapshot.setFieldAllowance(new BigDecimal("300.00"));
        snapshot.setPerformanceSalary(new BigDecimal("700.00"));
        snapshot.setSalaryTotal(new BigDecimal("8000.00"));
        snapshot.setSalaryVersion("2026-V1");
        return snapshot;
    }

    private SysPost canonicalPost()
    {
        SysPost post = new SysPost();
        post.setPostId(402L);
        post.setPostCode("SALESLEAD");
        post.setPostName("销售组长");
        post.setStatus("0");
        return post;
    }
}
