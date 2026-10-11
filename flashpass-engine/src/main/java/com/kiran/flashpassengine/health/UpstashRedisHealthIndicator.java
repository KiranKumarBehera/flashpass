package com.kiran.flashpassengine.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * ⚡ Upstash Cloud Redis Health Indicator
 * Probes the distributed Redis in-memory cache over encrypted TLS (Port 6379),
 * confirming cache-aside operational status and measuring latency.
 */
@Component
public class UpstashRedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    public UpstashRedisHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        long start = System.currentTimeMillis();
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pingResult = connection.ping();
            long latencyMs = System.currentTimeMillis() - start;

            return Health.up()
                    .withDetail("cluster", "Upstash Cloud Serverless Redis")
                    .withDetail("transport", "TLS Encrypted Socket")
                    .withDetail("port", 6379)
                    .withDetail("response", pingResult)
                    .withDetail("latencyMs", latencyMs)
                    .build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("cluster", "Upstash Cloud Serverless Redis")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
