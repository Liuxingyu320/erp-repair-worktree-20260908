package com.erp.oa.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaSignEvent;

public interface OaSignEventMapper
{
    int insertOaSignEvent(OaSignEvent event);

    List<OaSignEvent> selectEventsByPackageId(Long packageId);

    String selectLatestEventHashByPackageId(@Param("packageId") Long packageId);

    OaSignEvent selectEventByTypeAndRequestId(@Param("eventType") String eventType,
            @Param("requestId") String requestId);

    int deleteEventsByPackageId(Long packageId);
}
