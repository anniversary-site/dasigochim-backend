package com.dasigochim.auth.service;

public interface SocialOAuthClient {
    SocialProfile getGoogleProfile(String idToken);
    record SocialProfile(String provider, String providerId, String nickname) {}
}
