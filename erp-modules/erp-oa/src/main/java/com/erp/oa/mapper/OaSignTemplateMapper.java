package com.erp.oa.mapper;

import java.util.List;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTemplate;

public interface OaSignTemplateMapper
{
    int insertOaSignTemplate(OaSignTemplate template);

    int updateOaSignTemplate(OaSignTemplate template);

    OaSignTemplate selectOaSignTemplateById(Long templateId);

    List<OaSignTemplate> selectOaSignTemplateList(OaSignTemplate template);

    List<OaSignTemplate> selectMatchedActiveTemplates(OaSignPackage signPackage);
}
