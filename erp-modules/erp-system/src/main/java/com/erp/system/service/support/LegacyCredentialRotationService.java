package com.erp.system.service.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.domain.dto.LegacyCredentialRotationExecuteRequest;
import com.erp.system.domain.dto.LegacyCredentialRotationPreviewRequest;
import com.erp.system.domain.maintenance.LegacyCredentialCandidate;
import com.erp.system.domain.maintenance.LegacyCredentialMigrationAudit;
import com.erp.system.domain.vo.LegacyCredentialRotationCandidateVo;
import com.erp.system.domain.vo.LegacyCredentialRotationPreviewVo;
import com.erp.system.domain.vo.LegacyCredentialRotationResultVo;
import com.erp.system.domain.vo.SysTemporaryCredentialVo;
import com.erp.system.mapper.LegacyCredentialMaintenanceMapper;
import com.erp.system.service.ISysConfigService;

@Service
public class LegacyCredentialRotationService
{
    private static final int MAX_BATCH_SIZE = 50;
    private static final Pattern SAFE_BATCH = Pattern.compile("[a-f0-9]{32}");
    private static final Pattern SAFE_DIGEST = Pattern.compile("[a-f0-9]{64}");

    private final LegacyCredentialMaintenanceProperties properties;
    private final LegacyCredentialMaintenanceMapper mapper;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final ISysConfigService configService;
    private final UserSessionInvalidationService sessionInvalidationService;
    private final LegacyCredentialAuditor auditor = new LegacyCredentialAuditor();

    public LegacyCredentialRotationService(LegacyCredentialMaintenanceProperties properties,
            LegacyCredentialMaintenanceMapper mapper, TemporaryPasswordGenerator passwordGenerator,
            ISysConfigService configService, UserSessionInvalidationService sessionInvalidationService)
    {
        this.properties = properties;
        this.mapper = mapper;
        this.passwordGenerator = passwordGenerator;
        this.configService = configService;
        this.sessionInvalidationService = sessionInvalidationService;
    }

    public boolean isOpen()
    {
        if (properties.getLegacyConfigKey() == null || properties.getLegacyConfigKey().isBlank())
        {
            return false;
        }
        String value = mapper.selectConfigValueForMaintenance(properties.getLegacyConfigKey());
        boolean configured = value != null && !value.isEmpty();
        value = null;
        return configured;
    }

    public LegacyCredentialRotationPreviewVo preview(LegacyCredentialRotationPreviewRequest request,
            Long operatorUserId)
    {
        assertOperator(operatorUserId);
        int limit = request == null || request.getLimit() == null ? MAX_BATCH_SIZE : request.getLimit();
        if (limit <= 0 || limit > MAX_BATCH_SIZE)
        {
            throw new ServiceException("单批轮换人数必须为 1 到 50");
        }
        List<Long> requestedIds = normalizeIds(request == null ? null : request.getCandidateUserIds());
        if (requestedIds.size() > MAX_BATCH_SIZE)
        {
            throw new ServiceException("单批轮换人数不能超过 50");
        }

        String legacyPassword = requireLegacyPassword();
        try
        {
            List<LegacyCredentialCandidate> matches = new ArrayList<>();
            for (LegacyCredentialCandidate candidate : mapper.selectCandidates())
            {
                if (auditor.classify(candidate, legacyPassword).startsWith("SHARED_"))
                {
                    matches.add(candidate);
                }
            }
            matches.sort(Comparator.comparing(LegacyCredentialCandidate::getUserId));
            List<LegacyCredentialCandidate> selected;
            if (requestedIds.isEmpty())
            {
                selected = matches.stream().limit(limit).collect(Collectors.toList());
            }
            else
            {
                Set<Long> requested = new HashSet<>(requestedIds);
                selected = matches.stream().filter(item -> requested.contains(item.getUserId()))
                        .collect(Collectors.toList());
                if (selected.size() != requested.size())
                {
                    throw new ServiceException("候选账号已变化，请重新执行只读审计");
                }
            }
            if (selected.isEmpty())
            {
                throw new ServiceException("没有可轮换的旧共享凭据账号");
            }
            LegacyCredentialAuditResult result = auditor.audit(selected, legacyPassword);
            String batchId = UUID.randomUUID().toString().replace("-", "");
            mapper.insertAudit(toAudit(batchId, "ROTATION_PREVIEW", "PREVIEWED",
                    operatorUserId, result));
            List<LegacyCredentialRotationCandidateVo> candidates = selected.stream()
                    .map(item -> new LegacyCredentialRotationCandidateVo(item.getUserId(),
                            item.getDeptId(), item.getStatus()))
                    .collect(Collectors.toList());
            return new LegacyCredentialRotationPreviewVo(batchId, result.getDigest(),
                    Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)), candidates);
        }
        finally
        {
            legacyPassword = null;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public LegacyCredentialRotationResultVo execute(LegacyCredentialRotationExecuteRequest request,
            Long operatorUserId)
    {
        assertOperator(operatorUserId);
        validateExecuteRequest(request);
        LegacyCredentialMigrationAudit preview = mapper.selectPreviewAudit(request.getBatchId());
        if (preview == null || !"PREVIEWED".equals(preview.getStatus())
                || !operatorUserId.equals(preview.getOperatorUserId())
                || preview.getCreatedAt() == null
                || preview.getCreatedAt().toInstant().isBefore(Instant.now().minus(15, ChronoUnit.MINUTES)))
        {
            throw new ServiceException("轮换预览不存在、已过期或已被使用");
        }
        if (!request.getCandidateDigest().equals(preview.getCandidateDigest()))
        {
            throw new ServiceException("候选摘要与预览不一致");
        }
        List<Long> userIds = normalizeIds(request.getCandidateUserIds());
        if (userIds.isEmpty() || userIds.size() > MAX_BATCH_SIZE
                || userIds.size() != preview.getTotalUserCount())
        {
            throw new ServiceException("轮换候选数量与预览不一致");
        }

        String legacyPassword = requireLegacyPassword();
        try
        {
            List<LegacyCredentialCandidate> locked = mapper.selectCandidatesForUpdate(userIds);
            if (locked.size() != userIds.size())
            {
                throw new ServiceException("轮换候选账号已变化");
            }
            LegacyCredentialAuditResult current = auditor.audit(locked, legacyPassword);
            if (current.getSharedMatches() != locked.size()
                    || !current.getDigest().equals(preview.getCandidateDigest()))
            {
                throw new ServiceException("轮换候选凭据或状态已变化，请重新预览");
            }
            String passwordPolicy = configService.selectConfigByKey("sys.account.chrtype");
            Date expiresAt = Date.from(Instant.now().plus(24, ChronoUnit.HOURS));
            List<SysTemporaryCredentialVo> credentials = new ArrayList<>();
            for (LegacyCredentialCandidate candidate : locked)
            {
                String temporaryPassword = passwordGenerator.generate(passwordPolicy);
                String encoded = SecurityUtils.encryptPassword(temporaryPassword);
                if (mapper.resetCredentialAndDisable(candidate.getUserId(), encoded,
                        expiresAt, operatorUserId) != 1)
                {
                    throw new ServiceException("旧账号凭据轮换失败");
                }
                sessionInvalidationService.record(candidate.getUserId(),
                        UserSessionInvalidationService.LEGACY_CREDENTIAL_ROTATED);
                credentials.add(new SysTemporaryCredentialVo(candidate.getUserId(),
                        candidate.getUserName(), temporaryPassword, expiresAt));
            }
            if (mapper.updatePreviewStatus(request.getBatchId(), "PREVIEWED", "EXECUTED") != 1)
            {
                throw new ServiceException("轮换预览已被其他操作使用");
            }
            mapper.insertAudit(toAudit(request.getBatchId(), "ROTATION_EXECUTE", "COMPLETED",
                    operatorUserId, current));
            return new LegacyCredentialRotationResultVo(credentials);
        }
        finally
        {
            legacyPassword = null;
        }
    }

    private LegacyCredentialMigrationAudit toAudit(String batchId, String phase, String status,
            Long operatorUserId, LegacyCredentialAuditResult result)
    {
        LegacyCredentialMigrationAudit audit = new LegacyCredentialMigrationAudit();
        audit.setBatchId(batchId);
        audit.setPhase(phase);
        audit.setTargetDatabase(mapper.selectCurrentDatabase());
        audit.setTotalUserCount(result.getTotal());
        audit.setSharedMatchCount(result.getSharedMatches());
        audit.setActiveSharedMatchCount(result.getActiveSharedMatches());
        audit.setChangeRequiredCount(result.getChangeRequired());
        audit.setTemporaryCount(result.getTemporary());
        audit.setOperatorUserId(operatorUserId);
        audit.setCandidateDigest(result.getDigest());
        audit.setStatus(status);
        return audit;
    }

    private String requireLegacyPassword()
    {
        if (properties.getLegacyConfigKey() == null || properties.getLegacyConfigKey().isBlank())
        {
            throw new ServiceException("旧凭据迁移已关闭");
        }
        String legacyPassword = mapper.selectConfigValueForMaintenance(properties.getLegacyConfigKey());
        if (legacyPassword == null || legacyPassword.isEmpty())
        {
            throw new ServiceException("旧凭据迁移已关闭");
        }
        return legacyPassword;
    }

    private static List<Long> normalizeIds(List<Long> ids)
    {
        if (ids == null) return new ArrayList<>();
        List<Long> normalized = ids.stream().filter(id -> id != null && id > 0)
                .distinct().sorted().collect(Collectors.toList());
        if (normalized.size() != ids.size())
        {
            throw new ServiceException("候选用户编号包含空值、重复值或非法值");
        }
        return normalized;
    }

    private static void validateExecuteRequest(LegacyCredentialRotationExecuteRequest request)
    {
        if (request == null || request.getBatchId() == null
                || !SAFE_BATCH.matcher(request.getBatchId()).matches()
                || request.getCandidateDigest() == null
                || !SAFE_DIGEST.matcher(request.getCandidateDigest()).matches())
        {
            throw new ServiceException("轮换预览凭证格式无效");
        }
    }

    private static void assertOperator(Long operatorUserId)
    {
        if (operatorUserId == null || operatorUserId <= 0)
        {
            throw new ServiceException("无法识别凭据轮换操作者");
        }
    }
}
