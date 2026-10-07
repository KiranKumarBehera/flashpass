package com.kiran.flashpassengine.model;

import jakarta.persistence.*;

@Entity
@Table(name = "venues")
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String city;

    private Integer capacity;

    private String seatingRows = "A,B,C,D";

    private Integer seatsPerRow = 10;

    public Venue() {}

    public Venue(String name, String city, Integer capacity, String seatingRows, Integer seatsPerRow) {
        this.name = name;
        this.city = city;
        this.capacity = capacity;
        this.seatingRows = seatingRows != null ? seatingRows : "A,B,C,D";
        this.seatsPerRow = seatsPerRow != null ? seatsPerRow : 10;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public String getSeatingRows() { return seatingRows; }
    public void setSeatingRows(String seatingRows) { this.seatingRows = seatingRows; }

    public Integer getSeatsPerRow() { return seatsPerRow; }
    public void setSeatsPerRow(Integer seatsPerRow) { this.seatsPerRow = seatsPerRow; }
}
