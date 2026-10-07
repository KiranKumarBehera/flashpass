package com.kiran.flashpassengine.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String artist;

    private String category;

    private String venue;

    private String city;

    private Long venueId;

    private LocalDateTime eventDate;

    private Double basePriceVip = 5000.0;

    private Double basePriceStd = 2500.0;

    public Event() {}

    public Event(String name, String venue, LocalDateTime eventDate) {
        this.name = name;
        this.venue = venue;
        this.eventDate = eventDate;
    }

    public Event(String name, String artist, String category, String venue, String city, Long venueId, LocalDateTime eventDate, Double basePriceVip, Double basePriceStd) {
        this.name = name;
        this.artist = artist;
        this.category = category;
        this.venue = venue;
        this.city = city;
        this.venueId = venueId;
        this.eventDate = eventDate;
        this.basePriceVip = basePriceVip;
        this.basePriceStd = basePriceStd;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Long getVenueId() { return venueId; }
    public void setVenueId(Long venueId) { this.venueId = venueId; }

    public LocalDateTime getEventDate() { return eventDate; }
    public void setEventDate(LocalDateTime eventDate) { this.eventDate = eventDate; }

    public Double getBasePriceVip() { return basePriceVip; }
    public void setBasePriceVip(Double basePriceVip) { this.basePriceVip = basePriceVip; }

    public Double getBasePriceStd() { return basePriceStd; }
    public void setBasePriceStd(Double basePriceStd) { this.basePriceStd = basePriceStd; }
}