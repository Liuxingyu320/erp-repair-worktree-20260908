package com.erp.system.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HrEmployeeCompletenessSnapshot
{
    private final int completionPercent;
    private final int completedFieldCount;
    private final int applicableFieldCount;
    private final int notApplicableFieldCount;
    private final int trackedFieldCount;
    private final List<String> missingFields;
    private final int requiredCompletionPercent;
    private final int requiredCompletedFieldCount;
    private final int requiredApplicableFieldCount;
    private final List<String> missingRequiredFields;
    private final List<String> missingOptionalFields;
    private final Map<String, List<String>> missingByResponsibility;

    public HrEmployeeCompletenessSnapshot(int completionPercent, int completedFieldCount,
            int applicableFieldCount, int notApplicableFieldCount, int trackedFieldCount,
            List<String> missingFields)
    {
        this(completionPercent, completedFieldCount, applicableFieldCount, notApplicableFieldCount,
                trackedFieldCount, missingFields, completionPercent, completedFieldCount,
                applicableFieldCount, missingFields, Collections.emptyList(), Collections.emptyMap());
    }

    public HrEmployeeCompletenessSnapshot(int completionPercent, int completedFieldCount,
            int applicableFieldCount, int notApplicableFieldCount, int trackedFieldCount,
            List<String> missingFields, int requiredCompletionPercent,
            int requiredCompletedFieldCount, int requiredApplicableFieldCount,
            List<String> missingRequiredFields, List<String> missingOptionalFields,
            Map<String, List<String>> missingByResponsibility)
    {
        this.completionPercent = completionPercent;
        this.completedFieldCount = completedFieldCount;
        this.applicableFieldCount = applicableFieldCount;
        this.notApplicableFieldCount = notApplicableFieldCount;
        this.trackedFieldCount = trackedFieldCount;
        this.missingFields = Collections.unmodifiableList(List.copyOf(missingFields));
        this.requiredCompletionPercent = requiredCompletionPercent;
        this.requiredCompletedFieldCount = requiredCompletedFieldCount;
        this.requiredApplicableFieldCount = requiredApplicableFieldCount;
        this.missingRequiredFields = Collections.unmodifiableList(List.copyOf(missingRequiredFields));
        this.missingOptionalFields = Collections.unmodifiableList(List.copyOf(missingOptionalFields));
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        if (missingByResponsibility != null)
            missingByResponsibility.forEach((key, value) -> grouped.put(key,
                    Collections.unmodifiableList(List.copyOf(value == null ? Collections.emptyList() : value))));
        this.missingByResponsibility = Collections.unmodifiableMap(grouped);
    }

    public int getCompletionPercent() { return completionPercent; }
    public int getCompletedFieldCount() { return completedFieldCount; }
    public int getApplicableFieldCount() { return applicableFieldCount; }
    public int getNotApplicableFieldCount() { return notApplicableFieldCount; }
    public int getTrackedFieldCount() { return trackedFieldCount; }
    public List<String> getMissingFields() { return missingFields; }
    /** Explicit alias for the compatible coverage metric. */
    public int getCoveragePercent() { return completionPercent; }
    public int getRequiredCompletionPercent() { return requiredCompletionPercent; }
    public int getRequiredCompletedFieldCount() { return requiredCompletedFieldCount; }
    public int getRequiredApplicableFieldCount() { return requiredApplicableFieldCount; }
    public List<String> getMissingRequiredFields() { return missingRequiredFields; }
    public List<String> getMissingOptionalFields() { return missingOptionalFields; }
    public Map<String, List<String>> getMissingByResponsibility() { return missingByResponsibility; }
}
