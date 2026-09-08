package com.erp.oa.service;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import com.erp.oa.domain.OaCompanySealConfig;
import com.erp.oa.domain.OaLaborContract;
import com.erp.oa.domain.OaLaborContractTemplate;
import com.erp.oa.domain.dto.OaLaborContractSignRequest;
import com.erp.oa.domain.dto.OaLaborContractVerifyRequest;
import com.erp.oa.domain.dto.OaLaborContractVerifyResult;

public interface IOaLaborContractService
{
    List<OaLaborContractTemplate> selectTemplateList(OaLaborContractTemplate template);

    OaLaborContractTemplate saveTemplate(OaLaborContractTemplate template);

    OaCompanySealConfig saveSealConfig(OaCompanySealConfig config);

    OaCompanySealConfig getActiveSealConfig();

    OaLaborContract saveContract(OaLaborContract contract, Long selectedShopDeptId);

    OaLaborContract sendContract(Long contractId, Long selectedShopDeptId);

    List<OaLaborContract> selectContractList(OaLaborContract contract, Long selectedShopDeptId);

    OaLaborContract getContractDetail(Long contractId, Long selectedShopDeptId);

    OaLaborContract voidContract(Long contractId, Long selectedShopDeptId);

    OaLaborContract getPreviewContract(Long contractId, Long selectedShopDeptId);

    List<OaLaborContract> selectMyContracts(OaLaborContract contract);

    OaLaborContract getMyContractDetail(Long contractId);

    OaLaborContract signContract(Long contractId, OaLaborContractSignRequest request);

    OaLaborContractVerifyResult verifyContractHash(OaLaborContractVerifyRequest request);

    OaLaborContractVerifyResult verifyContractHash(OaLaborContractVerifyRequest request, Long selectedShopDeptId);

    void downloadContractFile(Long contractId, String kind, Long selectedShopDeptId, HttpServletResponse response)
            throws IOException;
}
