//package com.kiran.flashpassengine.controller;
//
//import com.kiran.flashpassengine.model.Event;
//import com.kiran.flashpassengine.model.Seat;
//import com.kiran.flashpassengine.service.SeatService;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/api")
//@CrossOrigin(origins = "*") // Allows React frontend to communicate with this backend without CORS errors
//public class EventController {
//
//    private final SeatService seatService;
//
//    public EventController(SeatService seatService) {
//        this.seatService = seatService;
//    }
//
//    // Endpoint 1: Fetch all events
//    @GetMapping("/events")
//    public ResponseEntity<List<Event>> getAllEvents() {
//        return ResponseEntity.ok(seatService.getAllEvents());
//    }
//
//    // Endpoint 2: Fetch all seats for a specific event
//    @GetMapping("/events/{eventId}/seats")
//    public ResponseEntity<List<Seat>> getSeatsForEvent(@PathVariable Long eventId) {
//        List<Seat> seats = seatService.getSeatsForEvent(eventId);
//        return ResponseEntity.ok(seats);
//    }
//    // Endpoint 3: Lock a seat
//    @PostMapping("/seats/{seatId}/lock")
//    public ResponseEntity<?> lockSeat(@PathVariable Long seatId) {
//        try {
//            Seat lockedSeat = seatService.lockSeat(seatId);
//            return ResponseEntity.ok(lockedSeat);
//        } catch (IllegalStateException e) {
//            // Returns HTTP 400 Bad Request if seat is already locked or booked
//            return ResponseEntity.badRequest().body(e.getMessage());
//        } catch (RuntimeException e) {
//            // Returns HTTP 404 Not Found if seat ID doesn't exist
//            return ResponseEntity.status(404).body(e.getMessage());
//        }
//    }
//}

package com.kiran.flashpassengine.controller;

import com.kiran.flashpassengine.model.Event;
import com.kiran.flashpassengine.model.Seat;
import com.kiran.flashpassengine.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EventController {

    private final SeatService seatService;

    public EventController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping("/events")
    public ResponseEntity<List<Event>> getAllEvents() {
        return ResponseEntity.ok(seatService.getAllEvents());
    }

    // Organizer: Create new event tour date & auto-generate seat inventory
    @PostMapping("/events")
    public ResponseEntity<Event> createEvent(
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

    @GetMapping("/events/{eventId}/seats")
    public ResponseEntity<List<Seat>> getSeatsForEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(seatService.getSeatsForEvent(eventId));
    }

    // Lock Seat
    @PostMapping("/seats/{seatId}/lock")
    public ResponseEntity<Seat> lockSeat(
            @PathVariable Long seatId, 
            @RequestParam(required = false, defaultValue = "Kiran") String user) {
        return ResponseEntity.ok(seatService.lockSeat(seatId, user));
    }

    // Confirm Booking
    @PostMapping("/seats/{seatId}/book")
    public ResponseEntity<Seat> bookSeat(
            @PathVariable Long seatId, 
            @RequestParam(required = false, defaultValue = "Kiran") String user) {
        return ResponseEntity.ok(seatService.bookSeat(seatId, user));
    }

    // Release Seat
    @PostMapping("/seats/{seatId}/release")
    public ResponseEntity<Seat> releaseSeat(
            @PathVariable Long seatId, 
            @RequestParam(required = false, defaultValue = "Kiran") String user) {
        return ResponseEntity.ok(seatService.releaseSeat(seatId, user));
    }

    // Reset Stadium
    @PostMapping("/events/{eventId}/reset")
    public ResponseEntity<List<Seat>> resetEventSeats(@PathVariable Long eventId) {
        return ResponseEntity.ok(seatService.resetEventSeats(eventId));
    }

    // Customer: View all confirmed tickets booked by current user
    @GetMapping("/tickets/my-tickets")
    public ResponseEntity<List<Seat>> getMyTickets(@RequestParam String user) {
        return ResponseEntity.ok(seatService.getMyTickets(user));
    }

    // Organizer: Live Sales & Capacity Telemetry Overview
    @GetMapping("/analytics/overview")
    public ResponseEntity<java.util.Map<String, Object>> getAnalyticsOverview() {
        return ResponseEntity.ok(seatService.getAnalyticsOverview());
    }
}