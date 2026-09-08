package com.erp.system.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.text.Convert;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.redis.service.RedisService;
import com.erp.common.redis.service.RedisService.KeyScanResult;
import com.erp.system.domain.SysConfig;
import com.erp.system.api.domain.SysUser;
import com.erp.system.config.SysConfigDescriptorRegistry;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.support.SysConfigSensitivityPolicy;

/**
 * 参数配置 服务层实现
 * 
 * @author erp
 */
@Service
public class SysConfigServiceImpl implements ISysConfigService
{
    private static final String SIGN_HR_USER_ID_KEY = "sign.hr.user-id";

    private static final int CACHE_SCAN_BATCH_SIZE = 500;

    private static final int CACHE_SCAN_LIMIT = 10000;

    @Autowired
    private SysConfigMapper configMapper;

    @Autowired
    private RedisService redisService;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysConfigSensitivityPolicy sensitivityPolicy = new SysConfigSensitivityPolicy();

    @Autowired
    private SysConfigDescriptorRegistry configDescriptorRegistry;

    /**
     * 项目启动时，初始化参数到缓存
     */
    @PostConstruct
    public void init()
    {
        loadingConfigCache();
    }

    /**
     * 查询参数配置信息
     * 
     * @param configId 参数配置ID
     * @return 参数配置信息
     */
    @Override
    public SysConfig selectConfigById(Long configId)
    {
        SysConfig config = new SysConfig();
        config.setConfigId(configId);
        return configMapper.selectConfig(config);
    }

    /**
     * 根据键名查询参数配置信息
     * 
     * @param configKey 参数key
     * @return 参数键值
     */
    @Override
    public String selectConfigByKey(String configKey)
    {
        String configValue = Convert.toStr(redisService.getCacheObject(getCacheKey(configKey)));
        if (StringUtils.isNotEmpty(configValue))
        {
            return configValue;
        }
        SysConfig config = new SysConfig();
        config.setConfigKey(configKey);
        SysConfig retConfig = configMapper.selectConfig(config);
        if (StringUtils.isNotNull(retConfig))
        {
            redisService.setCacheObject(getCacheKey(configKey), retConfig.getConfigValue());
            return retConfig.getConfigValue();
        }
        return StringUtils.EMPTY;
    }

    @Override
    public String selectConfigByKeyForExternal(String configKey)
    {
        sensitivityPolicy.assertExternalReadAllowed(configKey);
        return selectConfigByKey(configKey);
    }

    /**
     * 查询参数配置列表
     * 
     * @param config 参数配置信息
     * @return 参数配置集合
     */
    @Override
    public List<SysConfig> selectConfigList(SysConfig config)
    {
        return configMapper.selectConfigList(config);
    }

    /**
     * 新增参数配置
     * 
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertConfig(SysConfig config)
    {
        configMapper.lockSignHrState();
        requireConfiguredValue(config.getConfigValue());
        if (SysConfigSensitivityPolicy.MASK.equals(config.getConfigValue()))
        {
            throw new ServiceException("参数值不能使用系统掩码占位符");
        }
        validateSignHrConfig(config);
        configDescriptorRegistry.prepareForWrite(config, null);
        config.setVersion(1);
        int row = configMapper.insertConfig(config);
        if (row > 0)
        {
            syncSignHrPermissionsIfNeeded(config.getConfigKey());
            redisService.setCacheObject(getCacheKey(config.getConfigKey()), config.getConfigValue());
        }
        return row;
    }

    /**
     * 修改参数配置
     * 
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateConfig(SysConfig config)
    {
        if (config == null || config.getVersion() == null)
        {
            throw new ServiceException("参数版本不能为空");
        }
        configMapper.lockSignHrState();
        SysConfig temp = configMapper.selectConfigById(config.getConfigId());
        if (temp == null)
        {
            throw new ServiceException("参数不存在");
        }
        if (StringUtils.isEmpty(config.getConfigType()))
        {
            config.setConfigType(temp.getConfigType());
        }
        if (UserConstants.YES.equals(temp.getConfigType())
                && !UserConstants.YES.equals(config.getConfigType()))
        {
            throw new ServiceException("内置参数不能改为非内置参数");
        }
        resolveUpdatedConfigValue(temp, config);
        validateSignHrConfig(config);
        configDescriptorRegistry.prepareForWrite(config, temp);
        if (!StringUtils.equals(temp.getConfigKey(), config.getConfigKey()))
        {
            redisService.deleteObject(getCacheKey(temp.getConfigKey()));
        }

        int row = configMapper.updateConfig(config);
        if (row == 0)
        {
            throw new ServiceException("参数已被其他管理员修改，请刷新后重试");
        }
        config.setVersion(config.getVersion() + 1);
        if (row > 0)
        {
            if (isSignHrKey(temp.getConfigKey()) || isSignHrKey(config.getConfigKey()))
            {
                configMapper.syncSignHrPermissions();
            }
            redisService.setCacheObject(getCacheKey(config.getConfigKey()), config.getConfigValue());
        }
        return row;
    }

    /**
     * 批量删除参数信息
     * 
     * @param configIds 需要删除的参数ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfigByIds(Long[] configIds)
    {
        configMapper.lockSignHrState();
        List<String> deletedKeys = new ArrayList<>();
        boolean syncSignHrPermissions = false;
        for (Long configId : configIds)
        {
            SysConfig config = selectConfigById(configId);
            if (StringUtils.equals(UserConstants.YES, config.getConfigType()))
            {
                throw new ServiceException(String.format("内置参数【%1$s】不能删除 ", config.getConfigKey()));
            }
            configMapper.deleteConfigById(configId);
            deletedKeys.add(config.getConfigKey());
            syncSignHrPermissions = syncSignHrPermissions || isSignHrKey(config.getConfigKey());
        }
        if (syncSignHrPermissions)
        {
            configMapper.syncSignHrPermissions();
        }
        for (String configKey : deletedKeys)
        {
            redisService.deleteObject(getCacheKey(configKey));
        }
    }

    /**
     * 加载参数缓存数据
     */
    @Override
    public void loadingConfigCache()
    {
        List<SysConfig> configsList = configMapper.selectConfigList(new SysConfig());
        for (SysConfig config : configsList)
        {
            redisService.setCacheObject(getCacheKey(config.getConfigKey()), config.getConfigValue());
        }
    }

    /**
     * 清空参数缓存数据
     */
    @Override
    public void clearConfigCache()
    {
        KeyScanResult result = redisService.scanKeys(CacheConstants.SYS_CONFIG_KEY + "*",
                CACHE_SCAN_BATCH_SIZE, CACHE_SCAN_LIMIT);
        if (result.isTruncated())
        {
            throw new ServiceException("参数缓存数量超过安全上限，请联系管理员处理");
        }
        Collection<String> keys = result.getKeys();
        if (keys != null && !keys.isEmpty())
        {
            redisService.deleteObject(keys);
        }
    }

    /**
     * 重置参数缓存数据
     */
    @Override
    public void resetConfigCache()
    {
        clearConfigCache();
        loadingConfigCache();
    }

    /**
     * 校验参数键名是否唯一
     * 
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    public boolean checkConfigKeyUnique(SysConfig config)
    {
        Long configId = StringUtils.isNull(config.getConfigId()) ? -1L : config.getConfigId();
        SysConfig info = configMapper.checkConfigKeyUnique(config.getConfigKey());
        if (StringUtils.isNotNull(info) && info.getConfigId().longValue() != configId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 设置cache key
     * 
     * @param configKey 参数键
     * @return 缓存键key
     */
    private String getCacheKey(String configKey)
    {
        return CacheConstants.SYS_CONFIG_KEY + configKey;
    }

    private void syncSignHrPermissionsIfNeeded(String configKey)
    {
        if (isSignHrKey(configKey))
        {
            configMapper.syncSignHrPermissions();
        }
    }

    private boolean isSignHrKey(String configKey)
    {
        return SIGN_HR_USER_ID_KEY.equals(configKey);
    }

    private void validateSignHrConfig(SysConfig config)
    {
        if (config == null || !isSignHrKey(config.getConfigKey()))
        {
            return;
        }
        String value = config.getConfigValue();
        if (value == null || !value.matches("[1-9][0-9]*"))
        {
            throw new ServiceException("默认签约任务接收人必须配置为有效用户ID");
        }
        long userId;
        try
        {
            userId = Long.parseLong(value);
        }
        catch (NumberFormatException ex)
        {
            throw new ServiceException("默认签约任务接收人必须配置为有效用户ID");
        }
        SysUser user = userMapper.selectUserById(userId);
        if (user == null || !UserConstants.NORMAL.equals(user.getStatus())
                || !UserConstants.NORMAL.equals(user.getDelFlag()))
        {
            throw new ServiceException("默认签约任务接收人必须是正常且未删除的有效用户");
        }
    }

    private void resolveUpdatedConfigValue(SysConfig existing, SysConfig update)
    {
        boolean sensitive = sensitivityPolicy.isSensitive(existing.getConfigKey())
                || sensitivityPolicy.isSensitive(update.getConfigKey());
        if (!sensitive)
        {
            requireConfiguredValue(update.getConfigValue());
            return;
        }
        if (!Boolean.TRUE.equals(update.getUpdateSensitiveValue()))
        {
            update.setConfigValue(existing.getConfigValue());
            return;
        }
        requireConfiguredValue(update.getConfigValue());
        if (SysConfigSensitivityPolicy.MASK.equals(update.getConfigValue()))
        {
            throw new ServiceException("敏感参数不能写入掩码值");
        }
    }

    private void requireConfiguredValue(String configValue)
    {
        if (StringUtils.isEmpty(configValue))
        {
            throw new ServiceException("参数键值不能为空");
        }
    }
}
