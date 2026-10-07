package com.kiran.flashpassengine.model;

import jakarta.persistence.*;

@Entity
@Table(name = "seats")
public class Seat implements java.io.Serializable{
	private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    private Double price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Event event;

    // Version for Optimistic Locking (Prevents race conditions!)
    @Version
    private Long version;

    public Seat() {}

    public Seat(String seatNumber, SeatStatus status, Double price, Event event) {
        this.seatNumber = seatNumber;
        this.status = status;
        this.price = price;
        this.event = event;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public SeatStatus getStatus() { return status; }
    public void setStatus(SeatStatus status) { this.status = status; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}