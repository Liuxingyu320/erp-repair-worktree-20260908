package com.erp.system.service.impl;

import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.system.domain.SysNotice;
import com.erp.system.domain.SysNoticeRead;
import com.erp.system.domain.vo.SysNoticeReadUserVo;
import com.erp.system.mapper.SysNoticeReadMapper;
import com.erp.system.service.ISysNoticeReadService;
import com.erp.system.service.support.NoticeHtmlSanitizer;

/**
 * 公告已读记录 服务层实现
 *
 * @author erp
 */
@Service
public class SysNoticeReadServiceImpl implements ISysNoticeReadService
{
    private final SysNoticeReadMapper noticeReadMapper;
    private final NoticeHtmlSanitizer noticeHtmlSanitizer;

    public SysNoticeReadServiceImpl(SysNoticeReadMapper noticeReadMapper, NoticeHtmlSanitizer noticeHtmlSanitizer)
    {
        this.noticeReadMapper = noticeReadMapper;
        this.noticeHtmlSanitizer = noticeHtmlSanitizer;
    }

    /**
     * 标记已读
     */
    @Override
    public void markRead(Long noticeId, Long userId)
    {
        requireReadable(noticeId, userId);
        SysNoticeRead record = new SysNoticeRead();
        record.setNoticeId(noticeId);
        record.setUserId(userId);
        noticeReadMapper.insertNoticeRead(record);
    }

    /**
     * 查询某用户未读公告数量
     */
    @Override
    public int selectUnreadCount(Long userId)
    {
        return noticeReadMapper.selectUnreadCount(userId);
    }

    /**
     * 查询公告列表并标记当前用户已读状态
     */
    @Override
    public List<SysNotice> selectNoticeListWithReadStatus(Long userId, int limit)
    {
        List<SysNotice> notices = noticeReadMapper.selectNoticeListWithReadStatus(userId, limit);
        if (notices != null)
        {
            notices.forEach(notice -> notice.setNoticeContent(noticeHtmlSanitizer.sanitize(notice.getNoticeContent())));
        }
        return notices;
    }

    /**
     * 批量标记已读
     */
    @Override
    public void markReadBatch(Long userId, Long[] noticeIds)
    {
        if (noticeIds == null || noticeIds.length == 0)
        {
            return;
        }
        requireUserId(userId);
        Long[] normalizedIds = Arrays.stream(noticeIds).filter(Objects::nonNull).distinct().toArray(Long[]::new);
        if (normalizedIds.length == 0)
        {
            return;
        }
        if (normalizedIds.length != noticeReadMapper.selectReadableRecipientCountBatch(normalizedIds, userId))
        {
            throw new ServiceException("公告不存在、已下线或不属于当前用户");
        }
        noticeReadMapper.insertNoticeReadBatch(userId, normalizedIds);
    }

    /**
     * 将全部启用且未读的公告标记为已读
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(Long userId)
    {
        if (userId == null)
        {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        noticeReadMapper.insertAllUnreadNotices(userId);
        return noticeReadMapper.selectUnreadCount(userId);
    }

    /**
     * 查询已阅读某公告的用户列表
     */
    @Override
    public List<SysNoticeReadUserVo> selectReadUsersByNoticeId(Long noticeId, String searchValue)
    {
        return noticeReadMapper.selectReadUsersByNoticeId(noticeId, searchValue);
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public com.erp.system.domain.vo.SysNoticeReadPage selectReadUsersPage(Long noticeId, String searchValue)
    {
        try
        {
            if (noticeId == null || noticeId <= 0) throw new ServiceException("公告ID无效");
            List<SysNoticeReadUserVo> rows = noticeReadMapper.selectReadUsersByNoticeId(noticeId, searchValue);
            long total = new com.github.pagehelper.PageInfo<>(rows).getTotal();
            // Do not allow the pending pagination context to constrain the summary.
            com.github.pagehelper.PageHelper.clearPage();
            com.erp.system.domain.vo.SysNoticeReadSummary summary = noticeReadMapper.selectReadSummary(noticeId);
            return new com.erp.system.domain.vo.SysNoticeReadPage(rows, total, summary);
        }
        finally
        {
            com.github.pagehelper.PageHelper.clearPage();
        }
    }

    /**
     * 删除公告时清理对应已读记录
     */
    @Override
    public void deleteByNoticeIds(Long[] noticeIds)
    {
        noticeReadMapper.deleteByNoticeIds(noticeIds);
    }

    @Override
    public SysNotice selectReadableNoticeById(Long noticeId, Long userId)
    {
        requireUserId(userId);
        if (noticeId == null || noticeId <= 0)
        {
            throw new ServiceException("公告ID无效");
        }
        SysNotice notice = noticeReadMapper.selectReadableNoticeById(noticeId, userId);
        if (notice == null)
        {
            throw new ServiceException("公告不存在、已下线或不属于当前用户");
        }
        notice.setNoticeContent(noticeHtmlSanitizer.sanitize(notice.getNoticeContent()));
        return notice;
    }

    private void requireReadable(Long noticeId, Long userId)
    {
        requireUserId(userId);
        if (noticeId == null || noticeId <= 0
                || noticeReadMapper.selectReadableRecipientCount(noticeId, userId) != 1)
        {
            throw new ServiceException("公告不存在、已下线或不属于当前用户");
        }
    }

    private static void requireUserId(Long userId)
    {
        if (userId == null || userId <= 0)
        {
            throw new IllegalArgumentException("用户ID不能为空");
        }
    }
}
