package com.erp.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 公告接收人快照。
 */
public interface SysNoticeRecipientMapper
{
    int insertBatch(@Param("noticeId") Long noticeId,
            @Param("userIds") List<Long> userIds,
            @Param("deliveredTime") Date deliveredTime,
            @Param("recipientSource") String recipientSource);

    int countByNoticeId(Long noticeId);

    int deleteByNoticeId(Long noticeId);
}
