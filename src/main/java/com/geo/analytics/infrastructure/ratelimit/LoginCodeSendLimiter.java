package com.geo.analytics.infrastructure.ratelimit;

import com.geo.analytics.infrastructure.config.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * ログインコードの送信回数の上限（#147）。アドレスごと・接続元ごとに直近の送信時刻を覚えて数える。
 *
 * <p>Bucket4j のような「枠が少しずつ回復する」方式だと、最初の1時間に上限の約2倍まで通ってしまい、
 * 「どの1時間でも5回まで・どの24時間でも10回まで」というオーナー確定の決めごとどおりにならない（2026-09-26）。
 * 覚える時刻はアドレスごとに最大10個・接続元ごとに最大20個なので、メモリ上で正確に数えられる。
 *
 * <p>登録されていないアドレスも同じように数える。登録済みだけが上限に当たると、上限に当たるかどうかで
 * 登録の有無が分かってしまうため（#144 確定事項12）。
 */
@Component
public class LoginCodeSendLimiter {

    public enum Rejection {
        RESEND_TOO_SOON,
        SEND_LIMIT
    }

    public record Decision(Rejection rejection, Duration retryAfter) {
        static final Decision ALLOWED = new Decision(null, Duration.ZERO);

        public boolean allowed() {
            return rejection == null;
        }
    }

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);
    // Why: 架空のアドレスを大量に送られても覚える量が際限なく増えないよう、覚える件数に上限を置く。
    //      あふれた分は古いものから忘れるが、接続元ごとの上限が別にかかっているので総当たりは防げる。
    private static final long MAX_TRACKED_KEYS = 100_000;

    private final Clock clock;
    private final Duration resendInterval;
    private final int maxPerAddressPerHour;
    private final int maxPerAddressPerDay;
    private final int maxPerClientPerHour;
    private final Cache<String, ArrayDeque<Instant>> sendsByAddress;
    private final Cache<String, ArrayDeque<Instant>> sendsByClient;
    // Why: アドレスと接続元の2つの記録を、確かめてから記録するまで一度に行う。別々に行うと、同時の要求で上限を超えて通る。
    //      送信の要求は低頻度なので、全体で1つのロックでも詰まらない（synchronized は仮想スレッドを固定するため使わない）。
    private final ReentrantLock lock = new ReentrantLock();

    @Autowired
    public LoginCodeSendLimiter(AppProperties appProperties) {
        this(appProperties, Clock.systemUTC());
    }

    LoginCodeSendLimiter(AppProperties appProperties, Clock clock) {
        AppProperties.LoginCodePolicy policy = appProperties.getAuth().getLoginCode();
        this.clock = clock;
        this.resendInterval = policy.getResendInterval();
        this.maxPerAddressPerHour = policy.getMaxSendsPerAddressPerHour();
        this.maxPerAddressPerDay = policy.getMaxSendsPerAddressPerDay();
        this.maxPerClientPerHour = policy.getMaxSendsPerClientPerHour();
        this.sendsByAddress = Caffeine.newBuilder().maximumSize(MAX_TRACKED_KEYS).expireAfterAccess(DAY).build();
        this.sendsByClient = Caffeine.newBuilder().maximumSize(MAX_TRACKED_KEYS).expireAfterAccess(HOUR).build();
    }

    /** 送ってよければ送信を記録して許可を返す。断るときは記録せず、理由と待ち時間を返す。 */
    public Decision tryAcquire(String email, String clientAddress) {
        String addressKey = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        String clientKey = clientAddress == null ? "" : clientAddress;
        Instant now = clock.instant();
        lock.lock();
        try {
            ArrayDeque<Instant> byAddress = sendsByAddress.get(addressKey, k -> new ArrayDeque<>());
            ArrayDeque<Instant> byClient = sendsByClient.get(clientKey, k -> new ArrayDeque<>());
            forgetOlderThan(byAddress, now.minus(DAY));
            forgetOlderThan(byClient, now.minus(HOUR));

            Duration limitWait = max(
                    waitForWindow(byAddress, now, HOUR, maxPerAddressPerHour),
                    max(waitForWindow(byAddress, now, DAY, maxPerAddressPerDay),
                            waitForWindow(byClient, now, HOUR, maxPerClientPerHour)));
            Instant last = byAddress.peekLast();
            Duration cooldownWait = last == null ? Duration.ZERO : positive(Duration.between(now, last.plus(resendInterval)));
            if (limitWait.isPositive()) {
                return new Decision(Rejection.SEND_LIMIT, max(limitWait, cooldownWait));
            }
            if (cooldownWait.isPositive()) {
                return new Decision(Rejection.RESEND_TOO_SOON, cooldownWait);
            }
            byAddress.addLast(now);
            byClient.addLast(now);
            return Decision.ALLOWED;
        } finally {
            lock.unlock();
        }
    }

    private static void forgetOlderThan(ArrayDeque<Instant> sends, Instant cutoff) {
        while (!sends.isEmpty() && !sends.peekFirst().isAfter(cutoff)) {
            sends.pollFirst();
        }
    }

    /** 直近 {@code window} の送信が上限に達していれば、そのうち最も古い送信が窓から外れるまでの時間を返す。 */
    private static Duration waitForWindow(ArrayDeque<Instant> sends, Instant now, Duration window, int max) {
        Instant cutoff = now.minus(window);
        int inWindow = 0;
        Instant oldestInWindow = null;
        for (Iterator<Instant> it = sends.descendingIterator(); it.hasNext(); ) {
            Instant sent = it.next();
            if (!sent.isAfter(cutoff)) {
                break;
            }
            inWindow++;
            oldestInWindow = sent;
        }
        if (inWindow < max || oldestInWindow == null) {
            return Duration.ZERO;
        }
        return positive(Duration.between(now, oldestInWindow.plus(window)));
    }

    private static Duration positive(Duration d) {
        return d.isNegative() ? Duration.ZERO : d;
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
