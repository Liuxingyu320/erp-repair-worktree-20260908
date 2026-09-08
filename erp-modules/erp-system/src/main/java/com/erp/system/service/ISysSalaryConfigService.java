package com.erp.system.service;

import java.util.List;
import com.erp.system.domain.SysRoleSalaryScheme;
import com.erp.system.domain.SysSalaryScheme;
import com.erp.system.domain.SysSalarySchemeExport;
import com.erp.system.domain.SysSalarySchemeItem;
import com.erp.system.domain.SysUserSalaryScheme;
import com.erp.system.domain.SysSalarySchemeRevision;
import com.erp.system.domain.dto.SysSalaryImpactPreviewRequest;
import com.erp.system.domain.dto.SysSalaryRevisionRollbackRequest;
import com.erp.system.domain.vo.SysSalaryImpactPreview;

/**
 * 薪资配置 服务层
 *
 * @author erp
 */
public interface ISysSalaryConfigService
{
    public List<SysSalaryScheme> selectSalarySchemeList(SysSalaryScheme scheme);

    public List<SysSalarySchemeExport> selectSalarySchemeExportList(SysSalaryScheme scheme);

    public List<SysSalaryScheme> selectSalarySchemeOptions();

    public SysSalaryScheme selectSalarySchemeById(Long schemeId);

    public int insertSalaryScheme(SysSalaryScheme scheme);

    public int updateSalaryScheme(SysSalaryScheme scheme);

    public int deleteSalarySchemeByIds(Long[] schemeIds, Integer expectedVersion,
            String changeReason, boolean emergencyCorrection);

    public List<SysSalarySchemeItem> selectSalarySchemeItemsBySchemeId(Long schemeId);

    public SysSalarySchemeItem selectSalarySchemeItemById(Long itemId);

    public int insertSalarySchemeItem(SysSalarySchemeItem item);

    public int updateSalarySchemeItem(SysSalarySchemeItem item);

    public int deleteSalarySchemeItemById(Long itemId, Integer expectedVersion, String changeReason,
            boolean emergencyCorrection);

    public SysSalaryImpactPreview previewSalaryImpact(SysSalaryImpactPreviewRequest request);

    public List<SysSalarySchemeRevision> selectSalarySchemeRevisions(Long schemeId);

    public int rollbackSalarySchemeRevision(Long revisionId, SysSalaryRevisionRollbackRequest request);

    public List<SysRoleSalaryScheme> selectRoleSalarySchemesByRoleId(Long roleId);

    public List<SysRoleSalaryScheme> selectRoleSalarySchemesByRoleIds(Long[] roleIds);

    public int saveRoleSalarySchemes(Long roleId, List<SysRoleSalaryScheme> bindings);

    public List<SysUserSalaryScheme> selectUserSalarySchemesByUserId(Long userId);

    public List<SysUserSalaryScheme> selectUserSalarySchemesByUserIds(Long[] userIds);

    public int saveUserSalarySchemes(Long userId, List<SysUserSalaryScheme> bindings);
}
