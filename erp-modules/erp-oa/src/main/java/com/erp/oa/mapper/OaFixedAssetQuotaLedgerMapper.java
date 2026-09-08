package com.erp.oa.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaFixedAssetQuotaLedger;

public interface OaFixedAssetQuotaLedgerMapper
{
    BigDecimal sumUsedQuotaAmount(@Param("shopDeptId") Long shopDeptId, @Param("quotaYear") Integer quotaYear);

    int insertLedger(OaFixedAssetQuotaLedger ledger);
}
