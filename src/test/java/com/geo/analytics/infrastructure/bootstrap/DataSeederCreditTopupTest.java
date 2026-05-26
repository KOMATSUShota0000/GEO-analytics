package com.geo.analytics.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geo.analytics.domain.entity.OrganizationEntity;
import com.geo.analytics.infrastructure.repository.OrganizationRepository;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class DataSeederCreditTopupTest {

    private static final UUID ORG_ID = DefaultTenantIds.DEFAULT_ORGANIZATION_ID;
    private static final UUID WS_ID = DefaultTenantIds.WORKSPACE_ID;

    private DataSeeder seederWith(OrganizationRepository orgRepo, WorkspaceRepository wsRepo,
                                   OrganizationUserRepository userRepo, PasswordEncoder encoder) {
        return new DataSeeder(null, wsRepo, userRepo, orgRepo, encoder);
    }

    private OrganizationEntity orgWithBalance(long balance) {
        // OrganizationEntity has protected constructor; mock to bypass and stub getters/setters.
        OrganizationEntity org = mock(OrganizationEntity.class);
        AtomicLong holder = new AtomicLong(balance);
        lenient().when(org.getCreditBalance()).thenAnswer(inv -> holder.get());
        lenient().doAnswer(inv -> {
            holder.set(inv.getArgument(0, Long.class));
            return null;
        }).when(org).setCreditBalance(anyLong());
        return org;
    }

    @Test
    void zeroBalance_isToppedUp_toOneMillion() {
        OrganizationRepository orgRepo = mock(OrganizationRepository.class);
        WorkspaceRepository wsRepo = mock(WorkspaceRepository.class);
        OrganizationUserRepository userRepo = mock(OrganizationUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        OrganizationEntity org = orgWithBalance(0);
        when(orgRepo.findByIdForUpdate(ORG_ID)).thenReturn(Optional.of(org));
        when(wsRepo.count()).thenReturn(1L);

        seederWith(orgRepo, wsRepo, userRepo, encoder).seedData(ORG_ID, WS_ID);

        assertThat(org.getCreditBalance()).isEqualTo(DataSeeder.DEV_CREDIT_TOPUP);
    }

    @Test
    void nonZeroBalance_isPreserved() {
        OrganizationRepository orgRepo = mock(OrganizationRepository.class);
        WorkspaceRepository wsRepo = mock(WorkspaceRepository.class);
        OrganizationUserRepository userRepo = mock(OrganizationUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        OrganizationEntity org = orgWithBalance(500);
        when(orgRepo.findByIdForUpdate(ORG_ID)).thenReturn(Optional.of(org));
        when(wsRepo.count()).thenReturn(1L);

        seederWith(orgRepo, wsRepo, userRepo, encoder).seedData(ORG_ID, WS_ID);

        assertThat(org.getCreditBalance()).isEqualTo(500L);
    }

    @Test
    void orgNotFound_isNoOp() {
        OrganizationRepository orgRepo = mock(OrganizationRepository.class);
        WorkspaceRepository wsRepo = mock(WorkspaceRepository.class);
        OrganizationUserRepository userRepo = mock(OrganizationUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(orgRepo.findByIdForUpdate(ORG_ID)).thenReturn(Optional.empty());
        when(wsRepo.count()).thenReturn(1L);

        // should not throw
        seederWith(orgRepo, wsRepo, userRepo, encoder).seedData(ORG_ID, WS_ID);
    }
}
