package com.erp.oa.mapper;

import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaFixedAssetQuota;

public interface OaFixedAssetQuotaMapper
{
    OaFixedAssetQuota selectQuota(@Param("shopDeptId") Long shopDeptId, @Param("quotaYear") Integer quotaYear);

    OaFixedAssetQuota selectQuotaForUpdate(@Param("shopDeptId") Long shopDeptId,
            @Param("quotaYear") Integer quotaYear);

    int insertQuotaIfAbsent(@Param("shopDeptId") Long shopDeptId,
            @Param("quotaYear") Integer quotaYear,
            @Param("annualRepairRatio") java.math.BigDecimal annualRepairRatio,
            @Param("createBy") String createBy);

    int insertQuota(OaFixedAssetQuota quota);

    int updateQuota(OaFixedAssetQuota quota);
}
