package com.kiran.flashpassengine;

import com.kiran.flashpassengine.model.Seat;
import com.kiran.flashpassengine.model.SeatStatus;
import com.kiran.flashpassengine.repository.SeatRepository;
import com.kiran.flashpassengine.service.SeatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class SeatConcurrencyTest {

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    @DisplayName("Should allow only 1 user to lock the seat when 10 users click simultaneously")
    public void testConcurrentSeatLocking() throws InterruptedException {
        // 1. Pick Seat #2 (Seat A2) for our race
        Seat targetSeat = seatRepository.findById(2L).orElseThrow();
        targetSeat.setStatus(SeatStatus.AVAILABLE);
        seatRepository.save(targetSeat);

        int numberOfConcurrentUsers = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfConcurrentUsers);

        // The Starting Pistol: Keeps all 10 threads waiting at the gate
        CountDownLatch startingPistol = new CountDownLatch(1);
        
        // Counter for when all threads finish
        CountDownLatch finishedGate = new CountDownLatch(numberOfConcurrentUsers);

        AtomicInteger successfulLocks = new AtomicInteger(0);
        AtomicInteger failedLocks = new AtomicInteger(0);

        // 2. Queue up 10 users ready to click "Book" on Seat #2
        for (int i = 0; i < numberOfConcurrentUsers; i++) {
            executorService.submit(() -> {
                try {
                    // Wait at starting line
                    startingPistol.await();

                    // Try to lock Seat #2
                    seatService.lockSeat(2L);
                    successfulLocks.incrementAndGet();
                } catch (Exception e) {
                    // OptimisticLock or SeatUnavailable caught here!
                    failedLocks.incrementAndGet();
                } finally {
                    finishedGate.countDown();
                }
            });
        }

        // 3. FIRE THE PISTOL! All 10 threads attack Seat #2 simultaneously!
        startingPistol.countDown();

        // Wait for all 10 threads to finish
        finishedGate.await();
        executorService.shutdown();

        // 4. VERIFY THE RESULT
        System.out.println(">>> Concurrency Test Finished!");
        System.out.println(">>> Successful locks: " + successfulLocks.get());
        System.out.println(">>> Failed / Rejected locks: " + failedLocks.get());

        // Exactly 1 user MUST succeed, and exactly 9 users MUST be rejected
        assertEquals(1, successfulLocks.get(), "Only 1 user should have successfully locked the seat!");
        assertEquals(9, failedLocks.get(), "9 users should have failed due to optimistic locking/availability!");
    }
}