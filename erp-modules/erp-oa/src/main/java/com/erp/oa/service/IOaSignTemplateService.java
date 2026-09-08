package com.erp.oa.service;

import java.util.List;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTemplate;

public interface IOaSignTemplateService
{
    List<OaSignTemplateType.Option> listTemplateTypes();

    List<OaSignTemplate> selectTemplateList(OaSignTemplate template);

    OaSignTemplate saveTemplate(OaSignTemplate template);

    List<OaSignTemplate> matchTemplates(OaSignPackage signPackage);
}
