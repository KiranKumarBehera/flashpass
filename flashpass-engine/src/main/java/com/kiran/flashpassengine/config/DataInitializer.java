package com.kiran.flashpassengine.config;

import com.kiran.flashpassengine.model.*;
import com.kiran.flashpassengine.repository.*;
import com.kiran.flashpassengine.service.AuthService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final VenueRepository venueRepository;

    public DataInitializer(
            EventRepository eventRepository, 
            SeatRepository seatRepository,
            UserRepository userRepository,
            VenueRepository venueRepository) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
        this.userRepository = userRepository;
        this.venueRepository = venueRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. Seed Users (RBAC)
        if (userRepository.count() == 0) {
            System.out.println(">>> Seeding default users (Fans & Organizers)...");
            String hashedPass = AuthService.hashPassword("pass123");
            String hashedAdmin = AuthService.hashPassword("admin123");

            userRepository.save(new User("kiran", "kiran@flashpass.io", hashedPass, "Kiran Kumar Behera", UserRole.ROLE_FAN));
            userRepository.save(new User("aarav", "aarav@flashpass.io", hashedPass, "Aarav Sharma", UserRole.ROLE_FAN));
            userRepository.save(new User("priya", "priya@flashpass.io", hashedPass, "Priya Patel", UserRole.ROLE_FAN));
            userRepository.save(new User("organizer", "admin@flashpass.io", hashedAdmin, "LiveNation Global Admin", UserRole.ROLE_ORGANIZER));
            System.out.println(">>> Successfully seeded 4 initial users.");
        }

        // 2. Seed Venues
        if (venueRepository.count() == 0) {
            System.out.println(">>> Seeding venues and stadiums...");
            venueRepository.save(new Venue("DY Patil Stadium", "Mumbai", 55000, "A,B,C,D", 10));
            venueRepository.save(new Venue("Jawaharlal Nehru Stadium", "New Delhi", 60000, "A,B,C,D,E", 10));
            venueRepository.save(new Venue("Wankhede Stadium", "Mumbai", 33000, "A,B,C", 10));
            venueRepository.save(new Venue("Narendra Modi Stadium", "Ahmedabad", 132000, "A,B,C,D,E,F", 10));
            System.out.println(">>> Successfully seeded 4 venues.");
        }

        // 3. Seed Multi-Show Events (Demonstrating Same Tour in Different Venues & Times!)
        if (eventRepository.count() == 0) {
            // Show 1: Coldplay Night 1 in Mumbai
            seedEvent("Coldplay: Music of the Spheres (Mumbai - Night 1)", "Coldplay", "Rock Arena", 
                      "DY Patil Stadium", "Mumbai", 1L, LocalDateTime.now().plusDays(25), 
                      new String[]{"A", "B", "C", "D"}, 5000.0, 2500.0);

            // Show 2: Coldplay Night 2 in Mumbai (Same event tour, different date/time!)
            seedEvent("Coldplay: Music of the Spheres (Mumbai - Night 2)", "Coldplay", "Rock Arena", 
                      "DY Patil Stadium", "Mumbai", 1L, LocalDateTime.now().plusDays(26), 
                      new String[]{"A", "B", "C", "D"}, 5000.0, 2500.0);

            // Show 3: Coldplay in Ahmedabad (Same tour, completely different venue!)
            seedEvent("Coldplay: Music of the Spheres (Ahmedabad Finale)", "Coldplay", "Rock Arena", 
                      "Narendra Modi Stadium", "Ahmedabad", 4L, LocalDateTime.now().plusDays(35), 
                      new String[]{"A", "B", "C", "D", "E"}, 6000.0, 3000.0);

            // Show 4: Diljit Dosanjh in Delhi
            seedEvent("Diljit Dosanjh: Dil-Luminati Tour", "Diljit Dosanjh", "Punjabi Pop", 
                      "Jawaharlal Nehru Stadium", "New Delhi", 2L, LocalDateTime.now().plusDays(40), 
                      new String[]{"A", "B", "C", "D", "E"}, 6500.0, 3200.0);

            // Show 5: Taylor Swift in Mumbai
            seedEvent("Taylor Swift: The Eras Tour (Acoustic Arena)", "Taylor Swift", "Pop Arena", 
                      "Wankhede Stadium", "Mumbai", 3L, LocalDateTime.now().plusDays(60), 
                      new String[]{"A", "B", "C"}, 9500.0, 4500.0);
        } else if (eventRepository.count() < 4) {
            // Add extra shows if only 1 exists
            seedEvent("Coldplay: Music of the Spheres (Mumbai - Night 2)", "Coldplay", "Rock Arena", 
                      "DY Patil Stadium", "Mumbai", 1L, LocalDateTime.now().plusDays(26), 
                      new String[]{"A", "B", "C", "D"}, 5000.0, 2500.0);

            seedEvent("Diljit Dosanjh: Dil-Luminati Tour", "Diljit Dosanjh", "Punjabi Pop", 
                      "Jawaharlal Nehru Stadium", "New Delhi", 2L, LocalDateTime.now().plusDays(40), 
                      new String[]{"A", "B", "C", "D", "E"}, 6500.0, 3200.0);

            seedEvent("Taylor Swift: The Eras Tour (Acoustic Arena)", "Taylor Swift", "Pop Arena", 
                      "Wankhede Stadium", "Mumbai", 3L, LocalDateTime.now().plusDays(60), 
                      new String[]{"A", "B", "C"}, 9500.0, 4500.0);
        }
    }

    private void seedEvent(String name, String artist, String category, String venue, String city, Long venueId, 
                           LocalDateTime date, String[] rows, Double vipPrice, Double stdPrice) {
        System.out.println(">>> Seeding Event: " + name + " at " + venue);
        Event event = new Event(name, artist, category, venue, city, venueId, date, vipPrice, stdPrice);
        Event saved = eventRepository.save(event);

        List<Seat> seats = new ArrayList<>();
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            boolean isVip = r < 2; // first 2 rows VIP
            Double price = isVip ? vipPrice : stdPrice;
            for (int num = 1; num <= 10; num++) {
                seats.add(new Seat(row + num, SeatStatus.AVAILABLE, price, saved));
            }
        }
        seatRepository.saveAll(seats);
        System.out.println(">>> Successfully seeded " + seats.size() + " seats for Event ID: " + saved.getId());
    }
}