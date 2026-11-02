package com.mingdy.gateway;

import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

// Gateway 는 인증처럼 요청 앞단에서 공통으로 처리할 일을 모으는 곳이라 입력값 방어 코드가 많다.
// MVC 대신 리액티브(WebFlux)를 쓴다. Gateway 는 Redis 를 호출하고 서비스로 요청을 넘기는 일이 대부분이라
// CPU 작업보다 I/O 대기가 많기 때문이다.
// 리액티브는 응답을 기다리는 동안 스레드를 붙잡지 않고 반납하므로, 적은 스레드로 많은 동시 요청을 처리할 수 있다.
// 어느 쪽이 유리한지는 애플리케이션 특성에 따라 다르다.
@SpringBootApplication
public class GatewayApplication {

    private static final String USER_ID_HEADER = "X-User-Id";

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    // 사용자마다 토큰 버킷을 나누는 키. 키가 비면 Gateway 가 서비스로 보내지 않고 403 으로 막는다.
    @Bean
    public KeyResolver userIdKeyResolver() {
        return exchange -> {
            // 강의에서는 기존 API의 사용자 식별 헤더를 사용한다. 운영에서는 인증된 principal을 키로 삼는다.
            Long userId = singleUserId(exchange.getRequest().getHeaders().get(USER_ID_HEADER));
            return userId == null ? Mono.empty() : Mono.just("user:" + userId);
        };
    }

    // 헤더가 없거나 여러 개면 어느 사용자인지 알 수 없으므로 null. 0 이하도 유효한 ID 가 아니다.
    private static Long singleUserId(List<String> headerValues) {
        if (headerValues == null || headerValues.size() != 1) {
            return null;
        }
        Long userId = toLongOrNull(headerValues.getFirst());
        return userId != null && userId > 0 ? userId : null;
    }

    // 숫자가 아니면 null. 형식이 깨진 값을 걸러내는 용도.
    private static Long toLongOrNull(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}