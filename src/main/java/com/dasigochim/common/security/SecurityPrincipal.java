package com.dasigochim.common.security;

public record SecurityPrincipal(Long id, PrincipalType type, String role) {}
