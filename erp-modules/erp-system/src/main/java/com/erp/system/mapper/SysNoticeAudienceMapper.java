package com.erp.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SysNoticeAudience;
import com.erp.system.domain.vo.SysNoticeAudienceRecipientVo;
import com.erp.system.domain.vo.SysNoticeAudienceTargetVo;

/**
 * 公告受众规则和解析查询。
 */
public interface SysNoticeAudienceMapper
{
    List<SysNoticeAudience> selectByNoticeId(Long noticeId);

    int deleteByNoticeId(Long noticeId);

    int insertBatch(@Param("audiences") List<SysNoticeAudience> audiences);

    List<SysNoticeAudienceRecipientVo> selectResolvedRecipients(
            @Param("audiences") List<SysNoticeAudience> audiences);

    List<SysNoticeAudienceTargetVo> selectDepartmentOptions();

    List<SysNoticeAudienceTargetVo> selectRoleOptions();

    List<SysNoticeAudienceTargetVo> selectUserOptions(@Param("keyword") String keyword,
            @Param("limit") int limit);
}
