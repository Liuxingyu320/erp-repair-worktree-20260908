package com.erp.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.log.sanitize.SensitiveLogSanitizer;
import com.erp.system.api.domain.SysOperLog;
import com.erp.system.domain.vo.SysOperLogDetailVo;
import com.erp.system.domain.vo.SysOperLogExportVo;
import com.erp.system.domain.vo.SysOperLogListVo;
import com.erp.system.mapper.SysOperLogMapper;
import com.erp.system.service.ISysOperLogService;

/**
 * 操作日志 服务层处理
 * 
 * @author erp
 */
@Service
public class SysOperLogServiceImpl implements ISysOperLogService
{
    @Autowired
    private SysOperLogMapper operLogMapper;

    @Autowired
    private SensitiveLogSanitizer sensitiveLogSanitizer;

    /**
     * 新增操作日志
     * 
     * @param operLog 操作日志对象
     * @return 结果
     */
    @Override
    public int insertOperlog(SysOperLog operLog)
    {
        sanitizeLogBody(operLog);
        return operLogMapper.insertOperlog(operLog);
    }

    /**
     * 查询系统操作日志集合
     * 
     * @param operLog 操作日志对象
     * @return 操作日志集合
     */
    @Override
    public List<SysOperLogListVo> selectOperLogSummaryList(SysOperLog operLog)
    {
        return operLogMapper.selectOperLogSummaryList(operLog);
    }

    @Override
    public List<SysOperLogExportVo> selectOperLogExportList(SysOperLog operLog)
    {
        return operLogMapper.selectOperLogExportList(operLog);
    }

    /**
     * 批量删除系统操作日志
     * 
     * @param operIds 需要删除的操作日志ID
     * @return 结果
     */
    @Override
    public int deleteOperLogByIds(Long[] operIds)
    {
        return operLogMapper.deleteOperLogByIds(operIds);
    }

    /**
     * 查询操作日志详细
     * 
     * @param operId 操作ID
     * @return 操作日志对象
     */
    @Override
    public SysOperLogDetailVo selectOperLogDetailById(Long operId)
    {
        SysOperLog source = operLogMapper.selectOperLogById(operId);
        if (source == null)
        {
            return null;
        }

        SysOperLogDetailVo detail = new SysOperLogDetailVo();
        detail.setOperId(source.getOperId());
        detail.setTitle(source.getTitle());
        detail.setBusinessType(source.getBusinessType());
        detail.setMethod(source.getMethod());
        detail.setRequestMethod(source.getRequestMethod());
        detail.setOperatorType(source.getOperatorType());
        detail.setOperName(source.getOperName());
        detail.setDeptName(source.getDeptName());
        detail.setOperUrl(source.getOperUrl());
        detail.setOperIp(source.getOperIp());
        detail.setStatus(source.getStatus());
        detail.setOperTime(source.getOperTime());
        detail.setCostTime(source.getCostTime());
        detail.setRequestSummary(protectedSummary(source.getOperParam(), "未记录请求正文", "历史请求正文已保护"));
        detail.setResultSummary(protectedSummary(source.getJsonResult(), "未记录响应正文", "历史响应正文已保护"));
        if (Integer.valueOf(1).equals(source.getStatus()))
        {
            detail.setErrorSummary(protectedSummary(source.getErrorMsg(), "操作失败，未记录错误摘要",
                    "错误详情已保护，请依据业务记录和稳定错误码排查"));
        }
        return detail;
    }

    private String protectedSummary(String value, String emptySummary, String protectedSummary)
    {
        return StringUtils.isBlank(value) ? emptySummary : protectedSummary;
    }

    private void sanitizeLogBody(SysOperLog operLog)
    {
        operLog.setOperParam(sensitiveLogSanitizer.sanitizeJsonText(operLog.getOperParam()));
        operLog.setJsonResult(sensitiveLogSanitizer.sanitizeJsonText(operLog.getJsonResult()));
        operLog.setErrorMsg(sensitiveLogSanitizer.sanitizeText(operLog.getErrorMsg()));
    }

    private void clearLogBody(SysOperLog operLog)
    {
        if (operLog != null)
        {
            operLog.setOperParam(null);
            operLog.setJsonResult(null);
            operLog.setErrorMsg(null);
        }
    }
}
