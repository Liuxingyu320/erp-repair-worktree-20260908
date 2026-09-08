package com.erp.approval.api.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalInstanceSnapshot;
import com.erp.approval.api.domain.ApprovalServiceStatus;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.approval.api.domain.ApprovalStartResponse;
import com.erp.approval.api.domain.ApprovalWithdrawRequest;
import com.erp.common.core.domain.R;
import feign.FeignException;

/** Fail-closed fallback for approval service calls. */
@Component
public class RemoteApprovalFallbackFactory
        implements FallbackFactory<RemoteApprovalService>
{
    private static final Logger log = LoggerFactory.getLogger(
            RemoteApprovalFallbackFactory.class);

    @Override
    public RemoteApprovalService create(Throwable throwable)
    {
        int responseCode = remoteStatus(throwable);
        String errorType = throwable == null ? "Unknown"
                : throwable.getClass().getSimpleName();
        log.error("统一审批服务调用失败，type={}, status={}",
                errorType, responseCode);
        return new RemoteApprovalService()
        {
            @Override
            public R<ApprovalStartResponse> start(ApprovalStartRequest request,
                    String source)
            {
                return R.fail(responseCode, "发起审批失败");
            }

            @Override
            public R<Boolean> withdraw(ApprovalWithdrawRequest request,
                    String source)
            {
                return R.fail(responseCode, "撤回审批失败");
            }

            @Override
            public R<ApprovalInstanceSnapshot> getInstance(Long instanceId,
                    String source)
            {
                return R.fail(responseCode, "查询审批实例失败");
            }

            @Override
            public R<Boolean> canAccessInstance(Long instanceId, Long userId,
                    String source)
            {
                return R.fail(responseCode, "校验审批参与人失败");
            }

            @Override
            public R<ApprovalServiceStatus> getServiceStatus(String source)
            {
                return R.fail(responseCode, "查询审批服务状态失败");
            }
        };
    }

    private static int remoteStatus(Throwable throwable)
    {
        Throwable current = throwable;
        while (current != null)
        {
            if (current instanceof FeignException feignException
                    && feignException.status() > 0)
            {
                return feignException.status();
            }
            current = current.getCause();
        }
        return R.FAIL;
    }
}
