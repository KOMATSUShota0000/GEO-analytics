package com.geo.analytics.infrastructure.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks transactional data access that is intentionally allowed without {@link com.geo.analytics.infrastructure.tenant.TenantContextHolder}
 * bindings (e.g. login lookup, workspace resolution before tenant scope is established).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface GlobalAccess {}
