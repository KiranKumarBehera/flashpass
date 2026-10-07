package com.kiran.flashpassengine.config;

import com.kiran.flashpassengine.model.Event;
import com.kiran.flashpassengine.model.Seat;
import com.kiran.flashpassengine.model.SeatStatus;
import com.kiran.flashpassengine.repository.EventRepository;
import com.kiran.flashpassengine.repository.SeatRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    public DataInitializer(EventRepository eventRepository, SeatRepository seatRepository) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // Only seed data if database is currently empty
        if (eventRepository.count() == 0) {
            System.out.println(">>> Seeding initial Event and Seats into Neon Database...");

            Event event = new Event(
                    "Coldplay: Music of the Spheres World Tour",
                    "DY Patil Stadium, Mumbai",
                    LocalDateTime.now().plusDays(30)
            );
            event = eventRepository.save(event);

            List<Seat> seats = new ArrayList<>();
            String[] rows = {"A", "B", "C", "D"};

            for (String row : rows) {
                for (int num = 1; num <= 10; num++) {
                    String seatNumber = row + num; // e.g. A1, A2... D10
                    Double price = row.equals("A") || row.equals("B") ? 5000.0 : 2500.0;
                    seats.add(new Seat(seatNumber, SeatStatus.AVAILABLE, price, event));
                }
            }

            seatRepository.saveAll(seats);
            System.out.println(">>> Successfully seeded " + seats.size() + " seats for Event ID: " + event.getId());
        } else {
            System.out.println(">>> Database already contains event data. Skipping seeding.");
        }
    }
}