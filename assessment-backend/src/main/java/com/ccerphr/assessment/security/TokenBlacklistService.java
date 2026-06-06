package com.ccerphr.assessment.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    public void revoke(String token, long ttlMillis) {
        if (token == null || token.isBlank() || ttlMillis <= 0) {
            return;
        }
        long expireAt = System.currentTimeMillis() + ttlMillis;
        blacklist.put(hash(token), expireAt);
    }

    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Long expireAt = blacklist.get(hash(token));
        if (expireAt == null) {
            return false;
        }
        if (expireAt < System.currentTimeMillis()) {
            blacklist.remove(hash(token));
            return false;
        }
        return true;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanup() {
        long now = System.currentTimeMillis();
        int before = blacklist.size();
        Iterator<Map.Entry<String, Long>> it = blacklist.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (entry.getValue() < now) {
                it.remove();
            }
        }
        int after = blacklist.size();
        if (before != after) {
            log.debug("Token blacklist cleanup: {} -> {}", before, after);
        }
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(token.hashCode());
        }
    }
}
