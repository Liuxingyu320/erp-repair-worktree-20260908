package com.erp.system.mapper;

import java.util.List;
import com.erp.system.domain.SysSalarySchemeRevision;

/** 薪资方案修订数据层；刻意不提供更新和删除操作。 */
public interface SysSalarySchemeRevisionMapper
{
    int insertRevision(SysSalarySchemeRevision revision);

    SysSalarySchemeRevision selectRevisionById(Long revisionId);

    List<SysSalarySchemeRevision> selectRevisionsBySchemeId(Long schemeId);
}
