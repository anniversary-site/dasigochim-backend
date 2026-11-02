package com.dasigochim.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.external.social-login", havingValue = "mock", matchIfMissing = true)
public class MockSocialOAuthClient implements SocialOAuthClient {
    @Override
    public SocialProfile getGoogleProfile(String idToken) {
        String stableId = UUID.nameUUIDFromBytes(idToken.getBytes(StandardCharsets.UTF_8)).toString();
        return new SocialProfile("GOOGLE", "mock-" + stableId, "다시고침 사용자");
    }
}
