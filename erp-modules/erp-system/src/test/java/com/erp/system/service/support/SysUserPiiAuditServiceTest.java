package com.erp.system.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.SysUserPiiAccessAudit;
import com.erp.system.domain.dto.SysUserPiiAccessReason;
import com.erp.system.domain.vo.SysUserPiiExportVo;
import com.erp.system.mapper.SysUserPiiAccessAuditMapper;

class SysUserPiiAuditServiceTest
{
    @Test
    void failsClosedWhenAuditEvidenceCannotBeWritten()
    {
        SysUserPiiAccessAuditMapper mapper = mock(SysUserPiiAccessAuditMapper.class);
        when(mapper.insertPiiAccessAudit(any(SysUserPiiAccessAudit.class))).thenReturn(0);
        SysUserPiiAuditService service = new SysUserPiiAuditService(mapper);

        assertThatThrownBy(() -> service.recordRead(7L, 9L,
                SysUserPiiAccessReason.BUSINESS_PROCESSING, true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("安全审计写入失败");
    }

    @Test
    void exportDigestChangesWhenProtectedDatasetChanges()
    {
        AtomicReference<SysUserPiiAccessAudit> captured = new AtomicReference<>();
        SysUserPiiAccessAuditMapper mapper = mock(SysUserPiiAccessAuditMapper.class);
        when(mapper.insertPiiAccessAudit(any(SysUserPiiAccessAudit.class))).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return 1;
        });
        SysUserPiiAuditService service = new SysUserPiiAuditService(mapper);
        SysUserPiiExportVo row = new SysUserPiiExportVo();
        row.setUserId(9L);
        row.setPhonenumber("13800000000");

        service.recordExport(7L, SysUserPiiAccessReason.LEGAL_AUDIT, List.of(row));
        String firstDigest = captured.get().getDatasetDigest();
        row.setPhonenumber("13900000000");
        service.recordExport(7L, SysUserPiiAccessReason.LEGAL_AUDIT, List.of(row));

        assertThat(firstDigest).hasSize(64).isNotEqualTo(captured.get().getDatasetDigest());
        assertThat(captured.get().getChangedFields()).isNull();
    }
}
