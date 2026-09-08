package com.erp.system.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/** Audit metadata only; never contains the revealed/exported value. */
public class HrSensitiveAccessLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long accessLogId;
    private Long operatorUserId;
    private String operatorName;
    private Long employeeUserId;
    private String fieldKey;
    private String exportScope;
    private String actionType;
    private String requestIp;
    private Date eventTime;
    private String result;
    private String resultMessage;
    public Long getAccessLogId(){return accessLogId;} public void setAccessLogId(Long v){accessLogId=v;}
    public Long getOperatorUserId(){return operatorUserId;} public void setOperatorUserId(Long v){operatorUserId=v;}
    public String getOperatorName(){return operatorName;} public void setOperatorName(String v){operatorName=v;}
    public Long getEmployeeUserId(){return employeeUserId;} public void setEmployeeUserId(Long v){employeeUserId=v;}
    public String getFieldKey(){return fieldKey;} public void setFieldKey(String v){fieldKey=v;}
    public String getExportScope(){return exportScope;} public void setExportScope(String v){exportScope=v;}
    public String getActionType(){return actionType;} public void setActionType(String v){actionType=v;}
    public String getRequestIp(){return requestIp;} public void setRequestIp(String v){requestIp=v;}
    public Date getEventTime(){return eventTime;} public void setEventTime(Date v){eventTime=v;}
    public String getResult(){return result;} public void setResult(String v){result=v;}
    public String getResultMessage(){return resultMessage;} public void setResultMessage(String v){resultMessage=v;}
    @Override public String toString()
    {
        return "HrSensitiveAccessLog{operatorUserId="+operatorUserId+", employeeUserId="+employeeUserId+
                ", fieldKey='"+fieldKey+"', actionType='"+actionType+"', requestIp='"+requestIp+
                "', eventTime="+eventTime+", result='"+result+"'}";
    }
}
