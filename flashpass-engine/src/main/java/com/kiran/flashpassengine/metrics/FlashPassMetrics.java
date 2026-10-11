package com.kiran.flashpassengine.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 📈 FlashPass Custom Domain Metrics
 * Exports real-time business telemetry and transaction latency to Prometheus / Micrometer.
 */
@Component
public class FlashPassMetrics {

    private final MeterRegistry registry;

    private final Counter seatLockSuccessCounter;
    private final Counter seatLockConflictCounter;
    private final Counter seatBookedCounter;
    private final Counter seatReleasedCounter;
    private final Timer seatLockTimer;
    private final Timer paymentTimer;

    public FlashPassMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.seatLockSuccessCounter = Counter.builder("flashpass.seats.locked.total")
                .description("Total seats successfully acquired with 5-minute optimistic hold")
                .tag("result", "success")
                .register(registry);

        this.seatLockConflictCounter = Counter.builder("flashpass.seats.locked.total")
                .description("Total concurrent seat lock conflicts (HTTP 409 ObjectOptimisticLockingFailureException)")
                .tag("result", "conflict")
                .register(registry);

        this.seatBookedCounter = Counter.builder("flashpass.seats.booked.total")
                .description("Total seats successfully purchased with holographic pass issued")
                .register(registry);

        this.seatReleasedCounter = Counter.builder("flashpass.seats.released.total")
                .description("Total seats released back to AVAILABLE via user cancellation or TTL daemon")
                .register(registry);

        this.seatLockTimer = Timer.builder("flashpass.seat.lock.duration")
                .description("Execution latency of atomic seat lock transaction")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.paymentTimer = Timer.builder("flashpass.payment.duration")
                .description("Execution latency of idempotent payment verification and compensating sagas")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordSeatLockSuccess(long durationNanos) {
        seatLockSuccessCounter.increment();
        seatLockTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordSeatLockConflict() {
        seatLockConflictCounter.increment();
    }

    public void recordSeatBooked() {
        seatBookedCounter.increment();
    }

    public void recordSeatReleased() {
        seatReleasedCounter.increment();
    }

    public void recordPayment(String method, boolean success, long durationNanos) {
        Counter.builder("flashpass.payments.processed.total")
                .description("Total payment transactions processed through idempotent gateway")
                .tag("method", method != null ? method : "UNKNOWN")
                .tag("status", success ? "SUCCESS" : "FAILED")
                .register(registry)
                .increment();

        paymentTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
