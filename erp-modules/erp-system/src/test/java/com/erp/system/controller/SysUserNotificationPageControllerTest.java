package com.erp.system.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.handler.GlobalExceptionHandler;
import com.erp.system.domain.SysUserNotification;
import com.erp.system.domain.dto.*;
import com.erp.system.domain.vo.*;
import com.erp.system.service.ISysUserNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;

class SysUserNotificationPageControllerTest
{
    ISysUserNotificationService service;MockMvc mvc;
    @BeforeEach void setup()
    {
        service=mock(ISysUserNotificationService.class);var controller=new SysUserNotificationController();
        ReflectionTestUtils.setField(controller,"notificationService",service);SecurityContextHolder.setUserId("42");
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void clear(){SecurityContextHolder.remove();}
    @Test void pageUsesLoginIdentityAndSerializesOnlyNewEndpointIdsAsStrings() throws Exception
    {
        SysUserNotification source=new SysUserNotification();source.setNotificationId(9007199254740993L);
        when(service.page(eq(42L),any())).thenReturn(new SysUserNotificationPageResult(List.of(SysUserNotificationPageResult.Row.from(source)),1,3,"9007199254740993",List.of("HR"),1,20));
        mvc.perform(get("/user-notification/page").param("userId","999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].notificationId").value("9007199254740993"))
                .andExpect(jsonPath("$.data.snapshotMaxId").value("9007199254740993")).andExpect(jsonPath("$.data.unreadCount").value(3));
        verify(service).page(eq(42L),any());
        assertThat(new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(source)).get("notificationId").isIntegralNumber()).isTrue();
    }
    @Test void readAllIgnoresSpoofedUserAndFilterFields() throws Exception
    {
        when(service.markAllRead(eq(42L),any())).thenReturn(new SysUserNotificationReadAllResult(2,"12",1));
        mvc.perform(post("/user-notification/read-all").contentType(MediaType.APPLICATION_JSON)
                .content("{\"snapshotMaxId\":\"12\",\"userId\":999,\"keyword\":\"filtered\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(2)).andExpect(jsonPath("$.data.snapshotMaxId").value("12"));
        verify(service).markAllRead(eq(42L),argThat(x -> x.validate()==12L));
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"snapshotMaxId\":-1}","{\"snapshotMaxId\":1.5}","{\"snapshotMaxId\":true}","{\"snapshotMaxId\":\"9223372036854775808\"}","{\"snapshotMaxId\":null}"})
    void malformedReadAllBodyNeverReachesService(String body) throws Exception
    {
        mvc.perform(post("/user-notification/read-all").contentType(MediaType.APPLICATION_JSON).content(body));verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"0","101","2147483648","1.5"})
    void invalidPageSizeNeverReachesService(String size) throws Exception
    {mvc.perform(get("/user-notification/page").param("pageSize",size));verifyNoInteractions(service);}
    @Test void invalidLongQueryRejectedEvenWhenServiceIsMocked() throws Exception
    {mvc.perform(get("/user-notification/page").param("snapshotMaxId","9223372036854775808"));verifyNoInteractions(service);}
    @Test void bothNewEndpointsRequireLoginWithoutAutomaticRoleGrant() throws Exception
    {
        assertThat(SysUserNotificationController.class.getMethod("page",SysUserNotificationPageQuery.class).getAnnotation(RequiresLogin.class)).isNotNull();
        assertThat(SysUserNotificationController.class.getMethod("readAll",SysUserNotificationReadAllRequest.class).getAnnotation(RequiresLogin.class)).isNotNull();
    }
}
