package com.erp.system.mapper;

import java.util.List;
import java.util.Date;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SysNotice;

/**
 * 通知公告表 数据层
 * 
 * @author erp
 */
public interface SysNoticeMapper
{
    /**
     * 查询公告信息
     * 
     * @param noticeId 公告ID
     * @return 公告信息
     */
    public SysNotice selectNoticeById(Long noticeId);

    public SysNotice selectNoticeByIdForUpdate(Long noticeId);

    /**
     * 查询公告列表
     * 
     * @param notice 公告信息
     * @return 公告集合
     */
    public List<SysNotice> selectNoticeList(SysNotice notice);

    /**
     * 新增公告
     * 
     * @param notice 公告信息
     * @return 结果
     */
    public int insertNotice(SysNotice notice);

    /**
     * 修改公告
     * 
     * @param notice 公告信息
     * @return 结果
     */
    public int updateNotice(SysNotice notice);

    public int updateNoticeLifecycle(@Param("notice") SysNotice notice,
            @Param("expectedLifecycle") String expectedLifecycle,
            @Param("newLifecycle") String newLifecycle);

    public List<Long> selectDueScheduledNoticeIds(@Param("now") Date now, @Param("limit") int limit);

    public int updateExpiredNotices(@Param("now") Date now);

    /**
     * 批量删除公告
     * 
     * @param noticeId 公告ID
     * @return 结果
     */
    public int deleteNoticeById(Long noticeId);

    /**
     * 批量删除公告信息
     * 
     * @param noticeIds 需要删除的公告ID
     * @return 结果
     */
    public int deleteNoticeByIds(Long[] noticeIds);
}
