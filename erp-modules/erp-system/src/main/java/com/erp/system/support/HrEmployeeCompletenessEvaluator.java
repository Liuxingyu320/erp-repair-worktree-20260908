package com.erp.system.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.erp.system.api.domain.SysUser;
import com.erp.system.support.HrEmployeeFieldRegistry.FieldDefinition;

@Component
public class HrEmployeeCompletenessEvaluator
{
    private final HrEmployeeFieldRegistry registry;

    public HrEmployeeCompletenessEvaluator(HrEmployeeFieldRegistry registry)
    {
        this.registry = registry;
    }

    public HrEmployeeCompletenessSnapshot evaluate(SysUser user)
    {
        int tracked = 0;
        int applicable = 0;
        int completed = 0;
        int requiredApplicable = 0;
        int requiredCompleted = 0;
        List<String> missing = new ArrayList<>();
        List<String> missingRequired = new ArrayList<>();
        List<String> missingOptional = new ArrayList<>();
        Map<String, List<String>> missingByResponsibility = new LinkedHashMap<>();
        for (FieldDefinition field : registry.getFields())
        {
            if (!field.isProfileCompleteness())
            {
                continue;
            }
            tracked++;
            if (!registry.isApplicableForCompleteness(user, field))
            {
                continue;
            }
            applicable++;
            boolean complete = registry.isCompleteForCompleteness(user, field);
            if (complete)
            {
                completed++;
            }
            else
            {
                missing.add(field.getKey());
            }
            if (registry.isRequiredForCompleteness(user, field))
            {
                requiredApplicable++;
                if (complete) requiredCompleted++;
                else
                {
                    missingRequired.add(field.getKey());
                    missingByResponsibility.computeIfAbsent(field.getResponsibility().name(),
                            ignored -> new ArrayList<>()).add(field.getKey());
                }
            }
            else if (!complete && field.getRequirementTier()
                    == HrEmployeeFieldRegistry.RequirementTier.OPTIONAL)
            {
                missingOptional.add(field.getKey());
            }
        }
        int percent = applicable == 0 ? 100 : (int) Math.round(completed * 100.0 / applicable);
        int requiredPercent = requiredApplicable == 0 ? 100
                : (int) Math.round(requiredCompleted * 100.0 / requiredApplicable);
        return new HrEmployeeCompletenessSnapshot(percent, completed, applicable,
                tracked - applicable, tracked, missing, requiredPercent, requiredCompleted,
                requiredApplicable, missingRequired, missingOptional, missingByResponsibility);
    }
}
