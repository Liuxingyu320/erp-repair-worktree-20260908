package com.erp.system.support;

import org.springframework.stereotype.Component;

import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.vo.HrOnboardingDetailVo;
import com.erp.system.domain.vo.HrOnboardingListVo;

/** Deterministic masking and explicit safe-DTO mapping for onboarding clients. */
@Component
public class HrSensitiveFieldMasker
{
    public String maskPhone(String value) { return mask(value, 3, 4); }
    public String maskIdNumber(String value) { return mask(value, 4, 4); }
    public String maskBankAccount(String value) { return mask(value, 4, 4); }
    public String maskAddress(String value) { return mask(value, 2, 0); }

    private String mask(String value, int prefixLength, int suffixLength)
    {
        if (value == null || value.isEmpty()) return value;
        int[] codePoints = value.codePoints().toArray();
        if (codePoints.length <= prefixLength + suffixLength)
        {
            return repeatMask(codePoints.length);
        }
        String prefix = new String(codePoints, 0, prefixLength);
        String suffix = suffixLength == 0 ? ""
                : new String(codePoints, codePoints.length - suffixLength, suffixLength);
        return prefix + repeatMask(codePoints.length - prefixLength - suffixLength) + suffix;
    }

    private String repeatMask(int length)
    {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) result.append('*');
        return result.toString();
    }

    public HrOnboardingListVo toListVo(HrOnboarding source)
    {
        if (source == null) return null;
        HrOnboardingListVo target = new HrOnboardingListVo();
        mapList(source, target);
        return target;
    }

    public HrOnboardingDetailVo toMaskedDetailVo(HrOnboarding source)
    {
        if (source == null) return null;
        HrOnboardingDetailVo target = new HrOnboardingDetailVo();
        mapList(source, target);
        target.setIdNumberMasked(maskIdNumber(source.getIdNumber()));
        target.setBankAccountMasked(maskBankAccount(source.getBankAccount()));
        target.setRegisteredResidenceMasked(maskAddress(source.getRegisteredResidence()));
        target.setCurrentAddressMasked(maskAddress(source.getCurrentAddress()));
        target.setEmergencyContactPhoneMasked(maskPhone(source.getEmergencyContactPhone()));
        target.setDirectSupervisorUserId(source.getDirectSupervisorUserId());
        target.setDepartmentSupervisor(source.getDepartmentSupervisor());
        target.setJobGrade(source.getJobGrade());
        target.setSex(source.getSex());
        target.setBirthDate(source.getBirthDate());
        target.setIdType(source.getIdType());
        target.setMaritalStatus(source.getMaritalStatus());
        target.setEthnicity(source.getEthnicity());
        target.setEmergencyContact(source.getEmergencyContact());
        target.setEmergencyContactRelation(source.getEmergencyContactRelation());
        target.setWorkLocation(source.getWorkLocation());
        target.setWorkCityLevel(source.getWorkCityLevel());
        target.setBankName(source.getBankName());
        target.setContractType(source.getContractType());
        target.setSocialType(source.getSocialType());
        target.setProbationPeriod(source.getProbationPeriod());
        target.setLegalEntity(source.getLegalEntity());
        target.setRemark(source.getRemark());
        target.setPreferredConflictAction(source.getPreferredConflictAction());
        target.setPreferredBindUserId(source.getPreferredBindUserId());
        return target;
    }

    private void mapList(HrOnboarding source, HrOnboardingListVo target)
    {
        target.setOnboardingId(source.getOnboardingId());
        target.setOnboardingNo(source.getOnboardingNo());
        target.setEmployeeName(source.getEmployeeName());
        target.setPhoneNumberMasked(source.getPhoneNumberMasked() == null
                ? maskPhone(source.getPhoneNumber()) : source.getPhoneNumberMasked());
        target.setTargetDeptId(source.getTargetDeptId());
        target.setTargetStoreId(source.getTargetStoreId());
        target.setTargetPostId(source.getTargetPostId());
        target.setCompanyName(source.getCompanyName());
        target.setDeptLevel1Name(source.getDeptLevel1Name());
        target.setDeptLevel2Name(source.getDeptLevel2Name());
        target.setDeptLevel3Name(source.getDeptLevel3Name());
        target.setStoreName(source.getStoreName());
        target.setPositionName(source.getPositionName());
        target.setEmployeeCategory(source.getEmployeeCategory());
        target.setExpectedEntryDate(source.getExpectedEntryDate());
        target.setStatus(source.getStatus());
        target.setOwnerUserId(source.getOwnerUserId());
        target.setOwnerName(source.getOwnerName());
        target.setVersion(source.getVersion());
    }
}
