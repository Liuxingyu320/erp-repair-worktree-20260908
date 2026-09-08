package com.erp.common.security.utils;

import java.util.Collection;
import java.util.List;
import com.alibaba.fastjson2.JSONArray;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.SpringUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.redis.service.RedisService;
import com.erp.common.redis.service.RedisService.KeyScanResult;
import com.erp.system.api.domain.SysDictData;

/**
 * 字典工具类
 * 
 * @author erp
 */
public class DictUtils
{
    private static final int CACHE_SCAN_BATCH_SIZE = 500;

    private static final int CACHE_SCAN_LIMIT = 10000;

    /**
     * 设置字典缓存
     * 
     * @param key 参数键
     * @param dictDatas 字典数据列表
     */
    public static void setDictCache(String key, List<SysDictData> dictDatas)
    {
        SpringUtils.getBean(RedisService.class).setCacheObject(getCacheKey(key), dictDatas);
    }

    /**
     * 获取字典缓存
     * 
     * @param key 参数键
     * @return dictDatas 字典数据列表
     */
    public static List<SysDictData> getDictCache(String key)
    {
        JSONArray arrayCache = SpringUtils.getBean(RedisService.class).getCacheObject(getCacheKey(key));
        if (StringUtils.isNotNull(arrayCache))
        {
            return arrayCache.toList(SysDictData.class);
        }
        return null;
    }

    /**
     * 删除指定字典缓存
     * 
     * @param key 字典键
     */
    public static void removeDictCache(String key)
    {
        SpringUtils.getBean(RedisService.class).deleteObject(getCacheKey(key));
    }

    /**
     * 清空字典缓存
     */
    public static void clearDictCache()
    {
        RedisService redisService = SpringUtils.getBean(RedisService.class);
        KeyScanResult result = redisService.scanKeys(CacheConstants.SYS_DICT_KEY + "*",
                CACHE_SCAN_BATCH_SIZE, CACHE_SCAN_LIMIT);
        if (result.isTruncated())
        {
            throw new ServiceException("字典缓存数量超过安全上限，请联系管理员处理");
        }
        Collection<String> keys = result.getKeys();
        if (keys != null && !keys.isEmpty())
        {
            redisService.deleteObject(keys);
        }
    }

    /**
     * 设置cache key
     * 
     * @param configKey 参数键
     * @return 缓存键key
     */
    public static String getCacheKey(String configKey)
    {
        return CacheConstants.SYS_DICT_KEY + configKey;
    }
}
