package com.geo.analytics.integration.support;

import com.geo.analytics.infrastructure.persistence.GlobalAccess;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("rls-it")
public class RlsTenantQueryFacade {

    @PersistenceContext
    private EntityManager entityManager;

    @GlobalAccess
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> selectAllTenants() {
        return entityManager
                .createNativeQuery("SELECT * FROM public.tenants ORDER BY id")
                .getResultList();
    }
}
