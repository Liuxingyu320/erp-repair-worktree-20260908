package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import com.erp.system.domain.SysNotice;
import com.erp.system.mapper.SysNoticeReadMapper;
import com.erp.system.service.support.NoticeHtmlSanitizer;

@DisplayName("公告已读服务")
class SysNoticeReadServiceImplTest
{
    @Test
    @DisplayName("全部已读写入和真实数量回查在异常回滚事务中")
    void markAllReadShouldRollbackForAnyException() throws Exception
    {
        Transactional transactional = SysNoticeReadServiceImpl.class
                .getMethod("markAllRead", Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    @Test
    @DisplayName("全部已读先写入全部未读公告再查询真实剩余数量")
    void markAllReadShouldInsertThenQueryActualUnreadCount()
    {
        List<String> calls = new ArrayList<>();
        SysNoticeReadServiceImpl service = service((method, args) -> {
            calls.add(method + ":" + args[0]);
            if ("insertAllUnreadNotices".equals(method))
            {
                return 5;
            }
            if ("selectUnreadCount".equals(method))
            {
                return 2;
            }
            throw unexpected(method);
        });

        int unreadCount = service.markAllRead(42L);

        assertThat(unreadCount).isEqualTo(2);
        assertThat(calls).containsExactly("insertAllUnreadNotices:42", "selectUnreadCount:42");
    }

    @Test
    @DisplayName("全部已读缺少用户ID时失败关闭且不访问数据库")
    void markAllReadShouldFailClosedForNullUserId()
    {
        SysNoticeReadServiceImpl service = service((method, args) -> {
            throw unexpected(method);
        });

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.markAllRead(null))
                .withMessageContaining("用户");
    }

    @Test
    @DisplayName("全部已读真实数量回查失败时原样抛出异常以触发回滚")
    void markAllReadShouldPropagateUnreadCountFailure()
    {
        IllegalStateException failure = new IllegalStateException("未读数量回查失败");
        SysNoticeReadServiceImpl service = service((method, args) -> {
            if ("insertAllUnreadNotices".equals(method))
            {
                return 5;
            }
            if ("selectUnreadCount".equals(method))
            {
                throw failure;
            }
            throw unexpected(method);
        });

        assertThatThrownBy(() -> service.markAllRead(42L)).isSameAs(failure);
    }

    @Test
    @DisplayName("顶部公告流会清理历史富文本中的危险内容")
    void topNoticeFeedShouldSanitizeHistoricalContent()
    {
        SysNotice notice = new SysNotice();
        notice.setNoticeId(7L);
        notice.setNoticeContent("<p>公告</p><svg onload=alert(1)></svg><script>alert(2)</script>");
        SysNoticeReadServiceImpl service = service((method, args) -> {
            if ("selectNoticeListWithReadStatus".equals(method))
            {
                return Collections.singletonList(notice);
            }
            throw unexpected(method);
        });

        List<SysNotice> result = service.selectNoticeListWithReadStatus(9L, 5);

        assertThat(result.get(0).getNoticeContent()).contains("<p>公告</p>");
        assertThat(result.get(0).getNoticeContent()).doesNotContain("svg", "onload", "script");
    }

    @Test
    @DisplayName("非接收人不能标记公告已读")
    void markReadShouldFailClosedForNonRecipient()
    {
        SysNoticeReadServiceImpl service = service((method, args) -> {
            if ("selectReadableRecipientCount".equals(method))
            {
                assertThat(args).containsExactly(8L, 9L);
                return 0;
            }
            throw unexpected(method);
        });

        assertThatThrownBy(() -> service.markRead(8L, 9L))
                .isInstanceOf(com.erp.common.core.exception.ServiceException.class)
                .hasMessageContaining("不属于当前用户");
    }

    @Test
    @DisplayName("批量已读过滤后为空时不生成空IN语句")
    void markReadBatchShouldIgnoreAllNullNoticeIds()
    {
        SysNoticeReadServiceImpl service = service((method, args) -> {
            throw unexpected(method);
        });

        service.markReadBatch(9L, new Long[] { null, null });
    }

    @Test
    @DisplayName("收件箱详情仅从接收人快照读取并再次清理富文本")
    void inboxDetailShouldSanitizeReadableSnapshotNotice()
    {
        SysNotice notice = new SysNotice();
        notice.setNoticeId(8L);
        notice.setNoticeContent("<p>仅接收人可见</p><script>alert(1)</script>");
        SysNoticeReadServiceImpl service = service((method, args) -> {
            if ("selectReadableNoticeById".equals(method))
            {
                assertThat(args).containsExactly(8L, 9L);
                return notice;
            }
            throw unexpected(method);
        });

        SysNotice result = service.selectReadableNoticeById(8L, 9L);

        assertThat(result.getNoticeContent()).contains("仅接收人可见").doesNotContain("script");
    }

    private static SysNoticeReadServiceImpl service(BiFunction<String, Object[], Object> handler)
    {
        SysNoticeReadMapper mapper = (SysNoticeReadMapper) Proxy.newProxyInstance(
                SysNoticeReadMapper.class.getClassLoader(), new Class<?>[] { SysNoticeReadMapper.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class)
                    {
                        return method.invoke(new Object(), args);
                    }
                    return handler.apply(method.getName(), args == null ? new Object[0] : args);
                });
        return new SysNoticeReadServiceImpl(mapper, new NoticeHtmlSanitizer());
    }

    private static AssertionError unexpected(String method)
    {
        return new AssertionError("Unexpected mapper call: " + method);
    }
}
