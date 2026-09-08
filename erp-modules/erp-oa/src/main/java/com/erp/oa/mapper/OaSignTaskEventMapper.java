package com.erp.oa.mapper;

import java.util.List;
import com.erp.oa.domain.OaSignTaskEvent;

public interface OaSignTaskEventMapper
{
    int insertOaSignTaskEvent(OaSignTaskEvent event);

    List<OaSignTaskEvent> selectTaskEvents(Long taskId);

    OaSignTaskEvent selectLastTaskEvent(Long taskId);

    OaSignTaskEvent selectEventByRequestId(String requestId);

    int deleteTaskEvents(Long taskId);
}
