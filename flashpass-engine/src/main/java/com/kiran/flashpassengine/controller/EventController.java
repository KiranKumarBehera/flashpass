package com.kiran.flashpassengine.controller;

import com.kiran.flashpassengine.exception.ResourceNotFoundException;
import com.kiran.flashpassengine.model.Event;
import com.kiran.flashpassengine.model.Seat;
import com.kiran.flashpassengine.repository.SeatRepository;
import com.kiran.flashpassengine.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EventController {

    private final SeatService seatService;
    private final SeatRepository seatRepository;

    public EventController(SeatService seatService, SeatRepository seatRepository) {
        this.seatService = seatService;
        this.seatRepository = seatRepository;
    }

    // Public: Fetch all events
    @GetMapping("/events")
    public ResponseEntity<List<Event>> getAllEvents() {
        return ResponseEntity.ok(seatService.getAllEvents());
    }

    // Organizer/Admin: Create new event tour date & auto-generate seat inventory
    @PostMapping("/events")
    @PreAuthorize("hasAnyAuthority('ROLE_ORGANIZER', 'ROLE_ADMIN')")
    public ResponseEntity<?> createEvent(
            @RequestBody Event event,
            @RequestParam(required = false, defaultValue = "A,B,C,D") String rows,
            @RequestParam(required = false, defaultValue = "10") Integer seatsPerRow) {

        if (event.getName() == null || event.getName().isBlank()) {
            throw new IllegalArgumentException("Event name is required");
        }
        if (event.getVenue() == null || event.getVenue().isBlank()) {
            throw new IllegalArgumentException("Venue is required");
        }
        if (event.getEventDate() == null) {
            event.setEventDate(java.time.LocalDateTime.now().plusDays(30));
        }
        return ResponseEntity.ok(seatService.createEventWithSeats(event, rows, seatsPerRow));
    }

    // Public: Fetch all seats for a specific event
    @GetMapping("/events/{eventId}/seats")
    public ResponseEntity<List<Seat>> getSeatsForEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(seatService.getSeatsForEvent(eventId));
    }

    // Authenticated: Lock Seat (Cryptographically verified user from JWT)
    @PostMapping("/seats/{seatId}/lock")
    public ResponseEntity<Seat> lockSeat(@PathVariable Long seatId, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        return ResponseEntity.ok(seatService.lockSeat(seatId, username));
    }

    // Authenticated: Confirm Booking (Cryptographically verified user from JWT)
    @PostMapping("/seats/{seatId}/book")
    public ResponseEntity<Seat> bookSeat(@PathVariable Long seatId, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        return ResponseEntity.ok(seatService.bookSeat(seatId, username));
    }

    // Authenticated: Release Seat (Lock or confirmed booking refund)
    @PostMapping("/seats/{seatId}/release")
    public ResponseEntity<Seat> releaseSeat(@PathVariable Long seatId, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        return ResponseEntity.ok(seatService.releaseSeat(seatId, username));
    }

    // Dedicated Stress-Test Benchmark: 10 concurrent threads race for a single seat
    @PostMapping("/seats/{seatId}/race-test")
    public ResponseEntity<Map<String, Object>> runConcurrencyRaceBattle(@PathVariable Long seatId) {
        String[] botNames = {
            "FlashBot-1", "TurboFan-2", "SonicFan-3", "HyperBot-4", "RapidFan-5",
            "QuantumBot-6", "RocketFan-7", "BlitzBot-8", "ApexFan-9", "PhantomBot-10"
        };

        Seat targetSeat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with ID: " + seatId));

        long startTime = System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();

        for (String bot : botNames) {
            futures.add(executor.submit(() -> {
                try {
                    seatService.lockSeat(seatId, bot);
                    return Map.of("bot", bot, "status", "SUCCESS", "code", 200, "message", "Lock Acquired (Winner)");
                } catch (Exception e) {
                    return Map.of("bot", bot, "status", "COLLISION_PREVENTED", "code", 409, "message", e.getMessage());
                }
            }));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        List<Map<String, Object>> results = new ArrayList<>();
        for (Future<Map<String, Object>> f : futures) {
            try {
                results.add(f.get());
            } catch (Exception ignored) {}
        }

        long duration = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(Map.of(
            "targetSeat", targetSeat,
            "duration", duration,
            "results", results
        ));
    }

    // Organizer/Admin: Reset Stadium
    @PostMapping("/events/{eventId}/reset")
    @PreAuthorize("hasAnyAuthority('ROLE_ORGANIZER', 'ROLE_ADMIN')")
    public ResponseEntity<?> resetEventSeats(@PathVariable Long eventId) {
        return ResponseEntity.ok(seatService.resetEventSeats(eventId));
    }

    // Customer: View all confirmed tickets booked by current authenticated user
    @GetMapping("/tickets/my-tickets")
    public ResponseEntity<List<Seat>> getMyTickets(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        return ResponseEntity.ok(seatService.getMyTickets(username));
    }

    // Organizer/Admin: Live Sales & Capacity Telemetry Overview
    @GetMapping("/analytics/overview")
    @PreAuthorize("hasAnyAuthority('ROLE_ORGANIZER', 'ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getAnalyticsOverview() {
        return ResponseEntity.ok(seatService.getAnalyticsOverview());
    }
}