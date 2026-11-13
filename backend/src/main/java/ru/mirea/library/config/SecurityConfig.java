package ru.mirea.library.config;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import ru.mirea.library.security.*;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public FilterRegistrationBean<JwtFilter> jwtRegistration(JwtFilter filter) {
        FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtFilter jwt, ErrorWriter errors) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        handler ->
                                handler.authenticationEntryPoint(
                                                (req, res, e) ->
                                                        errors.write(
                                                                res,
                                                                401,
                                                                "UNAUTHORIZED",
                                                                "Требуется вход в систему"))
                                        .accessDeniedHandler(
                                                (req, res, e) ->
                                                        errors.write(
                                                                res,
                                                                403,
                                                                "FORBIDDEN",
                                                                "Недостаточно прав для этой"
                                                                    + " операции")))
                .authorizeHttpRequests(
                        auth ->
                                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers(
                                                "/swagger-ui.html",
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**",
                                                "/error")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/auth/login",
                                                "/api/auth/refresh",
                                                "/api/auth/logout")
                                        .permitAll()
                                        .requestMatchers(
                                                "/api/auth/me", "/api/auth/change-password")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.GET, "/api/**")
                                        .hasRole("VIEWER")
                                        .requestMatchers(HttpMethod.DELETE, "/api/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/api/readers")
                                        .hasRole("ADMIN")
                                        .requestMatchers(HttpMethod.PUT, "/api/readers/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/api/book-requests")
                                        .hasRole("LIBRARIAN")
                                        .requestMatchers(HttpMethod.PUT, "/api/book-requests/**")
                                        .hasRole("LIBRARIAN")
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
