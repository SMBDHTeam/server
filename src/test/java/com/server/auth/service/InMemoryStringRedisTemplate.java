package com.server.auth.service;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 테스트용 {@link StringRedisTemplate} 대역.
 *
 * <p>{@link RefreshTokenStore} 가 실제로 쓰는 연산만 다룬다. set·delete·keys 다.
 * {@code ValueOperations} 는 메서드가 많아 전부 구현하지 않고, 쓰는 것만 프록시로 받는다.
 * 쓰지 않는 메서드가 호출되면 바로 실패해 대역이 조용히 어긋나는 것을 막는다.
 *
 * <p>TTL 은 무시한다. 만료는 Redis 의 몫이고, 여기서 검증하려는 것은 회전과 재사용 판정이다.
 */
class InMemoryStringRedisTemplate extends StringRedisTemplate {

    private final Map<String, String> store = new LinkedHashMap<>();

    Set<String> keys() {
        return Set.copyOf(store.keySet());
    }

    @Override
    public ValueOperations<String, String> opsForValue() {
        return (ValueOperations<String, String>) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> {
                    if ("set".equals(method.getName()) && args != null && args.length >= 2) {
                        store.put((String) args[0], (String) args[1]);
                        return null;
                    }
                    throw new UnsupportedOperationException(
                            "테스트 대역이 지원하지 않는 연산이다: " + method.getName());
                });
    }

    @Override
    public Boolean hasKey(String key) {
        return store.containsKey(key);
    }

    @Override
    public Boolean delete(String key) {
        return store.remove(key) != null;
    }

    @Override
    public Long delete(Collection<String> keys) {
        return keys.stream().filter(key -> store.remove(key) != null).count();
    }

    @Override
    public Set<String> keys(String pattern) {
        // refresh:{userId}:* 만 쓰므로 * 만 처리하면 충분하다.
        Pattern regex = Pattern.compile(Pattern.quote(pattern).replace("*", "\\E.*\\Q"));
        return store.keySet().stream()
                .filter(key -> regex.matcher(key).matches())
                .collect(Collectors.toSet());
    }
}
