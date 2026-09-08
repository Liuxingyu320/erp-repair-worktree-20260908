package com.erp.system.service;

import java.util.List;
import com.erp.system.api.domain.SysLegalEntity;

public interface ISysLegalEntityService
{
    List<SysLegalEntity> selectLegalEntityList(SysLegalEntity query);
    List<SysLegalEntity> selectActiveLegalEntities();
    SysLegalEntity selectLegalEntityById(Long legalEntityId);
    SysLegalEntity saveLegalEntity(SysLegalEntity legalEntity);
}
