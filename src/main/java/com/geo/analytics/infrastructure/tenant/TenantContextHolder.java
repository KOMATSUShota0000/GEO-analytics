package com.geo.analytics.infrastructure.tenant;

import java.lang.ScopedValue;
import java.util.Optional;
import java.util.UUID;

public final class TenantContextHolder {
    //AIに投げるクエリ（想定質問）に対する処理を並行で行っていて、DBに触るすべての操作でRLSが効く。
    //そのRLSの条件に必要なテナント情報を、ScopedValueでスレッドローカル（今動いてるスレッドにだけ見える値）に保持する。
    //スレッド=並行処理の流れ一つずつって感じ
    
    //contextは今のテナント情報を入れておく箱。これをScopedValueに入れて、各クエリの子スレッドにテナント情報を渡す
    public static final ScopedValue<TenantIdentity> CONTEXT = ScopedValue.newInstance();

    private TenantContextHolder() {}

    public static boolean isBound() {
        return CONTEXT.isBound();
    }

    public static TenantIdentity requireContext() {
        //...isBound()はScopedValueに値が入ってるかどうかを返す。入ってなければIllegalStateExceptionを投げる。
        if (!CONTEXT.isBound()) {
            throw new IllegalStateException("Tenant identity is not bound in this scope");
        }
        return CONTEXT.get();
    }

    public static Optional<TenantIdentity> current() {
        //テナント情報が入ってたらOptionalに入れて返す。入ってなければ空のOptionalを返す。
        return CONTEXT.isBound() ? Optional.of(CONTEXT.get()) : Optional.empty();
    }

    public static Optional<UUID> getOrganizationId() {
        if (!CONTEXT.isBound()) {
            return Optional.empty();
        }
        UUID id = CONTEXT.get().organizationId();
        return id == null ? Optional.empty() : Optional.of(id);
    }

    public static Optional<UUID> getTenantId() {
        if (!CONTEXT.isBound()) {
            return Optional.empty();
        }
        UUID id = CONTEXT.get().tenantId();
        return id == null ? Optional.empty() : Optional.of(id);
    }

    public static Optional<UUID> getUserId() {
        if (!CONTEXT.isBound()) {
            return Optional.empty();
        }
        UUID id = CONTEXT.get().userId();
        return id == null ? Optional.empty() : Optional.of(id);
    }
}
