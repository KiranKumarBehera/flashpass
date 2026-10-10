package com.kiran.flashpassengine.controller;

import com.kiran.flashpassengine.model.Venue;
import com.kiran.flashpassengine.repository.VenueRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/venues")
@CrossOrigin(origins = "*")
public class VenueController {

    private final VenueRepository venueRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public VenueController(
            VenueRepository venueRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.venueRepository = venueRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public ResponseEntity<List<Venue>> getAllVenues() {
        return ResponseEntity.ok(venueRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORGANIZER', 'ROLE_ADMIN')")
    public ResponseEntity<?> createVenue(@RequestBody Venue venue) {
        if (venue.getName() == null || venue.getName().isBlank()) {
            throw new IllegalArgumentException("Venue name is required");
        }
        if (venue.getCity() == null || venue.getCity().isBlank()) {
            throw new IllegalArgumentException("City is required");
        }
        Venue saved = venueRepository.save(venue);
        // Real-Time STOMP Broadcast to all connected browsers
        messagingTemplate.convertAndSend("/topic/venues", saved);
        return ResponseEntity.ok(saved);
    }
}
