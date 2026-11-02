package com.dasigochim.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.external.social-login", havingValue = "google")
public class GoogleSocialOAuthClient implements SocialOAuthClient {
    private final GoogleIdTokenVerifier verifier;

    public GoogleSocialOAuthClient(@Value("${app.external.google-client-id}") String clientId) {
        if (clientId == null || clientId.isBlank() || clientId.equals("mock-not-used")) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID 환경변수가 필요합니다.");
        }
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport.Builder().build(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
    }

    @Override
    public SocialProfile getGoogleProfile(String idToken) {
        try {
            GoogleIdToken verifiedToken = verifier.verify(idToken);
            if (verifiedToken == null) {
                throw invalidToken();
            }
            GoogleIdToken.Payload payload = verifiedToken.getPayload();
            String subject = payload.getSubject();
            if (subject == null || subject.isBlank()) {
                throw invalidToken();
            }
            String name = stringClaim(payload.get("name"));
            if (name == null || name.isBlank()) {
                name = payload.getEmail();
            }
            if (name == null || name.isBlank()) {
                name = "Google 사용자";
            }
            return new SocialProfile("GOOGLE", subject, name);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private String stringClaim(Object value) {
        return value instanceof String string ? string : null;
    }

    private BusinessException invalidToken() {
        return new BusinessException(ErrorCode.INVALID_TOKEN, "Google ID token이 올바르지 않습니다.");
    }
}
