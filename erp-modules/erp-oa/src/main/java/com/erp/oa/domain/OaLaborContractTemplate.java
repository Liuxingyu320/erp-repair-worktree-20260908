package com.erp.oa.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class OaLaborContractTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long templateId;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称长度不能超过100个字符")
    @Excel(name = "模板名称")
    private String templateName;

    @NotBlank(message = "社保口径不能为空")
    @Size(max = 20, message = "社保口径长度不能超过20个字符")
    @Excel(name = "社保口径")
    private String socialType;

    @Excel(name = "模板版本")
    private String templateVersion;

    @NotBlank(message = "模板文件不能为空")
    private String templateFileUrl;

    @Excel(name = "状态", readConverterExp = "0=启用,1=停用")
    private String status;

    @Excel(name = "内置模板", readConverterExp = "Y=是,N=否")
    private String builtIn;

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public String getSocialType() { return socialType; }
    public void setSocialType(String socialType) { this.socialType = socialType; }

    public String getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(String templateVersion) { this.templateVersion = templateVersion; }

    public String getTemplateFileUrl() { return templateFileUrl; }
    public void setTemplateFileUrl(String templateFileUrl) { this.templateFileUrl = templateFileUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBuiltIn() { return builtIn; }
    public void setBuiltIn(String builtIn) { this.builtIn = builtIn; }
}
