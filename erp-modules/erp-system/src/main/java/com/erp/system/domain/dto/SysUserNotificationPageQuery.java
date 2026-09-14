package com.erp.system.domain.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import com.erp.common.core.exception.ServiceException;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** User identity is deliberately absent. Dates are Shanghai business-day boundaries. */
public class SysUserNotificationPageQuery
{
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private String keyword;
    private String routeType;
    private String readStatus;
    private String startDate;
    private String endDate;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private String snapshotMaxId;
    private String keywordPattern;
    private LocalDateTime startTime;
    private LocalDateTime endTimeExclusive;
    private Long parsedSnapshotMaxId;

    public void validate()
    {
        keyword = boundedText(keyword, 100, "关键字");
        routeType = boundedText(routeType, 64, "消息类型");
        if (readStatus == null) readStatus = "";
        if (!readStatus.equals("") && !readStatus.equals("0") && !readStatus.equals("1"))
            throw new ServiceException("阅读状态只能为空、0或1");
        if (pageNum == null || pageNum < 1) throw new ServiceException("页码必须为正整数");
        if (pageSize == null || pageSize < 1 || pageSize > 100) throw new ServiceException("每页数量必须为1至100");
        parsedSnapshotMaxId = parseSnapshot(snapshotMaxId, false);
        LocalDate start = date(startDate); LocalDate end = date(endDate);
        if (start != null && end != null && start.isAfter(end)) throw new ServiceException("开始日期不能晚于结束日期");
        // DATETIME stores business wall-clock time; never convert through the JVM default timezone.
        startTime = start == null ? null : start.atStartOfDay(BUSINESS_ZONE).toLocalDateTime();
        // MySQL DATETIME cannot exceed year 9999, so this upper bound already includes its entire last day.
        endTimeExclusive = end == null || end.equals(LocalDate.of(9999,12,31)) ? null
                : end.plusDays(1).atStartOfDay(BUSINESS_ZONE).toLocalDateTime();
        keywordPattern = keyword.isEmpty() ? null : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    static String boundedText(String text, int max, String label)
    {
        if (text != null && text.length() > max) throw new ServiceException(label + "不能超过" + max + "个字符");
        return text == null ? "" : text.trim();
    }
    public static Long parseSnapshot(String value, boolean required)
    {
        if (value == null || value.isEmpty())
        {
            if (required) throw new ServiceException("消息快照上限不能为空");
            return null;
        }
        if (!value.matches("0|[1-9][0-9]{0,18}")) throw new ServiceException("消息快照上限必须为非负Long整数");
        try { return Long.parseLong(value); }
        catch (NumberFormatException failure) { throw new ServiceException("消息快照上限超出Long范围"); }
    }
    private static LocalDate date(String value)
    {
        if (value == null || value.isEmpty()) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new ServiceException("日期必须为YYYY-MM-DD");
        try
        {
            LocalDate result = LocalDate.parse(value, DATE);
            if (result.getYear() < 1) throw new ServiceException("日期年份无效");
            return result;
        }
        catch (DateTimeParseException failure) { throw new ServiceException("日期无效"); }
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String value) { keyword = value; }
    public String getRouteType() { return routeType; }
    public void setRouteType(String value) { routeType = value; }
    public String getReadStatus() { return readStatus; }
    public void setReadStatus(String value) { readStatus = value; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String value) { startDate = value; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String value) { endDate = value; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer value) { pageNum = value; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer value) { pageSize = value; }
    public String getSnapshotMaxId() { return snapshotMaxId; }
    public void setSnapshotMaxId(String value) { snapshotMaxId = value; }
    @JsonIgnore public String getKeywordPattern() { return keywordPattern; }
    @JsonIgnore public LocalDateTime getStartTime() { return startTime; }
    @JsonIgnore public LocalDateTime getEndTimeExclusive() { return endTimeExclusive; }
    @JsonIgnore public Long getParsedSnapshotMaxId() { return parsedSnapshotMaxId; }
    @JsonIgnore public long getOffset() { return (pageNum - 1L) * pageSize; }
}
