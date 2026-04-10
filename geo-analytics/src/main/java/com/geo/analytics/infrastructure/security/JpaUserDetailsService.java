package com.geo.analytics.infrastructure.security;

import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class JpaUserDetailsService implements UserDetailsService {

    private final OrganizationUserRepository organizationUserRepository;

    public JpaUserDetailsService(OrganizationUserRepository organizationUserRepository) {
        this.organizationUserRepository = organizationUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        OrganizationUser u = organizationUserRepository
                .findByEmailAndDeletedAtIsNull(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        String p = u.getPasswordHash();
        if (p == null || p.isBlank()) {
            throw new UsernameNotFoundException(username);
        }
        return User.builder()
                .username(u.getEmail())
                .password(p)
                .roles(u.getRole().name())
                .build();
    }
}
