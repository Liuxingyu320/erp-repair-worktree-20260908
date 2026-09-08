package com.erp.system.domain.hr;

import java.util.ArrayList;
import java.util.List;

/**
 * 人事档案完整度结果。
 */
public class HrCompletenessResult
{
    private int onboardingCompleteness;
    private int profileCompleteness;
    private Boolean onboardingComplete;
    private List<String> missingOnboardingFields = new ArrayList<>();
    private List<String> missingProfileFields = new ArrayList<>();

    public int getOnboardingCompleteness()
    {
        return onboardingCompleteness;
    }

    public void setOnboardingCompleteness(int onboardingCompleteness)
    {
        this.onboardingCompleteness = onboardingCompleteness;
    }

    public int getProfileCompleteness()
    {
        return profileCompleteness;
    }

    public void setProfileCompleteness(int profileCompleteness)
    {
        this.profileCompleteness = profileCompleteness;
    }

    public Boolean getOnboardingComplete()
    {
        return onboardingComplete;
    }

    public void setOnboardingComplete(Boolean onboardingComplete)
    {
        this.onboardingComplete = onboardingComplete;
    }

    public List<String> getMissingOnboardingFields()
    {
        return missingOnboardingFields;
    }

    public void setMissingOnboardingFields(List<String> missingOnboardingFields)
    {
        this.missingOnboardingFields = missingOnboardingFields;
    }

    public List<String> getMissingProfileFields()
    {
        return missingProfileFields;
    }

    public void setMissingProfileFields(List<String> missingProfileFields)
    {
        this.missingProfileFields = missingProfileFields;
    }
}
