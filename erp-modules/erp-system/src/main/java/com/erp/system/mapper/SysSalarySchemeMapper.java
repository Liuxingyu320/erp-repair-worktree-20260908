package com.erp.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.SysSalaryScheme;

/**
 * 薪资方案 数据层
 *
 * @author erp
 */
public interface SysSalarySchemeMapper
{
    public List<SysSalaryScheme> selectSalarySchemeList(SysSalaryScheme scheme);

    public List<SysSalaryScheme> selectSalarySchemeOptions();

    public SysSalaryScheme selectSalarySchemeById(Long schemeId);

    public int insertSalaryScheme(SysSalaryScheme scheme);

    public int updateSalaryScheme(SysSalaryScheme scheme);

    public int bumpSalarySchemeVersion(@Param("schemeId") Long schemeId,
            @Param("expectedVersion") Integer expectedVersion, @Param("updateBy") String updateBy);

    public int deleteSalarySchemeByIdAndVersion(@Param("schemeId") Long schemeId,
            @Param("expectedVersion") Integer expectedVersion);
}
