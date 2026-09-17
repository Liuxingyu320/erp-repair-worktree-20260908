package com.erp.oa.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaFixedAssetConfig;

public interface OaFixedAssetConfigMapper
{
    List<OaFixedAssetConfig> selectConfigList(OaFixedAssetConfig config);

    List<com.erp.oa.domain.vo.OaFixedAssetStoreSummary> selectConfigStoreList(OaFixedAssetConfig config);

    OaFixedAssetConfig selectConfigById(Long configId);

    BigDecimal sumAssetAmountByShop(Long shopDeptId);

    OaFixedAssetConfig selectOeItemSnapshot(Long oeItemId);
    OaFixedAssetConfig selectOeItemSnapshotForUpdate(Long oeItemId);

    OaFixedAssetConfig selectActiveConfigByShopAndOeItem(@Param("shopDeptId") Long shopDeptId, @Param("oeItemId") Long oeItemId);

    int insertConfig(OaFixedAssetConfig config);

    int updateConfig(OaFixedAssetConfig config);

    int deleteConfigById(Long configId);
}
