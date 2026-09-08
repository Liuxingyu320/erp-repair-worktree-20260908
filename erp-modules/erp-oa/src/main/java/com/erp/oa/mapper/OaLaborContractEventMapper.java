package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaLaborContractEvent;

public interface OaLaborContractEventMapper
{
    int insertOaLaborContractEvent(OaLaborContractEvent event);

    List<OaLaborContractEvent> selectEventsByContractId(Long contractId);

    String selectLatestEventHashByContractId(@Param("contractId") Long contractId);
}
