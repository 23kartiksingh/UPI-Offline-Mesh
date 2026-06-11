package com.demo.upimesh.mesh.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class IdempotencyService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${upi.mesh.idempotency-ttl-seconds:86400}")
    private long ttlSeconds;

    /**
     * Try to claim a hash in Redis using an atomic setIfAbsent.
     * Returns true if this caller is the first; false if someone else already claimed it.
     */
    public boolean claim(String packetHash) {
        String key = "idempotency:mesh:" + packetHash;
        String timestamp = Instant.now().toString();
        
        Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent(key, timestamp, Duration.ofSeconds(ttlSeconds));
                
        return Boolean.TRUE.equals(claimed);
    }
}
