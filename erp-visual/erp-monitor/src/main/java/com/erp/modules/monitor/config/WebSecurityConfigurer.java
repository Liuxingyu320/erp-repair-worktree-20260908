package com.erp.modules.monitor.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import de.codecentric.boot.admin.server.config.AdminServerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

@EnableWebSecurity
@Configuration
@EnableConfigurationProperties(MonitorAccountsProperties.class)
public class WebSecurityConfigurer
{
    private final String adminContextPath;
    private final MonitorAccountsProperties accounts;

    public WebSecurityConfigurer(AdminServerProperties properties, MonitorAccountsProperties accounts)
    {
        this.adminContextPath = properties.getContextPath();
        this.accounts=accounts;
    }

    @Bean
    PasswordEncoder monitorPasswordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService monitorUsers(PasswordEncoder encoder)
    {
        requireCredentials(accounts.getAdminUsername(),accounts.getAdminPassword(),"admin");
        List<UserDetails> users=new ArrayList<>();
        users.add(User.withUsername(accounts.getAdminUsername()).password(encoder.encode(accounts.getAdminPassword()))
                .roles("MONITOR_ADMIN").build());
        if (accounts.isRegistrationEnabled())
        {
            requireCredentials(accounts.getRegistrarUsername(),accounts.getRegistrarPassword(),"registrar");
            if (Objects.equals(accounts.getAdminUsername(),accounts.getRegistrarUsername()))
                throw new IllegalStateException("Monitor administrator and registrar identities must differ");
            users.add(User.withUsername(accounts.getRegistrarUsername()).password(encoder.encode(accounts.getRegistrarPassword()))
                    .roles("MONITOR_REGISTRAR").build());
        }
        return new InMemoryUserDetailsManager(users);
    }

    /** Only authenticated machine registration/de-registration is exempt from browser CSRF. */
    @Bean
    @Order(1)
    SecurityFilterChain registrationChain(HttpSecurity http) throws Exception
    {
        return http.securityMatcher(this::isBasicRegistrationRequest)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("MONITOR_REGISTRAR"))
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .csrf(csrf -> csrf.disable()).build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception
    {
        SavedRequestAwareAuthenticationSuccessHandler success = new SavedRequestAwareAuthenticationSuccessHandler();
        success.setDefaultTargetUrl(adminContextPath + "/");
        CookieCsrfTokenRepository repository=CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET,adminContextPath+"/assets/**",adminContextPath+"/login",
                            adminContextPath+"/actuator/health").permitAll()
                    .anyRequest().hasRole("MONITOR_ADMIN"))
                .formLogin(form -> form.loginPage(adminContextPath+"/login").successHandler(success).permitAll())
                .logout(logout -> logout.logoutUrl(adminContextPath+"/logout"))
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(repository).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new OncePerRequestFilter() {
                    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
                            throws ServletException,IOException {
                        CsrfToken token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
                        if(token!=null)token.getToken();
                        chain.doFilter(request,response);
                    }
                },CsrfFilter.class).build();
    }

    private boolean isBasicRegistrationRequest(HttpServletRequest request)
    {
        if (!accounts.isRegistrationEnabled()) return false;
        String authorization=request.getHeader("Authorization");
        if (authorization==null || !authorization.regionMatches(true,0,"Basic ",0,6)) return false;
        String path=request.getRequestURI().substring(request.getContextPath().length());
        String endpoint=adminContextPath+"/instances";
        return "POST".equals(request.getMethod()) && endpoint.equals(path)
                || "DELETE".equals(request.getMethod()) && path.startsWith(endpoint+"/")
                && path.substring(endpoint.length()+1).matches("[A-Za-z0-9_-]+");
    }

    private static void requireCredentials(String username,String password,String kind)
    {
        if (username==null || username.isBlank() || password==null || password.length()<16)
            throw new IllegalStateException("Configure external monitor "+kind+" username and a password of at least 16 characters before enabling monitor");
    }
}
