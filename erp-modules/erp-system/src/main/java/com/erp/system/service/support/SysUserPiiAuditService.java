package com.erp.system.service.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.SysUserPiiAccessAudit;
import com.erp.system.domain.dto.SysUserPiiAccessReason;
import com.erp.system.domain.vo.SysUserPiiExportVo;
import com.erp.system.mapper.SysUserPiiAccessAuditMapper;

/** Writes metadata-only audit evidence for protected personal-data operations. */
@Service
public class SysUserPiiAuditService
{
    private final SysUserPiiAccessAuditMapper mapper;

    public SysUserPiiAuditService(SysUserPiiAccessAuditMapper mapper)
    {
        this.mapper = mapper;
    }

    public void recordRead(Long viewerUserId, Long targetUserId,
            SysUserPiiAccessReason reason, boolean found)
    {
        insert(viewerUserId, targetUserId, reason, "READ", found ? "SUCCESS" : "NOT_FOUND",
                null, found ? 1 : 0, null);
    }

    public void recordUpdate(Long viewerUserId, Long targetUserId,
            SysUserPiiAccessReason reason, Set<String> changedFields)
    {
        String fields = changedFields == null ? null : changedFields.stream().sorted()
                .collect(Collectors.joining(","));
        insert(viewerUserId, targetUserId, reason, "UPDATE", "SUCCESS", fields,
                changedFields == null || changedFields.isEmpty() ? 0 : 1, null);
    }

    public void recordExport(Long viewerUserId, SysUserPiiAccessReason reason,
            Collection<SysUserPiiExportVo> rows)
    {
        int count = rows == null ? 0 : rows.size();
        String canonicalRows = rows == null ? "" : rows.stream()
                .sorted(Comparator.comparing(SysUserPiiExportVo::getUserId,
                        Comparator.nullsFirst(Long::compareTo)))
                .map(SysUserPiiExportVo::auditDigestMaterial)
                .collect(Collectors.joining("\u001e"));
        insert(viewerUserId, null, reason, "EXPORT", "SUCCESS", null, count,
                sha256(count + ":" + canonicalRows));
    }

    private void insert(Long viewerUserId, Long targetUserId, SysUserPiiAccessReason reason,
            String action, String result, String changedFields, int rowCount, String digest)
    {
        SysUserPiiAccessAudit audit = new SysUserPiiAccessAudit();
        audit.setViewerUserId(viewerUserId);
        audit.setTargetUserId(targetUserId);
        audit.setReasonCode(reason.name());
        audit.setActionCode(action);
        audit.setResultCode(result);
        audit.setChangedFields(changedFields);
        audit.setRowCount(rowCount);
        audit.setDatasetDigest(digest);
        if (mapper.insertPiiAccessAudit(audit) != 1)
        {
            throw new ServiceException("个人信息安全审计写入失败");
        }
    }

    static String sha256(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
