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
        // Seed Event 1: Coldplay if no events exist
        if (eventRepository.count() == 0) {
            seedEvent("Coldplay: Music of the Spheres World Tour", 
                      "DY Patil Stadium, Mumbai", 
                      LocalDateTime.now().plusDays(25), 
                      new String[]{"A", "B", "C", "D"}, 
                      5000.0, 2500.0);
        }

        // Seed Event 2: Diljit Dosanjh (50 seats)
        if (eventRepository.count() < 2) {
            seedEvent("Diljit Dosanjh: Dil-Luminati Tour", 
                      "Jawaharlal Nehru Stadium, New Delhi", 
                      LocalDateTime.now().plusDays(40), 
                      new String[]{"A", "B", "C", "D", "E"}, 
                      6500.0, 3200.0);
        }

        // Seed Event 3: Taylor Swift (30 seats)
        if (eventRepository.count() < 3) {
            seedEvent("Taylor Swift: The Eras Tour (Acoustic Arena)", 
                      "Wankhede Stadium, Mumbai", 
                      LocalDateTime.now().plusDays(60), 
                      new String[]{"A", "B", "C"}, 
                      9500.0, 4500.0);
        }
    }

    private void seedEvent(String name, String venue, LocalDateTime date, String[] rows, Double vipPrice, Double stdPrice) {
        System.out.println(">>> Seeding Event: " + name + " at " + venue);
        Event event = eventRepository.save(new Event(name, venue, date));
        List<Seat> seats = new ArrayList<>();
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            boolean isVip = r < 2; // first 2 rows VIP
            Double price = isVip ? vipPrice : stdPrice;
            for (int num = 1; num <= 10; num++) {
                seats.add(new Seat(row + num, SeatStatus.AVAILABLE, price, event));
            }
        }
        seatRepository.saveAll(seats);
        System.out.println(">>> Successfully seeded " + seats.size() + " seats for Event ID: " + event.getId());
    }
}