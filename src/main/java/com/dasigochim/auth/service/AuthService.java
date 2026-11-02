package com.dasigochim.auth.service;

import com.dasigochim.admin.repository.AdminRepository;
import com.dasigochim.common.exception.BusinessException;
import com.dasigochim.common.exception.ErrorCode;
import com.dasigochim.common.security.JwtTokenProvider;
import com.dasigochim.common.security.PrincipalType;
import com.dasigochim.user.domain.User;
import com.dasigochim.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final SocialOAuthClient socialOAuthClient;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public JwtTokenProvider.TokenPair loginWithGoogle(String idToken) {
        SocialOAuthClient.SocialProfile profile = socialOAuthClient.getGoogleProfile(idToken);
        User user = userRepository.findByProviderAndProviderId(profile.provider(), profile.providerId())
                .orElseGet(() -> userRepository.save(new User(profile.provider(), profile.providerId(), profile.nickname())));

        // 역할 선택을 클라이언트에 맡기지 않는다. 운영자가 미리 연결한 Google 계정만 관리자 JWT를 받는다.
        var admin = adminRepository.findByUserId(user.getId());
        if (admin.isPresent()) {
            return tokenProvider.issue(admin.get().getId(), PrincipalType.ADMIN);
        }
        return tokenProvider.issue(user.getId(), PrincipalType.USER);
    }

    @Transactional(readOnly = true)
    public JwtTokenProvider.TokenPair loginAdmin(String loginId, String password) {
        var admin = adminRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN, "관리자 로그인 정보가 올바르지 않습니다."));
        if (admin.getPasswordHash() == null || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "관리자 로그인 정보가 올바르지 않습니다.");
        }
        return tokenProvider.issue(admin.getId(), PrincipalType.ADMIN);
    }

    public JwtTokenProvider.TokenPair refresh(String refreshToken) {
        return tokenProvider.refresh(refreshToken);
    }
}
