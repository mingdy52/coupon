package com.mingdy.coupon.infrastructure.cache;

import java.util.Arrays;
import java.util.List;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

// Lua 스크립트 실행 헬퍼. 결과 타입별 캐스팅과 null 처리를 한곳에 모은다.
// 스크립트는 classpath 의 .lua 파일에서 읽고, 실행 결과를 Java 타입으로 받는다.
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisLuaSupport {

    // 배열을 반환하는 스크립트용. 제네릭 타입은 런타임에 사라져 List<String>.class 를 쓸 수 없으므로
    // List.class(raw type)로 받는다. rawtypes 경고는 이 때문에 억제한다.
    @SuppressWarnings("rawtypes")
    public static RedisScript<List> listLuaScript(String path) {
        return RedisScript.of(new ClassPathResource(path), List.class);
    }

    // 정수를 반환하는 스크립트용. Lua 정수는 Long 으로 받는다.
    public static RedisScript<Long> longLuaScript(String path) {
        return RedisScript.of(new ClassPathResource(path), Long.class);
    }

    // 배열 결과를 List<String> 으로 캐스팅해 반환한다.
    // @SuppressWarnings 는 컴파일러 경고를 숨기는 어노테이션이다. 동작에는 영향이 없다.
    //   rawtypes  : 제네릭 없는 List 를 쓴다는 경고
    //   unchecked : List → List<String> 캐스팅을 컴파일러가 검증할 수 없다는 경고
    //               (StringRedisTemplate 이라 원소는 항상 String 이므로 안전하다)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<String> runForStrings(
            StringRedisTemplate redisTemplate,
            RedisScript<List> script,
            List<String> keys,
            Object... args
    ) {
        return (List<String>) redisTemplate.execute(script, keys, toStringArgs(args));
    }

    // 정수 결과를 반환한다. 스크립트가 nil 을 반환하면 null 이 되므로 예외로 막는다.
    public static long runForLong(
            StringRedisTemplate redisTemplate,
            RedisScript<Long> script,
            List<String> keys,
            Object... args
    ) {
        Long result = redisTemplate.execute(script, keys, toStringArgs(args));
        if (result == null) {
            throw new IllegalStateException("Lua 결과 null");
        }
        return result;
    }

    // StringRedisTemplate 은 ARGV 를 String 으로 직렬화하므로 숫자 같은 인자도 문자열로 바꿔 넘긴다.
    private static Object[] toStringArgs(Object[] args) {
        return Arrays.stream(args).map(String::valueOf).toArray();
    }
}
