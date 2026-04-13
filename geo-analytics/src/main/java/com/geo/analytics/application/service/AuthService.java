package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.LoginRequest;
import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import com.geo.analytics.infrastructure.security.TokenService;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public record AuthTokenPair(String accessToken, String refreshToken) {}

    private final AuthenticationManager authenticationManager;
    private final OrganizationUserRepository organizationUserRepository;
    private final SessionManagementService sessionManagementService;
    private final TokenService tokenService;

    public AuthService(
            AuthenticationManager authenticationManager,
            OrganizationUserRepository organizationUserRepository,
            SessionManagementService sessionManagementService,
            TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.organizationUserRepository = organizationUserRepository;
        this.sessionManagementService = sessionManagementService;
        this.tokenService = tokenService;
    }

    public AuthTokenPair login(LoginRequest request) {
        OrganizationUser user = organizationUserRepository
                .findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        TenantContextHolder.set(user.getOrganizationId(), null);
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        UUID sessionId = sessionManagementService.createNewSession(user.getId());
        String accessToken = tokenService.generateAccessToken(user, sessionId);
        String refreshToken = tokenService.generateRefreshToken(user, sessionId);
        return new AuthTokenPair(accessToken, refreshToken);
    }
}
