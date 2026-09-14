package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaLaborContract;

public interface OaLaborContractMapper
{
    OaLaborContract selectScopedEmployeeIdentity(@Param("employeeId") Long employeeId,
            @Param("shopDeptId") Long shopDeptId);

    int insertOaLaborContract(OaLaborContract contract);

    int updateOaLaborContract(OaLaborContract contract);

    OaLaborContract selectOaLaborContractById(Long contractId);

    OaLaborContract selectOaLaborContractByIdForUpdate(Long contractId);

    int markSigned(@Param("contract") OaLaborContract contract,
            @Param("expectedVersion") String expectedVersion, @Param("expectedHash") String expectedHash);

    int markVoided(@Param("contract") OaLaborContract contract, @Param("expectedStatus") String expectedStatus);

    List<OaLaborContract> selectOaLaborContractList(OaLaborContract contract);

    List<OaLaborContract> selectMyOaLaborContractList(OaLaborContract contract);

    OaLaborContract selectOaLaborContractByEvidenceHash(@Param("hash") String hash);
}
