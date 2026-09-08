package com.erp.oa.service;

import java.util.List;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.dto.OaSignTaskConfirmRequest;
import com.erp.oa.domain.dto.OaSignTaskNotificationRetryRequest;
import com.erp.oa.domain.dto.OaSignTaskRetryRequest;
import com.erp.oa.domain.dto.OaSignExceptionResolutionRequest;
import com.erp.oa.domain.dto.OaSignTaskBatchDeleteRequest;
import com.erp.oa.domain.vo.OaSignTaskDetail;
import com.erp.oa.domain.vo.OaSignTaskBatchDeleteResult;
import com.erp.oa.domain.vo.OaSignTaskMetrics;

public interface IOaSignTaskService
{
    OaSignTask createTask(OaSignTask task);

    List<OaSignTask> selectTaskList(OaSignTask task, Long selectedShopDeptId);

    OaSignTaskMetrics getTaskMetrics(Long selectedShopDeptId);

    OaSignTaskDetail getTaskDetail(Long taskId, Long selectedShopDeptId);

    OaSignTaskDetail revalidate(Long taskId, OaSignTaskRetryRequest request,
            Long selectedShopDeptId, String ipAddress, String userAgent);

    OaSignTaskDetail confirm(Long taskId, OaSignTaskConfirmRequest request,
            Long selectedShopDeptId, String ipAddress, String userAgent);

    OaSignTaskDetail send(Long taskId, OaSignTaskRetryRequest request,
            Long selectedShopDeptId, String ipAddress, String userAgent);

    OaSignTaskDetail retry(Long taskId, OaSignTaskRetryRequest request,
            Long selectedShopDeptId, String ipAddress, String userAgent);

    int retryNotification(Long taskId, OaSignTaskNotificationRetryRequest request,
            Long selectedShopDeptId);

    OaSignTaskDetail cancel(Long taskId, OaSignTaskRetryRequest request,
            Long selectedShopDeptId, String ipAddress, String userAgent);

    OaSignTaskDetail resolveException(Long taskId, OaSignExceptionResolutionRequest request,
            Long selectedShopDeptId, String ipAddress, String userAgent);

    OaSignTaskBatchDeleteResult hardDeleteUnfinishedTasks(OaSignTaskBatchDeleteRequest request,
            Long selectedShopDeptId);
}
