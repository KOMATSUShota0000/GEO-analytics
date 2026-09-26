package com.geo.analytics.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.domain.enums.OrganizationUserRole;
import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.repository.OrganizationRepository;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class DataSeederSeedUserTest {

    private static final UUID ORG_ID = DefaultTenantIds.DEFAULT_ORGANIZATION_ID;

    private final OrganizationUserRepository userRepo = mock(OrganizationUserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    private DataSeeder seederWithEmail(String email) {
        AppProperties props = new AppProperties();
        props.getBootstrap().setEmail(email);
        return new DataSeeder(
                null, mock(WorkspaceRepository.class), userRepo, mock(OrganizationRepository.class), encoder, props);
    }

    @Test
    void createsAdminWithConfiguredEmail_whenAbsent() {
        when(userRepo.findByEmailAndDeletedAtIsNull("me+dev@gmail.com")).thenReturn(Optional.empty());
        when(encoder.encode("bootstrap")).thenReturn("hashed");

        seederWithEmail("me+dev@gmail.com").ensureSeedUser(ORG_ID);

        ArgumentCaptor<OrganizationUser> saved = ArgumentCaptor.forClass(OrganizationUser.class);
        verify(userRepo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("me+dev@gmail.com");
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(saved.getValue().getRole()).isEqualTo(OrganizationUserRole.ADMIN);
    }

    @Test
    void doesNothing_whenUserAlreadyExists() {
        when(userRepo.findByEmailAndDeletedAtIsNull("bootstrap@example.com"))
                .thenReturn(Optional.of(new OrganizationUser()));

        seederWithEmail("bootstrap@example.com").ensureSeedUser(ORG_ID);

        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void blankEmail_fallsBackToDefault() {
        AppProperties props = new AppProperties();

        props.getBootstrap().setEmail("   ");
        assertThat(props.getBootstrap().getEmail()).isEqualTo(AppProperties.Bootstrap.DEFAULT_EMAIL);

        props.getBootstrap().setEmail(null);
        assertThat(props.getBootstrap().getEmail()).isEqualTo(AppProperties.Bootstrap.DEFAULT_EMAIL);

        props.getBootstrap().setEmail(" me@gmail.com ");
        assertThat(props.getBootstrap().getEmail()).isEqualTo("me@gmail.com");
    }
}
