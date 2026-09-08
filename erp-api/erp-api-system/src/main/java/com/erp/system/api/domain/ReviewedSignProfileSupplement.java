package com.erp.system.api.domain;

/**
 * HR-reviewed personal facts that the OA signing workflow may supplement on an
 * employee profile.
 *
 * <p>The deliberately closed contract prevents contract, salary, job grade,
 * legal-entity, service-person classification, and insurance decisions from
 * crossing this boundary.</p>
 */
public class ReviewedSignProfileSupplement
{
    private String requestId;

    private Long employeeId;

    /** SHA-256 of the five System-owned signing profile facts read by OA. */
    private String expectedProfileHash;

    private String currentAddress;

    /** STUDENT or NON_STUDENT. */
    private String studentStatus;

    private String schoolName;

    /** RETIRED or NOT_RETIRED. */
    private String retirementStatus;

    /** Employee-declared month in yyyy-MM form, used by the minor declaration. */
    private String incomeStartYearMonth;

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public Long getEmployeeId()
    {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId)
    {
        this.employeeId = employeeId;
    }

    public String getExpectedProfileHash()
    {
        return expectedProfileHash;
    }

    public void setExpectedProfileHash(String expectedProfileHash)
    {
        this.expectedProfileHash = expectedProfileHash;
    }

    public String getCurrentAddress()
    {
        return currentAddress;
    }

    public void setCurrentAddress(String currentAddress)
    {
        this.currentAddress = currentAddress;
    }

    public String getStudentStatus()
    {
        return studentStatus;
    }

    public void setStudentStatus(String studentStatus)
    {
        this.studentStatus = studentStatus;
    }

    public String getSchoolName()
    {
        return schoolName;
    }

    public void setSchoolName(String schoolName)
    {
        this.schoolName = schoolName;
    }

    public String getRetirementStatus()
    {
        return retirementStatus;
    }

    public void setRetirementStatus(String retirementStatus)
    {
        this.retirementStatus = retirementStatus;
    }

    public String getIncomeStartYearMonth()
    {
        return incomeStartYearMonth;
    }

    public void setIncomeStartYearMonth(String incomeStartYearMonth)
    {
        this.incomeStartYearMonth = incomeStartYearMonth;
    }
}
