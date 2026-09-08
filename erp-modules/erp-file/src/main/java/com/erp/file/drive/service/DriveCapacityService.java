package com.erp.file.drive.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveCapacityConfig;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.DriveSpace;
import com.erp.file.drive.domain.vo.DriveCapacityOverviewVo;
import com.erp.file.drive.domain.vo.DriveUserQuotaVo;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.mapper.DriveCapacityMapper;
import com.erp.file.drive.mapper.DriveOrganizationMapper;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import com.erp.file.drive.mapper.DriveUploadReservationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计算并约束物理容量、安全保留和公共/个人/组织逻辑额度池。
 */
@Service
public class DriveCapacityService
{
    private static final long CONFIG_ID = 1L;

    private final DriveCapacityMapper capacityMapper;
    private final DriveOrganizationMapper organizationMapper;
    private final DriveSpaceMapper spaceMapper;
    private final DriveUploadReservationMapper reservationMapper;
    private final DriveEffectiveQuotaService effectiveQuotaService;
    private final DriveAuthorizationService authorization;
    private final DriveProperties properties;

    public DriveCapacityService(DriveCapacityMapper capacityMapper,
            DriveOrganizationMapper organizationMapper, DriveSpaceMapper spaceMapper,
            DriveUploadReservationMapper reservationMapper,
            DriveEffectiveQuotaService effectiveQuotaService,
            DriveAuthorizationService authorization, DriveProperties properties)
    {
        this.capacityMapper = capacityMapper;
        this.organizationMapper = organizationMapper;
        this.spaceMapper = spaceMapper;
        this.reservationMapper = reservationMapper;
        this.effectiveQuotaService = effectiveQuotaService;
        this.authorization = authorization;
        this.properties = properties;
    }

    public DriveCapacityOverviewVo overview(DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        return calculateOverview(requireConfig());
    }

    public DriveCapacityOverviewVo calculateOverview()
    {
        return calculateOverview(requireConfig());
    }

    public DriveCapacityOverviewVo preview(Long physicalCapacityBytes, int reservePercent,
            long publicPoolBytes, long personalPoolBytes, long organizationPoolBytes,
            String enforcementMode, DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        String mode = validate(physicalCapacityBytes, reservePercent, publicPoolBytes,
                personalPoolBytes, organizationPoolBytes, enforcementMode, "影响预览");
        requireReservationEnabledForBlock(mode);
        DriveCapacityConfig current = requireConfig();
        DriveCapacityConfig proposed = new DriveCapacityConfig();
        proposed.setConfigId(CONFIG_ID);
        proposed.setPhysicalCapacityBytes(physicalCapacityBytes);
        proposed.setReservePercent(reservePercent);
        proposed.setPublicPoolBytes(publicPoolBytes);
        proposed.setPersonalPoolBytes(personalPoolBytes);
        proposed.setOrganizationPoolBytes(organizationPoolBytes);
        proposed.setEnforcementMode(mode);
        proposed.setVersion(current.getVersion());
        return calculateOverview(proposed);
    }

    @Transactional
    public DriveCapacityOverviewVo update(Long physicalCapacityBytes, int reservePercent,
            long publicPoolBytes, long personalPoolBytes, long organizationPoolBytes,
            String enforcementMode, int version, String reason, DriveActor actor)
    {
        authorization.requireQuotaManage(actor);
        DriveCapacityConfig current = capacityMapper.selectConfigForUpdate(CONFIG_ID);
        if (current == null)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                    "云盘容量配置尚未初始化");
        }
        String mode = validate(physicalCapacityBytes, reservePercent, publicPoolBytes,
                personalPoolBytes, organizationPoolBytes, enforcementMode, reason);
        requireReservationEnabledForBlock(mode);
        if (current.getVersion() == null || current.getVersion() != version)
        {
            throw concurrentModification();
        }

        DriveCapacityConfig next = new DriveCapacityConfig();
        next.setConfigId(CONFIG_ID);
        next.setPhysicalCapacityBytes(physicalCapacityBytes);
        next.setReservePercent(reservePercent);
        next.setPublicPoolBytes(publicPoolBytes);
        next.setPersonalPoolBytes(personalPoolBytes);
        next.setOrganizationPoolBytes(organizationPoolBytes);
        next.setEnforcementMode(mode);
        next.setVersion(version);
        next.setUpdateBy(username(actor));
        next.setUpdateTime(new Date());
        next.setRemark(reason.trim());

        validatePoolTotal(next);
        DriveCapacityOverviewVo proposed = calculateOverview(next);
        if (DriveConstants.CAPACITY_BLOCK.equals(mode)) requireWithinPools(proposed);
        if (capacityMapper.updateConfig(next) != 1) throw concurrentModification();
        return calculateOverview(requireConfig());
    }

    /**
     * 所有会改变逻辑分配量的事务都先锁定同一行，避免并发超分。
     */
    public DriveCapacityConfig lockForAllocationChange()
    {
        DriveCapacityConfig config = capacityMapper.selectConfigForUpdate(CONFIG_ID);
        if (config == null)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                    "云盘容量配置尚未初始化");
        }
        return config;
    }

    /**
     * 在创建上传预占的短事务内调用。固定容量行串行化所有预占，避免并发越界。
     */
    public void requireUploadCapacityAndLock(long bytes)
    {
        if (bytes <= 0)
        {
            throw invalid("上传预占容量必须大于 0");
        }
        DriveCapacityConfig config = lockForAllocationChange();
        if (!DriveConstants.CAPACITY_BLOCK.equals(config.getEnforcementMode())) return;
        Long allocatable = allocatable(config.getPhysicalCapacityBytes(),
                value(config.getReservePercent(), 20));
        if (allocatable == null)
        {
            throw capacityExceeded("强制容量模式尚未配置物理容量");
        }
        long committed = committedUsedBytes();
        long pending = pendingUploadBytes();
        long accounted = safeAdd(committed, pending);
        if (accounted > allocatable || bytes > allocatable - accounted)
        {
            throw capacityExceeded("云盘全局可分配容量不足");
        }
    }

    /**
     * 兼容升级期间的旧配置：若数据库已经处于强制容量模式，则不允许在预占
     * 开关关闭时继续上传，否则对象写入阶段会绕过全局容量并发控制。
     */
    public void requireLegacyUploadAllowedWithoutReservation()
    {
        if (properties.isCapacityReservationEnabled()) return;
        DriveCapacityConfig config = capacityMapper.selectConfig(CONFIG_ID);
        if (config != null && DriveConstants.CAPACITY_BLOCK.equals(
                config.getEnforcementMode()))
        {
            throw invalid("当前强制容量模式要求先启用上传容量预占，请联系管理员");
        }
    }

    /** 容量行已由调用方锁定时，确认当前已提交与预占仍处于安全范围。 */
    public void requireCurrentAccountedUsageWithinCapacity(DriveCapacityConfig config)
    {
        if (config == null || !DriveConstants.CAPACITY_BLOCK.equals(
                config.getEnforcementMode())) return;
        Long allocatable = allocatable(config.getPhysicalCapacityBytes(),
                value(config.getReservePercent(), 20));
        long accounted = safeAdd(committedUsedBytes(), pendingUploadBytes());
        if (allocatable == null || accounted > allocatable)
        {
            throw capacityExceeded("云盘已提交用量与上传预占超过可分配容量");
        }
    }

    public void requireCurrentAllocationsWithinPools()
    {
        DriveCapacityConfig config = lockForAllocationChange();
        if (DriveConstants.CAPACITY_BLOCK.equals(config.getEnforcementMode()))
        {
            requireWithinPools(calculateOverview(config));
        }
    }

    public void requireOrganizationAllocationWithinPool(long organizationAllocatedBytes)
    {
        DriveCapacityConfig config = lockForAllocationChange();
        if (DriveConstants.CAPACITY_BLOCK.equals(config.getEnforcementMode())
                && organizationAllocatedBytes > nonNegative(config.getOrganizationPoolBytes()))
        {
            throw new DriveException(DriveErrorCodes.DRIVE_CAPACITY_EXCEEDED,
                    "修改后组织盘逻辑分配将超出组织盘池");
        }
    }

    private DriveCapacityOverviewVo calculateOverview(DriveCapacityConfig config)
    {
        long publicAllocated = 0L;
        for (DriveSpace space : safeList(spaceMapper.selectByType(DriveConstants.SPACE_COMPANY)))
        {
            publicAllocated = safeAdd(publicAllocated, nonNegative(space.getQuotaBytes()));
        }
        long actualUsed = committedUsedBytes();
        long pendingUpload = pendingUploadBytes();
        long accounted = safeAdd(actualUsed, pendingUpload);
        Date now = new Date();
        Date staleCleaningBefore = new Date(now.getTime() - reservationTimeoutMillis());
        int staleReservations = reservationMapper.countStale(now, staleCleaningBefore);
        int cleanupFailedReservations = reservationMapper.countCleanupFailed();

        long personalAllocated = 0L;
        for (DriveUserQuotaVo user : effectiveQuotaService.listEffectiveUsers())
        {
            personalAllocated = safeAdd(personalAllocated, user.quotaBytes());
        }
        long organizationAllocated = 0L;
        for (DriveOrganizationSpaceConfig org : safeList(organizationMapper.selectConfigs()))
        {
            if (Boolean.TRUE.equals(org.getEnabled())
                    && !DriveConstants.STATUS_ARCHIVED.equals(org.getLifecycleStatus()))
            {
                organizationAllocated = safeAdd(organizationAllocated,
                        nonNegative(org.getQuotaBytes()));
            }
        }

        Long allocatable = allocatable(config.getPhysicalCapacityBytes(),
                value(config.getReservePercent(), 20));
        List<String> warnings = warnings(config, allocatable, publicAllocated,
                personalAllocated, organizationAllocated, pendingUpload,
                accounted, cleanupFailedReservations);
        return new DriveCapacityOverviewVo(config.getPhysicalCapacityBytes(),
                value(config.getReservePercent(), 20), allocatable,
                nonNegative(config.getPublicPoolBytes()),
                nonNegative(config.getPersonalPoolBytes()),
                nonNegative(config.getOrganizationPoolBytes()), publicAllocated,
                personalAllocated, organizationAllocated, actualUsed, pendingUpload,
                accounted, staleReservations, cleanupFailedReservations,
                config.getEnforcementMode(), value(config.getVersion(), 0), warnings);
    }

    private static List<String> warnings(DriveCapacityConfig config, Long allocatable,
            long publicAllocated, long personalAllocated, long organizationAllocated,
            long pendingUpload, long accounted,
            int cleanupFailedReservations)
    {
        List<String> warnings = new ArrayList<>();
        if (config.getPhysicalCapacityBytes() == null)
        {
            warnings.add("尚未确认物理可用容量，不能启用强制容量模式");
        }
        if (publicAllocated > nonNegative(config.getPublicPoolBytes()))
        {
            warnings.add("公司公共盘已分配额度超过公共盘池");
        }
        if (personalAllocated > nonNegative(config.getPersonalPoolBytes()))
        {
            warnings.add("个人盘已分配额度超过个人盘池");
        }
        if (organizationAllocated > nonNegative(config.getOrganizationPoolBytes()))
        {
            warnings.add("组织盘已分配额度超过组织盘池");
        }
        if (allocatable != null)
        {
            long poolTotal = safeAdd(safeAdd(nonNegative(config.getPublicPoolBytes()),
                    nonNegative(config.getPersonalPoolBytes())),
                    nonNegative(config.getOrganizationPoolBytes()));
            if (poolTotal > allocatable) warnings.add("三个逻辑额度池之和超过可分配容量");
            if (accounted > allocatable)
            {
                warnings.add("已提交用量与上传预占超过扣除安全保留后的可分配容量");
            }
            else
            {
                int percent = usagePercent(accounted, allocatable);
                if (percent >= 100) warnings.add("实际容量使用率已达到 100%");
                else if (percent >= 95) warnings.add("实际容量使用率已达到 95%，请立即扩容或清理");
                else if (percent >= 85) warnings.add("实际容量使用率已达到 85%，请安排扩容或清理");
                else if (percent >= 70) warnings.add("实际容量使用率已达到 70%，请关注增长趋势");
            }
        }
        if (pendingUpload > 0)
        {
            warnings.add("当前存在 " + pendingUpload + " 字节上传中或待清理容量预占");
        }
        if (cleanupFailedReservations > 0)
        {
            warnings.add("存在 " + cleanupFailedReservations + " 条上传对象清理失败预占");
        }
        return warnings;
    }

    private void requireReservationEnabledForBlock(String mode)
    {
        if (DriveConstants.CAPACITY_BLOCK.equals(mode)
                && !properties.isCapacityReservationEnabled())
        {
            throw invalid("开启强制容量模式前必须先启用上传容量预占");
        }
    }

    private static void requireWithinPools(DriveCapacityOverviewVo overview)
    {
        if (overview.physicalCapacityBytes() == null
                || overview.allocatableCapacityBytes() == null)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_CAPACITY_EXCEEDED,
                    "强制容量模式必须先确认物理容量");
        }
        if (overview.publicAllocatedBytes() > overview.publicPoolBytes())
        {
            throw capacityExceeded("公司公共盘已分配额度超过公共盘池");
        }
        if (overview.personalAllocatedBytes() > overview.personalPoolBytes())
        {
            throw capacityExceeded("个人盘已分配额度超过个人盘池");
        }
        if (overview.organizationAllocatedBytes() > overview.organizationPoolBytes())
        {
            throw capacityExceeded("组织盘已分配额度超过组织盘池");
        }
        long poolTotal = safeAdd(safeAdd(overview.publicPoolBytes(),
                overview.personalPoolBytes()), overview.organizationPoolBytes());
        if (poolTotal > overview.allocatableCapacityBytes())
        {
            throw capacityExceeded("三个逻辑额度池之和超过可分配容量");
        }
        if (overview.capacityAccountedBytes() > overview.allocatableCapacityBytes())
        {
            throw capacityExceeded("已提交用量与上传预占超过扣除安全保留后的可分配容量");
        }
    }

    private long committedUsedBytes()
    {
        return nonNegative(spaceMapper.selectTotalUsedBytes());
    }

    private long pendingUploadBytes()
    {
        return nonNegative(reservationMapper.sumPendingBytes());
    }

    private long reservationTimeoutMillis()
    {
        long minutes = Math.max(1L, properties.getUploadReservationTimeoutMinutes());
        try
        {
            return Math.multiplyExact(minutes, 60_000L);
        }
        catch (ArithmeticException ex)
        {
            return Long.MAX_VALUE;
        }
    }

    private static int usagePercent(long used, long capacity)
    {
        if (capacity <= 0) return used > 0 ? 100 : 0;
        return BigInteger.valueOf(Math.max(0L, used))
                .multiply(BigInteger.valueOf(100L))
                .divide(BigInteger.valueOf(capacity))
                .min(BigInteger.valueOf(Integer.MAX_VALUE))
                .intValue();
    }

    private static String validate(Long physicalCapacityBytes, int reservePercent,
            long publicPoolBytes, long personalPoolBytes, long organizationPoolBytes,
            String enforcementMode, String reason)
    {
        if (physicalCapacityBytes != null && physicalCapacityBytes <= 0)
        {
            throw invalid("物理容量必须大于 0");
        }
        if (reservePercent < 0 || reservePercent > 90
                || publicPoolBytes < 0 || personalPoolBytes < 0 || organizationPoolBytes < 0)
        {
            throw invalid("容量池或安全保留比例无效");
        }
        String mode = enforcementMode == null ? ""
                : enforcementMode.trim().toUpperCase(Locale.ROOT);
        if (!List.of(DriveConstants.CAPACITY_WARN, DriveConstants.CAPACITY_BLOCK).contains(mode))
        {
            throw invalid("容量执行模式无效");
        }
        if (DriveConstants.CAPACITY_BLOCK.equals(mode) && physicalCapacityBytes == null)
        {
            throw invalid("强制容量模式必须先确认物理容量");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500)
        {
            throw invalid("请填写 500 字以内的容量调整原因");
        }
        return mode;
    }

    private static void validatePoolTotal(DriveCapacityConfig config)
    {
        Long allocatable = allocatable(config.getPhysicalCapacityBytes(), config.getReservePercent());
        if (allocatable == null) return;
        long total = safeAdd(safeAdd(config.getPublicPoolBytes(), config.getPersonalPoolBytes()),
                config.getOrganizationPoolBytes());
        if (total > allocatable)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_CAPACITY_EXCEEDED,
                    "三个逻辑额度池之和不能超过扣除安全保留后的容量");
        }
    }

    private DriveCapacityConfig requireConfig()
    {
        DriveCapacityConfig config = capacityMapper.selectConfig(CONFIG_ID);
        if (config == null)
        {
            throw new DriveException(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE,
                    "云盘容量配置尚未初始化");
        }
        return config;
    }

    private static Long allocatable(Long physical, int reservePercent)
    {
        if (physical == null) return null;
        BigInteger result = BigInteger.valueOf(physical)
                .multiply(BigInteger.valueOf(100L - reservePercent))
                .divide(BigInteger.valueOf(100L));
        return result.longValueExact();
    }

    private static long safeAdd(long left, long right)
    {
        try
        {
            return Math.addExact(left, right);
        }
        catch (ArithmeticException ex)
        {
            throw invalid("容量合计超出系统可支持范围");
        }
    }

    private static long nonNegative(Long value)
    {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static int value(Integer value, int fallback)
    {
        return value == null ? fallback : value;
    }

    private static <T> List<T> safeList(List<T> values)
    {
        return values == null ? List.of() : values;
    }

    private static String username(DriveActor actor)
    {
        return actor == null || actor.username() == null ? "" : actor.username();
    }

    private static DriveException invalid(String message)
    {
        return new DriveException(DriveErrorCodes.DRIVE_POLICY_INVALID, message);
    }

    private static DriveException capacityExceeded(String message)
    {
        return new DriveException(DriveErrorCodes.DRIVE_CAPACITY_EXCEEDED, message);
    }

    private static DriveException concurrentModification()
    {
        return new DriveException(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION,
                "容量配置已被其他操作更新，请刷新后重试");
    }
}
