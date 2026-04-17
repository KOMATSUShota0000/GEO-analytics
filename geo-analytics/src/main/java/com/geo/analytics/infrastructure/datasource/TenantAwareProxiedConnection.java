package com.geo.analytics.infrastructure.datasource;

import java.sql.Connection;

/**
 * テナントガード付き {@link Connection} プロキシの識別子。{@link Connection#unwrap(Class)} でこの型のみ追加で公開する。
 *
 * <p>JDK の動的プロキシは、マーカー型を外側クラスのネスト型にするとクラスローダー境界（例: JDBC ドライバと Spring DevTools）で
 * {@code Proxy.newProxyInstance} の可視性検証に失敗することがあるため、トップレベル型として定義する。
 */
public interface TenantAwareProxiedConnection extends Connection {}
