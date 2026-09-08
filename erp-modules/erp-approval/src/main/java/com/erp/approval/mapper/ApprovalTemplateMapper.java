package com.erp.approval.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.approval.domain.ApprovalTemplate;

public interface ApprovalTemplateMapper
{
    List<ApprovalTemplate> selectTemplateList(ApprovalTemplate filter);

    ApprovalTemplate selectTemplateById(Long templateId);

    ApprovalTemplate selectTemplateByIdForUpdate(Long templateId);

    ApprovalTemplate selectTemplateByBusinessCode(String businessCode);

    int insertTemplate(ApprovalTemplate template);

    int updateTemplateWithLock(@Param("template") ApprovalTemplate template,
            @Param("expectedVersion") Long expectedVersion);
}
