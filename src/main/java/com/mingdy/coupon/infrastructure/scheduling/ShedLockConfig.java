package com.mingdy.coupon.infrastructure.scheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

// defaultLockAtMostFor: 락을 잡은 서버가 죽어도 10초 뒤에는 다른 서버가 배출을 이어받는다.
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10S")
public class ShedLockConfig {

    // 락은 쓰던 Redis 에 저장한다. "coupon" 은 락 키를 구분하는 환경 이름.
    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "coupon");
    }
}