package com.erp.oa.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.erp.common.core.web.domain.BaseEntity;

public class OaSalaryConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long configId;

    @NotNull(message = "店铺ID不能为空")
    private Long shopDeptId;

    @NotBlank(message = "上班时间不能为空")
    private String workStartTime;

    @NotBlank(message = "下班时间不能为空")
    private String workEndTime;

    @NotNull(message = "日标准工时不能为空")
    private BigDecimal workHoursPerDay;

    @NotNull(message = "迟到罚款不能为空")
    private BigDecimal latePenaltyPerMin;

    @NotNull(message = "早退罚款不能为空")
    private BigDecimal earlyPenaltyPerMin;

    @NotNull(message = "缺勤扣款不能为空")
    private BigDecimal absentPenaltyPerDay;

    @NotNull(message = "加班费不能为空")
    private BigDecimal overtimePayPerHour;

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }

    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }

    public String getWorkStartTime() { return workStartTime; }
    public void setWorkStartTime(String workStartTime) { this.workStartTime = workStartTime; }

    public String getWorkEndTime() { return workEndTime; }
    public void setWorkEndTime(String workEndTime) { this.workEndTime = workEndTime; }

    public BigDecimal getWorkHoursPerDay() { return workHoursPerDay; }
    public void setWorkHoursPerDay(BigDecimal workHoursPerDay) { this.workHoursPerDay = workHoursPerDay; }

    public BigDecimal getLatePenaltyPerMin() { return latePenaltyPerMin; }
    public void setLatePenaltyPerMin(BigDecimal latePenaltyPerMin) { this.latePenaltyPerMin = latePenaltyPerMin; }

    public BigDecimal getEarlyPenaltyPerMin() { return earlyPenaltyPerMin; }
    public void setEarlyPenaltyPerMin(BigDecimal earlyPenaltyPerMin) { this.earlyPenaltyPerMin = earlyPenaltyPerMin; }

    public BigDecimal getAbsentPenaltyPerDay() { return absentPenaltyPerDay; }
    public void setAbsentPenaltyPerDay(BigDecimal absentPenaltyPerDay) { this.absentPenaltyPerDay = absentPenaltyPerDay; }

    public BigDecimal getOvertimePayPerHour() { return overtimePayPerHour; }
    public void setOvertimePayPerHour(BigDecimal overtimePayPerHour) { this.overtimePayPerHour = overtimePayPerHour; }
}
