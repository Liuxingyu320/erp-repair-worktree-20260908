package com.erp.oa.domain;

import java.math.BigDecimal;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class OaSalaryRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long salaryId;

    private Long userId;

    @Excel(name = "用户账号")
    private String userName;

    @Excel(name = "员工姓名")
    private String nickName;

    private Long deptId;

    @Excel(name = "所属部门")
    private String deptName;

    private Long shopDeptId;

    @Excel(name = "核算店铺")
    private String shopDeptName;

    @Excel(name = "工资月份")
    private String salaryMonth;

    @Excel(name = "出勤天数")
    private Integer workDays;

    @Excel(name = "请假天数")
    private Integer leaveDays;

    @Excel(name = "缺勤天数")
    private Integer absentDays;

    @Excel(name = "应出勤(分钟)")
    private Integer scheduledMinutes;

    @Excel(name = "核定工作(分钟)")
    private Integer workedMinutes;

    @Excel(name = "带薪请假(分钟)")
    private Integer paidLeaveMinutes;

    @Excel(name = "无薪请假(分钟)")
    private Integer unpaidLeaveMinutes;

    @Excel(name = "缺勤(分钟)")
    private Integer absenceMinutes;

    private String attendanceSourceVersion;

    @Excel(name = "迟到(分钟)")
    private Integer lateTotalMinutes;

    @Excel(name = "早退(分钟)")
    private Integer earlyTotalMinutes;

    @Excel(name = "加班(小时)")
    private BigDecimal overtimeHours;

    @Excel(name = "合同综合工资")
    private BigDecimal baseSalary;

    @Excel(name = "考勤扣款")
    private BigDecimal attendanceDeduction;

    @Excel(name = "迟到扣款")
    private BigDecimal lateDeduction;

    @Excel(name = "早退扣款")
    private BigDecimal earlyDeduction;

    @Excel(name = "缺勤扣款")
    private BigDecimal absentDeduction;

    @Excel(name = "加班费")
    private BigDecimal overtimePay;

    @Excel(name = "其他奖金")
    private BigDecimal otherBonus;

    @Excel(name = "其他扣款")
    private BigDecimal otherDeduction;

    @Excel(name = "实发工资")
    private BigDecimal totalSalary;

    @Excel(name = "状态", readConverterExp = "draft=草稿,confirmed=已确认")
    private String status;

    public Long getSalaryId() { return salaryId; }
    public void setSalaryId(Long salaryId) { this.salaryId = salaryId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }

    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }

    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }

    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }

    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String shopDeptName) { this.shopDeptName = shopDeptName; }

    public String getSalaryMonth() { return salaryMonth; }
    public void setSalaryMonth(String salaryMonth) { this.salaryMonth = salaryMonth; }

    public Integer getWorkDays() { return workDays; }
    public void setWorkDays(Integer workDays) { this.workDays = workDays; }

    public Integer getLeaveDays() { return leaveDays; }
    public void setLeaveDays(Integer leaveDays) { this.leaveDays = leaveDays; }

    public Integer getAbsentDays() { return absentDays; }
    public void setAbsentDays(Integer absentDays) { this.absentDays = absentDays; }

    public Integer getScheduledMinutes() { return scheduledMinutes; }
    public void setScheduledMinutes(Integer scheduledMinutes) { this.scheduledMinutes = scheduledMinutes; }

    public Integer getWorkedMinutes() { return workedMinutes; }
    public void setWorkedMinutes(Integer workedMinutes) { this.workedMinutes = workedMinutes; }

    public Integer getPaidLeaveMinutes() { return paidLeaveMinutes; }
    public void setPaidLeaveMinutes(Integer paidLeaveMinutes) { this.paidLeaveMinutes = paidLeaveMinutes; }

    public Integer getUnpaidLeaveMinutes() { return unpaidLeaveMinutes; }
    public void setUnpaidLeaveMinutes(Integer unpaidLeaveMinutes) { this.unpaidLeaveMinutes = unpaidLeaveMinutes; }

    public Integer getAbsenceMinutes() { return absenceMinutes; }
    public void setAbsenceMinutes(Integer absenceMinutes) { this.absenceMinutes = absenceMinutes; }

    public String getAttendanceSourceVersion() { return attendanceSourceVersion; }
    public void setAttendanceSourceVersion(String attendanceSourceVersion) { this.attendanceSourceVersion = attendanceSourceVersion; }

    public Integer getLateTotalMinutes() { return lateTotalMinutes; }
    public void setLateTotalMinutes(Integer lateTotalMinutes) { this.lateTotalMinutes = lateTotalMinutes; }

    public Integer getEarlyTotalMinutes() { return earlyTotalMinutes; }
    public void setEarlyTotalMinutes(Integer earlyTotalMinutes) { this.earlyTotalMinutes = earlyTotalMinutes; }

    public BigDecimal getOvertimeHours() { return overtimeHours; }
    public void setOvertimeHours(BigDecimal overtimeHours) { this.overtimeHours = overtimeHours; }

    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }

    public BigDecimal getAttendanceDeduction() { return attendanceDeduction; }
    public void setAttendanceDeduction(BigDecimal attendanceDeduction) { this.attendanceDeduction = attendanceDeduction; }

    public BigDecimal getLateDeduction() { return lateDeduction; }
    public void setLateDeduction(BigDecimal lateDeduction) { this.lateDeduction = lateDeduction; }

    public BigDecimal getEarlyDeduction() { return earlyDeduction; }
    public void setEarlyDeduction(BigDecimal earlyDeduction) { this.earlyDeduction = earlyDeduction; }

    public BigDecimal getAbsentDeduction() { return absentDeduction; }
    public void setAbsentDeduction(BigDecimal absentDeduction) { this.absentDeduction = absentDeduction; }

    public BigDecimal getOvertimePay() { return overtimePay; }
    public void setOvertimePay(BigDecimal overtimePay) { this.overtimePay = overtimePay; }

    public BigDecimal getOtherBonus() { return otherBonus; }
    public void setOtherBonus(BigDecimal otherBonus) { this.otherBonus = otherBonus; }

    public BigDecimal getOtherDeduction() { return otherDeduction; }
    public void setOtherDeduction(BigDecimal otherDeduction) { this.otherDeduction = otherDeduction; }

    public BigDecimal getTotalSalary() { return totalSalary; }
    public void setTotalSalary(BigDecimal totalSalary) { this.totalSalary = totalSalary; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
