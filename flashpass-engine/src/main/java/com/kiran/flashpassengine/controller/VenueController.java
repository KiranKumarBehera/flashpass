package com.kiran.flashpassengine.controller;

import com.kiran.flashpassengine.model.User;
import com.kiran.flashpassengine.model.UserRole;
import com.kiran.flashpassengine.model.Venue;
import com.kiran.flashpassengine.repository.UserRepository;
import com.kiran.flashpassengine.repository.VenueRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/venues")
@CrossOrigin(origins = "*")
public class VenueController {

    private final VenueRepository venueRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public VenueController(
            VenueRepository venueRepository, 
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.venueRepository = venueRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public ResponseEntity<List<Venue>> getAllVenues() {
        return ResponseEntity.ok(venueRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> createVenue(
            @RequestBody Venue venue,
            @RequestParam(required = false, defaultValue = "organizer") String user) {
        // Enforce RBAC: Fans cannot register venues
        if (!"organizer".equalsIgnoreCase(user) && !"admin".equalsIgnoreCase(user)) {
            User u = userRepository.findByUsername(user).orElse(null);
            if (u == null || (u.getRole() != UserRole.ROLE_ORGANIZER && u.getRole() != UserRole.ROLE_ADMIN)) {
                return ResponseEntity.status(403).body(java.util.Map.of(
                    "status", 403,
                    "error", "FORBIDDEN",
                    "message", "Access Denied: Only Organizers and Admins can register venues."
                ));
            }
        }
        if (venue.getName() == null || venue.getName().isBlank()) {
            throw new IllegalArgumentException("Venue name is required");
        }
        if (venue.getCity() == null || venue.getCity().isBlank()) {
            throw new IllegalArgumentException("City is required");
        }
        Venue saved = venueRepository.save(venue);
        // 📢 Real-Time STOMP Broadcast to all connected browsers!
        messagingTemplate.convertAndSend("/topic/venues", saved);
        return ResponseEntity.ok(saved);
    }
}
