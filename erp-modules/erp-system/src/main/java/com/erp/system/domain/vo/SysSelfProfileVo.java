package com.erp.system.domain.vo;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;

/** Fixed self-profile response; credential and employee-profile fields are absent. */
public class SysSelfProfileVo
{
    private Long userId;
    private String userName;
    private String nickName;
    private String email;
    private String phonenumber;
    private String sex;
    private String currentAddress;
    private String avatar;
    private Date createTime;
    private SysUserListVo.DepartmentSummary dept;
    private Map<String, Object> profile = new LinkedHashMap<>();

    public static SysSelfProfileVo from(SysUser source)
    {
        SysSelfProfileVo target = new SysSelfProfileVo();
        target.userId = source.getUserId();
        target.userName = source.getUserName();
        target.nickName = source.getNickName();
        target.email = source.getEmail();
        target.phonenumber = source.getPhonenumber();
        target.sex = source.getSex();
        target.currentAddress = source.getProfile() == null ? null : source.getProfile().getCurrentAddress();
        target.profile = profileValues(source.getProfile());
        target.avatar = source.getAvatar();
        target.createTime = source.getCreateTime();
        if (source.getDept() != null)
        {
            target.dept = new SysUserListVo.DepartmentSummary();
            target.dept.setDeptId(source.getDept().getDeptId());
            target.dept.setDeptName(source.getDept().getDeptName());
        }
        return target;
    }

    private static Map<String, Object> profileValues(SysUserProfile source)
    {
        Map<String, Object> values = new LinkedHashMap<>();
        if (source == null) return values;

        put(values, "currentAddress", source.getCurrentAddress());
        put(values, "emergencyContact", source.getEmergencyContact());
        put(values, "emergencyContactRelation", source.getEmergencyContactRelation());
        put(values, "emergencyContactPhone", source.getEmergencyContactPhone());
        put(values, "maritalStatus", source.getMaritalStatus());
        put(values, "ethnicity", source.getEthnicity());
        put(values, "politicalStatus", source.getPoliticalStatus());

        put(values, "birthDate", source.getBirthDate());
        put(values, "idType", source.getIdType());
        put(values, "idNumberMasked", mask(source.getIdNumber(), 4, 4));
        put(values, "registeredResidenceMasked", mask(source.getRegisteredResidence(), 2, 0));
        put(values, "householdType", source.getHouseholdType());
        put(values, "nationality", source.getNationality());

        put(values, "firstEducation", source.getFirstEducation());
        put(values, "firstDegree", source.getFirstDegree());
        put(values, "firstGraduationDate", source.getFirstGraduationDate());
        put(values, "firstGraduationSchool", source.getFirstGraduationSchool());
        put(values, "firstMajor", source.getFirstMajor());
        put(values, "highestEducation", source.getHighestEducation());
        put(values, "highestDegree", source.getHighestDegree());
        put(values, "highestGraduationDate", source.getHighestGraduationDate());
        put(values, "highestGraduationSchool", source.getHighestGraduationSchool());
        put(values, "highestMajor", source.getHighestMajor());

        put(values, "bankName", source.getBankName());
        put(values, "bankAccountMasked", mask(source.getBankAccount(), 4, 4));
        put(values, "socialType", source.getSocialType());
        put(values, "socialSecurityType", source.getSocialSecurityType());
        put(values, "socialSecurityLocation", source.getSocialSecurityLocation());
        put(values, "housingFundLocation", source.getHousingFundLocation());

        put(values, "employeeNo", source.getEmployeeNo());
        put(values, "positionNo", source.getPositionNo());
        put(values, "companyName", source.getCompanyName());
        put(values, "deptLevel1Name", source.getDeptLevel1Name());
        put(values, "deptLevel2Name", source.getDeptLevel2Name());
        put(values, "deptLevel3Name", source.getDeptLevel3Name());
        put(values, "storeName", source.getStoreName());
        put(values, "positionNames", source.getPositionNames());
        put(values, "jobGrade", source.getJobGrade());
        put(values, "departmentSupervisor", source.getDepartmentSupervisor());
        put(values, "directSupervisor", source.getDirectSupervisor());
        put(values, "employeeStatus", source.getEmployeeStatus());
        put(values, "employeeCategory", source.getEmployeeCategory());
        put(values, "entryDate", source.getEntryDate());
        put(values, "workLocation", source.getWorkLocation());
        put(values, "attendanceMethod", source.getAttendanceMethod());

        put(values, "legalEntity", source.getLegalEntity());
        put(values, "contractType", source.getContractType());
        put(values, "contractTerm", source.getContractTerm());
        put(values, "contractStartDate", source.getContractStartDate());
        put(values, "contractEndDate", source.getContractEndDate());
        put(values, "renewalCount", source.getRenewalCount());
        put(values, "probationPeriod", source.getProbationPeriod());
        put(values, "probationStartDate", source.getProbationStartDate());
        put(values, "probationEndDate", source.getProbationEndDate());
        put(values, "plannedRegularizationDate", source.getPlannedRegularizationDate());
        put(values, "actualRegularizationDate", source.getActualRegularizationDate());
        return values;
    }

    private static void put(Map<String, Object> values, String key, Object value)
    {
        values.put(key, value);
    }

    private static String mask(String value, int prefixLength, int suffixLength)
    {
        if (value == null || value.isEmpty()) return value;
        int[] points = value.codePoints().toArray();
        if (points.length <= prefixLength + suffixLength) return "*".repeat(points.length);
        String prefix = new String(points, 0, prefixLength);
        String suffix = suffixLength == 0 ? ""
                : new String(points, points.length - suffixLength, suffixLength);
        return prefix + "*".repeat(points.length - prefixLength - suffixLength) + suffix;
    }

    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getNickName() { return nickName; }
    public String getEmail() { return email; }
    public String getPhonenumber() { return phonenumber; }
    public String getSex() { return sex; }
    public String getCurrentAddress() { return currentAddress; }
    public String getAvatar() { return avatar; }
    public Date getCreateTime() { return createTime; }
    public SysUserListVo.DepartmentSummary getDept() { return dept; }
    public Map<String, Object> getProfile() { return profile; }
}
