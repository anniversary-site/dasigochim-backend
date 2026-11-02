package com.dasigochim.common.security;

import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    public TokenPair issue(Long id, PrincipalType principalType) {
        String role = principalType == PrincipalType.ADMIN ? "ROLE_ADMIN" : "ROLE_USER";
        return new TokenPair(create(id, principalType, role, "access", properties.accessExpirationSeconds()),
                create(id, principalType, role, "refresh", properties.refreshExpirationSeconds()),
                properties.accessExpirationSeconds(), principalType);
    }

    public TokenPair refresh(String refreshToken) {
        Claims claims = parse(refreshToken);
        if (!"refresh".equals(claims.get("tokenType", String.class))) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return issue(Long.valueOf(claims.getSubject()), PrincipalType.valueOf(claims.get("principalType", String.class)));
    }

    public SecurityPrincipal parseAccessToken(String token) {
        Claims claims = parse(token);
        if (!"access".equals(claims.get("tokenType", String.class))) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return new SecurityPrincipal(Long.valueOf(claims.getSubject()),
                PrincipalType.valueOf(claims.get("principalType", String.class)), claims.get("role", String.class));
    }

    private String create(Long id, PrincipalType type, String role, String tokenType, long expirationSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(id.toString())
                .claim("principalType", type.name())
                .claim("role", role)
                .claim("tokenType", tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn, PrincipalType principalType) {}
}
