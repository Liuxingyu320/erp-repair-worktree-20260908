package com.erp.system.service;

import java.util.List;
import com.erp.system.domain.SysNotice;
import com.erp.system.domain.dto.SysNoticeAudiencePreviewRequest;
import com.erp.system.domain.dto.SysNoticePublishRequest;
import com.erp.system.domain.vo.SysNoticeAudienceOptionsVo;
import com.erp.system.domain.vo.SysNoticeAudiencePreviewVo;

/**
 * 公告 服务层
 * 
 * @author erp
 */
public interface ISysNoticeService
{
    /**
     * 查询公告信息
     * 
     * @param noticeId 公告ID
     * @return 公告信息
     */
    public SysNotice selectNoticeById(Long noticeId);

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

    /**
     * 删除公告信息
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

    SysNoticeAudiencePreviewVo previewAudience(SysNoticeAudiencePreviewRequest request);

    SysNoticeAudienceOptionsVo selectAudienceOptions(String keyword);

    SysNotice publishNotice(Long noticeId, SysNoticePublishRequest request, String operator);

    SysNotice cancelScheduledNotice(Long noticeId, Long version, String operator);

    SysNotice offlineNotice(Long noticeId, Long version, String operator);

    SysNotice createNewVersion(Long noticeId, Long version, String operator);

    List<Long> selectDueScheduledNoticeIds(int limit);

    boolean publishScheduledNotice(Long noticeId);

    int offlineExpiredNotices();
}
