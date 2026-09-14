package com.erp.inventory.service.impl;

import java.util.LinkedHashSet;
import java.util.Set;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.RemoteFileService;
import com.erp.system.api.domain.DriveBusinessFile;
import org.springframework.stereotype.Service;

/** Called only after the transfer's actor and warehouse scope have been checked. */
@Service
public class InvTransferEvidenceService
{
    private final RemoteFileService files;

    public InvTransferEvidenceService(RemoteFileService files) { this.files = files; }

    public static Set<Long> parse(String references)
    {
        Set<Long> ids = new LinkedHashSet<>();
        if (references == null || references.isBlank()) return ids;
        if (references.length() > 2000) throw invalid();
        String[] parts = references.split(",", -1);
        if (parts.length > 10) throw new ServiceException("每项最多选择10个凭证附件");
        for (String part : parts)
        {
            if (!part.matches("drive:[1-9][0-9]{0,18}")) throw invalid();
            try
            {
                Long id = Long.valueOf(part.substring(6));
                if (!ids.add(id)) throw invalid();
            }
            catch (NumberFormatException failure) { throw invalid(); }
        }
        return ids;
    }

    public void validate(String references)
    {
        for (Long id : parse(references))
        {
            if (files == null) throw new ServiceException("凭证校验暂不可用，请稍后重试");
            R<DriveBusinessFile> result = files.validateDriveBusinessFile(id,
                    "TRANSFER_EVIDENCE", SecurityConstants.INNER);
            if (result == null || R.isError(result) || result.getData() == null
                    || !id.equals(result.getData().getNodeId()))
                throw new ServiceException("凭证附件不可用或当前账号无权绑定，请重新选择");
        }
    }

    /** Historic free text remains readable, but cannot serve as a new required credential. */
    public void validateResolution(String references, String original,
            String previous, boolean required)
    {
        if (references == null || references.isBlank())
        {
            if (required) throw invalid();
            return;
        }
        boolean historic = references.equals(original) || references.equals(previous);
        if (historic && !required && !references.startsWith("drive:")) return;
        validate(references);
    }

    private static ServiceException invalid()
    { return new ServiceException("请选择或上传受控凭证附件；历史文字引用不能作为新凭证"); }
}
