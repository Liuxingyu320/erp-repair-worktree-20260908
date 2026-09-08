package com.erp.oa.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

public class OaSalaryAttendanceIssueVo
{
    private Long recordId;
    private Long userId;
    private String userName;
    private String nickName;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date workDate;

    private String reason;
    private String category = "ATTENDANCE";

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public Date getWorkDate() { return workDate; }
    public void setWorkDate(Date workDate) { this.workDate = workDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
