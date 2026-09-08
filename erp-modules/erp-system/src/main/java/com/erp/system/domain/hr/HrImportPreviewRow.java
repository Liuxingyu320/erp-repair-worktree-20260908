package com.erp.system.domain.hr;

import java.util.ArrayList;
import java.util.List;
import com.erp.system.api.domain.SysUserProfile;

/**
 * 人事导入预览行。
 */
public class HrImportPreviewRow
{
    private Integer rowNumber;
    private String action;
    private String status;
    private String message;
    private SysUserProfile profile;
    private Long matchedProfileId;
    private Long matchedUserId;
    private List<String> missingFields = new ArrayList<>();

    public Integer getRowNumber()
    {
        return rowNumber;
    }

    public void setRowNumber(Integer rowNumber)
    {
        this.rowNumber = rowNumber;
    }

    public String getAction()
    {
        return action;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public SysUserProfile getProfile()
    {
        return profile;
    }

    public void setProfile(SysUserProfile profile)
    {
        this.profile = profile;
    }

    public Long getMatchedProfileId()
    {
        return matchedProfileId;
    }

    public void setMatchedProfileId(Long matchedProfileId)
    {
        this.matchedProfileId = matchedProfileId;
    }

    public Long getMatchedUserId()
    {
        return matchedUserId;
    }

    public void setMatchedUserId(Long matchedUserId)
    {
        this.matchedUserId = matchedUserId;
    }

    public List<String> getMissingFields()
    {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields)
    {
        this.missingFields = missingFields;
    }
}
