package com.dasigochim.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dasigochim.common.response.ApiResponse;
import com.dasigochim.common.security.AgentTokenFilter;
import com.dasigochim.common.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    private final AgentTokenFilter agentTokenFilter;
    private final ObjectMapper objectMapper;

    /**
     * AI 서버 전용 체인. JWT 가 아니라 {@code X-Agent-Token} 으로 통과시킨다.
     *
     * <p>기본 체인보다 먼저 등록해야 {@code /internal/**} 이 JWT 체인에 잡히지 않는다.
     * CORS 는 열지 않는다 — 브라우저가 부를 경로가 아니다.
     */
    @Bean
    @Order(1)
    SecurityFilterChain internalFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(agentTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/api/v1/admin/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/buildings/**", "/api/v1/reports/map", "/api/v1/reports/*").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/health", "/files/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 관리자 JWT의 id는 admins.id다. 사용자 전용 API에 통과시키면 users.id와 우연히
                        // 같을 때 다른 사용자의 데이터를 조회할 수 있으므로 역할을 명시적으로 분리한다.
                        .requestMatchers("/api/v1/me/**", "/api/v1/ai/**").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/reports/**").hasRole("USER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/reports/**").hasRole("USER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/reports/**").hasRole("USER")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getWriter(), ApiResponse.fail("INVALID_TOKEN", "인증이 필요합니다."));
                }))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * AgentTokenFilter 를 <b>서블릿 전역 체인에서 뺀다.</b>
     *
     * <p>Spring Boot 는 Filter 타입 빈을 자동으로 {@code /*} 에 등록한다. 그대로 두면
     * 이 필터가 Security 체인 밖에서도 돌아 {@code /actuator/health} 와
     * {@code /api/v1/auth/**} 까지 401 로 막는다. 이 필터는 internalChain 안에서만 돌아야 한다.
     *
     * <p>JwtAuthenticationFilter 도 같은 방식으로 전역 등록되지만, 토큰이 없으면 그냥
     * 통과시키는 관대한 필터라 증상이 드러나지 않을 뿐이다.
     */
    @Bean
    FilterRegistrationBean<AgentTokenFilter> disableAgentTokenFilterAutoRegistration(AgentTokenFilter filter) {
        FilterRegistrationBean<AgentTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Location"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
