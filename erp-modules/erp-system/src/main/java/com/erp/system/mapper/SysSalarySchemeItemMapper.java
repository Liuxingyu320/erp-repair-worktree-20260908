package com.erp.system.mapper;

import java.util.List;
import com.erp.system.domain.SysSalarySchemeItem;

/**
 * 薪资方案档位 数据层
 *
 * @author erp
 */
public interface SysSalarySchemeItemMapper
{
    public List<SysSalarySchemeItem> selectSalarySchemeItemList(SysSalarySchemeItem item);

    public List<SysSalarySchemeItem> selectSalarySchemeItemsBySchemeId(Long schemeId);

    public SysSalarySchemeItem selectSalarySchemeItemById(Long itemId);

    public int insertSalarySchemeItem(SysSalarySchemeItem item);

    public int updateSalarySchemeItem(SysSalarySchemeItem item);

    public int deleteSalarySchemeItemsBySchemeId(Long schemeId);

    public int deleteSalarySchemeItemById(Long itemId);
}
