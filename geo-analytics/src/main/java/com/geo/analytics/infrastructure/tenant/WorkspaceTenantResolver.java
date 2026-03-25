package com.geo.analytics.infrastructure.tenant;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class WorkspaceTenantResolver implements CurrentTenantIdentifierResolver<UUID> {
    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID current = TenantContext.get();
        return current != null ? current : DefaultTenantIds.WORKSPACE_ID;
    }
    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
