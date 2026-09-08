package com.erp.oa.api.domain;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统模块发布给OA的版本化人事签约业务事件。
 *
 * <p>事件头和员工快照均为固定契约；扩展属性只能承载场景附加信息，不能替代前后快照。</p>
 */
public class HrSignBusinessEvent
{
    private String eventId;
    private String scenario;
    private Long employeeId;
    private String sourceType;
    private String sourceBusinessId;
    private Long sourceEventVersion;
    private Date occurredTime;
    private Long operatorUserId;
    private HrEmployeeSigningSnapshot beforeSnapshot;
    private HrEmployeeSigningSnapshot afterSnapshot;
    private Map<String, Object> attributes = new LinkedHashMap<>();

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceBusinessId() { return sourceBusinessId; }
    public void setSourceBusinessId(String sourceBusinessId) { this.sourceBusinessId = sourceBusinessId; }
    public Long getSourceEventVersion() { return sourceEventVersion; }
    public void setSourceEventVersion(Long sourceEventVersion) { this.sourceEventVersion = sourceEventVersion; }
    public Date getOccurredTime() { return occurredTime; }
    public void setOccurredTime(Date occurredTime) { this.occurredTime = occurredTime; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public HrEmployeeSigningSnapshot getBeforeSnapshot() { return beforeSnapshot; }
    public void setBeforeSnapshot(HrEmployeeSigningSnapshot beforeSnapshot) { this.beforeSnapshot = beforeSnapshot; }
    public HrEmployeeSigningSnapshot getAfterSnapshot() { return afterSnapshot; }
    public void setAfterSnapshot(HrEmployeeSigningSnapshot afterSnapshot) { this.afterSnapshot = afterSnapshot; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes)
    {
        this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }
}
