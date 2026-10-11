package com.kiran.flashpassengine.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 🐘 Neon Serverless PostgreSQL Health Indicator
 * Probes the pooled Neon PostgreSQL instance via PgBouncer, verifying database connectivity
 * and calculating real-time round-trip latency to AWS us-east-2.
 */
@Component
public class NeonDatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public NeonDatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        long start = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("SELECT 1");
            long latencyMs = System.currentTimeMillis() - start;

            return Health.up()
                    .withDetail("cluster", "Neon Serverless PostgreSQL 16")
                    .withDetail("region", "AWS us-east-2 (Ohio)")
                    .withDetail("multiplexing", "PgBouncer Connection Pooling")
                    .withDetail("query", "SELECT 1")
                    .withDetail("latencyMs", latencyMs)
                    .build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("cluster", "Neon Serverless PostgreSQL 16")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
