package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaLaborContract;

public interface OaLaborContractMapper
{
    int insertOaLaborContract(OaLaborContract contract);

    int updateOaLaborContract(OaLaborContract contract);

    OaLaborContract selectOaLaborContractById(Long contractId);

    List<OaLaborContract> selectOaLaborContractList(OaLaborContract contract);

    List<OaLaborContract> selectMyOaLaborContractList(OaLaborContract contract);

    OaLaborContract selectOaLaborContractByEvidenceHash(@Param("hash") String hash);
}
