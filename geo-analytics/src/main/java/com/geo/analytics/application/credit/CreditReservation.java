package com.geo.analytics.application.credit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CreditReservation {
    long amount() default 80L;

    String settleNote() default "rubric_audit";
}
