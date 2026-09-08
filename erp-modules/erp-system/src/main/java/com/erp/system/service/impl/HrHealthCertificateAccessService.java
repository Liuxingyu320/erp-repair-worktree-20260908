package com.erp.system.service.impl;

import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import com.erp.common.datascope.annotation.DataScope;
import com.erp.system.domain.vo.HrHealthCertificateVo;
import com.erp.system.domain.vo.HrHealthCertificateOpsSummaryVo;
import com.erp.system.mapper.HrHealthCertificateMapper;

@Service
public class HrHealthCertificateAccessService
{
    private final HrHealthCertificateMapper mapper;

    public HrHealthCertificateAccessService(HrHealthCertificateMapper mapper)
    {
        this.mapper = mapper;
    }

    @DataScope(deptAlias = "d")
    public List<HrHealthCertificateVo> selectScopedList(
            HrHealthCertificateVo query)
    {
        List<HrHealthCertificateVo> rows = mapper.selectScopedList(
                query == null ? new HrHealthCertificateVo() : query);
        return rows == null ? Collections.emptyList() : rows;
    }

    @DataScope(deptAlias = "d")
    public HrHealthCertificateOpsSummaryVo selectOpsSummary(
            HrHealthCertificateVo query)
    {
        return mapper.selectOpsSummary(
                query == null ? new HrHealthCertificateVo() : query);
    }
}
