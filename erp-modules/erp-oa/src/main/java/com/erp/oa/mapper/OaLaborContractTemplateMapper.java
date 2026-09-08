package com.erp.oa.mapper;

import java.util.List;
import com.erp.oa.domain.OaLaborContractTemplate;

public interface OaLaborContractTemplateMapper
{
    int insertOaLaborContractTemplate(OaLaborContractTemplate template);

    OaLaborContractTemplate selectOaLaborContractTemplateById(Long templateId);

    List<OaLaborContractTemplate> selectOaLaborContractTemplateList(OaLaborContractTemplate template);

    int updateOaLaborContractTemplate(OaLaborContractTemplate template);
}
