package com.erp.system.domain;

import java.util.ArrayList;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * HR岗位入职配置 hr_onboarding_position_config。
 */
public class HrOnboardingPositionConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long configId;
    private Long postId;
    private String employeeCategory;
    private String dataScopeStrategy;
    private String contractTypeMode;
    private String defaultContractType;
    private String socialTypeMode;
    private String defaultSocialType;
    private String probationPeriodMode;
    private String defaultProbationPeriod;
    private String jobGrade;
    private Boolean accountEnabled;
    private String status;
    private Integer version;
    private List<Long> roleIds = new ArrayList<Long>();

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getEmployeeCategory() { return employeeCategory; }
    public void setEmployeeCategory(String employeeCategory) { this.employeeCategory = employeeCategory; }
    public String getDataScopeStrategy() { return dataScopeStrategy; }
    public void setDataScopeStrategy(String dataScopeStrategy) { this.dataScopeStrategy = dataScopeStrategy; }
    public String getContractTypeMode() { return contractTypeMode; }
    public void setContractTypeMode(String contractTypeMode) { this.contractTypeMode = contractTypeMode; }
    public String getDefaultContractType() { return defaultContractType; }
    public void setDefaultContractType(String defaultContractType) { this.defaultContractType = defaultContractType; }
    public String getSocialTypeMode() { return socialTypeMode; }
    public void setSocialTypeMode(String socialTypeMode) { this.socialTypeMode = socialTypeMode; }
    public String getDefaultSocialType() { return defaultSocialType; }
    public void setDefaultSocialType(String defaultSocialType) { this.defaultSocialType = defaultSocialType; }
    public String getProbationPeriodMode() { return probationPeriodMode; }
    public void setProbationPeriodMode(String probationPeriodMode) { this.probationPeriodMode = probationPeriodMode; }
    public String getDefaultProbationPeriod() { return defaultProbationPeriod; }
    public void setDefaultProbationPeriod(String defaultProbationPeriod) { this.defaultProbationPeriod = defaultProbationPeriod; }
    public String getJobGrade() { return jobGrade; }
    public void setJobGrade(String jobGrade) { this.jobGrade = jobGrade; }
    public Boolean getAccountEnabled() { return accountEnabled; }
    public void setAccountEnabled(Boolean accountEnabled) { this.accountEnabled = accountEnabled; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds == null ? new ArrayList<Long>() : roleIds; }
}
