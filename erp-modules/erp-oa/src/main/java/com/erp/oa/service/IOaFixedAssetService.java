package com.erp.oa.service;

import java.util.List;
import com.erp.oa.domain.OaFixedAssetConfig;
import com.erp.oa.domain.OaFixedAssetRepair;
import com.erp.oa.domain.dto.OaFixedAssetRepairBatchRequest;
import com.erp.oa.domain.vo.OaFixedAssetQuotaSummary;
import com.erp.oa.domain.vo.OaFixedAssetRepairPrecheckVo;

public interface IOaFixedAssetService
{
    List<OaFixedAssetConfig> selectConfigList(OaFixedAssetConfig config, Long selectedShopDeptId);

    OaFixedAssetConfig selectConfigById(Long configId, Long selectedShopDeptId);

    OaFixedAssetConfig saveConfig(OaFixedAssetConfig config, Long selectedShopDeptId);

    int deleteConfigById(Long configId, Long selectedShopDeptId);

    OaFixedAssetQuotaSummary getQuotaSummary(Long shopDeptId, Integer quotaYear, Long selectedShopDeptId);

    List<OaFixedAssetRepair> selectRepairList(OaFixedAssetRepair repair, Long selectedShopDeptId);

    OaFixedAssetRepair selectRepairById(Long repairId, Long selectedShopDeptId);

    OaFixedAssetRepair submitRepair(OaFixedAssetRepair repair, Long selectedShopDeptId);

    OaFixedAssetRepairPrecheckVo precheckRepair(OaFixedAssetRepair repair,
            Long selectedShopDeptId);

    List<OaFixedAssetRepair> submitRepairBatch(OaFixedAssetRepairBatchRequest request, Long selectedShopDeptId);

    OaFixedAssetRepair approveExceptionRepair(OaFixedAssetRepair repair, Long selectedShopDeptId);

    OaFixedAssetRepair confirmApprovedRepair(Long repairId, Long selectedShopDeptId);
}
