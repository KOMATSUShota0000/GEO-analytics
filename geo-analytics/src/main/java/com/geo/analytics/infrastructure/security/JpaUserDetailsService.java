package com.geo.analytics.infrastructure.security;
import com.geo.analytics.domain.entity.UserEntity;
import com.geo.analytics.infrastructure.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
@Service
public class JpaUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    public JpaUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    @Override
    public UserDetails loadUserByUsername(String username) {
        UserEntity u = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        String p = u.getPassword();
        if (p == null || p.isBlank()) {
            throw new UsernameNotFoundException(username);
        }
        return User.builder()
                .username(u.getUsername())
                .password(p)
                .roles(u.getRole().name())
                .build();
    }
}
