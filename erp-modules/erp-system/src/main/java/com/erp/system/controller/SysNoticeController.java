package com.erp.system.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.web.controller.BaseController;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.core.web.page.TableDataInfo;
import com.erp.common.log.annotation.Log;
import com.erp.common.log.enums.BusinessType;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.common.security.annotation.Logical;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.domain.SysNotice;
import com.erp.system.domain.dto.SysNoticeAudiencePreviewRequest;
import com.erp.system.domain.dto.SysNoticePublishRequest;
import com.erp.system.domain.dto.SysNoticeVersionRequest;
import com.erp.system.domain.vo.SysNoticeReadUserVo;
import com.erp.system.service.ISysNoticeReadService;
import com.erp.system.service.ISysNoticeService;
import com.erp.system.service.impl.SysNoticeWorkflowException;

/**
 * 公告 信息操作处理
 * 
 * @author erp
 */
@RestController
@RequestMapping("/notice")
public class SysNoticeController extends BaseController
{
    @Autowired
    private ISysNoticeService noticeService;

    @Autowired
    private ISysNoticeReadService noticeReadService;

    /**
     * 获取通知公告列表
     */
    @RequiresPermissions("system:notice:list")
    @GetMapping("/list")
    public TableDataInfo list(SysNotice notice)
    {
        startPage();
        List<SysNotice> list = noticeService.selectNoticeList(notice);
        return getDataTable(list);
    }

    /**
     * 根据通知公告编号获取详细信息
     */
    @RequiresPermissions("system:notice:query")
    @GetMapping(value = "/{noticeId}")
    public AjaxResult getInfo(@PathVariable Long noticeId)
    {
        return success(noticeService.selectNoticeById(noticeId));
    }

    /**
     * 当前用户按发布时接收人快照读取公告；不能借后台查询接口越权。
     */
    @RequiresLogin
    @GetMapping(value = "/inbox/{noticeId}")
    public AjaxResult getInboxInfo(@PathVariable Long noticeId)
    {
        return success(noticeReadService.selectReadableNoticeById(noticeId, SecurityUtils.getUserId()));
    }

    /**
     * 新增通知公告
     */
    @RequiresPermissions("system:notice:add")
    @Log(title = "通知公告", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysNotice notice)
    {
        notice.setCreateBy(SecurityUtils.getUsername());
        try
        {
            noticeService.insertNotice(notice);
            return success(notice);
        }
        catch (SysNoticeWorkflowException ex)
        {
            return workflowError(ex);
        }
    }

    /**
     * 修改通知公告
     */
    @RequiresPermissions("system:notice:edit")
    @Log(title = "通知公告", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysNotice notice)
    {
        notice.setUpdateBy(SecurityUtils.getUsername());
        try
        {
            noticeService.updateNotice(notice);
            return success(notice);
        }
        catch (SysNoticeWorkflowException ex)
        {
            return workflowError(ex);
        }
    }

    @RequiresPermissions(value = { "system:notice:add", "system:notice:edit" }, logical = Logical.OR)
    @PostMapping("/audience-preview")
    public AjaxResult audiencePreview(@Validated @RequestBody SysNoticeAudiencePreviewRequest request)
    {
        try
        {
            return success(noticeService.previewAudience(request));
        }
        catch (SysNoticeWorkflowException ex)
        {
            return workflowError(ex);
        }
    }

    @RequiresPermissions(value = { "system:notice:add", "system:notice:edit" }, logical = Logical.OR)
    @GetMapping("/audience-options")
    public AjaxResult audienceOptions(String keyword)
    {
        try
        {
            return success(noticeService.selectAudienceOptions(keyword));
        }
        catch (SysNoticeWorkflowException ex)
        {
            return workflowError(ex);
        }
    }

    @RequiresPermissions("system:notice:publish")
    @Log(title = "公告发布", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{noticeId}/publish")
    public AjaxResult publish(@PathVariable Long noticeId,
            @Validated @RequestBody SysNoticePublishRequest request)
    {
        try
        {
            return success(noticeService.publishNotice(noticeId, request, SecurityUtils.getUsername()));
        }
        catch (SysNoticeWorkflowException ex)
        {
            return workflowError(ex);
        }
    }

    @RequiresPermissions("system:notice:publish")
    @Log(title = "取消公告计划发布", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{noticeId}/cancel-schedule")
    public AjaxResult cancelSchedule(@PathVariable Long noticeId,
            @Validated @RequestBody SysNoticeVersionRequest request)
    {
        try
        {
            return success(noticeService.cancelScheduledNotice(
                    noticeId, request.getVersion(), SecurityUtils.getUsername()));
        }
        catch (SysNoticeWorkflowException ex)
        {
            return workflowError(ex);
        }
    }

    @RequiresPermissions("system:notice:publish")
    @Log(title = "公告下线", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{noticeId}/offline")
    public AjaxResult offline(@PathVariable Long noticeId,
            @Validated @RequestBody SysNoticeVersionRequest request)
    {
        try
        {
            return success(noticeService.offlineNotice(
                    noticeId, request.getVersion(), SecurityUtils.getUsername()));
        }
        catch (SysNoticeWorkflowException ex)
        {
            return workflowError(ex);
        }
    }

    @RequiresPermissions("system:notice:edit")
    @Log(title = "创建公告新版本", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{noticeId}/new-version")
    public AjaxResult createNewVersion(@PathVariable Long noticeId,
            @Validated @RequestBody SysNoticeVersionRequest request)
    {
        try
        {
            return success(noticeService.createNewVersion(
                    noticeId, request.getVersion(), SecurityUtils.getUsername()));
        }
        catch (SysNoticeWorkflowException ex)
        {
            return workflowError(ex);
        }
    }

    /**
     * 首页顶部公告列表（返回全部正常公告，带当前用户已读标记，最多5条）
     */
    @RequiresLogin
    @GetMapping("/listTop")
    @ResponseBody
    public AjaxResult listTop()
    {
        Long userId = SecurityUtils.getUserId();
        List<SysNotice> list = noticeReadService.selectNoticeListWithReadStatus(userId, 5);
        long unreadCount = noticeReadService.selectUnreadCount(userId);
        AjaxResult result = AjaxResult.success(list);
        result.put("unreadCount", unreadCount);
        return result;
    }

    /**
     * 标记公告已读
     */
    @RequiresLogin
    @PostMapping("/markRead")
    @ResponseBody
    public AjaxResult markRead(Long noticeId)
    {
        Long userId = SecurityUtils.getUserId();
        noticeReadService.markRead(noticeId, userId);
        return success();
    }

    /**
     * 将当前用户的全部启用公告标记为已读
     */
    @RequiresLogin
    @PostMapping("/markReadAll")
    @ResponseBody
    public AjaxResult markReadAll()
    {
        Long userId = SecurityUtils.getUserId();
        int unreadCount = noticeReadService.markAllRead(userId);
        AjaxResult result = success();
        result.put("unreadCount", unreadCount);
        return result;
    }

    /**
     * 已读用户列表数据
     */
    @RequiresPermissions("system:notice:list")
    @GetMapping("/readUsers/list")
    @ResponseBody
    public TableDataInfo readUsersList(Long noticeId, String searchValue)
    {
        startPage();
        return noticeReadService.selectReadUsersPage(noticeId, searchValue);
    }

    /**
     * 删除通知公告
     */
    @RequiresPermissions("system:notice:remove")
    @Log(title = "通知公告", businessType = BusinessType.DELETE,
            isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/{noticeIds}")
    public AjaxResult remove(@PathVariable Long[] noticeIds)
    {
        try
        {
            return toAjax(noticeService.deleteNoticeByIds(noticeIds));
        }
        catch (SysNoticeWorkflowException ex)
        {
            return workflowError(ex);
        }
    }

    private AjaxResult workflowError(SysNoticeWorkflowException ex)
    {
        AjaxResult result = AjaxResult.error(ex.getCode(), ex.getMessage());
        result.put("businessCode", ex.getBusinessCode());
        return result;
    }
}
