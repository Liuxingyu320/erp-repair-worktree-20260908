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
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.dto.HrEmployeeTransferRequest;
import com.erp.system.domain.dto.HrTransferRiskConfirmation;

@DisplayName("HR调岗业务策略")
class HrTransferPolicyTest
{
    private static final LocalDate EFFECTIVE_DATE =
            LocalDate.of(2026, 7, 30);
    private static final LocalDate OPERATION_DATE =
            LocalDate.of(2026, 7, 31);
    private static final String RISK_STATEMENT =
            "该操作将按历史日期补录并立即修改当前员工档案";

    private final HrTransferPolicy policy = new HrTransferPolicy();

    @Test
    @DisplayName("请求和历史确认在校验前统一规范化")
    void normalizesRequestAndHistoricalConfirmation()
    {
        HrEmployeeTransferRequest request = validRequest();
        request.setRequestId("  transfer-1  ");
        request.setTargetDeptName("  新零售部  ");
        request.setPostCode("  p004  ");
        request.setJobGradeCode("  p4  ");
        request.setJobGradeName("  p4  ");
        request.setDirectSupervisorName("  王主管  ");
        request.setLegalEntityCode("  le-a  ");
        HrTransferRiskConfirmation confirmation =
                validConfirmation(beforeSnapshot(), appliedSnapshot(request));
        confirmation.setEmployeeName("  员工甲  ");
        confirmation.setRiskStatement("  " + RISK_STATEMENT + "  ");
        confirmation.setReason("  已核对纸质调岗单  ");
        request.setRiskConfirmation(confirmation);

        policy.normalizeAndValidate(9L, request);

        assertThat(request.getRequestId()).isEqualTo("transfer-1");
        assertThat(request.getTargetDeptName()).isEqualTo("新零售部");
        assertThat(request.getPostCode()).isEqualTo("p004");
        assertThat(request.getJobGradeCode()).isEqualTo("P4");
        assertThat(request.getJobGradeName()).isEqualTo("P4");
        assertThat(request.getDirectSupervisorName()).isEqualTo("王主管");
        assertThat(request.getLegalEntityCode()).isEqualTo("LE-A");
        assertThat(confirmation.getEmployeeName()).isEqualTo("员工甲");
        assertThat(confirmation.getRiskStatement()).isEqualTo(RISK_STATEMENT);
        assertThat(confirmation.getReason()).isEqualTo("已核对纸质调岗单");
    }

    @Test
    @DisplayName("员工标识、单一职级和薪资约束均失败关闭")
    void rejectsMalformedRequestAndSalary()
    {
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                0L, validRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("员工ID不能为空");

        HrEmployeeTransferRequest mismatchedGrade = validRequest();
        mismatchedGrade.setJobGradeName("P5");
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                9L, mismatchedGrade))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("职级代码和名称必须一致");

        HrEmployeeTransferRequest preciseSalary = validRequest();
        preciseSalary.setBaseSalary(new BigDecimal("5000.001"));
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                9L, preciseSalary))
                .isInstanceOf(ServiceException.class)
                .hasMessage("基本工资小数不能超过2位");

        HrEmployeeTransferRequest wrongTotal = validRequest();
        wrongTotal.setSalaryTotal(new BigDecimal("9000.01"));
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                9L, wrongTotal))
                .isInstanceOf(ServiceException.class)
                .hasMessage("薪资合计必须等于各薪资项之和");
    }

    @Test
    @DisplayName("组织岗位和直属主管必须与启用的权威主数据一致")
    void validatesCanonicalMasterData()
    {
        HrEmployeeTransferRequest request = validRequest();
        assertThatCode(() -> {
            policy.validateDept(validDept(), request);
            policy.validatePost(validPost(), request);
            policy.validateSupervisor(validSupervisor(), request);
        }).doesNotThrowAnyException();

        SysDept renamedDept = validDept();
        renamedDept.setDeptName("另一个组织");
        assertThatThrownBy(() -> policy.validateDept(renamedDept, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("目标组织名称与系统主数据不一致");

        SysPost disabledPost = validPost();
        disabledPost.setStatus("1");
        assertThatThrownBy(() -> policy.validatePost(disabledPost, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("岗位已停用");

        assertThatThrownBy(() -> policy.validateSupervisor(null, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("直属主管不存在或已停用");

        HrEmployeeTransferRequest withoutSupervisor = validRequest();
        withoutSupervisor.setDirectSupervisorId(null);
        withoutSupervisor.setDirectSupervisorName(null);
        assertThatCode(() -> policy.validateSupervisor(
                null, withoutSupervisor)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("调岗快照只应用请求和权威主数据中的稳定事实")
    void appliesTransferSnapshotAndDetectsBusinessChange()
    {
        HrEmployeeSigningSnapshot before = beforeSnapshot();
        HrEmployeeSigningSnapshot after = beforeSnapshot();

        policy.apply(after, validRequest(), validDept(),
                validPost(), validSupervisor());

        assertThat(after.getShopDeptId()).isEqualTo(30L);
        assertThat(after.getDeptName()).isEqualTo("新零售部");
        assertThat(after.getPostId()).isEqualTo(40L);
        assertThat(after.getPositionNo()).isEqualTo("P004-E009");
        assertThat(after.getJobGradeCode()).isEqualTo("P4");
        assertThat(after.getDirectSupervisorId()).isEqualTo(50L);
        assertThat(after.getDepartmentSupervisorName()).isEqualTo("李负责人");
        assertThat(after.getLegalEntityCode()).isEqualTo("LE-A");
        assertThat(after.getSalaryTotal()).isEqualByComparingTo("9000.00");
        assertThat(after.getTransferEffectiveDate()).isEqualTo(EFFECTIVE_DATE);
        assertThat(policy.changed(before, after)).isTrue();
        assertThat(policy.changed(after, after)).isFalse();
    }

    @Test
    @DisplayName("所有服务端调岗风险按稳定顺序派生")
    void derivesEveryRiskCodeInStableOrder()
    {
        HrEmployeeSigningSnapshot before = beforeSnapshot();
        HrEmployeeSigningSnapshot after = appliedSnapshot(validRequest());

        assertThat(policy.riskCodes(before, after, true)).containsExactly(
                "HISTORICAL_BACKFILL",
                "LEGAL_ENTITY_CHANGED",
                "CROSS_CITY",
                "JOB_GRADE_DECREASED",
                "SALARY_DECREASED");
    }

    @Test
    @DisplayName("历史调岗二次确认要求所有冻结事实一致")
    void validatesHistoricalConfirmation()
    {
        HrEmployeeSigningSnapshot before = beforeSnapshot();
        HrEmployeeSigningSnapshot after = appliedSnapshot(validRequest());
        HrTransferRiskConfirmation confirmation =
                validConfirmation(before, after);

        assertThatCode(() -> policy.validateHistoricalConfirmation(
                confirmation, before, after, EFFECTIVE_DATE, OPERATION_DATE))
                .doesNotThrowAnyException();

        confirmation.setAfterPostId(41L);
        assertThatThrownBy(() -> policy.validateHistoricalConfirmation(
                confirmation, before, after, EFFECTIVE_DATE, OPERATION_DATE))
                .isInstanceOf(ServiceException.class)
                .hasMessage("二次确认内容与当前调岗信息不一致");
    }

    @Test
    @DisplayName("幂等重放同时核对冻结payload和当前调岗终态")
    void matchesFrozenPayloadAndCurrentState()
    {
        HrEmployeeTransferRequest request = validRequest();
        HrEmployeeSigningSnapshot expected = appliedSnapshot(request);
        HrEmployeeSigningSnapshot current = appliedSnapshot(request);

        assertThat(policy.matchesRequest(expected, request)).isTrue();
        assertThat(policy.sameState(current, expected)).isTrue();

        request.setWorkLocation("北京市朝阳区");
        assertThat(policy.matchesRequest(expected, request)).isFalse();
        request.setWorkLocation("上海市浦东新区");
        current.setSalaryVersion("V3");
        assertThat(policy.sameState(current, expected)).isFalse();
    }

    @Test
    @DisplayName("重放不一致详情稳定列出冻结载荷和当前漂移字段")
    void reportsStableReplayMismatchFields()
    {
        HrEmployeeTransferRequest request = validRequest();
        HrEmployeeSigningSnapshot expected = appliedSnapshot(request);
        HrEmployeeSigningSnapshot current = appliedSnapshot(request);
        request.setWorkLocation("北京市朝阳区");
        current.setDeptId(31L);
        current.setSalaryTotal(new BigDecimal("8999.00"));
        current.setSalaryVersion("V3");

        assertThat(policy.replayMismatchDetail(current, expected, request))
                .isEqualTo("mismatchFields=frozenPayload,deptId,salaryTotal,salaryVersion");
    }

    private HrEmployeeTransferRequest validRequest()
    {
        HrEmployeeTransferRequest request = new HrEmployeeTransferRequest();
        request.setRequestId("transfer-1");
        request.setEffectiveDate(EFFECTIVE_DATE);
        request.setTargetDeptId(30L);
        request.setTargetDeptName("新零售部");
        request.setPostId(40L);
        request.setPostCode("p004");
        request.setPostName("门店经理");
        request.setJobGradeCode("P4");
        request.setJobGradeName("P4");
        request.setWorkLocation("上海市浦东新区");
        request.setWorkCityLevel("一线");
        request.setDirectSupervisorId(50L);
        request.setDirectSupervisorName("王主管");
        request.setLegalEntityId(60L);
        request.setLegalEntityCode("LE-A");
        request.setLegalEntityName("甲公司");
        request.setBaseSalary(new BigDecimal("5000.00"));
        request.setPostSalary(new BigDecimal("2000.00"));
        request.setFieldAllowance(new BigDecimal("300.00"));
        request.setPerformanceSalary(new BigDecimal("1700.00"));
        request.setSalaryTotal(new BigDecimal("9000.00"));
        request.setSalaryVersion("V2");
        return request;
    }

    private HrEmployeeSigningSnapshot beforeSnapshot()
    {
        HrEmployeeSigningSnapshot snapshot =
                new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(9L);
        snapshot.setEmployeeNo("E009");
        snapshot.setEmployeeName("员工甲");
        snapshot.setShopDeptId(10L);
        snapshot.setShopDeptName("旧组织");
        snapshot.setDeptId(10L);
        snapshot.setDeptName("旧组织");
        snapshot.setPostId(20L);
        snapshot.setPostCode("p002");
        snapshot.setPostName("店员");
        snapshot.setJobGradeCode("P5");
        snapshot.setJobGradeName("P5");
        snapshot.setWorkLocation("杭州市西湖区");
        snapshot.setWorkCityLevel("二线");
        snapshot.setDirectSupervisorId(21L);
        snapshot.setDirectSupervisorName("旧主管");
        snapshot.setDepartmentSupervisorName("旧负责人");
        snapshot.setLegalEntityId(61L);
        snapshot.setLegalEntityCode("LE-B");
        snapshot.setLegalEntityName("乙公司");
        snapshot.setBaseSalary(new BigDecimal("6000.00"));
        snapshot.setPostSalary(new BigDecimal("2000.00"));
        snapshot.setFieldAllowance(new BigDecimal("500.00"));
        snapshot.setPerformanceSalary(new BigDecimal("1500.00"));
        snapshot.setSalaryTotal(new BigDecimal("10000.00"));
        snapshot.setSalaryVersion("V1");
        return snapshot;
    }

    private HrEmployeeSigningSnapshot appliedSnapshot(
            HrEmployeeTransferRequest request)
    {
        HrEmployeeSigningSnapshot snapshot = beforeSnapshot();
        policy.apply(snapshot, request, validDept(),
                validPost(), validSupervisor());
        return snapshot;
    }

    private SysDept validDept()
    {
        SysDept dept = new SysDept();
        dept.setDeptId(30L);
        dept.setDeptName("新零售部");
        dept.setLeader("李负责人");
        dept.setStatus("0");
        dept.setDelFlag("0");
        return dept;
    }

    private SysPost validPost()
    {
        SysPost post = new SysPost();
        post.setPostId(40L);
        post.setPostCode("p004");
        post.setPostName("门店经理");
        post.setStatus("0");
        return post;
    }

    private SysUser validSupervisor()
    {
        SysUser supervisor = new SysUser();
        supervisor.setUserId(50L);
        supervisor.setNickName("王主管");
        supervisor.setStatus("0");
        supervisor.setDelFlag("0");
        return supervisor;
    }

    private HrTransferRiskConfirmation validConfirmation(
            HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        HrTransferRiskConfirmation confirmation =
                new HrTransferRiskConfirmation();
        confirmation.setConfirmed(true);
        confirmation.setEmployeeId(before.getEmployeeId());
        confirmation.setEmployeeName(before.getEmployeeName());
        confirmation.setBeforeDeptId(before.getDeptId());
        confirmation.setBeforeDeptName(before.getDeptName());
        confirmation.setBeforePostId(before.getPostId());
        confirmation.setBeforePostName(before.getPostName());
        confirmation.setAfterDeptId(after.getDeptId());
        confirmation.setAfterDeptName(after.getDeptName());
        confirmation.setAfterPostId(after.getPostId());
        confirmation.setAfterPostName(after.getPostName());
        confirmation.setEffectiveDate(EFFECTIVE_DATE);
        confirmation.setOperationDate(OPERATION_DATE);
        confirmation.setRiskStatement(RISK_STATEMENT);
        confirmation.setReason("已核对纸质调岗单");
        return confirmation;
    }
}
