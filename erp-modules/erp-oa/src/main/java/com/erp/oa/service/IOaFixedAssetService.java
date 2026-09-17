package com.erp.oa.service;

import java.util.List;
import com.erp.oa.domain.OaFixedAssetConfig;
import com.erp.oa.domain.OaFixedAssetRepair;
import com.erp.oa.domain.dto.OaFixedAssetRepairBatchRequest;
import com.erp.oa.domain.vo.OaFixedAssetQuotaSummary;
import com.erp.oa.domain.vo.OaFixedAssetRepairPrecheckVo;

public interface IOaFixedAssetService
{
    default com.erp.oa.domain.vo.OaFixedAssetConfigSnapshot selectConfigSnapshot(Long shopId, Long selectedShopId) { throw new UnsupportedOperationException("config snapshot unavailable"); }
    default com.erp.oa.domain.vo.OaFixedAssetConfigSnapshot saveConfigBatch(com.erp.oa.domain.dto.OaFixedAssetConfigBatchRequest request, Long selectedShopId) { throw new UnsupportedOperationException("config batch unavailable"); }
    default com.erp.oa.domain.vo.OaFixedAssetConfigSnapshot selectConfigCommand(String requestId, Long shopId, Long selectedShopId) { throw new UnsupportedOperationException("config command unavailable"); }
    default int deleteConfigById(Long configId, Long selectedShopId, Long expectedVersion) { throw new UnsupportedOperationException("versioned config delete unavailable"); }
    List<OaFixedAssetConfig> selectConfigList(OaFixedAssetConfig config, Long selectedShopDeptId);

    List<com.erp.oa.domain.vo.OaFixedAssetStoreSummary> selectConfigStoreList(OaFixedAssetConfig config, Long selectedShopDeptId);

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
