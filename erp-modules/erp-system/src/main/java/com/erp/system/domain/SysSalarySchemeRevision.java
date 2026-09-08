package com.erp.system.domain;

import com.erp.common.core.web.domain.BaseEntity;

/** 薪资方案只追加修订记录 sys_salary_scheme_revision。 */
public class SysSalarySchemeRevision extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long revisionId;
    private Long schemeId;
    private Integer version;
    private String changeType;
    private String changeReason;
    private String snapshotJson;

    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long revisionId) { this.revisionId = revisionId; }
    public Long getSchemeId() { return schemeId; }
    public void setSchemeId(Long schemeId) { this.schemeId = schemeId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
}
