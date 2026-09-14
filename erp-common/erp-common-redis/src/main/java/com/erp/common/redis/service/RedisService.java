package com.erp.common.redis.service;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

/**
 * spring redis 工具类
 * 
 * @author erp
 **/
@SuppressWarnings(value = { "unchecked", "rawtypes" })
@Component
public class RedisService
{
    @Autowired
    public RedisTemplate redisTemplate;

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key 缓存的键值
     * @param value 缓存的值
     */
    public <T> void setCacheObject(final String key, final T value)
    {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key 缓存的键值
     * @param value 缓存的值
     * @param timeout 时间
     * @param timeUnit 时间颗粒度
     */
    public <T> void setCacheObject(final String key, final T value, final Long timeout, final TimeUnit timeUnit)
    {
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    /**
     * 缓存不存在时写入对象，用于分布式幂等锁等原子占位场景。
     *
     * @param key 缓存的键值
     * @param value 缓存的值
     * @param timeout 时间
     * @param timeUnit 时间颗粒度
     * @return true=写入成功；false=键已存在
     */
    public <T> Boolean setCacheObjectIfAbsent(final String key, final T value, final Long timeout, final TimeUnit timeUnit)
    {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, timeUnit);
    }

    /**
     * 设置有效时间
     *
     * @param key Redis键
     * @param timeout 超时时间
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout)
    {
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置有效时间
     *
     * @param key Redis键
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout, final TimeUnit unit)
    {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 获取有效时间
     *
     * @param key Redis键
     * @return 有效时间
     */
    public long getExpire(final String key)
    {
        return redisTemplate.getExpire(key);
    }

    /**
     * 判断 key是否存在
     *
     * @param key 键
     * @return true 存在 false不存在
     */
    public Boolean hasKey(String key)
    {
        return redisTemplate.hasKey(key);
    }

    /**
     * 获得缓存的基本对象。
     *
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public <T> T getCacheObject(final String key)
    {
        ValueOperations<String, T> operation = redisTemplate.opsForValue();
        return operation.get(key);
    }

    /** One GET, retaining the exact stored bytes before deserialization. Never return this in an API. */
    public CacheSnapshot getCacheSnapshot(final String key)
    {
        byte[] rawKey = redisTemplate.getKeySerializer().serialize(key);
        byte[] value = (byte[]) redisTemplate.execute((RedisCallback<byte[]>)
                connection -> connection.stringCommands().get(rawKey));
        return value == null ? null : new CacheSnapshot(value, deserializeCacheValue(value));
    }

    public Object deserializeCacheValue(byte[] bytes)
    {
        return redisTemplate.getValueSerializer().deserialize(bytes);
    }

    /** Single-key CAS; returns the bytes actually written, or null if revoked/changed. */
    public byte[] compareAndSetCacheObject(String key, byte[] expected, Object value,
            long timeout, TimeUnit unit)
    {
        if (expected == null || timeout <= 0)
        {
            throw new IllegalArgumentException("A cache snapshot and positive TTL are required");
        }
        byte[] rawKey = redisTemplate.getKeySerializer().serialize(key);
        byte[] replacement = redisTemplate.getValueSerializer().serialize(value);
        byte[] script = ("if redis.call('get', KEYS[1]) == ARGV[1] then "
                + "redis.call('psetex', KEYS[1], ARGV[3], ARGV[2]); return 1 end; return 0")
                .getBytes(StandardCharsets.UTF_8);
        Long changed = (Long) redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.scriptingCommands().eval(script, ReturnType.INTEGER, 1, rawKey,
                        expected, replacement, Long.toString(unit.toMillis(timeout)).getBytes(StandardCharsets.UTF_8)));
        return Long.valueOf(1).equals(changed) ? replacement : null;
    }

    /** Release only the lock acquired by this owner; a later lease must survive. */
    public boolean deleteCacheObjectIfValueMatches(String key, Object expectedValue)
    {
        if (expectedValue == null)
        {
            return false;
        }
        byte[] rawKey = redisTemplate.getKeySerializer().serialize(key);
        byte[] expected = redisTemplate.getValueSerializer().serialize(expectedValue);
        byte[] script = ("if redis.call('get', KEYS[1]) == ARGV[1] then "
                + "return redis.call('del', KEYS[1]) end; return 0").getBytes(StandardCharsets.UTF_8);
        Long removed = (Long) redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.scriptingCommands().eval(script, ReturnType.INTEGER, 1, rawKey, expected));
        return Long.valueOf(1).equals(removed);
    }

    public static final class CacheSnapshot
    {
        private final byte[] bytes;
        private final Object value;

        public CacheSnapshot(byte[] bytes, Object value)
        {
            this.bytes = bytes.clone();
            this.value = value;
        }

        public byte[] getBytes() { return bytes.clone(); }
        public Object getValue() { return value; }
    }

    /**
     * 一次批量读取多个字符串键，返回值顺序与传入键顺序一致。
     *
     * @param keys 缓存键集合
     * @return 对应缓存值（不存在的键对应 null）
     */
    public <T> List<T> getMultiCacheObject(final Collection<String> keys)
    {
        ValueOperations<String, T> operation = redisTemplate.opsForValue();
        return operation.multiGet(keys);
    }

    /**
     * 删除单个对象
     *
     * @param key
     */
    public boolean deleteObject(final String key)
    {
        return redisTemplate.delete(key);
    }

    /**
     * 删除集合对象
     *
     * @param collection 多个对象
     * @return
     */
    public boolean deleteObject(final Collection collection)
    {
        return redisTemplate.delete(collection) > 0;
    }

    /**
     * 缓存List数据
     *
     * @param key 缓存的键值
     * @param dataList 待缓存的List数据
     * @return 缓存的对象
     */
    public <T> long setCacheList(final String key, final List<T> dataList)
    {
        Long count = redisTemplate.opsForList().rightPushAll(key, dataList);
        return count == null ? 0 : count;
    }

    /**
     * 获得缓存的list对象
     *
     * @param key 缓存的键值
     * @return 缓存键值对应的数据
     */
    public <T> List<T> getCacheList(final String key)
    {
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    /**
     * 缓存Set
     *
     * @param key 缓存键值
     * @param dataSet 缓存的数据
     * @return 缓存数据的对象
     */
    public <T> BoundSetOperations<String, T> setCacheSet(final String key, final Set<T> dataSet)
    {
        BoundSetOperations<String, T> setOperation = redisTemplate.boundSetOps(key);
        Iterator<T> it = dataSet.iterator();
        while (it.hasNext())
        {
            setOperation.add(it.next());
        }
        return setOperation;
    }

    /**
     * 获得缓存的set
     *
     * @param key
     * @return
     */
    public <T> Set<T> getCacheSet(final String key)
    {
        return redisTemplate.opsForSet().members(key);
    }

    /** Atomically add one value to a cached set. */
    public <T> long addCacheSetValue(final String key, final T value)
    {
        Long count = redisTemplate.opsForSet().add(key, value);
        return count == null ? 0 : count;
    }

    /** Atomically remove one value from a cached set. */
    public <T> long removeCacheSetValue(final String key, final T value)
    {
        Long count = redisTemplate.opsForSet().remove(key, value);
        return count == null ? 0 : count;
    }

    /**
     * 缓存Map
     *
     * @param key
     * @param dataMap
     */
    public <T> void setCacheMap(final String key, final Map<String, T> dataMap)
    {
        if (dataMap != null) {
            redisTemplate.opsForHash().putAll(key, dataMap);
        }
    }

    /**
     * 获得缓存的Map
     *
     * @param key
     * @return
     */
    public <T> Map<String, T> getCacheMap(final String key)
    {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 往Hash中存入数据
     *
     * @param key Redis键
     * @param hKey Hash键
     * @param value 值
     */
    public <T> void setCacheMapValue(final String key, final String hKey, final T value)
    {
        redisTemplate.opsForHash().put(key, hKey, value);
    }

    /**
     * 获取Hash中的数据
     *
     * @param key Redis键
     * @param hKey Hash键
     * @return Hash中的对象
     */
    public <T> T getCacheMapValue(final String key, final String hKey)
    {
        HashOperations<String, String, T> opsForHash = redisTemplate.opsForHash();
        return opsForHash.get(key, hKey);
    }

    /**
     * 获取多个Hash中的数据
     *
     * @param key Redis键
     * @param hKeys Hash键集合
     * @return Hash对象集合
     */
    public <T> List<T> getMultiCacheMapValue(final String key, final Collection<Object> hKeys)
    {
        return redisTemplate.opsForHash().multiGet(key, hKeys);
    }

    /**
     * 删除Hash中的某条数据
     *
     * @param key Redis键
     * @param hKey Hash键
     * @return 是否成功
     */
    public boolean deleteCacheMapValue(final String key, final String hKey)
    {
        return redisTemplate.opsForHash().delete(key, hKey) > 0;
    }

    /**
     * 获得缓存的基本对象列表
     *
     * @param pattern 字符串前缀
     * @return 对象列表
     */
    public Collection<String> keys(final String pattern)
    {
        return redisTemplate.keys(pattern);
    }

    /**
     * 渐进扫描匹配的键，避免生产环境使用 KEYS 阻塞 Redis 事件循环。
     *
     * @param pattern 键匹配表达式
     * @return 去重后的键集合
     */
    public Collection<String> scanKeys(final String pattern)
    {
        return scanKeys(pattern, 500, Integer.MAX_VALUE).getKeys();
    }

    /**
     * 渐进扫描匹配的键，并限制单次业务请求最多装载的键数量。
     *
     * @param pattern 键匹配表达式
     * @param batchSize Redis 每批扫描建议数量
     * @param maxKeys 最多返回的唯一键数量
     * @return 扫描结果（包含是否因上限截断）
     */
    public KeyScanResult scanKeys(final String pattern, final int batchSize, final int maxKeys)
    {
        if (batchSize <= 0)
        {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        if (maxKeys <= 0)
        {
            throw new IllegalArgumentException("maxKeys must be greater than 0");
        }
        Set<String> keys = new LinkedHashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(batchSize).build();
        boolean truncated = false;
        long scannedCount = 0;
        try (Cursor cursor = redisTemplate.scan(options))
        {
            while (cursor.hasNext())
            {
                Object key = cursor.next();
                scannedCount++;
                if (key != null)
                {
                    String keyText = String.valueOf(key);
                    if (!keys.contains(keyText) && keys.size() >= maxKeys)
                    {
                        truncated = true;
                        break;
                    }
                    keys.add(keyText);
                }
            }
        }
        return new KeyScanResult(keys, truncated, scannedCount, maxKeys);
    }

    /**
     * 有界 Redis SCAN 的结果元数据。
     */
    public static final class KeyScanResult
    {
        private final Collection<String> keys;

        private final boolean truncated;

        private final long scannedCount;

        private final int limit;

        public KeyScanResult(Collection<String> keys, boolean truncated, long scannedCount, int limit)
        {
            this.keys = keys;
            this.truncated = truncated;
            this.scannedCount = scannedCount;
            this.limit = limit;
        }

        public Collection<String> getKeys()
        {
            return keys;
        }

        public boolean isTruncated()
        {
            return truncated;
        }

        public long getScannedCount()
        {
            return scannedCount;
        }

        public int getLimit()
        {
            return limit;
        }
    }
}
