package com.geo.analytics.infrastructure.persistence;

import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * トランザクション内で RLS 用セッション変数を設定する。{@link Ordered#LOWEST_PRECEDENCE} で
 * {@link org.springframework.transaction.interceptor.TransactionInterceptor} と同じ最終優先度バケットに置き、
 * 通常はインフラ側アドバイザの登録順によりトランザクション開始後に本アドバイスが評価されることを期待する。
 *
 * <p>{@link GlobalAccess} かつアクティブなトランザクションがある場合は {@code app.rls_bypass} を
 * {@code 'on'} にし、マイグレーションで定義されたテーブル（例: {@code user_sessions}）の RLS を
 * データベース側ポリシーで明示的に緩和する。
 */
@Aspect
@Component
@ConditionalOnProperty(prefix = "app.security.rls", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(Ordered.LOWEST_PRECEDENCE)
public class RlsConnectionInterceptor implements Ordered {

    private static final Logger SECURITY_AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final String COUNTER_GLOBAL_ACCESS = "rls_global_access_total";
    private static final String COUNTER_NAKED_BLOCKED = "rls_naked_query_blocked_total";
    private static final String TAG_CLASS = "class";
    private static final String TAG_METHOD = "method";

    private static final String SET_ORG = "SELECT set_config('app.current_org_id', ?, true)";
    private static final String SET_TENANT = "SELECT set_config('app.current_tenant_id', ?, true)";
    private static final String SET_RLS_BYPASS = "SELECT set_config('app.rls_bypass', 'on', true)";
    private static final String RESOLVE_ORG = "SELECT organization_id FROM workspaces WHERE id = ? AND deleted_at IS NULL";

    private final EntityManager entityManager;
    private final JdbcTemplate batchJdbcTemplate;
    private final boolean rlsEnabled;
    private final MeterRegistry meterRegistry;

    public RlsConnectionInterceptor(
            EntityManager entityManager,
            @Qualifier("batchJdbcTemplate") JdbcTemplate batchJdbcTemplate,
            AppProperties appProperties,
            MeterRegistry meterRegistry) {
        this.entityManager = entityManager;
        this.batchJdbcTemplate = batchJdbcTemplate;
        this.meterRegistry = meterRegistry;
        boolean enabled = true;
        if (appProperties.getSecurity() != null && appProperties.getSecurity().getRls() != null) {
            enabled = appProperties.getSecurity().getRls().isEnabled();
        }
        this.rlsEnabled = enabled;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    //ここから読む

    //@Transactionalが付いているクラスかメソッドが実行されたタイミングで以下の処理を実行する。
    //付箋の寿命が、トランザクションと同じだから、このアノテーションを選んだ。
    @Around(//withinのほうがクラスにアノテーションが付いてる場合、annotationのほうがメソッドにアノテーションが付いてる場合にマッチする。
            "@within(org.springframework.transaction.annotation.Transactional) || @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object aroundTransactional(ProceedingJoinPoint pjp) throws Throwable {
        if (!rlsEnabled) {
            // pjp は横取りする前の元のメソッドの呼び出し。つまり、この時点で横取りやめて元のメソッドの実行に返します
            return pjp.proceed();
        }
        
        //横取りする前の元のメソッドに @GlobalAccess の印が付いているか。印は「会社を問わず触ってよい例外」
        boolean global = hasGlobalAccess(pjp);
        //今のスレッドでトランザクションが動いているか。スレッドのメモ帳（actualTransactionActive）にトランザクション開始したタイミングで
        //springがトランザクション中みたいなことをメモしてくれる。そのメモを見て何か書いてあればtrueを返す。
        boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();

        if (!global && !txActive) {
            //ここのpjp は「呼ばれようとして、ここで止められたメソッド」そのもの
            //エラー原因をログにのこしている処理。
            recordNakedQueryBlocked(pjp);
            throw new SecurityException(
                    "Database access without an active transaction and without @GlobalAccess is blocked "
                            + "(possible missing @Transactional).");
        }

        //@GlobalAccess の付いたメソッドが呼ばれるたびに、毎回ログに記録しています
        if (global) {
            recordGlobalAccess(pjp);
        }

        if (!txActive) {
            return pjp.proceed();
        }

        Optional<UUID> org = TenantContextHolder.getOrganizationId();
        Optional<UUID> tenant = TenantContextHolder.getTenantId();
        if (org.isEmpty() && tenant.isEmpty()) {
            if (global) {
                //RlsBypass（RLSのすり抜け）を有効にしてる。org,tennant情報が空でも、RLSの制約をすり抜けてDBにアクセスできるようにしてる。テナント縦断のglobal処理だから。
                enableRlsBypassOnConnection();
                return pjp.proceed();
            }
            throw new SecurityException(
                    "Transactional database access requires tenant context (organization or workspace). "
                            + "Annotate with @GlobalAccess only for intentional system routes.");
        }

        //org.orElseGetは,orgが空なら,ラムダ式の処理を実行する。
        UUID resolvedOrg = org.orElseGet(() -> resolveOrgFromWorkspace(tenant.orElse(null)));
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            //テナント縦断の処理ならRLSすり抜けてええよ
            if (global) {
                applyRlsBypass(connection);
            }
            //orgが空じゃなければ、SET_ORGのSQLを実行して、付箋に組織IDをセットする。これでRLSの制約に引っかからずにDBにアクセスできる。
            if (resolvedOrg != null) {
                try (PreparedStatement ps = connection.prepareStatement(SET_ORG)) {
                    ps.setString(1, resolvedOrg.toString());
                    ps.execute();
                }
            }
            //tenantが空じゃなければ、SET_TENANTのSQLを実行して、付箋にワークスペースIDをセットする。これでRLSの制約に引っかからずにDBにアクセスできる。
            if (tenant.isPresent()) {
                try (PreparedStatement ps = connection.prepareStatement(SET_TENANT)) {
                    ps.setString(1, tenant.get().toString());
                    ps.execute();
                }
            }
        });
        return pjp.proceed();

        //ここまで読む
    }

    private void enableRlsBypassOnConnection() {
        Session session = entityManager.unwrap(Session.class);
        session.doWork(RlsConnectionInterceptor::applyRlsBypass);
    }

    private static void applyRlsBypass(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(SET_RLS_BYPASS);
        }
    }

    private static boolean hasGlobalAccess(ProceedingJoinPoint pjp) {
        //横取り元のメソッドが「どのメソッドか」という情報（シグネチャ）を取り出す命令。この情報の中に、メソッド名・属しているクラス・引数の型などが含まれている
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        //このメソッドに、この印が付いているかを確認してる。@GlobalAccessがついていたらこの時点でtrueを返す
        if (AnnotatedElementUtils.hasAnnotation(method, GlobalAccess.class)) {
            return true;
        }
        //上はメソッドに対して。下は、横取り元のメソッドが属しているクラスに、この印が付いているかを確認してる。
        Class<?> targetClass = pjp.getTarget() != null ? pjp.getTarget().getClass() : method.getDeclaringClass();
        return AnnotatedElementUtils.hasAnnotation(targetClass, GlobalAccess.class)
                || AnnotatedElementUtils.hasAnnotation(method.getDeclaringClass(), GlobalAccess.class);
    }

    private void recordGlobalAccess(ProceedingJoinPoint pjp) {
        Method m = ((MethodSignature) pjp.getSignature()).getMethod();
        //getDeclaringClass()はmethod型が持っているめそっどで、そのメソッドがどのクラスの中に書かれているか
        String className = m.getDeclaringClass().getName();
        String methodName = m.getName();
        SECURITY_AUDIT.info(
                "RlsConnectionInterceptor: @GlobalAccess invocation allowed class={} method={} target={}",
                className,
                methodName,
                pjp.getTarget() != null ? pjp.getTarget().getClass().getName() : "null");
        meterRegistry.counter(COUNTER_GLOBAL_ACCESS, TAG_CLASS, className, TAG_METHOD, methodName).increment();
    }

    private void recordNakedQueryBlocked(ProceedingJoinPoint pjp) {
        Method m = ((MethodSignature) pjp.getSignature()).getMethod();
        String className = m.getDeclaringClass().getName();
        String methodName = m.getName();
        //ここでログに「トランザクションなしでデータベースにアクセスしようとしたのでブロックしたよ」って書き込む。そしてどこが原因だったかもとってきてる
        SECURITY_AUDIT.error(
                "RlsConnectionInterceptor: blocked naked (non-transactional) database access class={} method={} target={}",
                //メソッドの定義されているクラス
                className,
                //そのメソッドの名前
                methodName,
                //メソッドが呼び出されたクラス
                pjp.getTarget() != null ? pjp.getTarget().getClass().getName() : "null");
        //meterRegistry は、一回の起動でのアプリの中のいろいろな数を集めておく帳簿。今回はエラーが起きたら+1する。
        meterRegistry.counter(COUNTER_NAKED_BLOCKED, TAG_CLASS, className, TAG_METHOD, methodName).increment();
    }

    private UUID resolveOrgFromWorkspace(UUID workspaceId) {
        if (workspaceId == null) {
            return null;
        }
        try {
            return batchJdbcTemplate.queryForObject(RESOLVE_ORG, UUID.class, workspaceId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
