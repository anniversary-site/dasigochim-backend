package com.dasigochim.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long accessExpirationSeconds, long refreshExpirationSeconds) {}
