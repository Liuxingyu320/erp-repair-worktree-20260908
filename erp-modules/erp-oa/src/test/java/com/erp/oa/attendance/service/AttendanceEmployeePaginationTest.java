package com.erp.oa.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.config.AttendanceRuntimePolicy;
import com.erp.oa.attendance.config.AttendanceV2Properties;
import com.erp.oa.attendance.domain.AttendanceModels.EmployeeOption;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.domain.AttendanceModels.Shift;
import com.erp.oa.attendance.domain.AttendanceModels.ShiftSegment;
import com.erp.oa.attendance.domain.AttendanceModels.Site;
import com.erp.oa.attendance.dto.AttendanceRequests.SchedulePublish;
import com.erp.oa.attendance.mapper.AttendanceV2Mapper;
import com.erp.oa.attendance.support.*;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceEmployeePaginationTest
{
    private AttendanceV2Mapper mapper;
    private AttendanceV2Service service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(AttendanceV2Mapper.class);
        ShopScopeService scope = mock(ShopScopeService.class);
        when(scope.resolveRequiredShopDept(101L)).thenReturn(101L);
        service = new AttendanceV2Service(mapper, new AttendanceRuleEngine(),
                mock(AttendanceLocationAuditPolicy.class),
                new AttendanceCoordinateTransformer(), new AttendanceGeoFence(),
                mock(AttendanceAddressResolver.class), mock(AttendanceChallengePolicy.class),
                new AttendanceDaySettlementCalculator(new AttendanceRuleEngine()),
                mock(AttendanceEvidenceStorageService.class), new AttendanceV2Properties(),
                mock(AttendanceRuntimePolicy.class), scope, mock(BusinessFeatureGate.class),
                Clock.systemUTC());
    }

    @Test
    void secondPageFindsEmployeesBeyondOneHundredAndReturnsTheExactTotal()
    {
        EmployeeOption employee = new EmployeeOption();
        employee.userId = 1001L;
        when(mapper.countActiveEmployeeOptions(101L, "员工")).thenReturn(143L);
        when(mapper.selectActiveEmployeeOptionsPage(101L, "员工", 100L, 100))
                .thenReturn(List.of(employee));
        Map<String, Object> result = service.employeeOptionsPage(101L, " 员工 ", 2, 100, 101L);
        assertThat(result.get("rows")).isEqualTo(List.of(employee));
        assertThat(result.get("total")).isEqualTo(143L);
        assertThat(result.get("pageNum")).isEqualTo(2);
        verify(mapper).selectActiveEmployeeOptionsPage(101L, "员工", 100L, 100);
    }

    @Test
    void legacyEmployeeOptionsKeepsItsExistingMapperAndReturnType()
    {
        EmployeeOption employee = new EmployeeOption();
        when(mapper.selectActiveEmployeeOptions(101L, null)).thenReturn(List.of(employee));
        assertThat(service.employeeOptions(101L, " ", 101L)).containsExactly(employee);
        verify(mapper, never()).countActiveEmployeeOptions(any(), any());
    }

    @Test
    void requestedShopCannotEscapeTheSelectedShop()
    {
        assertThatThrownBy(() -> service.employeeOptionsPage(202L, null, 1, 100, 101L))
                .hasMessageContaining("SHOP");
        verifyNoInteractions(mapper);
    }

    @Test
    void invalidPageSizeAndPageNumberNeverReachTheMapper()
    {
        assertThatThrownBy(() -> service.employeeOptionsPage(101L, null, 0, 100, 101L))
                .hasMessageContaining("EMPLOYEE_OPTION_PAGE_INVALID");
        assertThatThrownBy(() -> service.employeeOptionsPage(101L, null, 1, 101, 101L))
                .hasMessageContaining("EMPLOYEE_OPTION_PAGE_INVALID");
        assertThatThrownBy(() -> service.employeeOptionsPage(101L, null, 1, 0, 101L))
                .hasMessageContaining("EMPLOYEE_OPTION_PAGE_INVALID");
        verifyNoInteractions(mapper);
    }

    @Test
    void longKeywordAndHugePageRemainBounded()
    {
        assertThatThrownBy(() -> service.employeeOptionsPage(101L, "x".repeat(51), 1, 100, 101L))
                .hasMessageContaining("EMPLOYEE_OPTION_KEYWORD_TOO_LONG");
        when(mapper.countActiveEmployeeOptions(101L, null)).thenReturn(143L);
        Map<String, Object> result = service.employeeOptionsPage(101L, null, Integer.MAX_VALUE, 100, 101L);
        assertThat(result.get("rows")).isEqualTo(List.of());
        assertThat(result.get("total")).isEqualTo(143L);
        verify(mapper, never()).selectActiveEmployeeOptionsPage(any(), any(), anyLong(), anyInt());
    }

    @Test
    void omittedPageArgumentsUsePageOneAndOneHundredRows()
    {
        when(mapper.countActiveEmployeeOptions(101L, null)).thenReturn(143L);
        when(mapper.selectActiveEmployeeOptionsPage(101L, null, 0L, 100)).thenReturn(List.of());
        Map<String, Object> result = service.employeeOptionsPage(101L, null, null, null, 101L);
        assertThat(result.get("pageNum")).isEqualTo(1);
        assertThat(result.get("pageSize")).isEqualTo(100);
        verify(mapper).selectActiveEmployeeOptionsPage(101L, null, 0L, 100);
    }

    @Test
    void realMapperBindsPagingAndCountToIdenticalEmployeeScope() throws Exception
    {
        String xml = "mapper/oa/AttendanceV2Mapper.xml";
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(xml))
        {
            new XMLMapperBuilder(input, configuration, xml, configuration.getSqlFragments()).parse();
        }
        var params = Map.of("shopId", 101L, "keyword", "员工", "offset", 100L, "pageSize", 100);
        var page = configuration.getMappedStatement(AttendanceV2Mapper.class.getName()
                + ".selectActiveEmployeeOptionsPage").getBoundSql(params);
        var count = configuration.getMappedStatement(AttendanceV2Mapper.class.getName()
                + ".countActiveEmployeeOptions").getBoundSql(params);
        String pageSql = page.getSql().replaceAll("\\s+", " ").trim();
        String countSql = count.getSql().replaceAll("\\s+", " ").trim();
        assertThat(pageSql).endsWith("order by u.nick_name,u.user_id limit ? offset ?");
        assertThat(page.getParameterMappings()).extracting(value -> value.getProperty())
                .containsExactly("shopId", "keyword", "keyword", "pageSize", "offset");
        assertThat(count.getParameterMappings()).extracting(value -> value.getProperty())
                .containsExactly("shopId", "keyword", "keyword");
        assertThat(pageSql.substring(pageSql.indexOf("from sys_user"), pageSql.indexOf(" order by")))
                .isEqualTo(countSql.substring(countSql.indexOf("from sys_user")));
        assertThat(pageSql).contains("u.status='0'", "u.del_flag='0'", "p.employee_status in", "target_dept.dept_type='STORE'", "sys_user_shop", "find_in_set");
        assertThat(countSql).startsWith("select count(distinct u.user_id)");
    }

    private SchedulePublish publication(List<Long> ids, Map<Long, Long> versions)
    {
        SchedulePublish request = new SchedulePublish();
        request.shopId = 101L;
        request.scheduleIds = ids;
        request.scheduleVersions = versions;
        return request;
    }

    private Schedule draft(Long id, Long version)
    {
        Schedule row = new Schedule();
        row.scheduleId = id; row.shopId = 101L; row.userId = 7L;
        row.shiftId = 5L; row.siteId = 51L; row.rowVersion = version;
        row.status = "DRAFT";
        return row;
    }

    @Test
    void duplicatePublishIdsAreRejectedBeforeAnyRowLock()
    {
        assertThatThrownBy(() -> service.publishSchedules(publication(List.of(31L, 31L), Map.of(31L, 4L)), 101L))
                .hasMessageContaining("SCHEDULE_PUBLISH_SNAPSHOT_INVALID");
        verifyNoInteractions(mapper);
    }

    @Test
    void missingAndExtraSnapshotEntriesAreRejectedBeforeAnyRowLock()
    {
        assertThatThrownBy(() -> service.publishSchedules(publication(List.of(31L, 32L), Map.of(31L, 4L)), 101L))
                .hasMessageContaining("SCHEDULE_PUBLISH_SNAPSHOT_INVALID");
        assertThatThrownBy(() -> service.publishSchedules(publication(List.of(31L), Map.of(31L, 4L, 32L, 5L)), 101L))
                .hasMessageContaining("SCHEDULE_PUBLISH_SNAPSHOT_INVALID");
        verifyNoInteractions(mapper);
    }

    @Test
    void nullAndNegativeSnapshotVersionsAreRejectedBeforeAnyWrite()
    {
        Map<Long, Long> versions = new LinkedHashMap<>();
        versions.put(31L, null);
        assertThatThrownBy(() -> service.publishSchedules(publication(List.of(31L), versions), 101L))
                .hasMessageContaining("SCHEDULE_PUBLISH_SNAPSHOT_INVALID");
        assertThatThrownBy(() -> service.publishSchedules(publication(List.of(31L), Map.of(31L, -1L)), 101L))
                .hasMessageContaining("SCHEDULE_PUBLISH_SNAPSHOT_INVALID");
        verifyNoInteractions(mapper);
    }

    @Test
    void laterChangedDraftPreventsAllWritesInThatBatch()
    {
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(draft(31L, 4L));
        when(mapper.selectScheduleByIdForUpdate(32L)).thenReturn(draft(32L, 8L));
        assertThatThrownBy(() -> service.publishSchedules(publication(List.of(31L, 32L), Map.of(31L, 4L, 32L, 7L)), 101L))
                .hasMessageContaining("SCHEDULE_VERSION_CONFLICT");
        verify(mapper).selectScheduleByIdForUpdate(31L);
        verify(mapper).selectScheduleByIdForUpdate(32L);
        verify(mapper, never()).publishSchedule(any(), any(), any(), any());
        verify(mapper, never()).countActiveEmployeeInShop(any(), any());
    }

    @Test
    void matchingVersionsDoNotBypassShopScope()
    {
        Schedule foreign = draft(31L, 4L); foreign.shopId = 202L;
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(foreign);
        assertThatThrownBy(() -> service.publishSchedules(publication(List.of(31L), Map.of(31L, 4L)), 101L))
                .hasMessageContaining("SCHEDULE_NOT_FOUND_IN_SHOP");
        verify(mapper, never()).publishSchedule(any(), any(), any(), any());
    }

    @Test
    void allSnapshotRowsAreLockedBeforeDownstreamPublishChecks()
    {
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(draft(31L, 4L));
        when(mapper.selectScheduleByIdForUpdate(32L)).thenReturn(draft(32L, 7L));
        assertThatThrownBy(() -> service.publishSchedules(publication(List.of(31L, 32L), Map.of(31L, 4L, 32L, 7L)), 101L))
                .hasMessageContaining("EMPLOYEE_NOT_ACTIVE_IN_SHOP");
        var order = inOrder(mapper);
        order.verify(mapper).selectScheduleByIdForUpdate(31L);
        order.verify(mapper).selectScheduleByIdForUpdate(32L);
        order.verify(mapper).countActiveEmployeeInShop(7L, 101L);
        verify(mapper, never()).publishSchedule(any(), any(), any(), any());
    }

    @Test
    void matchingSnapshotPublishesAndKeepsTheExistingTransactionalBoundary() throws Exception
    {
        Schedule original = draft(31L, 4L);
        Schedule published = draft(31L, 5L); published.status = "PUBLISHED";
        published.businessDate = LocalDate.of(2026, 9, 7); published.standardMinutesSnapshot = 480;
        Shift shift = new Shift(); shift.shiftId = 5L; shift.shiftCode = "SYNTHETIC"; shift.shiftName = "合成班次";
        shift.startTime = LocalTime.of(8, 0); shift.endTime = LocalTime.of(16, 0); shift.standardMinutes = 480;
        ShiftSegment segment = new ShiftSegment(); segment.segmentType = "WORK"; segment.startMinuteOffset = 480; segment.endMinuteOffset = 960; segment.paid = true;
        Site site = new Site(); site.siteId = 51L; site.shopId = 101L; site.status = "ENABLED";
        site.siteName = "合成地点"; site.address = "合成测试地址"; site.coordinateSystem = "GCJ02";
        site.latitude = new java.math.BigDecimal("31.2304"); site.longitude = new java.math.BigDecimal("121.4737");
        site.radiusMeters = 200; site.maxAccuracyMeters = 100;
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(original);
        when(mapper.countActiveEmployeeInShop(7L, 101L)).thenReturn(1);
        when(mapper.selectShiftByIdForUpdate(5L)).thenReturn(shift);
        when(mapper.selectShiftSegments(5L)).thenReturn(List.of(segment));
        when(mapper.selectSiteByIdForUpdate(51L)).thenReturn(site);
        when(mapper.publishSchedule(eq(31L), eq(101L), any(), any())).thenReturn(1);
        when(mapper.insertScheduleSegmentSnapshotsFromShift(31L, 5L)).thenReturn(1);
        when(mapper.selectScheduleById(31L)).thenReturn(published);
        when(mapper.selectScheduleSegmentSnapshots(31L, false)).thenReturn(List.of());
        assertThat(service.publishSchedules(publication(List.of(31L), Map.of(31L, 4L)), 101L)).containsExactly(published);
        verify(mapper, times(1)).selectScheduleByIdForUpdate(31L);
        assertThat(AttendanceV2Service.class.getMethod("publishSchedules", SchedulePublish.class, Long.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class)).isNotNull();
    }
}
