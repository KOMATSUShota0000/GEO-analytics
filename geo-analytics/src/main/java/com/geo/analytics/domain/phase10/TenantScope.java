package com.geo.analytics.domain.phase10;

import java.lang.ScopedValue;
import java.util.Objects;

public final class TenantScope {

  public static final ScopedValue<TenantContext> CONTEXT = ScopedValue.newInstance();

  private TenantScope() {}

  public static void execute(TenantContext context, Runnable action) {
    ScopedValue.where(CONTEXT, Objects.requireNonNull(context, "context"))
        .run(Objects.requireNonNull(action, "action"));
  }

  public static int currentTenantIndex() {
    if (!CONTEXT.isBound()) {
      throw new IllegalStateException("TenantContext is not bound in this scope");
    }
    return CONTEXT.get().tenantIndex();
  }

  public static boolean currentRequiresPiiMasking() {
    if (!CONTEXT.isBound()) {
      throw new IllegalStateException("TenantContext is not bound in this scope");
    }
    return CONTEXT.get().requiresPiiMasking();
  }
}
