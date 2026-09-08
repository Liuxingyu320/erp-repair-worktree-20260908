package com.erp.oa.domain;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class OaSignPlan extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long planId;

    @NotBlank(message = "方案名称不能为空")
    @Size(max = 120, message = "方案名称长度不能超过120个字符")
    @Excel(name = "方案名称")
    private String planName;

    @NotBlank(message = "签约场景不能为空")
    @Excel(name = "签约场景")
    private String scenario;

    @Excel(name = "适用岗位")
    private String postName;

    @Excel(name = "用工类型")
    private String employmentType;

    @Excel(name = "社保口径")
    private String socialType;

    @Excel(name = "劳务人员类型")
    private String servicePersonType;

    @Excel(name = "保险类型")
    private String insuranceType;

    @Excel(name = "岗位等级")
    private String postLevelSnapshot;

    private String salaryVersion;
    private String entryDate;
    private String contractStartDate;
    private String contractEndDate;
    private String probationStartDate;
    private String probationEndDate;
    private BigDecimal baseSalary;
    private BigDecimal postSalary;
    private BigDecimal fieldAllowance;
    private BigDecimal salaryTotal;
    private Long shopDeptId;
    private Long legalEntityId;
    private String legalEntityName;
    private String ruleJson;
    private String defaultValuesJson;
    private Integer signDeadlineDays;
    private String reminderPolicyJson;
    private String autoSendConditionJson;

    @Excel(name = "状态", readConverterExp = "0=启用,1=停用")
    private String status;

    private Integer sortOrder;
    private List<OaSignPlanTemplate> templates;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public String getPostName() { return postName; }
    public void setPostName(String postName) { this.postName = postName; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public String getSocialType() { return socialType; }
    public void setSocialType(String socialType) { this.socialType = socialType; }

    public String getServicePersonType() { return servicePersonType; }
    public void setServicePersonType(String servicePersonType) { this.servicePersonType = servicePersonType; }

    public String getInsuranceType() { return insuranceType; }
    public void setInsuranceType(String insuranceType) { this.insuranceType = insuranceType; }

    public String getPostLevelSnapshot() { return postLevelSnapshot; }
    public void setPostLevelSnapshot(String postLevelSnapshot) { this.postLevelSnapshot = postLevelSnapshot; }

    public String getSalaryVersion() { return salaryVersion; }
    public void setSalaryVersion(String salaryVersion) { this.salaryVersion = salaryVersion; }

    public String getEntryDate() { return entryDate; }
    public void setEntryDate(String entryDate) { this.entryDate = entryDate; }

    public String getContractStartDate() { return contractStartDate; }
    public void setContractStartDate(String contractStartDate) { this.contractStartDate = contractStartDate; }

    public String getContractEndDate() { return contractEndDate; }
    public void setContractEndDate(String contractEndDate) { this.contractEndDate = contractEndDate; }

    public String getProbationStartDate() { return probationStartDate; }
    public void setProbationStartDate(String probationStartDate) { this.probationStartDate = probationStartDate; }

    public String getProbationEndDate() { return probationEndDate; }
    public void setProbationEndDate(String probationEndDate) { this.probationEndDate = probationEndDate; }

    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }

    public BigDecimal getPostSalary() { return postSalary; }
    public void setPostSalary(BigDecimal postSalary) { this.postSalary = postSalary; }

    public BigDecimal getFieldAllowance() { return fieldAllowance; }
    public void setFieldAllowance(BigDecimal fieldAllowance) { this.fieldAllowance = fieldAllowance; }

    public BigDecimal getSalaryTotal() { return salaryTotal; }
    public void setSalaryTotal(BigDecimal salaryTotal) { this.salaryTotal = salaryTotal; }

    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }

    public Long getLegalEntityId() { return legalEntityId; }
    public void setLegalEntityId(Long legalEntityId) { this.legalEntityId = legalEntityId; }

    public String getLegalEntityName() { return legalEntityName; }
    public void setLegalEntityName(String legalEntityName) { this.legalEntityName = legalEntityName; }

    public String getRuleJson() { return ruleJson; }
    public void setRuleJson(String ruleJson) { this.ruleJson = ruleJson; }

    public String getDefaultValuesJson() { return defaultValuesJson; }
    public void setDefaultValuesJson(String defaultValuesJson) { this.defaultValuesJson = defaultValuesJson; }

    public Integer getSignDeadlineDays() { return signDeadlineDays; }
    public void setSignDeadlineDays(Integer signDeadlineDays) { this.signDeadlineDays = signDeadlineDays; }

    public String getReminderPolicyJson() { return reminderPolicyJson; }
    public void setReminderPolicyJson(String reminderPolicyJson) { this.reminderPolicyJson = reminderPolicyJson; }

    public String getAutoSendConditionJson() { return autoSendConditionJson; }
    public void setAutoSendConditionJson(String autoSendConditionJson) { this.autoSendConditionJson = autoSendConditionJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public List<OaSignPlanTemplate> getTemplates() { return templates; }
    public void setTemplates(List<OaSignPlanTemplate> templates) { this.templates = templates; }
}
