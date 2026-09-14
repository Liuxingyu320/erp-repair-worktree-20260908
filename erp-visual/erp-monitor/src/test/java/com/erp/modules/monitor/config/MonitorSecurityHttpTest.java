package com.erp.modules.monitor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import de.codecentric.boot.admin.server.config.AdminServerProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class MonitorSecurityHttpTest
{
    private static final String ADMIN_PASSWORD="test-admin-password-1234";
    private static final String MACHINE_PASSWORD="test-registrar-password-1234";

    @ParameterizedTest
    @ValueSource(strings={"","/monitor"})
    void actualFilterChainsSeparateRegistrationManagementAndBrowserCsrf(String prefix) throws Exception
    {
        try (var context=context(prefix,true,ADMIN_PASSWORD))
        {
            MockMvc mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
            String admin=basic("monitor-admin",ADMIN_PASSWORD), machine=basic("monitor-register",MACHINE_PASSWORD);
            mvc.perform(get(prefix+"/actuator/health")).andExpect(status().isOk());
            for (String path:new String[]{"/instances","/instances/abc/actuator/env","/actuator/env","/actuator/health/db"})
            {
                mvc.perform(get(prefix+path).header("Accept","application/json")).andExpect(status().isUnauthorized());
                mvc.perform(get(prefix+path).header("Authorization",machine)).andExpect(status().isForbidden());
                mvc.perform(get(prefix+path).header("Authorization",admin)).andExpect(status().isOk());
            }
            mvc.perform(post(prefix+"/instances").header("Authorization",machine)).andExpect(status().isOk());
            mvc.perform(delete(prefix+"/instances/abc-123").header("Authorization",machine)).andExpect(status().isOk());
            mvc.perform(post(prefix+"/instances").header("Authorization",basic("monitor-register","wrong"))).andExpect(status().isUnauthorized());
            mvc.perform(post(prefix+"/instances")).andExpect(status().isForbidden());
            mvc.perform(post(prefix+"/instances/abc/actuator/shutdown").header("Authorization",machine)).andExpect(status().isForbidden());
            mvc.perform(delete(prefix+"/instances/abc/actuator/env").header("Authorization",machine)).andExpect(status().isForbidden());
            // The administrator's browser session is NOT part of the machine CSRF exception.
            var initial=mvc.perform(get(prefix+"/login")).andExpect(status().isOk()).andReturn();
            Cookie csrf=initial.getResponse().getCookie("XSRF-TOKEN");assertThat(csrf).isNotNull();
            var login=mvc.perform(post(prefix+"/login").cookie(csrf).param("_csrf",csrf.getValue())
                    .param("username","monitor-admin").param("password",ADMIN_PASSWORD)).andExpect(status().is3xxRedirection()).andReturn();
            MockHttpSession session=(MockHttpSession)login.getRequest().getSession(false);assertThat(session).isNotNull();
            mvc.perform(post(prefix+"/instances").session(session)).andExpect(status().isForbidden());
            var loggedIn=mvc.perform(get(prefix+"/instances").session(session)).andExpect(status().isOk()).andReturn();
            Cookie refreshed=loggedIn.getResponse().getCookie("XSRF-TOKEN");assertThat(refreshed).isNotNull();
            mvc.perform(post(prefix+"/instances").session(session).cookie(refreshed).header("X-XSRF-TOKEN",refreshed.getValue())).andExpect(status().isOk());
            mvc.perform(post(prefix+"/settings").header("Authorization",admin)).andExpect(status().isForbidden());
        }
    }

    @Test
    void disabledRegistrationCannotUseAFormerMachineIdentity() throws Exception
    {
        try(var context=context("",false,ADMIN_PASSWORD))
        {
            MockMvc mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
            mvc.perform(post("/instances").header("Authorization",basic("monitor-register",MACHINE_PASSWORD))).andExpect(status().isForbidden());
            mvc.perform(get("/instances").header("Authorization",basic("monitor-register",MACHINE_PASSWORD))).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void missingExternalAdminCredentialFailsClosedWithoutDefaultUser()
    {
        assertThatThrownBy(()->{try(var ignored=context("",false,"")) { }}).hasStackTraceContaining("Configure external monitor admin");
    }

    private static AnnotationConfigWebApplicationContext context(String prefix,boolean registration,String adminPassword)
    {
        var context=new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of(
                "test.context",prefix,"erp.monitor.accounts.admin-username","monitor-admin",
                "erp.monitor.accounts.admin-password",adminPassword,
                "erp.monitor.accounts.registration-enabled",registration,
                "erp.monitor.accounts.registrar-username","monitor-register",
                "erp.monitor.accounts.registrar-password",MACHINE_PASSWORD)));
        context.register(Endpoints.class,WebSecurityConfigurer.class);
        try {context.refresh();return context;} catch(RuntimeException ex) {context.close();throw ex;}
    }
    private static String basic(String username,String password)
    {
        return "Basic "+Base64.getEncoder().encodeToString((username+":"+password).getBytes(StandardCharsets.UTF_8));
    }
    @Configuration
    @EnableWebMvc
    static class Endpoints
    {
        @Bean AdminServerProperties adminProperties(Environment environment) {var p=new AdminServerProperties();p.setContextPath(environment.getProperty("test.context",""));return p;}
        @Bean TestEndpoints testEndpoints(){return new TestEndpoints();}
    }
    /** Isolates real security filters from upstream proxy/network behaviour, which is a deployment gate. */
    @RestController
    static class TestEndpoints
    {
        @RequestMapping({"/login","/instances","/instances/{id}","/instances/{id}/actuator/{endpoint}","/actuator/{endpoint}","/actuator/health/{part}","/settings",
                "/monitor/login","/monitor/instances","/monitor/instances/{id}","/monitor/instances/{id}/actuator/{endpoint}","/monitor/actuator/{endpoint}","/monitor/actuator/health/{part}","/monitor/settings"})
        public Map<String,String> ok(){return Map.of("status","UP");}
    }
}
