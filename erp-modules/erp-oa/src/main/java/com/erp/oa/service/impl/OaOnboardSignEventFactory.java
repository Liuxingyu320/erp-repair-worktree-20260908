package com.erp.oa.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.domain.OaSignOnboardContractSnapshot;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.system.api.domain.SignCandidateUser;

/** Creates the only event shape accepted by the Excel-import generation path. */
@Component
public class OaOnboardSignEventFactory
{
    public static final String SOURCE_TYPE = "MANUAL_SIGN_EXCEL_IMPORT";

    public HrSignBusinessEvent create(OaSignOnboardImportBatch batch,
            OaSignOnboardImportRow row, OaSignOnboardContractSnapshot contract,
            SignCandidateUser candidate, Long operatorUserId, String generationRequestId)
    {
        if (batch == null || row == null || contract == null || candidate == null
                || row.getRowId() == null || row.getSourceEventVersion() == null
                || row.getEmployeeId() == null
                || !row.getEmployeeId().equals(candidate.getUserId()))
        {
            throw new ServiceException("导入行签约事件事实不完整");
        }
        HrEmployeeSigningSnapshot snapshot = snapshot(batch, contract, candidate);
        HrSignBusinessEvent event = new HrSignBusinessEvent();
        event.setEventId("OA-ONBOARD-EXCEL:" + row.getRowId() + ":" + row.getSourceEventVersion());
        event.setScenario("ONBOARD");
        event.setEmployeeId(row.getEmployeeId());
        event.setSourceType(SOURCE_TYPE);
        event.setSourceBusinessId(String.valueOf(row.getRowId()));
        event.setSourceEventVersion(row.getSourceEventVersion());
        event.setOccurredTime(new Date());
        event.setOperatorUserId(operatorUserId);
        event.setBeforeSnapshot(snapshot);
        event.setAfterSnapshot(snapshot);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("importBatchId", batch.getBatchId());
        attributes.put("importRowId", row.getRowId());
        attributes.put("sourceRowNumber", row.getSourceRowNumber());
        attributes.put("fileSha256", batch.getFileSha256());
        attributes.put("generationRequestId", generationRequestId);
        attributes.put("noExternalContractConfirmed", row.getNoExternalContractConfirmed());
        attributes.put("historicalSupplement", row.getHistoricalSupplement());
        attributes.put("historicalSupplementReason", row.getHistoricalReason());
        attributes.put("contractEffectiveDate", contract.getContractStartDate());
        attributes.put("salaryWarningReason", row.getWarningReason());
        attributes.put("recommendedCompany", contract.getRecommendedCompany());
        attributes.put("recommendedLegalRepresentative", contract.getLegalRepresentative());
        attributes.put("recommendedRegisteredAddress", contract.getRegisteredAddress());
        attributes.put("legalEntityCreditCode", contract.getMatchedUnifiedSocialCreditCode());
        attributes.put("legalEntityAddress", contract.getMatchedRegisteredAddress());
        attributes.put("legalRepresentative", contract.getMatchedLegalRepresentative());
        attributes.put("legalEntityPhone", contract.getMatchedCompanyPhone());
        attributes.put("legalEntityResolveMode", row.getCompanyMatchMode());
        attributes.put("sealId", contract.getMatchedSealId());
        attributes.put("sealName", contract.getMatchedSealName());
        attributes.put("sealImageUrl", contract.getMatchedSealImageUrl());
        attributes.put("sealImageHash", contract.getMatchedSealImageHash());
        attributes.put("servicePersonType", contract.getServicePersonType());
        attributes.put("insuranceType", contract.getInsuranceType());
        attributes.put("studentStatus", contract.getStudentStatus());
        attributes.put("schoolName", contract.getSchoolName());
        attributes.put("retirementStatus", contract.getRetirementStatus());
        attributes.put("incomeStartYearMonth", contract.getIncomeStartYearMonth());
        event.setAttributes(attributes);
        return event;
    }

    HrEmployeeSigningSnapshot snapshot(OaSignOnboardImportBatch batch,
            OaSignOnboardContractSnapshot contract, SignCandidateUser candidate)
    {
        HrEmployeeSigningSnapshot value = new HrEmployeeSigningSnapshot();
        value.setEmployeeId(candidate.getUserId());
        value.setEmployeeNo(trim(candidate.getEmployeeNo()));
        value.setPositionNo(trim(candidate.getPostCode()));
        value.setEmployeeName(displayName(candidate));
        value.setPhone(trim(candidate.getPhonenumber()));
        value.setIdType(trim(candidate.getIdType()));
        value.setIdNumber(trim(candidate.getIdNumber()));
        value.setCurrentAddress(trim(contract.getCurrentAddress()));
        value.setEmployeeStatus(trim(candidate.getEmployeeStatus()));
        value.setAccountStatus(trim(candidate.getAccountStatus()));
        value.setShopDeptId(batch.getShopDeptId());
        value.setShopDeptName(batch.getShopDeptName());
        value.setDeptId(candidate.getDeptId());
        value.setDeptName(trim(candidate.getDeptName()));

        // Generation is allowed only after company master data is selected and frozen.
        value.setLegalEntityId(contract.getMatchedLegalEntityId());
        value.setLegalEntityCode(trim(contract.getMatchedLegalEntityCode()));
        value.setLegalEntityName(trim(contract.getMatchedLegalEntityName()));
        value.setPostId(candidate.getPostId());
        value.setPostCode(trim(candidate.getPostCode()));
        value.setPostName(trim(contract.getEmployeePost()));
        value.setJobGradeCode(trim(contract.getJobGradeCode()));
        value.setJobGradeName(trim(contract.getJobGradeCode()));
        value.setWorkLocation(trim(contract.getWorkLocation()));
        value.setWorkCityLevel(trim(contract.getCityLevel()));
        value.setContractTypeCode(trim(contract.getContractTypeCode()));
        value.setContractTermCode(trim(contract.getContractTermCode()));
        value.setSocialTypeCode(trim(contract.getSocialTypeCode()));
        LocalDate entryDate = parseDate(candidate.getEntryDate());
        value.setEntryDate(entryDate == null ? contract.getContractStartDate() : entryDate);
        value.setContractStartDate(contract.getContractStartDate());
        value.setContractEndDate(contract.getContractEndDate());
        value.setProbationStartDate(contract.getProbationStartDate());
        value.setProbationEndDate(contract.getProbationEndDate());
        value.setBaseSalary(contract.getBaseSalary());
        value.setPostSalary(contract.getPostSalary());
        value.setFieldAllowance(contract.getFieldAllowance());
        value.setPerformanceSalary(contract.getPerformanceSalary());
        value.setSalaryTotal(contract.getSalaryTotal());
        value.setSalaryVersion(trim(contract.getSalaryVersion()));
        return value;
    }

    private LocalDate parseDate(String value)
    {
        try { return value == null || value.isBlank() ? null : LocalDate.parse(value.trim()); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private String displayName(SignCandidateUser candidate)
    {
        String nickname = trim(candidate.getNickName());
        return nickname == null ? trim(candidate.getUserName()) : nickname;
    }

    private String trim(String value)
    {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
