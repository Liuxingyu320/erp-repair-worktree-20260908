package com.erp.system.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysLegalEntity;
import com.erp.system.mapper.SysLegalEntityMapper;
import com.erp.system.service.ISysLegalEntityService;

@Service
public class SysLegalEntityServiceImpl implements ISysLegalEntityService
{
    private final SysLegalEntityMapper legalEntityMapper;

    public SysLegalEntityServiceImpl(SysLegalEntityMapper legalEntityMapper)
    {
        this.legalEntityMapper = legalEntityMapper;
    }

    @Override
    public List<SysLegalEntity> selectLegalEntityList(SysLegalEntity query)
    {
        return legalEntityMapper.selectLegalEntityList(query == null ? new SysLegalEntity() : query);
    }

    @Override
    public List<SysLegalEntity> selectActiveLegalEntities()
    {
        return legalEntityMapper.selectActiveLegalEntities();
    }

    @Override
    public SysLegalEntity selectLegalEntityById(Long legalEntityId)
    {
        return legalEntityMapper.selectLegalEntityById(legalEntityId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysLegalEntity saveLegalEntity(SysLegalEntity legalEntity)
    {
        if (legalEntity == null)
        {
            throw new ServiceException("公司主体信息不能为空");
        }
        legalEntity.setLegalEntityCode(legalEntity.getLegalEntityCode().trim().toUpperCase());
        legalEntity.setLegalEntityName(legalEntity.getLegalEntityName().trim());
        legalEntity.setStatus(StringUtils.isBlank(legalEntity.getStatus()) ? "0" : legalEntity.getStatus());
        assertUnique(legalEntity);
        if (legalEntity.getLegalEntityId() == null)
        {
            legalEntity.setCreateBy(SecurityUtils.getUsername());
            if (legalEntityMapper.insertLegalEntity(legalEntity) != 1)
            {
                throw new ServiceException("公司主体创建失败");
            }
        }
        else
        {
            if (legalEntity.getVersion() == null)
            {
                throw new ServiceException("公司主体版本不能为空，请刷新后重试");
            }
            legalEntity.setUpdateBy(SecurityUtils.getUsername());
            if (legalEntityMapper.updateLegalEntity(legalEntity) != 1)
            {
                throw new ServiceException("公司主体已被其他操作更新，请刷新后重试");
            }
        }
        return legalEntityMapper.selectLegalEntityById(legalEntity.getLegalEntityId());
    }

    private void assertUnique(SysLegalEntity legalEntity)
    {
        SysLegalEntity sameCode = legalEntityMapper.selectLegalEntityByCode(legalEntity.getLegalEntityCode());
        if (sameCode != null && !sameCode.getLegalEntityId().equals(legalEntity.getLegalEntityId()))
        {
            throw new ServiceException("公司编码已存在");
        }
        SysLegalEntity sameName = legalEntityMapper.selectLegalEntityByName(legalEntity.getLegalEntityName());
        if (sameName != null && !sameName.getLegalEntityId().equals(legalEntity.getLegalEntityId()))
        {
            throw new ServiceException("公司法定全称已存在");
        }
    }
}
