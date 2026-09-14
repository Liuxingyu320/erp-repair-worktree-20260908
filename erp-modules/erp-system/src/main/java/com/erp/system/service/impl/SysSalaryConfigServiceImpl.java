package com.erp.system.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.domain.SysRoleSalaryScheme;
import com.erp.system.domain.SysSalaryScheme;
import com.erp.system.domain.SysSalarySchemeExport;
import com.erp.system.domain.SysSalarySchemeItem;
import com.erp.system.domain.SysSalarySchemeRevision;
import com.erp.system.domain.SysUserSalaryScheme;
import com.erp.system.domain.dto.SysSalaryImpactPreviewRequest;
import com.erp.system.domain.dto.SysSalaryRevisionRollbackRequest;
import com.erp.system.domain.dto.SysSalarySchemeSnapshot;
import com.erp.system.domain.vo.SysSalaryImpactPreview;
import com.erp.system.domain.vo.SysSalaryImpactStats;
import com.erp.system.mapper.SysRoleSalarySchemeMapper;
import com.erp.system.mapper.SysSalaryImpactMapper;
import com.erp.system.mapper.SysSalarySchemeItemMapper;
import com.erp.system.mapper.SysSalarySchemeMapper;
import com.erp.system.mapper.SysSalarySchemeRevisionMapper;
import com.erp.system.mapper.SysUserSalarySchemeMapper;
import com.erp.system.service.ISysSalaryConfigService;

/**
 * 薪资配置 服务层处理
 *
 * @author erp
 */
@Service
public class SysSalaryConfigServiceImpl implements ISysSalaryConfigService
{
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.security.service.LegacySalaryWriteGuard legacySalaryWrites =
            new com.erp.common.security.service.LegacySalaryWriteGuard();

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private SysSalarySchemeMapper schemeMapper;

    @Autowired
    private SysSalarySchemeItemMapper itemMapper;

    @Autowired
    private SysRoleSalarySchemeMapper roleSalarySchemeMapper;

    @Autowired
    private SysUserSalarySchemeMapper userSalarySchemeMapper;

    @Autowired
    private SysSalaryImpactMapper salaryImpactMapper;

    @Autowired
    private SysSalarySchemeRevisionMapper revisionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<SysSalaryScheme> selectSalarySchemeList(SysSalaryScheme scheme)
    {
        return schemeMapper.selectSalarySchemeList(scheme);
    }

    @Override
    public List<SysSalarySchemeExport> selectSalarySchemeExportList(SysSalaryScheme scheme)
    {
        List<SysSalarySchemeExport> rows = new ArrayList<>();
        List<SysSalaryScheme> schemes = selectSalarySchemeList(scheme);
        for (SysSalaryScheme salaryScheme : schemes)
        {
            List<SysSalarySchemeItem> items = itemMapper.selectSalarySchemeItemsBySchemeId(salaryScheme.getSchemeId());
            if (items == null || items.isEmpty())
            {
                rows.add(SysSalarySchemeExport.from(salaryScheme, null));
                continue;
            }
            for (SysSalarySchemeItem item : items)
            {
                rows.add(SysSalarySchemeExport.from(salaryScheme, item));
            }
        }
        return rows;
    }

    @Override
    public List<SysSalaryScheme> selectSalarySchemeOptions()
    {
        List<SysSalaryScheme> schemes = schemeMapper.selectSalarySchemeOptions();
        for (SysSalaryScheme scheme : schemes)
        {
            scheme.setItems(itemMapper.selectSalarySchemeItemsBySchemeId(scheme.getSchemeId()));
        }
        return schemes;
    }

    @Override
    public SysSalaryScheme selectSalarySchemeById(Long schemeId)
    {
        SysSalaryScheme scheme = schemeMapper.selectSalarySchemeById(schemeId);
        if (scheme != null)
        {
            scheme.setItems(itemMapper.selectSalarySchemeItemsBySchemeId(schemeId));
        }
        return scheme;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertSalaryScheme(SysSalaryScheme scheme)
    {
        legacySalaryWrites.reject();
        if (StringUtils.isEmpty(scheme.getStatus()))
        {
            scheme.setStatus("0");
        }
        scheme.setCreateBy(SecurityUtils.getUsername());
        scheme.setVersion(1);
        int rows = schemeMapper.insertSalaryScheme(scheme);
        insertSchemeItems(scheme);
        if (rows > 0)
        {
            recordRevision(scheme.getSchemeId(), 1, "CREATE", reasonOrDefault(scheme.getChangeReason(), "创建薪资方案"));
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateSalaryScheme(SysSalaryScheme scheme)
    {
        legacySalaryWrites.reject();
        SysSalaryScheme existing = requireScheme(scheme.getSchemeId());
        assertExpectedVersion(existing, scheme.getVersion());
        assertEffectiveSchemeMutationAllowed(existing, scheme.getEffectiveDate(),
                Boolean.TRUE.equals(scheme.getEmergencyCorrection()), scheme.getChangeReason());
        if (scheme.getItems() != null)
        {
            assertSchemeItemsCanBeReplaced(scheme.getSchemeId());
        }
        scheme.setUpdateBy(SecurityUtils.getUsername());
        int rows = schemeMapper.updateSalaryScheme(scheme);
        if (rows == 0)
        {
            throw new ServiceException("薪资方案已被其他管理员修改，请重新预览后再保存");
        }
        scheme.setVersion(existing.getVersion() + 1);
        if (scheme.getItems() != null)
        {
            itemMapper.deleteSalarySchemeItemsBySchemeId(scheme.getSchemeId());
            insertSchemeItems(scheme);
        }
        recordRevision(scheme.getSchemeId(), scheme.getVersion(),
                Boolean.TRUE.equals(scheme.getEmergencyCorrection()) ? "EMERGENCY_CORRECTION" : "UPDATE",
                reasonOrDefault(scheme.getChangeReason(), "修改薪资方案"));
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSalarySchemeByIds(Long[] schemeIds, Integer expectedVersion,
            String changeReason, boolean emergencyCorrection)
    {
        legacySalaryWrites.reject();
        if (schemeIds == null || schemeIds.length != 1)
        {
            throw new ServiceException("薪资方案必须逐条预览后删除");
        }
        if (StringUtils.isBlank(changeReason))
        {
            throw new ServiceException("删除薪资方案必须填写原因");
        }
        for (Long schemeId : schemeIds)
        {
            SysSalaryScheme existing = requireScheme(schemeId);
            assertExpectedVersion(existing, expectedVersion);
            assertEffectiveSchemeMutationAllowed(existing, existing.getEffectiveDate(),
                    emergencyCorrection, changeReason);
            recordRevision(schemeId, existing.getVersion() + 1,
                    emergencyCorrection ? "EMERGENCY_DELETE" : "DELETE", changeReason);
            itemMapper.deleteSalarySchemeItemsBySchemeId(schemeId);
        }
        roleSalarySchemeMapper.deleteRoleSalarySchemeBySchemeIds(schemeIds);
        userSalarySchemeMapper.deleteUserSalarySchemeBySchemeIds(schemeIds);
        int rows = schemeMapper.deleteSalarySchemeByIdAndVersion(schemeIds[0], expectedVersion);
        if (rows == 0)
        {
            throw new ServiceException("薪资方案已被其他管理员修改，请重新预览后再删除");
        }
        return rows;
    }

    @Override
    public List<SysSalarySchemeItem> selectSalarySchemeItemsBySchemeId(Long schemeId)
    {
        return itemMapper.selectSalarySchemeItemsBySchemeId(schemeId);
    }

    @Override
    public SysSalarySchemeItem selectSalarySchemeItemById(Long itemId)
    {
        return itemMapper.selectSalarySchemeItemById(itemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertSalarySchemeItem(SysSalarySchemeItem item)
    {
        legacySalaryWrites.reject();
        SysSalaryScheme scheme = requireScheme(item.getSchemeId());
        Integer expectedVersion = expectedItemVersion(item, scheme);
        assertEffectiveSchemeMutationAllowed(scheme, scheme.getEffectiveDate(),
                Boolean.TRUE.equals(item.getEmergencyCorrection()), item.getChangeReason());
        prepareSalarySchemeItem(item);
        item.setCreateBy(SecurityUtils.getUsername());
        bumpSchemeVersion(scheme.getSchemeId(), expectedVersion);
        int rows = itemMapper.insertSalarySchemeItem(item);
        recordRevision(scheme.getSchemeId(), expectedVersion + 1,
                Boolean.TRUE.equals(item.getEmergencyCorrection()) ? "EMERGENCY_ITEM_ADD" : "ITEM_ADD",
                reasonOrDefault(item.getChangeReason(), "新增薪资档位"));
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateSalarySchemeItem(SysSalarySchemeItem item)
    {
        legacySalaryWrites.reject();
        SysSalarySchemeItem existing = itemMapper.selectSalarySchemeItemById(item.getItemId());
        if (existing == null)
        {
            throw new ServiceException("薪资档位不存在");
        }
        SysSalaryScheme scheme = requireScheme(existing.getSchemeId());
        Integer expectedVersion = expectedItemVersion(item, scheme);
        assertEffectiveSchemeMutationAllowed(scheme, scheme.getEffectiveDate(),
                Boolean.TRUE.equals(item.getEmergencyCorrection()), item.getChangeReason());
        mergeExistingAmounts(item, existing);
        prepareSalarySchemeItem(item);
        item.setUpdateBy(SecurityUtils.getUsername());
        bumpSchemeVersion(scheme.getSchemeId(), expectedVersion);
        int rows = itemMapper.updateSalarySchemeItem(item);
        if (rows == 0)
        {
            throw new ServiceException("薪资档位已被删除或修改，请刷新后重试");
        }
        recordRevision(scheme.getSchemeId(), expectedVersion + 1,
                Boolean.TRUE.equals(item.getEmergencyCorrection()) ? "EMERGENCY_ITEM_UPDATE" : "ITEM_UPDATE",
                reasonOrDefault(item.getChangeReason(), "修改薪资档位"));
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSalarySchemeItemById(Long itemId, Integer expectedVersion, String changeReason,
            boolean emergencyCorrection)
    {
        legacySalaryWrites.reject();
        SysSalarySchemeItem item = itemMapper.selectSalarySchemeItemById(itemId);
        if (item == null)
        {
            throw new ServiceException("薪资档位不存在");
        }
        SysSalaryScheme scheme = requireScheme(item.getSchemeId());
        assertExpectedVersion(scheme, expectedVersion);
        assertEffectiveSchemeMutationAllowed(scheme, scheme.getEffectiveDate(), emergencyCorrection, changeReason);
        SysSalaryImpactStats impact = salaryImpactMapper.selectItemImpact(itemId);
        if (impact != null && safe(impact.getAffectedUserCount()) > 0)
        {
            throw new ServiceException("薪资档位仍影响员工，不能直接删除，请先调整绑定");
        }
        bumpSchemeVersion(scheme.getSchemeId(), expectedVersion);
        int rows = itemMapper.deleteSalarySchemeItemById(itemId);
        if (rows == 0)
        {
            throw new ServiceException("薪资档位已被删除或修改，请刷新后重试");
        }
        recordRevision(scheme.getSchemeId(), expectedVersion + 1,
                emergencyCorrection ? "EMERGENCY_ITEM_DELETE" : "ITEM_DELETE",
                reasonOrDefault(changeReason, "删除薪资档位"));
        return rows;
    }

    @Override
    public SysSalaryImpactPreview previewSalaryImpact(SysSalaryImpactPreviewRequest request)
    {
        if (request == null || StringUtils.isBlank(request.getOperation()))
        {
            throw new ServiceException("预览操作类型不能为空");
        }
        String operation = request.getOperation().trim().toUpperCase(Locale.ROOT);
        SysSalaryImpactPreview preview = new SysSalaryImpactPreview();
        preview.setOperation(operation);
        preview.setSchemeId(request.getSchemeId());
        preview.setEffectiveDate(request.getEffectiveDate());

        switch (operation)
        {
            case "SCHEME_CREATE":
                preview.getWarnings().add("新方案尚未绑定员工，保存后仍需单独配置员工或角色绑定");
                break;
            case "SCHEME_UPDATE":
            case "SCHEME_DELETE":
                SysSalaryScheme scheme = requireScheme(request.getSchemeId());
                preview.setCurrentVersion(scheme.getVersion());
                applyImpact(preview, salaryImpactMapper.selectSchemeImpact(scheme.getSchemeId()));
                if (request.getExpectedVersion() != null && !Objects.equals(request.getExpectedVersion(), scheme.getVersion()))
                {
                    preview.getConflicts().add("方案版本已变化，当前为 v" + scheme.getVersion() + "，请刷新后重新预览");
                }
                if ("SCHEME_DELETE".equals(operation) && preview.getAffectedUserCount() > 0)
                {
                    preview.getWarnings().add("删除会移除该方案的员工和角色绑定");
                }
                break;
            case "ITEM_CREATE":
                SysSalaryScheme itemScheme = requireScheme(request.getSchemeId());
                preview.setCurrentVersion(itemScheme.getVersion());
                if (request.getExpectedVersion() != null && !Objects.equals(request.getExpectedVersion(), itemScheme.getVersion()))
                {
                    preview.getConflicts().add("方案版本已变化，当前为 v" + itemScheme.getVersion() + "，请刷新后重新预览");
                }
                preview.getWarnings().add("新增档位不会自动变更现有员工档位");
                break;
            case "ITEM_UPDATE":
            case "ITEM_DELETE":
                SysSalarySchemeItem item = itemMapper.selectSalarySchemeItemById(request.getItemId());
                if (item == null)
                {
                    throw new ServiceException("薪资档位不存在");
                }
                SysSalaryScheme owner = requireScheme(item.getSchemeId());
                preview.setSchemeId(owner.getSchemeId());
                preview.setCurrentVersion(owner.getVersion());
                applyImpact(preview, salaryImpactMapper.selectItemImpact(item.getItemId()));
                if (request.getExpectedVersion() != null && !Objects.equals(request.getExpectedVersion(), owner.getVersion()))
                {
                    preview.getConflicts().add("方案版本已变化，当前为 v" + owner.getVersion() + "，请刷新后重新预览");
                }
                if ("ITEM_DELETE".equals(operation) && preview.getAffectedUserCount() > 0)
                {
                    preview.getConflicts().add("该档位仍被员工或角色使用，必须先调整绑定");
                }
                break;
            case "ROLE_BINDING":
                if (request.getRoleId() == null)
                {
                    throw new ServiceException("角色ID不能为空");
                }
                int roleUsers = salaryImpactMapper.countUsersByRoleId(request.getRoleId());
                int roleDirectUsers = salaryImpactMapper.countUsersWithDirectSalaryByRoleId(request.getRoleId());
                preview.setAffectedUserCount(roleUsers);
                preview.setRoleInheritedUserCount(roleUsers);
                preview.setDirectUserCount(roleDirectUsers);
                applyRequestedDateCounts(preview, roleUsers, firstEffectiveDate(request));
                if (roleDirectUsers > 0)
                {
                    preview.getWarnings().add(roleDirectUsers + " 名角色成员已有员工直接绑定，将按现有优先级规则决策");
                }
                break;
            case "USER_BINDING":
                if (request.getUserId() == null)
                {
                    throw new ServiceException("用户ID不能为空");
                }
                int inheritedBindings = salaryImpactMapper.countRoleSalaryBindingsByUserId(request.getUserId());
                preview.setAffectedUserCount(1);
                preview.setDirectUserCount(request.getUserBindings() == null || request.getUserBindings().isEmpty() ? 0 : 1);
                preview.setRoleInheritedUserCount(inheritedBindings > 0 ? 1 : 0);
                applyRequestedDateCounts(preview, 1, firstEffectiveDate(request));
                if (preview.getDirectUserCount() > 0 && inheritedBindings > 0)
                {
                    preview.getWarnings().add("该员工同时存在角色继承方案，请核对直接绑定优先级");
                }
                break;
            default:
                throw new ServiceException("不支持的薪资影响预览操作：" + operation);
        }

        preview.setRequiresConfirmation(preview.getAffectedUserCount() > 0
                || !preview.getWarnings().isEmpty() || !preview.getConflicts().isEmpty());
        return preview;
    }

    @Override
    public List<SysSalarySchemeRevision> selectSalarySchemeRevisions(Long schemeId)
    {
        requireScheme(schemeId);
        return revisionMapper.selectRevisionsBySchemeId(schemeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rollbackSalarySchemeRevision(Long revisionId, SysSalaryRevisionRollbackRequest request)
    {
        legacySalaryWrites.reject();
        SysSalarySchemeRevision revision = revisionMapper.selectRevisionById(revisionId);
        if (revision == null)
        {
            throw new ServiceException("薪资修订不存在");
        }
        SysSalaryScheme current = requireScheme(revision.getSchemeId());
        assertExpectedVersion(current, request.getExpectedVersion());
        assertSchemeItemsCanBeReplaced(current.getSchemeId());
        try
        {
            SysSalarySchemeSnapshot snapshot = objectMapper.readValue(revision.getSnapshotJson(), SysSalarySchemeSnapshot.class);
            SysSalaryScheme restored = snapshot.toScheme(current.getSchemeId(), request.getExpectedVersion());
            restored.setUpdateBy(SecurityUtils.getUsername());
            restored.setEmergencyCorrection(request.getEmergencyCorrection());
            restored.setChangeReason(request.getReason());
            assertEffectiveSchemeMutationAllowed(current, restored.getEffectiveDate(),
                    Boolean.TRUE.equals(request.getEmergencyCorrection()), request.getReason());
            int rows = schemeMapper.updateSalaryScheme(restored);
            if (rows == 0)
            {
                throw new ServiceException("薪资方案已被其他管理员修改，请重新预览后再回滚");
            }
            restored.setVersion(current.getVersion() + 1);
            itemMapper.deleteSalarySchemeItemsBySchemeId(current.getSchemeId());
            insertSchemeItems(restored);
            recordRevision(current.getSchemeId(), restored.getVersion(), "ROLLBACK",
                    "恢复到修订 v" + revision.getVersion() + "：" + request.getReason());
            return rows;
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("薪资修订快照无法恢复");
        }
    }

    @Override
    public List<SysRoleSalaryScheme> selectRoleSalarySchemesByRoleId(Long roleId)
    {
        return roleSalarySchemeMapper.selectRoleSalarySchemesByRoleId(roleId);
    }

    @Override
    public List<SysRoleSalaryScheme> selectRoleSalarySchemesByRoleIds(Long[] roleIds)
    {
        Long[] safeRoleIds = normalizeRoleIds(roleIds);
        if (safeRoleIds.length == 0)
        {
            return Collections.emptyList();
        }
        return roleSalarySchemeMapper.selectRoleSalarySchemesByRoleIds(safeRoleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveRoleSalarySchemes(Long roleId, List<SysRoleSalaryScheme> bindings)
    {
        legacySalaryWrites.reject();
        roleSalarySchemeMapper.deleteRoleSalarySchemeByRoleId(roleId);
        List<SysRoleSalaryScheme> safeBindings = bindings == null ? Collections.emptyList() : bindings;
        if (safeBindings.isEmpty())
        {
            return 1;
        }
        List<SysRoleSalaryScheme> rows = new ArrayList<>();
        for (SysRoleSalaryScheme binding : safeBindings)
        {
            binding.setRoleId(roleId);
            if (binding.getSchemeId() == null || binding.getItemId() == null)
            {
                throw new ServiceException("薪资方案和默认档位不能为空");
            }
            assertSchemeExists(binding.getSchemeId());
            SysSalarySchemeItem item = itemMapper.selectSalarySchemeItemById(binding.getItemId());
            if (item == null || !binding.getSchemeId().equals(item.getSchemeId()))
            {
                throw new ServiceException("默认档位不属于所选薪资方案");
            }
            if (binding.getPriority() == null)
            {
                binding.setPriority(10);
            }
            binding.setCreateBy(SecurityUtils.getUsername());
            rows.add(binding);
        }
        return roleSalarySchemeMapper.batchRoleSalaryScheme(rows);
    }

    @Override
    public List<SysUserSalaryScheme> selectUserSalarySchemesByUserId(Long userId)
    {
        return userSalarySchemeMapper.selectUserSalarySchemesByUserId(userId);
    }

    @Override
    public List<SysUserSalaryScheme> selectUserSalarySchemesByUserIds(Long[] userIds)
    {
        Long[] safeUserIds = normalizeRoleIds(userIds);
        if (safeUserIds.length == 0)
        {
            return Collections.emptyList();
        }
        return userSalarySchemeMapper.selectUserSalarySchemesByUserIds(safeUserIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveUserSalarySchemes(Long userId, List<SysUserSalaryScheme> bindings)
    {
        legacySalaryWrites.reject();
        userSalarySchemeMapper.deleteUserSalarySchemeByUserId(userId);
        List<SysUserSalaryScheme> safeBindings = bindings == null ? Collections.emptyList() : bindings;
        if (safeBindings.isEmpty())
        {
            return 1;
        }
        List<SysUserSalaryScheme> rows = new ArrayList<>();
        for (SysUserSalaryScheme binding : safeBindings)
        {
            binding.setUserId(userId);
            if (binding.getShopDeptId() == null)
            {
                throw new ServiceException("核算门店不能为空");
            }
            if (binding.getSchemeId() == null || binding.getItemId() == null)
            {
                throw new ServiceException("薪资方案和默认档位不能为空");
            }
            assertSchemeExists(binding.getSchemeId());
            SysSalarySchemeItem item = itemMapper.selectSalarySchemeItemById(binding.getItemId());
            if (item == null || !binding.getSchemeId().equals(item.getSchemeId()))
            {
                throw new ServiceException("默认档位不属于所选薪资方案");
            }
            if (binding.getPriority() == null)
            {
                binding.setPriority(10);
            }
            if (StringUtils.isEmpty(binding.getStatus()))
            {
                binding.setStatus("0");
            }
            binding.setCreateBy(SecurityUtils.getUsername());
            rows.add(binding);
        }
        return userSalarySchemeMapper.batchUserSalaryScheme(rows);
    }

    private void insertSchemeItems(SysSalaryScheme scheme)
    {
        if (scheme.getItems() == null || scheme.getItems().isEmpty())
        {
            return;
        }
        int sort = 1;
        for (SysSalarySchemeItem item : scheme.getItems())
        {
            item.setSchemeId(scheme.getSchemeId());
            if (item.getItemSort() == null)
            {
                item.setItemSort(sort);
            }
            prepareSalarySchemeItem(item);
            item.setCreateBy(SecurityUtils.getUsername());
            itemMapper.insertSalarySchemeItem(item);
            sort++;
        }
    }

    private void assertSchemeExists(Long schemeId)
    {
        requireScheme(schemeId);
    }

    private SysSalaryScheme requireScheme(Long schemeId)
    {
        SysSalaryScheme scheme = schemeId == null ? null : schemeMapper.selectSalarySchemeById(schemeId);
        if (scheme == null)
        {
            throw new ServiceException("薪资方案不存在");
        }
        return scheme;
    }

    private void assertSchemeItemsCanBeReplaced(Long schemeId)
    {
        assertSchemeExists(schemeId);
        List<SysRoleSalaryScheme> bindings = roleSalarySchemeMapper.selectRoleSalarySchemesBySchemeId(schemeId);
        if (bindings != null && !bindings.isEmpty())
        {
            throw new ServiceException("薪资方案已绑定角色，不允许整批替换档位");
        }
        SysSalaryImpactStats impact = salaryImpactMapper.selectSchemeImpact(schemeId);
        if (impact != null && safe(impact.getDirectUserCount()) > 0)
        {
            throw new ServiceException("薪资方案已绑定员工，不允许整批替换档位");
        }
    }

    private void assertExpectedVersion(SysSalaryScheme existing, Integer expectedVersion)
    {
        if (expectedVersion == null)
        {
            throw new ServiceException("薪资方案版本不能为空，请刷新后重新预览");
        }
        if (!Objects.equals(existing.getVersion(), expectedVersion))
        {
            throw new ServiceException("薪资方案版本已变化，当前为 v" + existing.getVersion() + "，请重新预览");
        }
    }

    private Integer expectedItemVersion(SysSalarySchemeItem item, SysSalaryScheme scheme)
    {
        Integer expectedVersion = item.getSchemeVersion() == null ? scheme.getVersion() : item.getSchemeVersion();
        assertExpectedVersion(scheme, expectedVersion);
        return expectedVersion;
    }

    private void bumpSchemeVersion(Long schemeId, Integer expectedVersion)
    {
        int rows = schemeMapper.bumpSalarySchemeVersion(schemeId, expectedVersion, SecurityUtils.getUsername());
        if (rows == 0)
        {
            throw new ServiceException("薪资方案已被其他管理员修改，请重新预览后再保存");
        }
    }

    private void assertEffectiveSchemeMutationAllowed(SysSalaryScheme existing, String proposedEffectiveDate,
            boolean emergencyCorrection, String changeReason)
    {
        LocalDate existingDate = parseDate(existing.getEffectiveDate(), "现有方案生效日期");
        if (existingDate == null || existingDate.isAfter(LocalDate.now(BUSINESS_ZONE)))
        {
            return;
        }
        if (emergencyCorrection)
        {
            if (!AuthUtil.hasPermi("system:salary:emergency"))
            {
                throw new ServiceException("缺少薪资紧急修正权限", HttpStatus.FORBIDDEN);
            }
            if (StringUtils.isBlank(changeReason))
            {
                throw new ServiceException("紧急修正必须填写原因");
            }
            return;
        }
        LocalDate proposed = parseDate(proposedEffectiveDate, "新方案生效日期");
        if (proposed == null || !proposed.isAfter(LocalDate.now(BUSINESS_ZONE)))
        {
            throw new ServiceException("已生效方案不能原地修改，请设置未来生效日期；紧急修正需独立权限和原因");
        }
    }

    private LocalDate parseDate(String value, String label)
    {
        if (StringUtils.isBlank(value))
        {
            return null;
        }
        try
        {
            return LocalDate.parse(value);
        }
        catch (Exception ex)
        {
            throw new ServiceException(label + "格式必须为 YYYY-MM-DD");
        }
    }

    private void recordRevision(Long schemeId, Integer version, String changeType, String reason)
    {
        SysSalaryScheme scheme = requireScheme(schemeId);
        List<SysSalarySchemeItem> items = itemMapper.selectSalarySchemeItemsBySchemeId(schemeId);
        try
        {
            SysSalarySchemeRevision revision = new SysSalarySchemeRevision();
            revision.setSchemeId(schemeId);
            revision.setVersion(version);
            revision.setChangeType(changeType);
            revision.setChangeReason(reasonOrDefault(reason, changeType));
            revision.setSnapshotJson(objectMapper.writeValueAsString(SysSalarySchemeSnapshot.from(scheme, items)));
            revision.setCreateBy(SecurityUtils.getUsername());
            revisionMapper.insertRevision(revision);
        }
        catch (Exception ex)
        {
            throw new ServiceException("薪资方案修订快照生成失败");
        }
    }

    private void applyImpact(SysSalaryImpactPreview preview, SysSalaryImpactStats stats)
    {
        if (stats == null)
        {
            return;
        }
        preview.setAffectedUserCount(stats.getAffectedUserCount());
        preview.setDirectUserCount(stats.getDirectUserCount());
        preview.setRoleInheritedUserCount(stats.getRoleInheritedUserCount());
        preview.setEffectiveNowCount(stats.getEffectiveNowCount());
        preview.setFutureEffectiveCount(stats.getFutureEffectiveCount());
        if (safe(stats.getConflictUserCount()) > 0)
        {
            preview.getWarnings().add(stats.getConflictUserCount() + " 名员工同时存在直接绑定和角色继承，请核对优先级");
        }
    }

    private void applyRequestedDateCounts(SysSalaryImpactPreview preview, int affectedUsers, String effectiveDate)
    {
        preview.setEffectiveDate(effectiveDate);
        LocalDate date = parseDate(effectiveDate, "生效日期");
        if (date != null && date.isAfter(LocalDate.now(BUSINESS_ZONE)))
        {
            preview.setFutureEffectiveCount(affectedUsers);
            preview.setEffectiveNowCount(0);
        }
        else
        {
            preview.setEffectiveNowCount(affectedUsers);
            preview.setFutureEffectiveCount(0);
        }
    }

    private String firstEffectiveDate(SysSalaryImpactPreviewRequest request)
    {
        if (StringUtils.isNotBlank(request.getEffectiveDate()))
        {
            return request.getEffectiveDate();
        }
        if (request.getUserBindings() != null)
        {
            for (SysUserSalaryScheme binding : request.getUserBindings())
            {
                if (binding != null && StringUtils.isNotBlank(binding.getEffectiveDate()))
                {
                    return binding.getEffectiveDate();
                }
            }
        }
        if (request.getRoleBindings() != null)
        {
            for (SysRoleSalaryScheme binding : request.getRoleBindings())
            {
                if (binding != null && StringUtils.isNotBlank(binding.getEffectiveDate()))
                {
                    return binding.getEffectiveDate();
                }
            }
        }
        return null;
    }

    private String reasonOrDefault(String reason, String defaultReason)
    {
        return StringUtils.isBlank(reason) ? defaultReason : reason.trim();
    }

    private int safe(Integer value)
    {
        return value == null ? 0 : value;
    }

    private void prepareSalarySchemeItem(SysSalarySchemeItem item)
    {
        if (item.getBaseSalary() == null)
        {
            throw new ServiceException("基本工资不能为空");
        }
        item.setManagementAllowance(defaultAmount(item.getManagementAllowance()));
        item.setOvertimePay(defaultAmount(item.getOvertimePay()));
        item.setRewardAllowance(defaultAmount(item.getRewardAllowance()));
        item.setFullAttendanceBonus(defaultAmount(item.getFullAttendanceBonus()));
        item.setSocialSubsidy(defaultAmount(item.getSocialSubsidy()));
        item.setCommuteSubsidy(defaultAmount(item.getCommuteSubsidy()));
        assertNonNegative("基本工资", item.getBaseSalary());
        assertNonNegative("管理津贴", item.getManagementAllowance());
        assertNonNegative("加班费", item.getOvertimePay());
        assertNonNegative("奖励津贴", item.getRewardAllowance());
        assertNonNegative("全勤奖", item.getFullAttendanceBonus());
        assertNonNegative("社保补贴", item.getSocialSubsidy());
        assertNonNegative("通勤补贴", item.getCommuteSubsidy());
        item.setTotalSalary(item.getBaseSalary()
                .add(item.getManagementAllowance())
                .add(item.getOvertimePay())
                .add(item.getRewardAllowance())
                .add(item.getFullAttendanceBonus())
                .add(item.getSocialSubsidy())
                .add(item.getCommuteSubsidy()));
    }

    private void mergeExistingAmounts(SysSalarySchemeItem item, SysSalarySchemeItem existing)
    {
        if (item.getSchemeId() == null)
        {
            item.setSchemeId(existing.getSchemeId());
        }
        if (item.getBaseSalary() == null)
        {
            item.setBaseSalary(existing.getBaseSalary());
        }
        if (item.getManagementAllowance() == null)
        {
            item.setManagementAllowance(existing.getManagementAllowance());
        }
        if (item.getOvertimePay() == null)
        {
            item.setOvertimePay(existing.getOvertimePay());
        }
        if (item.getRewardAllowance() == null)
        {
            item.setRewardAllowance(existing.getRewardAllowance());
        }
        if (item.getFullAttendanceBonus() == null)
        {
            item.setFullAttendanceBonus(existing.getFullAttendanceBonus());
        }
        if (item.getSocialSubsidy() == null)
        {
            item.setSocialSubsidy(existing.getSocialSubsidy());
        }
        if (item.getCommuteSubsidy() == null)
        {
            item.setCommuteSubsidy(existing.getCommuteSubsidy());
        }
    }

    private BigDecimal defaultAmount(BigDecimal value)
    {
        return value == null ? ZERO : value;
    }

    private void assertNonNegative(String label, BigDecimal value)
    {
        if (value.compareTo(ZERO) < 0)
        {
            throw new ServiceException(label + "不能为负数");
        }
    }

    private Long[] normalizeRoleIds(Long[] roleIds)
    {
        if (roleIds == null || roleIds.length == 0)
        {
            return new Long[0];
        }
        Set<Long> unique = new LinkedHashSet<Long>();
        Arrays.stream(roleIds)
                .filter(id -> id != null && id > 0)
                .forEach(unique::add);
        return unique.toArray(new Long[0]);
    }
}
