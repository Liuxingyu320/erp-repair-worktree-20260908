package com.erp.oa.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.dto.OaSignTaskBatchSendRequest;
import com.erp.oa.domain.dto.OaSignTaskRetryRequest;
import com.erp.oa.domain.vo.OaSignTaskBatchSendItem;
import com.erp.oa.domain.vo.OaSignTaskBatchSendResult;
import com.erp.oa.domain.vo.OaSignTaskDetail;
import com.erp.oa.mapper.OaSignTaskMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;
import com.erp.oa.service.IOaSignPackageService;
import com.erp.oa.service.IOaSignTaskService;

/**
 * Existing-task batch send coordinator.
 *
 * <p>This service deliberately has no outer transaction. Every task is sent through the
 * established task service so one failed item cannot roll another item back.</p>
 */
@Service
public class OaSignTaskBatchSendService
{
    private static final int MAX_BATCH_SIZE = 100;

    private final OaSignHrAccessService hrAccessService;
    private final ShopScopeService shopScopeService;
    private final OaSignTaskMapper taskMapper;
    private final OaSignOnboardImportRowMapper onboardRowMapper;
    private final IOaSignTaskService taskService;
    private final OaSignPackageMapper packageMapper;
    private final IOaSignPackageService packageService;

    public OaSignTaskBatchSendService(OaSignHrAccessService hrAccessService,
            @Qualifier("oaSignScopeService") ShopScopeService shopScopeService,
            OaSignTaskMapper taskMapper, IOaSignTaskService taskService,
            OaSignOnboardImportRowMapper onboardRowMapper,
            OaSignPackageMapper packageMapper,
            IOaSignPackageService packageService)
    {
        this.hrAccessService = hrAccessService;
        this.shopScopeService = shopScopeService;
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.onboardRowMapper = onboardRowMapper;
        this.packageMapper = packageMapper;
        this.packageService = packageService;
    }

    public OaSignTaskBatchSendResult batchSend(OaSignTaskBatchSendRequest request,
            Long selectedShopDeptId, String ipAddress, String userAgent)
    {
        if (request == null || blank(request.getRequestId()))
        {
            throw new ServiceException("请求编号不能为空");
        }
        if (request.getRequestId().trim().length() > 64)
        {
            throw new ServiceException("请求编号不能超过64个字符");
        }
        List<Long> taskIds = normalizeIds(request.getTaskIds());
        hrAccessService.requireCurrentHr();
        List<Long> scopeDeptIds = shopScopeService.resolveScopeDeptIds(selectedShopDeptId);

        List<OaSignTaskBatchSendItem> items = new ArrayList<>();
        for (Long taskId : taskIds)
        {
            OaSignTaskBatchSendItem item = new OaSignTaskBatchSendItem();
            item.setTaskId(taskId);
            OaSignTask task = taskMapper.selectOaSignTaskById(taskId);
            if (task == null)
            {
                setResult(item, "BLOCKED", null, null, "签约任务不存在");
                items.add(item);
                continue;
            }
            if (!inScope(task.getShopDeptId(), scopeDeptIds))
            {
                setResult(item, "BLOCKED", null, null, "签约任务不在当前店铺范围");
                items.add(item);
                continue;
            }
            try
            {
                hrAccessService.requireTaskOwner(task);
            }
            catch (ServiceException unauthorized)
            {
                setResult(item, "BLOCKED", null, null, "签约任务不存在或无权访问");
                items.add(item);
                continue;
            }
            item.setPackageId(task.getPackageId());
            item.setTaskStatus(task.getStatus());
            String status = upper(task.getStatus());
            int attempt = sendAttempt(task);
            if ("SENDING".equals(status))
            {
                setResult(item, "IN_PROGRESS", task.getPackageId(), task.getStatus(), "任务正在发送");
                items.add(item);
                continue;
            }
            if ("PENDING_COMPANY".equals(status) && preparedStagedFinal(task))
            {
                try
                {
                    packageService.sendStagedSignatureFirstFinalForSystem(
                            task.getPackageId(), task.getTaskId(), SecurityUtils.getUserId(),
                            itemRequestId(request.getRequestId(), taskId, attempt));
                    OaSignTask sent = taskMapper.selectOaSignTaskById(taskId);
                    if (sent != null && "PENDING_FINAL_CONFIRM".equals(upper(sent.getStatus())))
                    {
                        setResult(item, "SENT", sent.getPackageId(), sent.getStatus(),
                                "最终合同已发送，员工仅需确认文件，不再签名");
                        syncImportedRow(sent);
                    }
                    else
                    {
                        classifyConcurrent(item, sent);
                    }
                }
                catch (RuntimeException exception)
                {
                    classifyPreparedFinalConcurrent(item,
                            taskMapper.selectOaSignTaskById(taskId));
                }
                items.add(item);
                continue;
            }
            if (isAlreadySentStatus(status))
            {
                setResult(item, "ALREADY_SENT", task.getPackageId(), task.getStatus(),
                        "任务已发送，无需重复发送");
                syncImportedRow(task);
                items.add(item);
                continue;
            }
            if ("FAILED".equals(status) && "SEND_FAILED".equals(task.getFailureCode()))
            {
                OaSignTaskRetryRequest retryAction = new OaSignTaskRetryRequest();
                retryAction.setRequestId(retryRequestId(request.getRequestId(), taskId, attempt));
                retryAction.setReasonCode("BATCH_SEND_RETRY");
                retryAction.setReasonDetail("批量发送仅重试上次发送失败项");
                try
                {
                    OaSignTaskDetail retried = taskService.retry(taskId, retryAction,
                            selectedShopDeptId, ipAddress, userAgent);
                    OaSignTask recovered = retried == null ? null : retried.getTask();
                    task = recovered == null ? taskMapper.selectOaSignTaskById(taskId) : recovered;
                }
                catch (RuntimeException exception)
                {
                    task = taskMapper.selectOaSignTaskById(taskId);
                }
                status = task == null ? null : upper(task.getStatus());
                if ("SENDING".equals(status))
                {
                    setResult(item, "IN_PROGRESS", task.getPackageId(), task.getStatus(),
                            "另一请求正在发送该任务");
                    items.add(item);
                    continue;
                }
                if (isAlreadySentOrTerminalAfterSend(status))
                {
                    setResult(item, "ALREADY_SENT", task.getPackageId(), task.getStatus(),
                            "任务已由另一请求发送");
                    syncImportedRow(task);
                    items.add(item);
                    continue;
                }
                if (!"READY_TO_SEND".equals(status))
                {
                    setResult(item, "FAILED", task == null ? null : task.getPackageId(),
                            task == null ? null : task.getStatus(), "发送重试恢复失败，请打开任务查看原因");
                    items.add(item);
                    continue;
                }
                item.setPackageId(task.getPackageId());
                item.setTaskStatus(task.getStatus());
            }
            if (!"READY_TO_SEND".equals(status))
            {
                setResult(item, "BLOCKED", task.getPackageId(), task.getStatus(), "当前任务状态不能发送");
                items.add(item);
                continue;
            }

            OaSignTaskRetryRequest action = new OaSignTaskRetryRequest();
            action.setRequestId(itemRequestId(request.getRequestId(), taskId, attempt));
            action.setReasonCode("BATCH_SEND");
            action.setReasonDetail("任务中心批量发送");
            try
            {
                OaSignTaskDetail detail = taskService.send(taskId, action, selectedShopDeptId,
                        ipAddress, userAgent);
                OaSignTask sent = detail == null ? null : detail.getTask();
                classifyCompleted(item,
                        sent == null ? taskMapper.selectOaSignTaskById(taskId) : sent);
            }
            catch (RuntimeException exception)
            {
                classifyConcurrent(item, taskMapper.selectOaSignTaskById(taskId));
            }
            items.add(item);
        }

        OaSignTaskBatchSendResult result = new OaSignTaskBatchSendResult();
        result.setItems(items);
        summarize(result, items);
        return result;
    }

    private void classifyCompleted(OaSignTaskBatchSendItem item, OaSignTask task)
    {
        if (task == null)
        {
            setResult(item, "FAILED", null, null, "发送结果未能确认，请打开任务查看");
            return;
        }
        String status = upper(task.getStatus());
        if ("SENDING".equals(status))
        {
            setResult(item, "IN_PROGRESS", task.getPackageId(), task.getStatus(), "任务正在发送");
        }
        else if (isAlreadySentStatus(status))
        {
            setResult(item, "SENT", task.getPackageId(), task.getStatus(), "任务已发送");
            syncImportedRow(task);
        }
        else if ("FAILED".equals(status))
        {
            setResult(item, "FAILED", task.getPackageId(), task.getStatus(), "发送失败，请打开任务查看原因");
        }
        else
        {
            setResult(item, "FAILED", task.getPackageId(), task.getStatus(),
                    "发送结果未能确认，请打开任务查看");
        }
    }

    private void classifyConcurrent(OaSignTaskBatchSendItem item, OaSignTask latest)
    {
        if (latest == null)
        {
            setResult(item, "FAILED", null, null, "发送失败，请重试");
            return;
        }
        String status = upper(latest.getStatus());
        if ("SENDING".equals(status))
        {
            setResult(item, "IN_PROGRESS", latest.getPackageId(), latest.getStatus(),
                    "另一请求正在发送该任务");
        }
        else if (isAlreadySentOrTerminalAfterSend(status))
        {
            setResult(item, "ALREADY_SENT", latest.getPackageId(), latest.getStatus(),
                    "任务已由另一请求发送");
            syncImportedRow(latest);
        }
        else
        {
            setResult(item, "FAILED", latest.getPackageId(), latest.getStatus(),
                    "发送失败，请打开任务查看原因");
        }
    }

    /**
     * A prepared signature-first final remains {@code PENDING_COMPANY} until the dedicated
     * send transaction commits.  The generic concurrent classifier deliberately treats that
     * status as "the initial package was already sent", so using it here would turn any final
     * send exception into a false {@code ALREADY_SENT} result.  Only a state that can occur
     * after final delivery is evidence that another request completed this operation.
     */
    private void classifyPreparedFinalConcurrent(OaSignTaskBatchSendItem item,
            OaSignTask latest)
    {
        if (latest == null)
        {
            setResult(item, "FAILED", null, null,
                    "最终文件发送结果未能确认，请刷新任务后重试");
            return;
        }
        String status = upper(latest.getStatus());
        if ("SENDING".equals(status))
        {
            setResult(item, "IN_PROGRESS", latest.getPackageId(), latest.getStatus(),
                    "另一请求正在发送最终文件");
        }
        else if (status != null && Set.of("PENDING_FINAL_CONFIRM", "SIGNED",
                "REFUSED", "EXPIRED").contains(status))
        {
            setResult(item, "ALREADY_SENT", latest.getPackageId(), latest.getStatus(),
                    "最终文件已由另一请求发送");
            syncImportedRow(latest);
        }
        else
        {
            setResult(item, "FAILED", latest.getPackageId(), latest.getStatus(),
                    "最终文件未发送，请打开任务核对后重试");
        }
    }

    private void setResult(OaSignTaskBatchSendItem item, String result, Long packageId,
            String status, String message)
    {
        item.setResult(result);
        item.setPackageId(packageId);
        item.setTaskStatus(status);
        item.setMessage(message);
    }

    static String itemRequestId(String batchRequestId, Long taskId, int attempt)
    {
        return deterministicItemRequestId("BS", batchRequestId, taskId, attempt);
    }

    static String retryRequestId(String batchRequestId, Long taskId, int attempt)
    {
        return deterministicItemRequestId("BR", batchRequestId, taskId, attempt);
    }

    private static String deterministicItemRequestId(String prefix, String batchRequestId,
            Long taskId, int attempt)
    {
        try
        {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(batchRequestId.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(24);
            for (int index = 0; index < 12; index++)
            {
                hex.append(String.format(Locale.ROOT, "%02x", hash[index]));
            }
            return prefix + ":" + hex + ":" + taskId + ":" + Math.max(attempt, 0);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private List<Long> normalizeIds(List<Long> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            throw new ServiceException("任务编号不能为空");
        }
        if (ids.size() > MAX_BATCH_SIZE)
        {
            throw new ServiceException("单次最多处理" + MAX_BATCH_SIZE + "个任务");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : ids)
        {
            if (id == null || id <= 0)
            {
                throw new ServiceException("任务编号无效");
            }
            unique.add(id);
        }
        return new ArrayList<>(unique);
    }

    private void summarize(OaSignTaskBatchSendResult result, List<OaSignTaskBatchSendItem> items)
    {
        result.setTotalCount(items.size());
        for (OaSignTaskBatchSendItem item : items)
        {
            switch (item.getResult())
            {
                case "SENT" -> result.setSentCount(result.getSentCount() + 1);
                case "ALREADY_SENT" -> result.setAlreadySentCount(result.getAlreadySentCount() + 1);
                case "IN_PROGRESS" -> result.setInProgressCount(result.getInProgressCount() + 1);
                case "BLOCKED" -> result.setBlockedCount(result.getBlockedCount() + 1);
                default -> result.setFailedCount(result.getFailedCount() + 1);
            }
        }
    }

    private boolean inScope(Long shopDeptId, List<Long> scopeDeptIds)
    {
        return shopDeptId != null && scopeDeptIds != null && scopeDeptIds.contains(shopDeptId);
    }

    private boolean isAlreadySentStatus(String status)
    {
        return status != null && Set.of("PENDING_SIGN", "VIEWED", "PENDING_COMPANY",
                "PENDING_FINAL_CONFIRM", "SIGNED").contains(status);
    }

    private boolean preparedStagedFinal(OaSignTask task)
    {
        if (task == null || task.getPackageId() == null) return false;
        OaSignPackage signPackage = packageMapper.selectOaSignPackageById(task.getPackageId());
        return signPackage != null
                && task.getTaskId().equals(signPackage.getTaskId())
                && "SIGNATURE_FIRST".equals(signPackage.getSigningSequence())
                && "pending_company".equals(signPackage.getStatus())
                && "PREPARED_NOT_SENT".equals(signPackage.getFinalConfirmationStatus())
                && signPackage.getFinalDocumentVersion() != null
                && signPackage.getFinalDocumentRootHash() != null;
    }

    private boolean isAlreadySentOrTerminalAfterSend(String status)
    {
        return isAlreadySentStatus(status)
                || status != null && Set.of("REFUSED", "EXPIRED").contains(status);
    }

    private int sendAttempt(OaSignTask task)
    {
        return task == null || task.getRetryCount() == null ? 0 : Math.max(task.getRetryCount(), 0);
    }

    private void syncImportedRow(OaSignTask task)
    {
        if (task != null && task.getTaskId() != null
                && OaOnboardSignEventFactory.SOURCE_TYPE.equalsIgnoreCase(task.getSourceType()))
            onboardRowMapper.markSentByTaskId(task.getTaskId());
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private static String upper(String value)
    {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
