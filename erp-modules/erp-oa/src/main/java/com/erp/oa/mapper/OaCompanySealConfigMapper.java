package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaCompanySealConfig;

public interface OaCompanySealConfigMapper
{
    OaCompanySealConfig selectActiveSealConfig();

    OaCompanySealConfig selectSealById(@Param("sealId") Long sealId);

    List<OaCompanySealConfig> selectSealsByLegalEntity(@Param("legalEntityId") Long legalEntityId,
            @Param("activeOnly") boolean activeOnly);

    OaCompanySealConfig selectDefaultSealByLegalEntity(@Param("legalEntityId") Long legalEntityId);

    int clearDefaultSeal(@Param("legalEntityId") Long legalEntityId, @Param("exceptSealId") Long exceptSealId);

    int insertOaCompanySealConfig(OaCompanySealConfig config);

    int updateOaCompanySealConfig(OaCompanySealConfig config);
}
