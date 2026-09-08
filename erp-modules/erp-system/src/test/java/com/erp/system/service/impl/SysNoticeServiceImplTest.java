package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import com.erp.system.domain.SysNotice;
import com.erp.system.domain.SysNoticeAudience;
import com.erp.system.domain.dto.SysNoticePublishRequest;
import com.erp.system.domain.vo.SysNoticeAudienceRecipientVo;
import com.erp.system.mapper.SysNoticeAudienceMapper;
import com.erp.system.mapper.SysNoticeMapper;
import com.erp.system.mapper.SysNoticeRecipientMapper;
import com.erp.system.service.BusinessFeatureGate;
import com.erp.system.service.support.NoticeHtmlSanitizer;

@ExtendWith(MockitoExtension.class)
class SysNoticeServiceImplTest
{
    private static final String UNSAFE_HTML = "<p><strong>安全</strong><img src=x onerror=alert(1)></p>"
            + "<a href=\"javascript:alert(2)\">链接</a><script>alert(3)</script>";

    @Mock
    private SysNoticeMapper noticeMapper;
    @Mock
    private SysNoticeAudienceMapper audienceMapper;
    @Mock
    private SysNoticeRecipientMapper recipientMapper;
    @Mock
    private BusinessFeatureGate businessFeatureGate;

    private NoticeHtmlSanitizer sanitizer;
    private SysNoticeServiceImpl noticeService;

    @BeforeEach
    void setUp()
    {
        sanitizer = new NoticeHtmlSanitizer();
        noticeService = new SysNoticeServiceImpl(
                noticeMapper, audienceMapper, recipientMapper, sanitizer, businessFeatureGate);
    }

    @Test
    void sanitizeShouldPreserveBusinessFormattingAndRemoveExecutableMarkup()
    {
        String result = sanitizer.sanitize(UNSAFE_HTML);

        assertThat(result).contains("<p><strong>安全</strong></p>", "链接");
        assertThat(result).doesNotContain("script", "onerror", "javascript:", "<img");
    }

    @Test
    void legacyCreateRequestMustOnlyCreateSanitizedDraft()
    {
        SysNotice draft = notice(null, UNSAFE_HTML);
        draft.setStatus("0");
        draft.setAudienceType("ALL");
        draft.setAudiences(List.of(audience("ALL", 0L, false)));
        doAnswer(invocation -> {
            SysNotice value = invocation.getArgument(0);
            value.setNoticeId(101L);
            return 1;
        }).when(noticeMapper).insertNotice(draft);
        when(audienceMapper.insertBatch(anyList())).thenReturn(1);

        assertThat(noticeService.insertNotice(draft)).isEqualTo(1);

        assertThat(draft.getNoticeId()).isEqualTo(101L);
        assertThat(draft.getLifecycleStatus()).isEqualTo(SysNoticeServiceImpl.DRAFT);
        assertThat(draft.getStatus()).isEqualTo("1");
        assertThat(draft.getVersion()).isEqualTo(1L);
        assertThat(draft.getPublishedTime()).isNull();
        assertSafe(draft.getNoticeContent());
        verify(audienceMapper).deleteByNoticeId(101L);
        verify(audienceMapper).insertBatch(draft.getAudiences());
    }

    @Test
    void publishedNoticeCannotBeOverwrittenInPlace()
    {
        SysNotice request = notice(2L, "<p>篡改</p>");
        request.setVersion(4L);
        request.setAudienceType("ALL");
        request.setAudiences(List.of(audience("ALL", 0L, false)));
        SysNotice current = notice(2L, "<p>原发布内容</p>");
        current.setVersion(4L);
        current.setLifecycleStatus(SysNoticeServiceImpl.PUBLISHED);
        when(noticeMapper.selectNoticeByIdForUpdate(2L)).thenReturn(current);

        assertThatThrownBy(() -> noticeService.updateNotice(request))
                .isInstanceOf(SysNoticeWorkflowException.class)
                .hasMessageContaining("创建新版本");

        verify(noticeMapper, never()).updateNotice(any());
        verify(audienceMapper, never()).deleteByNoticeId(any());
    }

    @Test
    void immediatePublishMustPersistDeduplicatedSnapshotBeforeLifecycleChange()
    {
        SysNotice current = draft(10L, 2L, "DEPT");
        List<SysNoticeAudience> rules = List.of(audience("DEPT", 7L, true));
        when(businessFeatureGate.isEnabled(BusinessFeatureGate.NOTICE_WORKFLOW)).thenReturn(true);
        when(noticeMapper.selectNoticeByIdForUpdate(10L)).thenReturn(current);
        when(audienceMapper.selectByNoticeId(10L)).thenReturn(rules);
        when(audienceMapper.selectResolvedRecipients(anyList())).thenReturn(Arrays.asList(
                recipient(21L, "华东"), recipient(22L, "华东"), recipient(21L, "华东")));
        when(recipientMapper.countByNoticeId(10L)).thenReturn(0, 2);
        when(recipientMapper.insertBatch(eq(10L), anyList(), any(Date.class), eq("NORMAL_PUBLISH")))
                .thenReturn(2);
        when(noticeMapper.updateNoticeLifecycle(current, SysNoticeServiceImpl.DRAFT,
                SysNoticeServiceImpl.PUBLISHED)).thenReturn(1);
        when(noticeMapper.selectNoticeById(10L)).thenReturn(current);

        SysNotice result = noticeService.publishNotice(10L, publishRequest("IMMEDIATE", 2L), "admin");

        assertThat(result.getLifecycleStatus()).isEqualTo(SysNoticeServiceImpl.PUBLISHED);
        assertThat(result.getStatus()).isEqualTo("0");
        assertThat(result.getVersion()).isEqualTo(3L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> userIds = ArgumentCaptor.forClass(List.class);
        verify(recipientMapper).insertBatch(eq(10L), userIds.capture(), any(Date.class), eq("NORMAL_PUBLISH"));
        assertThat(userIds.getValue()).containsExactly(21L, 22L);

        InOrder order = inOrder(recipientMapper, noticeMapper);
        order.verify(recipientMapper).countByNoticeId(10L);
        order.verify(recipientMapper).insertBatch(eq(10L), anyList(), any(Date.class), eq("NORMAL_PUBLISH"));
        order.verify(recipientMapper).countByNoticeId(10L);
        order.verify(noticeMapper).updateNoticeLifecycle(
                current, SysNoticeServiceImpl.DRAFT, SysNoticeServiceImpl.PUBLISHED);
    }

    @Test
    void snapshotFailureMustNotAttemptPublishedLifecycleWrite()
    {
        SysNotice current = draft(11L, 1L, "USER");
        List<SysNoticeAudience> rules = List.of(audience("USER", 21L, false));
        when(businessFeatureGate.isEnabled(BusinessFeatureGate.NOTICE_WORKFLOW)).thenReturn(true);
        when(noticeMapper.selectNoticeByIdForUpdate(11L)).thenReturn(current);
        when(audienceMapper.selectByNoticeId(11L)).thenReturn(rules);
        when(audienceMapper.selectResolvedRecipients(anyList())).thenReturn(List.of(recipient(21L, "总部")));
        when(recipientMapper.countByNoticeId(11L)).thenReturn(0);
        when(recipientMapper.insertBatch(eq(11L), anyList(), any(Date.class), eq("NORMAL_PUBLISH")))
                .thenThrow(new IllegalStateException("snapshot write failed"));

        assertThatThrownBy(() -> noticeService.publishNotice(
                11L, publishRequest("IMMEDIATE", 1L), "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("snapshot write failed");

        verify(noticeMapper, never()).updateNoticeLifecycle(any(), anyString(), anyString());
    }

    @Test
    void schedulerMustPublishDueNoticeAtMostOnceAcrossRepeatedClaims()
    {
        SysNotice current = draft(12L, 3L, "USER");
        current.setLifecycleStatus(SysNoticeServiceImpl.SCHEDULED);
        current.setScheduledPublishTime(new Date(System.currentTimeMillis() - 5_000L));
        List<SysNoticeAudience> rules = List.of(audience("USER", 31L, false));
        when(noticeMapper.selectNoticeByIdForUpdate(12L)).thenReturn(current);
        when(audienceMapper.selectByNoticeId(12L)).thenReturn(rules);
        when(audienceMapper.selectResolvedRecipients(anyList())).thenReturn(List.of(recipient(31L, "总部")));
        when(recipientMapper.countByNoticeId(12L)).thenReturn(0, 1);
        when(recipientMapper.insertBatch(eq(12L), anyList(), any(Date.class), eq("SCHEDULED_PUBLISH")))
                .thenReturn(1);
        when(noticeMapper.updateNoticeLifecycle(current, SysNoticeServiceImpl.SCHEDULED,
                SysNoticeServiceImpl.PUBLISHED)).thenReturn(1);

        assertThat(noticeService.publishScheduledNotice(12L)).isTrue();
        assertThat(noticeService.publishScheduledNotice(12L)).isFalse();

        verify(recipientMapper, times(1)).insertBatch(
                eq(12L), anyList(), any(Date.class), eq("SCHEDULED_PUBLISH"));
        verify(noticeMapper, times(1)).updateNoticeLifecycle(
                current, SysNoticeServiceImpl.SCHEDULED, SysNoticeServiceImpl.PUBLISHED);
    }

    @Test
    void disabledWorkflowMustFailBeforeLockOrBroadcast()
    {
        when(businessFeatureGate.isEnabled(BusinessFeatureGate.NOTICE_WORKFLOW)).thenReturn(false);

        assertThatThrownBy(() -> noticeService.publishNotice(
                13L, publishRequest("IMMEDIATE", 1L), "admin"))
                .isInstanceOfSatisfying(SysNoticeWorkflowException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(403);
                    assertThat(ex.getBusinessCode()).isEqualTo("NOTICE_WORKFLOW_DISABLED");
                });

        verify(noticeMapper, never()).selectNoticeByIdForUpdate(any());
        verify(recipientMapper, never()).insertBatch(any(), anyList(), any(), anyString());
    }

    @Test
    void newVersionMustCreateSanitizedDraftLinkedToPublishedSource()
    {
        SysNotice source = notice(14L, UNSAFE_HTML);
        source.setLifecycleStatus(SysNoticeServiceImpl.PUBLISHED);
        source.setAudienceType("ALL");
        source.setVersion(7L);
        List<SysNoticeAudience> rules = List.of(audience("ALL", 0L, false));
        when(noticeMapper.selectNoticeByIdForUpdate(14L)).thenReturn(source);
        when(audienceMapper.selectByNoticeId(14L)).thenReturn(rules);
        doAnswer(invocation -> {
            SysNotice value = invocation.getArgument(0);
            value.setNoticeId(15L);
            return 1;
        }).when(noticeMapper).insertNotice(any(SysNotice.class));
        when(audienceMapper.insertBatch(anyList())).thenReturn(1);

        SysNotice draft = noticeService.createNewVersion(14L, 7L, "admin");

        assertThat(draft.getNoticeId()).isEqualTo(15L);
        assertThat(draft.getLifecycleStatus()).isEqualTo(SysNoticeServiceImpl.DRAFT);
        assertThat(draft.getVersion()).isEqualTo(8L);
        assertThat(draft.getPreviousNoticeId()).isEqualTo(14L);
        assertSafe(draft.getNoticeContent());
        verify(audienceMapper).deleteByNoticeId(15L);
        verify(audienceMapper).insertBatch(draft.getAudiences());
    }

    @Test
    void publishAndSchedulerEntryPointsMustRollbackForAnyException() throws Exception
    {
        Transactional publish = SysNoticeServiceImpl.class
                .getMethod("publishNotice", Long.class, SysNoticePublishRequest.class, String.class)
                .getAnnotation(Transactional.class);
        Transactional scheduled = SysNoticeServiceImpl.class
                .getMethod("publishScheduledNotice", Long.class)
                .getAnnotation(Transactional.class);

        assertThat(publish).isNotNull();
        assertThat(scheduled).isNotNull();
        assertThat(publish.rollbackFor()).contains(Exception.class);
        assertThat(scheduled.rollbackFor()).contains(Exception.class);
    }

    @Test
    void readMethodsShouldSanitizeHistoricalNoticeContent()
    {
        SysNotice detail = notice(3L, UNSAFE_HTML);
        SysNotice listItem = notice(4L, UNSAFE_HTML);
        when(noticeMapper.selectNoticeById(3L)).thenReturn(detail);
        when(audienceMapper.selectByNoticeId(3L)).thenReturn(List.of());
        when(noticeMapper.selectNoticeList(any(SysNotice.class))).thenReturn(List.of(listItem));

        SysNotice selected = noticeService.selectNoticeById(3L);
        List<SysNotice> list = noticeService.selectNoticeList(new SysNotice());

        assertSafe(selected.getNoticeContent());
        assertSafe(list.get(0).getNoticeContent());
    }

    private static SysNotice draft(Long id, Long version, String audienceType)
    {
        SysNotice notice = notice(id, "<p>公告正文</p>");
        notice.setLifecycleStatus(SysNoticeServiceImpl.DRAFT);
        notice.setStatus("1");
        notice.setVersion(version);
        notice.setAudienceType(audienceType);
        return notice;
    }

    private static SysNotice notice(Long id, String content)
    {
        SysNotice notice = new SysNotice();
        notice.setNoticeId(id);
        notice.setNoticeTitle("公告标题");
        notice.setNoticeType("2");
        notice.setNoticeContent(content);
        return notice;
    }

    private static SysNoticeAudience audience(String type, Long targetId, boolean includeChildren)
    {
        SysNoticeAudience audience = new SysNoticeAudience();
        audience.setTargetType(type);
        audience.setTargetId(targetId);
        audience.setIncludeChildren(includeChildren);
        return audience;
    }

    private static SysNoticeAudienceRecipientVo recipient(Long userId, String deptName)
    {
        SysNoticeAudienceRecipientVo recipient = new SysNoticeAudienceRecipientVo();
        recipient.setUserId(userId);
        recipient.setDeptName(deptName);
        return recipient;
    }

    private static SysNoticePublishRequest publishRequest(String mode, Long version)
    {
        SysNoticePublishRequest request = new SysNoticePublishRequest();
        request.setPublishMode(mode);
        request.setVersion(version);
        return request;
    }

    private static void assertSafe(String content)
    {
        assertThat(content).contains("安全", "链接");
        assertThat(content).doesNotContain("script", "onerror", "javascript:", "<img");
    }
}
