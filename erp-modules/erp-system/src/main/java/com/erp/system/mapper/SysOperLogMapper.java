package com.erp.system.mapper;

import java.util.List;
import com.erp.system.api.domain.SysOperLog;
import com.erp.system.domain.vo.SysOperLogExportVo;
import com.erp.system.domain.vo.SysOperLogListVo;

/**
 * 操作日志 数据层
 * 
 * @author erp
 */
public interface SysOperLogMapper
{
    /**
     * 新增操作日志
     * 
     * @param operLog 操作日志对象
     */
    public int insertOperlog(SysOperLog operLog);

    /**
     * 查询系统操作日志集合
     * 
     * @param operLog 操作日志对象
     * @return 操作日志集合
     */
    public List<SysOperLogListVo> selectOperLogSummaryList(SysOperLog operLog);

    /**
     * 查询安全导出集合。
     *
     * @param operLog 查询条件
     * @return 不含正文的导出集合
     */
    public List<SysOperLogExportVo> selectOperLogExportList(SysOperLog operLog);

    /**
     * 批量删除系统操作日志
     * 
     * @param operIds 需要删除的操作日志ID
     * @return 结果
     */
    public int deleteOperLogByIds(Long[] operIds);

    /**
     * 查询操作日志详细
     * 
     * @param operId 操作ID
     * @return 操作日志对象
     */
    public SysOperLog selectOperLogById(Long operId);

}
