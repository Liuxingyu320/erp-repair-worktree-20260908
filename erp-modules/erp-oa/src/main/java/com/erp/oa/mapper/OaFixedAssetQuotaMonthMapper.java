package com.erp.oa.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaFixedAssetQuotaMonth;

public interface OaFixedAssetQuotaMonthMapper
{
    OaFixedAssetQuotaMonth selectMonthQuota(@Param("shopDeptId") Long shopDeptId,
            @Param("quotaYear") Integer quotaYear, @Param("quotaMonth") Integer quotaMonth);

    BigDecimal sumReleasedQuotaAmount(@Param("shopDeptId") Long shopDeptId,
            @Param("quotaYear") Integer quotaYear, @Param("throughMonth") Integer throughMonth);

    int insertMonthQuota(OaFixedAssetQuotaMonth monthQuota);

    int updateMonthQuota(OaFixedAssetQuotaMonth monthQuota);
}
