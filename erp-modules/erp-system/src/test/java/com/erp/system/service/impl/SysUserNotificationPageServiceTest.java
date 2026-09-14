package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.*;
import com.erp.system.domain.dto.*;
import com.erp.system.mapper.*;

class SysUserNotificationPageServiceTest
{
    SysUserNotificationMapper mapper;
    InAppNotificationWriter writer;
    SysUserNotificationServiceImpl service;
    @BeforeEach void setup()
    {
        mapper=mock(SysUserNotificationMapper.class);
        writer=mock(InAppNotificationWriter.class);
        service=new SysUserNotificationServiceImpl(mapper,mock(SysUserDeviceTokenMapper.class),mock(SysUserPushDeliveryMapper.class),List.of(),writer);
    }
    @AfterEach void pagingDoesNotPublishNotifications() { verifyNoInteractions(writer); }
    @Test void firstPageCapturesOnlyThisUsersMaxAndReturnsGlobalUnreadAndTypes()
    {
        when(mapper.selectMaxIdByUserId(42L)).thenReturn(9007199254740993L);
        when(mapper.countPage(eq(42L),eq(9007199254740993L),any())).thenReturn(1L);
        SysUserNotification row=new SysUserNotification();row.setNotificationId(9007199254740993L);row.setTitle("own");
        when(mapper.selectPage(eq(42L),eq(9007199254740993L),any())).thenReturn(List.of(row));
        when(mapper.countUnreadByUserId(42L)).thenReturn(20L);when(mapper.selectRouteTypesByUserId(42L)).thenReturn(List.of("HR","INV"));
        var result=service.page(42L,new SysUserNotificationPageQuery());
        assertThat(result.snapshotMaxId()).isEqualTo("9007199254740993");assertThat(result.total()).isEqualTo(1);
        assertThat(result.unreadCount()).isEqualTo(20);assertThat(result.routeTypes()).containsExactly("HR","INV");
        assertThat(result.pageNum()).isEqualTo(1);assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.rows()).singleElement().satisfies(x -> assertThat(x.getNotificationId()).isEqualTo(9007199254740993L));
    }
    @Test void laterPageRetainsSuppliedSnapshotAcrossNewFilters()
    {
        var q=new SysUserNotificationPageQuery();q.setSnapshotMaxId("12");q.setKeyword("changed");q.setRouteType("HR");q.setPageNum(2);
        var result=service.page(42L,q);
        assertThat(result.snapshotMaxId()).isEqualTo("12");verify(mapper,never()).selectMaxIdByUserId(any());
        verify(mapper).selectPage(42L,12L,q);verify(mapper).countPage(42L,12L,q);verify(mapper).countUnreadByUserId(42L);
    }
    @Test void noMessagesUsesZeroSnapshot()
    { assertThat(service.page(42L,new SysUserNotificationPageQuery()).snapshotMaxId()).isEqualTo("0"); }
    @Test void literalKeywordEscapesWildcardsAndEscapeCharacter()
    {
        var q=new SysUserNotificationPageQuery();q.setKeyword("100%_\\!");q.validate();
        assertThat(q.getKeywordPattern()).isEqualTo("%100!%!_\\!!%");
    }
    @Test void dateBoundsAreShanghaiBusinessDaysRegardlessOfJvmZone()
    {
        TimeZone previous=TimeZone.getDefault();
        try
        {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            var q=new SysUserNotificationPageQuery();q.setStartDate("2026-09-12");q.setEndDate("2026-09-12");q.validate();
            assertThat(q.getStartTime()).isEqualTo(LocalDateTime.of(2026,9,12,0,0));
            assertThat(q.getEndTimeExclusive()).isEqualTo(LocalDateTime.of(2026,9,13,0,0));
        }
        finally { TimeZone.setDefault(previous); }
    }
    @Test void maximumPageOffsetDoesNotOverflowInteger()
    { var q=new SysUserNotificationPageQuery();q.setPageNum(Integer.MAX_VALUE);q.setPageSize(100);q.validate();assertThat(q.getOffset()).isEqualTo(214748364600L); }
    @Test void maximumLongAndZeroSnapshotsArePreserved()
    {
        for(String id:List.of("0",Long.toString(Long.MAX_VALUE)))
        { var q=new SysUserNotificationPageQuery();q.setSnapshotMaxId(id);assertThat(service.page(42L,q).snapshotMaxId()).isEqualTo(id); }
    }
    @ParameterizedTest @MethodSource("invalidQueries")
    void invalidQueryIsRejectedBeforeAnyMapper(Consumer<SysUserNotificationPageQuery> change)
    {
        var q=new SysUserNotificationPageQuery();change.accept(q);
        assertThatThrownBy(() -> service.page(42L,q)).isInstanceOf(ServiceException.class);verifyNoInteractions(mapper);
    }
    static Stream<Consumer<SysUserNotificationPageQuery>> invalidQueries()
    {
        return Stream.of(q -> q.setKeyword("x".repeat(101)),q -> q.setRouteType("x".repeat(65)),q -> q.setReadStatus("2"),q -> q.setReadStatus(" 0 "),
                q -> q.setPageNum(0),q -> q.setPageNum(null),q -> q.setPageSize(0),q -> q.setPageSize(101),q -> q.setPageSize(null),
                q -> q.setStartDate("2026-02-29"),q -> q.setEndDate("2026-09-31"),q -> q.setStartDate("2026-9-12"),q -> q.setEndDate("2026-09-12 00:00:00"),
                q -> q.setStartDate("0000-01-01"),q -> {q.setStartDate("2026-09-13");q.setEndDate("2026-09-12");},
                q -> q.setSnapshotMaxId("-1"),q -> q.setSnapshotMaxId("1.5"),q -> q.setSnapshotMaxId("9223372036854775808"),q -> q.setSnapshotMaxId(" 1"));
    }
    @ParameterizedTest @NullSource @ValueSource(longs={0,-1})
    void identityMustBePositiveForBothOperations(Long user)
    {
        assertThatThrownBy(() -> service.page(user,new SysUserNotificationPageQuery())).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.markAllRead(user,readAll("3"))).isInstanceOf(ServiceException.class);verifyNoInteractions(mapper);
    }
    @Test void readAllUsesOnlyOwnerAndSnapshotAndReturnsWholeAccountUnread()
    {
        when(mapper.markAllRead(42L,9L)).thenReturn(3);when(mapper.countUnreadByUserId(42L)).thenReturn(2L);
        var result=service.markAllRead(42L,readAll("9"));
        assertThat(result.changed()).isEqualTo(3);assertThat(result.snapshotMaxId()).isEqualTo("9");assertThat(result.unreadCount()).isEqualTo(2);
        verify(mapper).markAllRead(42L,9L);verify(mapper).countUnreadByUserId(42L);verifyNoMoreInteractions(mapper);
    }
    @Test void repeatedReadAllCanReturnZeroChanged()
    { when(mapper.countUnreadByUserId(42L)).thenReturn(5L);var result=service.markAllRead(42L,readAll("0"));assertThat(result.changed()).isZero();assertThat(result.unreadCount()).isEqualTo(5); }
    @ParameterizedTest @NullSource @EmptySource @ValueSource(strings={"-1","1.0","1e3","true","+1","9223372036854775808"," 1"})
    void invalidRequiredSnapshotCannotWrite(String value)
    { assertThatThrownBy(() -> service.markAllRead(42L,readAll(value))).isInstanceOf(ServiceException.class);verifyNoInteractions(mapper); }
    @Test void nullDtosCannotReadOrWrite()
    {
        assertThatThrownBy(() -> service.page(42L,null)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.markAllRead(42L,null)).isInstanceOf(ServiceException.class);verifyNoInteractions(mapper);
    }
    private SysUserNotificationReadAllRequest readAll(String id)
    {var r=new SysUserNotificationReadAllRequest();r.setSnapshotMaxId(id);return r;}
}
