package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.vo.SysNoticeReadPage;
import com.erp.system.domain.vo.SysNoticeReadSummary;
import com.erp.system.domain.vo.SysNoticeReadUserVo;
import com.erp.system.mapper.SysNoticeReadMapper;
import com.erp.system.service.support.NoticeHtmlSanitizer;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;

class SysNoticeReadSummaryTest
{
    private final SysNoticeReadMapper mapper = mock(SysNoticeReadMapper.class);
    private final SysNoticeReadServiceImpl service = new SysNoticeReadServiceImpl(
            mapper, mock(NoticeHtmlSanitizer.class));

    @AfterEach
    void clearPage() { PageHelper.clearPage(); }

    @Test
    void filteredPageTotalIsSeparateFromUnfilteredSummaryAndSummaryIsNotPaginated()
    {
        Page<SysNoticeReadUserVo> rows = new Page<>(1, 2);
        rows.add(new SysNoticeReadUserVo());
        rows.setTotal(3L);
        SysNoticeReadSummary summary = new SysNoticeReadSummary();
        summary.setReadCount(40L);
        summary.setRecipientCount(100L);
        when(mapper.selectReadUsersByNoticeId(17L, "张")).thenReturn(rows);
        when(mapper.selectReadSummary(17L)).thenAnswer(call -> {
            assertThat(PageHelper.getLocalPage()).isNull();
            return summary;
        });
        PageHelper.startPage(1, 2);

        SysNoticeReadPage result = service.selectReadUsersPage(17L, "张");

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(3L);
        assertThat(result.getSummary().getReadCount()).isEqualTo(40L);
        assertThat(result.getSummary().getRecipientCount()).isEqualTo(100L);
        verify(mapper).selectReadUsersByNoticeId(17L, "张");
        verify(mapper).selectReadSummary(17L);
        assertThat(PageHelper.getLocalPage()).isNull();
    }

    @Test
    void invalidIdAndReadFailureBothClearPagination()
    {
        PageHelper.startPage(2, 10);
        assertThatThrownBy(() -> service.selectReadUsersPage(0L, "x"))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(mapper);
        assertThat(PageHelper.getLocalPage()).isNull();
        when(mapper.selectReadUsersByNoticeId(17L, "x"))
                .thenThrow(new IllegalStateException("read failed"));
        PageHelper.startPage(2, 10);
        assertThatThrownBy(() -> service.selectReadUsersPage(17L, "x"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(PageHelper.getLocalPage()).isNull();
        verify(mapper, never()).selectReadSummary(17L);
    }

    @Test
    void pageAndSummaryDeclareOneReadOnlySnapshotTransaction() throws Exception
    {
        Transactional transaction = SysNoticeReadServiceImpl.class
                .getMethod("selectReadUsersPage", Long.class, String.class)
                .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
