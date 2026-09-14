package com.erp.system.domain.vo;

import com.erp.common.core.web.domain.BaseEntity;

/** 健康证审批发起运维查询；数据范围由服务层注入。 */
public class HrHealthCertificateApprovalStartOutboxQuery extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long certificateId;
    public Long getCertificateId() { return certificateId; }
    public void setCertificateId(Long value) { certificateId = value; }
    private Long outboxId;
    private String status;

    public Long getOutboxId() { return outboxId; }
    public void setOutboxId(Long value) { outboxId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
}
