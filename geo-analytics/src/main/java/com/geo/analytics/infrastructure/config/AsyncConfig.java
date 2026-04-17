package com.geo.analytics.infrastructure.config;

import com.geo.analytics.infrastructure.tenant.ContextPropagator;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean
    @Primary
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("taskScheduler-");
        return taskScheduler;
    }

    @Override
    public Executor getAsyncExecutor() {
        Executor delegate = Executors.newVirtualThreadPerTaskExecutor();
        return command -> delegate.execute(ContextPropagator.wrapRunnable(command));
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable throwable, Method method, Object... params) {
                log.error(
                    "Uncaught async exception declaringClass={} method={} parameterTypes={} arguments={}",
                    method.getDeclaringClass().getName(),
                    method.getName(),
                    Arrays.toString(method.getParameterTypes()),
                    Arrays.deepToString(params),
                    throwable);
            }
        };
    }
}
