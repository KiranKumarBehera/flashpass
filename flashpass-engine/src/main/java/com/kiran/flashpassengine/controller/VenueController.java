package com.kiran.flashpassengine.controller;

import com.kiran.flashpassengine.model.Venue;
import com.kiran.flashpassengine.repository.VenueRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/venues")
@CrossOrigin(origins = "*")
public class VenueController {

    private final VenueRepository venueRepository;

    public VenueController(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @GetMapping
    public ResponseEntity<List<Venue>> getAllVenues() {
        return ResponseEntity.ok(venueRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Venue> createVenue(@RequestBody Venue venue) {
        if (venue.getName() == null || venue.getName().isBlank()) {
            throw new IllegalArgumentException("Venue name is required");
        }
        if (venue.getCity() == null || venue.getCity().isBlank()) {
            throw new IllegalArgumentException("City is required");
        }
        Venue saved = venueRepository.save(venue);
        return ResponseEntity.ok(saved);
    }
}
