package com.geo.analytics.application.credit;

import com.geo.analytics.application.service.CreditVaultService;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class CreditReservationAspect {
    private final CreditVaultService creditVaultService;
    private final ReentrantLock vaultGate = new ReentrantLock();

    public CreditReservationAspect(CreditVaultService creditVaultService) {
        this.creditVaultService = creditVaultService;
    }

    @Around("@annotation(com.geo.analytics.application.credit.CreditReservation)")
    public Object aroundAdvice(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        CreditReservation reservation =
                signature.getMethod().getAnnotation(CreditReservation.class);
        UUID projectId = resolveProjectId(joinPoint.getArgs());
        long amount = reservation.amount();
        String settleNote = reservation.settleNote();
        UUID reservationId = reserveUnderLock(projectId, amount);
        boolean settled = false;
        try {
            Object outcome = joinPoint.proceed();
            settleUnderLock(reservationId, amount, settleNote);
            settled = true;
            return outcome;
        } finally {
            if (!settled) {
                refundUnderLock(reservationId);
            }
        }
    }

    private UUID reserveUnderLock(UUID projectId, long amount) {
        vaultGate.lock();
        try {
            return creditVaultService.reserve(projectId, amount);
        } finally {
            vaultGate.unlock();
        }
    }

    private void settleUnderLock(UUID reservationId, long amount, String settleNote) {
        vaultGate.lock();
        try {
            creditVaultService.settle(reservationId, amount, settleNote);
        } finally {
            vaultGate.unlock();
        }
    }

    private void refundUnderLock(UUID reservationId) {
        vaultGate.lock();
        try {
            creditVaultService.refund(reservationId);
        } finally {
            vaultGate.unlock();
        }
    }

    private static UUID resolveProjectId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid;
            }
        }
        throw new IllegalArgumentException("projectId");
    }
}
