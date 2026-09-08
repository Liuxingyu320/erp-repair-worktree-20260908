package com.erp.oa.mapper;

import java.util.List;
import com.erp.oa.domain.OaFixedAssetRepair;

public interface OaFixedAssetRepairMapper
{
    List<OaFixedAssetRepair> selectRepairList(OaFixedAssetRepair repair);

    OaFixedAssetRepair selectRepairById(Long repairId);

    int insertRepair(OaFixedAssetRepair repair);

    int updateRepair(OaFixedAssetRepair repair);
}
