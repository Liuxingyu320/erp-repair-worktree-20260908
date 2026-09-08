package com.erp.oa.domain;

import com.erp.common.core.web.domain.BaseEntity;

public class OaSignPlanTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long planId;
    private Long templateId;
    private String templateType;
    private Integer sortOrder;
    private OaSignTemplate template;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public OaSignTemplate getTemplate() { return template; }
    public void setTemplate(OaSignTemplate template) { this.template = template; }
}
