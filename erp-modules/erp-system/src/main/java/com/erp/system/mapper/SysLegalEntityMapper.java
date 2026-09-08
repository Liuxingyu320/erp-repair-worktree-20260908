package com.erp.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.api.domain.SysLegalEntity;

public interface SysLegalEntityMapper
{
    List<SysLegalEntity> selectLegalEntityList(SysLegalEntity query);
    List<SysLegalEntity> selectActiveLegalEntities();
    SysLegalEntity selectLegalEntityById(Long legalEntityId);
    SysLegalEntity selectLegalEntityByCode(@Param("legalEntityCode") String legalEntityCode);
    SysLegalEntity selectLegalEntityByName(@Param("legalEntityName") String legalEntityName);
    int insertLegalEntity(SysLegalEntity legalEntity);
    int updateLegalEntity(SysLegalEntity legalEntity);
}
