package com.erp.system.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Canonical 35-field onboarding spreadsheet and completeness registry.
 */
@Component
public class HrOnboardingFieldRegistry
{
    public enum StorageOwner { SYS_USER, SYS_USER_PROFILE, RELATION, DERIVED, ONBOARDING }
    public enum MaskingClass { NONE, PHONE, ID_NUMBER, BANK_ACCOUNT, ADDRESS }

    private final List<FieldRule> fields;
    private final Map<String, FieldRule> headers;
    private final Map<String, FieldRule> keys;

    public HrOnboardingFieldRegistry()
    {
        List<FieldRule> entries = new ArrayList<>();
        entries.add(field("employeeName", "姓名", StorageOwner.SYS_USER, false, MaskingClass.NONE, "BASIC", true));
        entries.add(field("employeeNo", "工号", StorageOwner.SYS_USER_PROFILE, true, MaskingClass.NONE, "EMPLOYMENT", true));
        entries.add(field("companyName", "所属公司", StorageOwner.DERIVED, true, MaskingClass.NONE, "ORGANIZATION", false));
        entries.add(field("deptLevel1Name", "1级部门", StorageOwner.DERIVED, true, MaskingClass.NONE, "ORGANIZATION", false));
        entries.add(field("deptLevel2Name", "2级部门", StorageOwner.DERIVED, true, MaskingClass.NONE, "ORGANIZATION", false));
        entries.add(field("deptLevel3Name", "3级部门", StorageOwner.DERIVED, true, MaskingClass.NONE, "ORGANIZATION", false));
        entries.add(field("storeName", "4级门店", StorageOwner.DERIVED, true, MaskingClass.NONE, "ORGANIZATION", false));
        entries.add(field("positionName", "职位", StorageOwner.DERIVED, true, MaskingClass.NONE, "ORGANIZATION", false));
        entries.add(field("jobGrade", "职级", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "ORGANIZATION", true));
        entries.add(field("phoneNumber", "手机号", StorageOwner.SYS_USER, false, MaskingClass.PHONE, "BASIC", true));
        entries.add(field("departmentSupervisor", "部门主管", StorageOwner.DERIVED, true, MaskingClass.NONE, "ORGANIZATION", false));
        entries.add(field("directSupervisorUserId", "直属主管", StorageOwner.RELATION, false, MaskingClass.NONE, "ORGANIZATION", false));
        entries.add(field("employeeStatus", "员工状态", StorageOwner.SYS_USER_PROFILE, true, MaskingClass.NONE, "EMPLOYMENT", true));
        entries.add(field("employeeCategory", "人员类别", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "EMPLOYMENT", true));
        entries.add(field("sex", "性别", StorageOwner.SYS_USER, false, MaskingClass.NONE, "BASIC", true));
        entries.add(field("birthDate", "出生日期", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "BASIC", true));
        entries.add(field("idType", "证件类型", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "IDENTITY", true));
        entries.add(field("idNumber", "证件号码", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.ID_NUMBER, "IDENTITY", true));
        entries.add(field("registeredResidence", "户口所在地", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.ADDRESS, "IDENTITY", true));
        entries.add(field("currentAddress", "现居住地址", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.ADDRESS, "IDENTITY", true));
        entries.add(field("maritalStatus", "婚姻状况", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "BASIC", true));
        entries.add(field("ethnicity", "民族", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "BASIC", true));
        entries.add(field("emergencyContact", "紧急联系人", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "CONTACT", true));
        entries.add(field("emergencyContactRelation", "与紧急联系人关系", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "CONTACT", true));
        entries.add(field("emergencyContactPhone", "紧急联系人电话", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.PHONE, "CONTACT", true));
        entries.add(field("expectedEntryDate", "预计入职日期", StorageOwner.ONBOARDING, false, MaskingClass.NONE, "EMPLOYMENT", true));
        entries.add(field("positionName", "入职岗位", StorageOwner.DERIVED, true, MaskingClass.NONE, "ORGANIZATION", false));
        entries.add(field("jobGrade", "岗位职级", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "ORGANIZATION", false));
        entries.add(field("workLocation", "工作所在地", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "EMPLOYMENT", true));
        entries.add(field("workCityLevel", "工作所在城市级别", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "EMPLOYMENT", true));
        entries.add(field("bankName", "开户银行", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "BANK", true));
        entries.add(field("bankAccount", "银行卡号", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.BANK_ACCOUNT, "BANK", true));
        entries.add(field("contractType", "合同类型", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "EMPLOYMENT", true));
        entries.add(field("socialType", "社保类型", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "EMPLOYMENT", true));
        entries.add(field("legalEntity", "法人单位", StorageOwner.SYS_USER_PROFILE, false, MaskingClass.NONE, "EMPLOYMENT", true));
        fields = Collections.unmodifiableList(entries);

        Map<String, FieldRule> byKey = new LinkedHashMap<>();
        Map<String, FieldRule> byHeader = new LinkedHashMap<>();
        for (FieldRule entry : entries)
        {
            byKey.putIfAbsent(entry.getKey(), entry);
        }
        for (FieldRule entry : entries)
        {
            byHeader.put(entry.getLabel(), byKey.get(entry.getKey()));
        }
        alias(byHeader, "入职日期", byKey.get("expectedEntryDate"));
        alias(byHeader, "户籍地址", byKey.get("registeredResidence"));
        headers = Collections.unmodifiableMap(byHeader);
        keys = Collections.unmodifiableMap(byKey);
    }

    private static FieldRule field(String key, String label, StorageOwner storageOwner,
            boolean derived, MaskingClass maskingClass, String group, boolean profileCompleteness)
    {
        return new FieldRule(key, label, storageOwner, derived, maskingClass, group, profileCompleteness,
                storageLimit(key));
    }

    private static int storageLimit(String key)
    {
        switch(key)
        {
            case "employeeName": case "employeeNo": case "jobGrade": case "employeeStatus":
            case "employeeCategory": case "idType": case "idNumber": case "ethnicity":
            case "emergencyContactRelation": case "workCityLevel": case "bankAccount":
            case "probationPeriod": return 64;
            case "phoneNumber": case "emergencyContactPhone": case "maritalStatus":
            case "contractType": case "socialType": return 32;
            case "companyName": case "deptLevel1Name": case "deptLevel2Name": case "deptLevel3Name":
            case "storeName": case "positionName": case "departmentSupervisor":
            case "directSupervisorUserId": case "emergencyContact": case "workLocation":
            case "bankName": case "legalEntity": return 100;
            case "registeredResidence": case "currentAddress": return 255;
            case "sex": return 1;
            case "birthDate": case "expectedEntryDate": return 10;
            default: return 255;
        }
    }

    private static void alias(Map<String, FieldRule> headers, String alias, FieldRule field)
    {
        headers.put(alias, field);
    }

    public List<FieldRule> getFields() { return fields; }
    public List<FieldRule> getBusinessFields() { return Collections.unmodifiableList(new ArrayList<>(keys.values())); }
    public List<String> getAcceptedHeaders() { return Collections.unmodifiableList(new ArrayList<>(headers.keySet())); }
    public FieldRule resolveHeader(String header) { return header == null ? null : headers.get(header.trim()); }
    public FieldRule getByKey(String key) { return keys.get(key); }

    public FieldRule validationSource(String key, String label, String metadataSourceKey)
    {
        FieldRule source = keys.get(metadataSourceKey);
        if (source == null) throw new IllegalArgumentException("Unknown metadata source: " + metadataSourceKey);
        return new FieldRule(key, label, source.storageOwner, false, source.maskingClass,
                source.completenessGroup, false,source.maxLength);
    }

    public static final class FieldRule
    {
        private final String key;
        private final String label;
        private final StorageOwner storageOwner;
        private final boolean derivedOrSystemGenerated;
        private final MaskingClass maskingClass;
        private final String completenessGroup;
        private final boolean profileCompleteness;
        private final int maxLength;

        private FieldRule(String key, String label, StorageOwner storageOwner, boolean derivedOrSystemGenerated,
                MaskingClass maskingClass, String completenessGroup, boolean profileCompleteness,int maxLength)
        {
            this.key = key;
            this.label = label;
            this.storageOwner = storageOwner;
            this.derivedOrSystemGenerated = derivedOrSystemGenerated;
            this.maskingClass = maskingClass;
            this.completenessGroup = completenessGroup;
            this.profileCompleteness = profileCompleteness;
            this.maxLength=maxLength;
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public StorageOwner getStorageOwner() { return storageOwner; }
        public boolean isDerivedOrSystemGenerated() { return derivedOrSystemGenerated; }
        public MaskingClass getMaskingClass() { return maskingClass; }
        public String getCompletenessGroup() { return completenessGroup; }
        public boolean isProfileCompleteness() { return profileCompleteness; }
        public int getMaxLength(){return maxLength;}
    }
}
