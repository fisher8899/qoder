package com.ccerphr.assessment.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的滑动窗口限速器。
 * 用于登录接口防暴力破解。多实例部署需替换为 Redis 实现。
 */
@Component
public class LoginRateLimiter {

    @Value("${app.login.rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.login.rate-limit.window-seconds:60}")
    private int windowSeconds;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    /**
     * 检查给定 key（IP 或 username）在窗口期内的尝试次数是否超限，并记录本次尝试。
     * 超限时抛出 RateLimitExceededException。
     */
    public void check(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        long windowStart = now - windowSeconds * 1000L;

        Deque<Long> bucket = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (bucket) {
            Iterator<Long> it = bucket.iterator();
            while (it.hasNext()) {
                if (it.next() < windowStart) {
                    it.remove();
                } else {
                    break;
                }
            }
            if (bucket.size() >= maxAttempts) {
                throw new RateLimitExceededException(
                    "登录尝试过于频繁，请 " + windowSeconds + " 秒后再试");
            }
            bucket.addLast(now);
        }
    }

    /**
     * 登录成功后清空对应 key 的尝试记录。
     */
    public void reset(String key) {
        if (key != null) {
            attempts.remove(key);
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
