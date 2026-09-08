package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;

class OaSignPackageDraftPolicyTest
{
    private final OaSignPackageDraftPolicy policy =
            new OaSignPackageDraftPolicy();

    @Test
    void manualDraftKeepsServerOwnedIdentityAndUsesCurrentHrAssignment()
    {
        OaSignPackage existing = existingManualDraft();
        OaSignPackage request = validRequest(" OnBoard ");
        request.setPackageId(999L);
        request.setEmployeeId(999L);
        request.setShopDeptId(999L);
        request.setSourcePlanId(999L);
        request.setSourcePlanName("伪造方案");
        request.setStatus(OaSignPackageStatus.SIGNED);

        OaSignPackage update = policy.buildDraftPackageUpdate(
                90L, request, existing, null, 101L);

        assertThat(update.getPackageId()).isEqualTo(90L);
        assertThat(update.getEmployeeId()).isEqualTo(201L);
        assertThat(update.getShopDeptId()).isEqualTo(1171L);
        assertThat(update.getShopDeptName()).isEqualTo("西湖店");
        assertThat(update.getSourcePlanId()).isEqualTo(55L);
        assertThat(update.getSourcePlanName()).isEqualTo("入职方案");
        assertThat(update.getTaskId()).isNull();
        assertThat(update.getPlanVersionId()).isEqualTo(66L);
        assertThat(update.getStatus()).isEqualTo(OaSignPackageStatus.DRAFT);
        assertThat(update.getScenario()).isEqualTo("onboard");
        assertThat(update.getParams()).containsEntry("assignedHrUserId", 101L);
    }

    @Test
    void taskDraftFreezesBindingPlanCompanyAndLifecycleFacts()
    {
        OaSignPackage existing = existingManualDraft();
        existing.setTaskId(9L);
        existing.setLegalEntityIdSnapshot(31L);
        existing.setLegalEntityCodeSnapshot("LEGAL-31");
        existing.setLegalEntityNameSnapshot("冻结公司");
        existing.setContractTermCodeSnapshot("OPEN_ENDED");
        existing.setPreviousContractEndDate("2026-06-30");
        existing.setPreviousEmploymentType("劳动合同");
        existing.setPreviousLegalEntityIdSnapshot(30L);
        existing.setPreviousRenewalCount(2);
        existing.setRenewalCount(3);
        existing.setActualRegularizationDate("2026-07-10");

        OaSignTask task = editableTask("REGULARIZE");
        OaSignPackage request = validRequest("offboard");
        request.setLegalEntityIdSnapshot(999L);
        request.setLegalEntityCodeSnapshot("FORGED");
        request.setLegalEntityNameSnapshot("伪造公司");
        request.setContractTermCodeSnapshot("FIXED_TERM");
        request.setPreviousContractEndDate("2099-01-01");
        request.setPreviousRenewalCount(99);
        request.setRenewalCount(100);
        request.setActualRegularizationDate("2099-01-02");

        OaSignPackage update = policy.buildDraftPackageUpdate(
                90L, request, existing, task, 999L);

        assertThat(update.getEmployeeId()).isEqualTo(201L);
        assertThat(update.getShopDeptId()).isEqualTo(1171L);
        assertThat(update.getTaskId()).isEqualTo(9L);
        assertThat(update.getSourcePlanId()).isEqualTo(77L);
        assertThat(update.getPlanVersionId()).isEqualTo(77L);
        assertThat(update.getSourcePlanName()).isNull();
        assertThat(update.getConfirmStatus()).isEqualTo(
                OaSignTaskStatus.NEEDS_DATA.name());
        assertThat(update.getScenario()).isEqualTo("regularize");
        assertThat(update.getLegalEntityIdSnapshot()).isEqualTo(31L);
        assertThat(update.getContractTermCodeSnapshot()).isEqualTo("OPEN_ENDED");
        assertThat(update.getPreviousContractEndDate()).isEqualTo("2026-06-30");
        assertThat(update.getPreviousRenewalCount()).isEqualTo(2);
        assertThat(update.getRenewalCount()).isEqualTo(3);
        assertThat(update.getActualRegularizationDate()).isEqualTo("2026-07-10");
        assertThat(update.getParams()).containsEntry("assignedHrUserId", 101L);
    }

    @Test
    void nonRegularizationTaskMayUseTheEditedActualRegularizationDate()
    {
        OaSignPackage existing = existingManualDraft();
        existing.setTaskId(9L);
        existing.setActualRegularizationDate("2026-07-10");
        OaSignPackage request = validRequest("offboard");
        request.setActualRegularizationDate("2026-07-11");

        OaSignPackage update = policy.buildDraftPackageUpdate(
                90L, request, existing, editableTask("ONBOARD"), 999L);

        assertThat(update.getScenario()).isEqualTo("onboard");
        assertThat(update.getActualRegularizationDate()).isEqualTo("2026-07-11");
    }

    @Test
    void emergencySourceReasonIsCanonicalAndCannotBeRemovedOrRewritten()
    {
        OaSignPackage existing = existingManualDraft();
        existing.setRemark("非生命周期任务来源： 纸质审批单已授权补录 ");

        assertThat(policy.resolveDraftRemark(existing, null))
                .isEqualTo("非生命周期任务来源：纸质审批单已授权补录");
        assertThat(policy.resolveDraftRemark(
                existing, " 非生命周期任务来源：纸质审批单已授权补录 "))
                .isEqualTo("非生命周期任务来源：纸质审批单已授权补录");

        assertThatThrownBy(() -> policy.resolveDraftRemark(
                existing, "纸质审批单已授权补录"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源标识和原因不可删除");
        assertThatThrownBy(() -> policy.resolveDraftRemark(
                existing, "非生命周期任务来源：其他原因"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源原因不可修改");
    }

    @Test
    void malformedEmergencyReasonFailsClosedButTaskRemarksStayEditable()
    {
        OaSignPackage malformed = existingManualDraft();
        malformed.setRemark("非生命周期任务来源： ");
        assertThatThrownBy(() -> policy.resolveDraftRemark(malformed, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源原因不完整");

        OaSignPackage taskPackage = existingManualDraft();
        taskPackage.setTaskId(9L);
        taskPackage.setRemark("非生命周期任务来源：普通任务备注");
        assertThat(policy.resolveDraftRemark(taskPackage, "生命周期任务新备注"))
                .isEqualTo("生命周期任务新备注");
    }

    @Test
    void requiredDraftSnapshotsFailClosed()
    {
        OaSignPackage draft = validRequest("onboard");
        assertThatCode(() -> policy.validateDraftPackage(draft))
                .doesNotThrowAnyException();

        draft.setEmployeeNameSnapshot(" ");
        assertThatThrownBy(() -> policy.validateDraftPackage(draft))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("员工姓名不能为空");
        draft.setEmployeeNameSnapshot("张三");
        draft.setEmployeePhoneSnapshot(null);
        assertThatThrownBy(() -> policy.validateDraftPackage(draft))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("手机号不能为空");
        draft.setEmployeePhoneSnapshot("13800000000");
        draft.setEmployeeIdCardSnapshot("");
        assertThatThrownBy(() -> policy.validateDraftPackage(draft))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("身份证号不能为空");
        draft.setEmployeeIdCardSnapshot("110101199001011234");
        draft.setScenario(" ");
        assertThatThrownBy(() -> policy.validateDraftPackage(draft))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约场景不能为空");
    }

    private OaSignPackage existingManualDraft()
    {
        OaSignPackage value = new OaSignPackage();
        value.setPackageId(90L);
        value.setEmployeeId(201L);
        value.setShopDeptId(1171L);
        value.setShopDeptName("西湖店");
        value.setSourcePlanId(55L);
        value.setSourcePlanName("入职方案");
        value.setPlanVersionId(66L);
        value.setConfirmStatus("LEGACY");
        value.setStatus(OaSignPackageStatus.DRAFT);
        return value;
    }

    private OaSignTask editableTask(String scenario)
    {
        OaSignTask value = new OaSignTask();
        value.setTaskId(9L);
        value.setEmployeeId(201L);
        value.setShopDeptId(1171L);
        value.setScenario(scenario);
        value.setPlanVersionId(77L);
        value.setAssignedHrUserId(101L);
        return value;
    }

    private OaSignPackage validRequest(String scenario)
    {
        OaSignPackage value = new OaSignPackage();
        value.setEmployeeNameSnapshot("张三");
        value.setEmployeePhoneSnapshot("13800000000");
        value.setEmployeeIdCardSnapshot("110101199001011234");
        value.setScenario(scenario);
        value.setEmploymentType("劳动合同");
        value.setContractTermCodeSnapshot("FIXED_TERM");
        value.setPostNameSnapshot("店长");
        value.setSalaryVersion("2026A");
        value.setEntryDate("2026-07-01");
        value.setContractStartDate("2026-07-01");
        value.setContractEndDate("2029-06-30");
        value.setRemark("确认无误");
        return value;
    }
}
