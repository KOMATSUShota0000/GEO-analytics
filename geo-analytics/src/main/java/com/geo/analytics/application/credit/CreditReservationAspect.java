package com.geo.analytics.application.credit;

import com.geo.analytics.application.service.CreditVaultService;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class CreditReservationAspect {

    private static final Logger log = LoggerFactory.getLogger(CreditReservationAspect.class);

    private final CreditVaultService creditVaultService;

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
        // 排他制御は CreditVaultService 側の @Transactional + organizationRepository.findByIdForUpdate(orgId)
        // による org 単位の DB 悲観ロックと、settle/refund の existsByParentReservationId による
        // 二重課金ガードで担保されている。Aspect 側の単一 ReentrantLock は全テナント直列化を招く冗長な排他のため撤去。
        UUID reservationId = creditVaultService.reserve(projectId, amount);
        boolean settled = false;
        try {
            Object outcome = joinPoint.proceed();
            creditVaultService.settle(reservationId, amount, settleNote);
            settled = true;
            return outcome;
        } finally {
            if (!settled) {
                // finally 内で refund が例外を投げると元の業務例外が握り潰される（Java の finally セマンティクス）。
                // refund の失敗は warn ログに留め、元の業務例外の伝播を妨げない。
                // 残留した RESERVE 行は孤児予約回収スケジューラ（StaleReservationSweeper）が後追いで返金する。
                try {
                    creditVaultService.refund(reservationId);
                } catch (RuntimeException refundException) {
                    log.warn(
                            "Credit refund failed during rollback; reservation will be swept later. reservationId={}",
                            reservationId,
                            refundException);
                }
            }
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
