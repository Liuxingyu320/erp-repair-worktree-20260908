package com.erp.system.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import com.erp.common.redis.service.RedisService;
import com.erp.common.redis.service.RedisService.KeyScanResult;

@DisplayName("Redis 有界扫描")
class RedisServiceScanTest
{
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @DisplayName("读取到上限之外的唯一键时标记截断并立即停止")
    void boundedScanStopsAndMarksTruncated()
    {
        RedisTemplate redisTemplate = mock(RedisTemplate.class);
        Cursor cursor = mock(Cursor.class);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, true);
        when(cursor.next()).thenReturn("login:a", "login:b", "login:c");
        RedisService service = new RedisService();
        service.redisTemplate = redisTemplate;

        KeyScanResult result = service.scanKeys("login:*", 20, 2);

        assertThat(result.getKeys()).containsExactly("login:a", "login:b");
        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getScannedCount()).isEqualTo(3);
        assertThat(result.getLimit()).isEqualTo(2);
        verify(cursor).close();
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @DisplayName("恰好达到上限但游标结束时不误报截断")
    void exactLimitWithoutMoreKeysIsNotTruncated()
    {
        RedisTemplate redisTemplate = mock(RedisTemplate.class);
        Cursor cursor = mock(Cursor.class);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("login:a", "login:b");
        RedisService service = new RedisService();
        service.redisTemplate = redisTemplate;

        KeyScanResult result = service.scanKeys("login:*", 20, 2);

        assertThat(result.getKeys()).containsExactly("login:a", "login:b");
        assertThat(result.isTruncated()).isFalse();
        assertThat(result.getScannedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("批次和上限必须为正数")
    void scanBoundsMustBePositive()
    {
        RedisService service = new RedisService();

        assertThatIllegalArgumentException().isThrownBy(() -> service.scanKeys("*", 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> service.scanKeys("*", 1, 0));
    }
}
