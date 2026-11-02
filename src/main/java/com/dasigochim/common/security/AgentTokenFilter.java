package com.dasigochim.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dasigochim.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code /internal/**} 전용 인증. AI 서버가 붙이는 {@code X-Agent-Token} 을 검증한다.
 *
 * <p>이 경로에는 JWT 가 없다. 브라우저가 부를 경로도 아니라서 CORS 도 열지 않는다
 * (SecurityConfig 의 internalChain 참고).
 *
 * <p>비교는 {@link MessageDigest#isEqual}로 한다. {@code String.equals} 는 첫 불일치
 * 문자에서 즉시 빠져나오므로 응답 시간이 토큰 앞부분의 일치 길이를 흘린다.
 */
@Slf4j
@Component
public class AgentTokenFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Agent-Token";
    /** 이 접두사 밖의 요청은 건드리지 않는다. */
    public static final String INTERNAL_PREFIX = "/internal/";
    private static final List<SimpleGrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_AGENT"));

    private final byte[] expected;
    private final ObjectMapper objectMapper;

    public AgentTokenFilter(@Value("${app.agent.token}") String token, ObjectMapper objectMapper) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    /**
     * {@code /internal/**} 밖에서는 아무 일도 하지 않는다.
     *
     * <p>Spring Boot 는 Filter 타입 빈을 서블릿 전역 체인에도 자동 등록한다. SecurityConfig 에서
     * {@code FilterRegistrationBean} 으로 그 등록을 꺼 두지만, 그것만 믿으면 등록 설정이
     * 사라지는 순간 <b>모든 요청이 401</b> 이 된다 (헬스체크와 로그인 포함). 실제로 그렇게 됐었다.
     * 필터 자신이 경로를 확인하게 해서 등록 방식과 무관하게 안전하도록 둔다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented == null || !MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8))) {
            // 토큰 값은 로그에 남기지 않는다. 어느 경로가 막혔는지만 남긴다.
            log.warn("내부 API 토큰 불일치 — {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.fail("INVALID_AGENT_TOKEN", "X-Agent-Token 이 유효하지 않습니다."));
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ai-server", null, AUTHORITIES));
        chain.doFilter(request, response);
    }
}
