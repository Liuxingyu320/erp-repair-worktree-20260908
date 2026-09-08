package com.erp.system.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.HrOnboardingPositionConfig;
import com.erp.system.domain.vo.HrOnboardingCompletionVo;
import com.erp.system.domain.vo.HrOnboardingCompletionVo.MissingField;
import com.erp.system.service.ISysConfigService;
import com.erp.system.support.HrOnboardingFieldRegistry;
import com.erp.system.support.HrOnboardingFieldRegistry.FieldRule;

/**
 * Single backend source for onboarding validation, completeness and actions.
 */
@Service
public class HrOnboardingRuleService
{
    private static final String POST_ENTRY_DUE_DAYS_KEY = "hr.onboarding.post_entry_due_days";
    private static final int DEFAULT_POST_ENTRY_DUE_DAYS = 7;

    public static final String ACTION_EDIT = "EDIT";
    public static final String ACTION_MARK_READY = "MARK_READY";
    public static final String ACTION_RETURN_TO_DRAFT = "RETURN_TO_DRAFT";
    public static final String ACTION_CONFIRM = "CONFIRM";
    public static final String ACTION_CANCEL = "CANCEL";
    public static final String ACTION_RESTORE = "RESTORE";

    public static final String BLOCK_DERIVED_COMPANY_MISSING = "DERIVED_COMPANY_MISSING";
    public static final String BLOCK_DERIVED_DEPARTMENT_SUPERVISOR_MISSING = "DERIVED_DEPARTMENT_SUPERVISOR_MISSING";
    public static final String BLOCK_DERIVED_STORE_MISSING = "DERIVED_STORE_MISSING";
    public static final String BLOCK_DERIVED_POSITION_MISSING = "DERIVED_POSITION_MISSING";

    private static final List<String> CREATE_KEYS = Collections.unmodifiableList(Arrays.asList(
            "employeeName", "phoneNumber", "expectedEntryDate", "targetDeptId", "targetPostId",
            "employeeCategory", "ownerUserId"));

    private static final List<String> READY_KEYS = Collections.unmodifiableList(Arrays.asList(
            "employeeName", "phoneNumber", "expectedEntryDate", "targetDeptId", "targetPostId",
            "employeeCategory", "ownerUserId", "targetStoreId", "jobGrade", "directSupervisorUserId",
            "sex", "idType", "idNumber", "registeredResidence", "currentAddress", "workLocation"));

    private final HrOnboardingFieldRegistry registry;
    private final ISysConfigService configService;
    private final Clock clock;

    @Autowired
    public HrOnboardingRuleService(HrOnboardingFieldRegistry registry, ISysConfigService configService)
    {
        this(registry, configService, Clock.systemDefaultZone());
    }

    HrOnboardingRuleService(HrOnboardingFieldRegistry registry, ISysConfigService configService, Clock clock)
    {
        this.registry = registry;
        this.configService = configService;
        this.clock = clock;
    }

    public HrOnboardingCompletionVo evaluateCreate(HrOnboarding item)
    {
        HrOnboardingCompletionVo result = evaluateOnboarding(item, CREATE_KEYS);
        result.setAllowedActions(actions(item, false, false, "CREATE"));
        return result;
    }

    public HrOnboardingCompletionVo evaluateReady(HrOnboarding item)
    {
        return evaluateReady(item,resolvePostEntryDueDays());
    }

    public Map<Long,HrOnboardingCompletionVo> evaluateReadyBatch(List<HrOnboarding> items)
    {
        if(items==null||items.isEmpty())return Collections.emptyMap();
        int dueDays=resolvePostEntryDueDays();
        Map<Long,HrOnboardingCompletionVo> result=new LinkedHashMap<>();
        for(HrOnboarding item:items)
            if(item!=null&&item.getOnboardingId()!=null)result.put(item.getOnboardingId(),evaluateReady(item,dueDays));
        return result;
    }

    private HrOnboardingCompletionVo evaluateReady(HrOnboarding item,int dueDays)
    {
        HrOnboardingCompletionVo result = evaluateOnboarding(item, READY_KEYS,dueDays);
        applyDerivedBlockingCodes(result, item);
        boolean canMarkReady = result.getMissingFields().isEmpty() && result.getBlockingCodes().isEmpty();
        result.setAllowedActions(actions(item, canMarkReady, false, "READY"));
        return result;
    }

    public HrOnboardingCompletionVo evaluateConfirm(HrOnboarding item, HrOnboardingPositionConfig config)
    {
        List<String> requiredKeys = new ArrayList<>(READY_KEYS);
        boolean configurationRisk = config == null || !"0".equals(config.getStatus());
        if (!configurationRisk)
        {
            configurationRisk |= applyConditionalMode(requiredKeys, "contractType", config.getContractTypeMode());
            configurationRisk |= applyConditionalMode(requiredKeys, "socialType", config.getSocialTypeMode());
            configurationRisk |= applyConditionalMode(requiredKeys, "probationPeriod", config.getProbationPeriodMode());
        }

        HrOnboardingCompletionVo result = evaluateOnboarding(item, requiredKeys,resolvePostEntryDueDays());
        applyDerivedBlockingCodes(result, item);
        if (item == null || !HrOnboarding.STATUS_READY.equals(item.getStatus()))
        {
            result.getBlockingCodes().add("STATUS_NOT_READY");
        }
        if (item == null || item.getActualEntryDate() == null)
        {
            result.getBlockingCodes().add("ACTUAL_ENTRY_DATE_REQUIRED");
        }
        if (configurationRisk)
        {
            result.getRiskCodes().add("ACCOUNT_CONFIGURATION_MISSING");
        }
        boolean canConfirm = result.getMissingFields().isEmpty() && result.getBlockingCodes().isEmpty();
        result.setAllowedActions(actions(item, false, canConfirm, "CONFIRM"));
        return result;
    }

    private boolean applyConditionalMode(List<String> requiredKeys, String key, String mode)
    {
        if ("REQUIRED".equals(mode))
        {
            requiredKeys.add(key);
            return false;
        }
        if ("OPTIONAL".equals(mode) || "NOT_APPLICABLE".equals(mode))
        {
            return false;
        }
        return true;
    }

    /**
     * Evaluates the profile fields that are presently shared with the 35-field onboarding registry.
     * Task 8's HrEmployeeFieldRegistry extends this rule source to the approved 68-field employee-master contract.
     */
    public HrOnboardingCompletionVo evaluateProfile(SysUser user)
    {
        List<FieldRule> profileRules = new ArrayList<>();
        for (FieldRule rule : registry.getBusinessFields())
        {
            if (rule.isProfileCompleteness() && isProfileReadable(rule.getKey())) profileRules.add(rule);
        }

        HrOnboardingCompletionVo result = new HrOnboardingCompletionVo();
        for (FieldRule rule : profileRules)
        {
            if (isEmpty(profileValue(user, rule.getKey()))) addMissing(result, rule);
        }
        result.setPercentage(percentage(profileRules.size(), result.getMissingFields().size()));
        return result;
    }

    private boolean isProfileReadable(String key)
    {
        return !"expectedEntryDate".equals(key) && !"ownerUserId".equals(key)
                && !"actualEntryDate".equals(key);
    }

    private Object profileValue(SysUser user, String key)
    {
        if (user == null) return null;
        if ("employeeName".equals(key)) return user.getNickName();
        if ("phoneNumber".equals(key)) return user.getPhonenumber();
        if ("sex".equals(key)) return user.getSex();
        SysUserProfile profile = user.getProfile();
        if (profile == null) return null;
        switch (key)
        {
            case "companyName": return profile.getCompanyName();
            case "employeeNo": return profile.getEmployeeNo();
            case "deptLevel1Name": return profile.getDeptLevel1Name();
            case "deptLevel2Name": return profile.getDeptLevel2Name();
            case "deptLevel3Name": return profile.getDeptLevel3Name();
            case "storeName": return profile.getStoreName();
            case "positionName": return profile.getPositionNames();
            case "jobGrade": return profile.getJobGrade();
            case "departmentSupervisor": return profile.getDepartmentSupervisor();
            case "employeeStatus": return profile.getEmployeeStatus();
            case "employeeCategory": return profile.getEmployeeCategory();
            case "birthDate": return profile.getBirthDate();
            case "idType": return profile.getIdType();
            case "idNumber": return profile.getIdNumber();
            case "registeredResidence": return profile.getRegisteredResidence();
            case "currentAddress": return profile.getCurrentAddress();
            case "maritalStatus": return profile.getMaritalStatus();
            case "ethnicity": return profile.getEthnicity();
            case "emergencyContact": return profile.getEmergencyContact();
            case "emergencyContactRelation": return profile.getEmergencyContactRelation();
            case "emergencyContactPhone": return profile.getEmergencyContactPhone();
            case "workLocation": return profile.getWorkLocation();
            case "workCityLevel": return profile.getWorkCityLevel();
            case "legalEntity": return profile.getLegalEntity();
            case "bankName": return profile.getBankName();
            case "bankAccount": return profile.getBankAccount();
            case "contractType": return profile.getContractType();
            case "socialType": return profile.getSocialType();
            default: return null;
        }
    }

    private void applyDerivedBlockingCodes(HrOnboardingCompletionVo result, HrOnboarding item)
    {
        if (item == null) return;
        if (item.getTargetDeptId() != null)
        {
            if (isEmpty(item.getCompanyName())) result.getBlockingCodes().add(BLOCK_DERIVED_COMPANY_MISSING);
            boolean hasDepartmentLayer = !isEmpty(item.getDeptLevel1Name())
                    || !isEmpty(item.getDeptLevel2Name()) || !isEmpty(item.getDeptLevel3Name());
            if (hasDepartmentLayer && isEmpty(item.getDepartmentSupervisor()))
            {
                result.getBlockingCodes().add(BLOCK_DERIVED_DEPARTMENT_SUPERVISOR_MISSING);
            }
        }
        if (item.getTargetStoreId() != null && isEmpty(item.getStoreName()))
        {
            result.getBlockingCodes().add(BLOCK_DERIVED_STORE_MISSING);
        }
        if (item.getTargetPostId() != null && isEmpty(item.getPositionName()))
        {
            result.getBlockingCodes().add(BLOCK_DERIVED_POSITION_MISSING);
        }
    }

    private HrOnboardingCompletionVo evaluateOnboarding(HrOnboarding item, List<String> requiredKeys)
    {
        return evaluateOnboarding(item,requiredKeys,resolvePostEntryDueDays());
    }

    private HrOnboardingCompletionVo evaluateOnboarding(HrOnboarding item, List<String> requiredKeys,int dueDays)
    {
        HrOnboardingCompletionVo result = new HrOnboardingCompletionVo();
        for (String key : requiredKeys)
        {
            if (isEmpty(onboardingValue(item, key))) addMissing(result, fieldFor(key));
        }
        result.setRequiredFieldCount(requiredKeys.size());
        result.setCompletedFieldCount(requiredKeys.size() - result.getMissingFields().size());
        result.setPercentage(percentage(result.getRequiredFieldCount(), result.getMissingFields().size()));
        applyPostEntryDeadline(result, item,dueDays);
        return result;
    }

    private void applyPostEntryDeadline(HrOnboardingCompletionVo result, HrOnboarding item,int dueDays)
    {
        if (item == null || item.getActualEntryDate() == null) return;
        LocalDate actualEntryDate = Instant.ofEpochMilli(item.getActualEntryDate().getTime())
                .atZone(clock.getZone()).toLocalDate();
        LocalDate dueDate = actualEntryDate.plusDays(dueDays);
        result.setPostEntryDueDate(Date.from(dueDate.atStartOfDay(clock.getZone()).toInstant()));
        result.setPostEntryOverdue(LocalDate.now(clock).isAfter(dueDate));
    }

    private int resolvePostEntryDueDays()
    {
        if (configService == null) return DEFAULT_POST_ENTRY_DUE_DAYS;
        String configuredValue = configService.selectConfigByKey(POST_ENTRY_DUE_DAYS_KEY);
        if (configuredValue == null) return DEFAULT_POST_ENTRY_DUE_DAYS;
        try
        {
            int configuredDays = Integer.parseInt(configuredValue.trim());
            return configuredDays > 0 ? configuredDays : DEFAULT_POST_ENTRY_DUE_DAYS;
        }
        catch (NumberFormatException ignored)
        {
            return DEFAULT_POST_ENTRY_DUE_DAYS;
        }
    }

    private int percentage(int total, int missing)
    {
        if (total == 0) return 100;
        return (total - missing) * 100 / total;
    }

    private void addMissing(HrOnboardingCompletionVo result, FieldRule rule)
    {
        result.getMissingFields().add(rule.getKey());
        result.getMissingLabels().add(rule.getLabel());
        result.getGroupedMissingFields()
                .computeIfAbsent(rule.getCompletenessGroup(), ignored -> new ArrayList<>())
                .add(new MissingField(rule.getKey(), rule.getLabel()));
    }

    private FieldRule fieldFor(String key)
    {
        FieldRule rule = registry.getByKey(key);
        if (rule != null) return rule;
        return validationFields().get(key);
    }

    private Map<String, FieldRule> validationFields()
    {
        Map<String, FieldRule> result = new LinkedHashMap<>();
        result.put("targetDeptId", registry.validationSource("targetDeptId", "目标组织", "storeName"));
        result.put("targetStoreId", registry.validationSource("targetStoreId", "目标门店", "storeName"));
        result.put("targetPostId", registry.validationSource("targetPostId", "目标岗位", "positionName"));
        result.put("ownerUserId", registry.validationSource("ownerUserId", "入职负责人", "departmentSupervisor"));
        result.put("probationPeriod", registry.validationSource("probationPeriod", "试用期", "contractType"));
        return result;
    }

    private Object onboardingValue(HrOnboarding item, String key)
    {
        if (item == null) return null;
        switch (key)
        {
            case "employeeName": return item.getEmployeeName();
            case "phoneNumber": return item.getPhoneNumber();
            case "expectedEntryDate": return item.getExpectedEntryDate();
            case "targetDeptId": return item.getTargetDeptId();
            case "targetStoreId": return item.getTargetStoreId();
            case "targetPostId": return item.getTargetPostId();
            case "employeeCategory": return item.getEmployeeCategory();
            case "ownerUserId": return item.getOwnerUserId();
            case "jobGrade": return item.getJobGrade();
            case "directSupervisorUserId": return item.getDirectSupervisorUserId();
            case "sex": return item.getSex();
            case "birthDate": return item.getBirthDate();
            case "idType": return item.getIdType();
            case "idNumber": return item.getIdNumber();
            case "registeredResidence": return item.getRegisteredResidence();
            case "currentAddress": return item.getCurrentAddress();
            case "maritalStatus": return item.getMaritalStatus();
            case "ethnicity": return item.getEthnicity();
            case "emergencyContact": return item.getEmergencyContact();
            case "emergencyContactRelation": return item.getEmergencyContactRelation();
            case "emergencyContactPhone": return item.getEmergencyContactPhone();
            case "workLocation": return item.getWorkLocation();
            case "workCityLevel": return item.getWorkCityLevel();
            case "legalEntity": return item.getLegalEntity();
            case "contractType": return item.getContractType();
            case "socialType": return item.getSocialType();
            case "probationPeriod": return item.getProbationPeriod();
            default: return null;
        }
    }

    private boolean isEmpty(Object value)
    {
        return value == null || value instanceof String && ((String) value).trim().isEmpty();
    }

    private List<String> actions(HrOnboarding item, boolean readyComplete, boolean confirmComplete, String stage)
    {
        String status = item == null || item.getStatus() == null ? HrOnboarding.STATUS_DRAFT : item.getStatus();
        if (HrOnboarding.STATUS_CANCELLED.equals(status)) return new ArrayList<>(Collections.singletonList(ACTION_RESTORE));
        if (HrOnboarding.STATUS_CONFIRMED.equals(status)) return new ArrayList<>();
        if (HrOnboarding.STATUS_READY.equals(status))
        {
            List<String> result = new ArrayList<>(Arrays.asList(ACTION_EDIT, ACTION_RETURN_TO_DRAFT));
            if ("CONFIRM".equals(stage) && confirmComplete) result.add(ACTION_CONFIRM);
            result.add(ACTION_CANCEL);
            return result;
        }
        List<String> result = new ArrayList<>(Collections.singletonList(ACTION_EDIT));
        if ("READY".equals(stage) && readyComplete) result.add(ACTION_MARK_READY);
        result.add(ACTION_CANCEL);
        return result;
    }

}
