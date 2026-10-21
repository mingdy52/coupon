package com.mingdy.coupon.infrastructure.messaging;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// @Async 를 붙이면 스프링 부트가 기본 `task executor`를 쓰지만
// 스레드 수·큐 크기를 정하려고 전용 `task executor`를 직접 등록한다.
// 스레드 1개 + 큐 10,000 → 인메모리 큐(Worker 1개 + LinkedBlockingQueue)와 같은 구조
@Configuration
@EnableAsync
public class AsyncIssuanceConfig {

    public static final String ISSUANCE_TASK_EXECUTOR = "issuanceTaskExecutor";

    @Bean(name = ISSUANCE_TASK_EXECUTOR)
    public Executor issuanceTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10_000);
        // 큐가 차면 기본 정책(AbortPolicy)으로 publishEvent 호출 스레드에서 TaskRejectedException 이 터진다.
        // 이때 Redis 재고는 이미 차감된 상태라 과소 발급이 생긴다.
        executor.setThreadNamePrefix("issuance-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 정상 종료(SIGTERM) 시 큐에 남은 작업까지 다 처리하고 셧다운 되도록 하는 옵션
        // 강제 종료(kill -9, OOM)에는 효과가 없어 큐에 남은 발급은 유실된다.
        executor.setAwaitTerminationSeconds(30);
        // 최대 30초까지만 기다린다. 그 안에 못 끝낸 작업은 유실된다.

        executor.initialize();
        return executor;
    }

}
