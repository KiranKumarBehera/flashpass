package com.kiran.flashpassengine.service;

import com.kiran.flashpassengine.exception.ResourceNotFoundException;
import com.kiran.flashpassengine.exception.SeatUnavailableException;
import com.kiran.flashpassengine.model.Event;
import com.kiran.flashpassengine.model.Seat;
import com.kiran.flashpassengine.model.SeatStatus;
import com.kiran.flashpassengine.repository.EventRepository;
import com.kiran.flashpassengine.repository.SeatRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeatService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final SimpMessagingTemplate messagingTemplate; // 📡 WebSocket Broadcaster

    public SeatService(EventRepository eventRepository, 
                       SeatRepository seatRepository, 
                       SimpMessagingTemplate messagingTemplate) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    @Cacheable(value = "eventSeats", key = "#eventId")
    public List<Seat> getSeatsForEvent(Long eventId) {
        System.out.println(">>> [DATABASE HIT] Fetching seats from Neon PostgreSQL...");
        return seatRepository.findByEventIdOrderBySeatNumberAsc(eventId);
    }

    // Overload for backwards compatibility & tests
    @Transactional
    @CacheEvict(value = "eventSeats", allEntries = true)
    public Seat lockSeat(Long seatId) {
        return lockSeat(seatId, "Kiran");
    }

    @Transactional
    @CacheEvict(value = "eventSeats", allEntries = true)
    public Seat lockSeat(Long seatId, String user) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with ID: " + seatId));

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            String holderInfo = seat.getLockedBy() != null ? " by " + seat.getLockedBy() : "";
            throw new SeatUnavailableException("Seat " + seat.getSeatNumber() + " is already " + seat.getStatus() + holderInfo);
        }

        seat.setStatus(SeatStatus.LOCKED);
        seat.setLockedBy(user != null && !user.isBlank() ? user : "Kiran");
        seat.setLockedAt(java.time.LocalDateTime.now());
        Seat savedSeat = seatRepository.save(seat);

        // 📢 Broadcast to all connected browsers in real-time!
        messagingTemplate.convertAndSend("/topic/seats", savedSeat);
        return savedSeat;
    }

    // Overload for backwards compatibility & tests
    @Transactional
    @CacheEvict(value = "eventSeats", allEntries = true)
    public Seat bookSeat(Long seatId) {
        return bookSeat(seatId, "Kiran");
    }

    @Transactional
    @CacheEvict(value = "eventSeats", allEntries = true)
    public Seat bookSeat(Long seatId, String user) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with ID: " + seatId));

        if (seat.getStatus() != SeatStatus.LOCKED) {
            throw new SeatUnavailableException("Seat " + seat.getSeatNumber() + " must be LOCKED before booking.");
        }

        // Validate user identity: only lock holder or ADMIN can book
        if (seat.getLockedBy() != null && user != null && !"ADMIN".equalsIgnoreCase(user) 
                && !seat.getLockedBy().equalsIgnoreCase(user)) {
            throw new SeatUnavailableException("Cannot book Seat " + seat.getSeatNumber() + ": currently held by " + seat.getLockedBy());
        }

        seat.setStatus(SeatStatus.BOOKED);
        seat.setBookedBy(user != null && !user.isBlank() ? user : seat.getLockedBy());
        seat.setLockedBy(null);
        seat.setLockedAt(null);
        Seat savedSeat = seatRepository.save(seat);

        // 📢 Broadcast confirmed booking to all connected browsers!
        messagingTemplate.convertAndSend("/topic/seats", savedSeat);
        return savedSeat;
    }

    // Overload for backwards compatibility & tests
    @Transactional
    @CacheEvict(value = "eventSeats", allEntries = true)
    public Seat releaseSeat(Long seatId) {
        return releaseSeat(seatId, "Kiran");
    }

    @Transactional
    @CacheEvict(value = "eventSeats", allEntries = true)
    public Seat releaseSeat(Long seatId, String user) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with ID: " + seatId));

        if (seat.getStatus() == SeatStatus.BOOKED) {
            throw new SeatUnavailableException("Cannot release Seat " + seat.getSeatNumber() + " because it is already BOOKED.");
        }

        // Validate user identity: only lock holder or ADMIN can release
        if (seat.getLockedBy() != null && user != null && !"ADMIN".equalsIgnoreCase(user) 
                && !seat.getLockedBy().equalsIgnoreCase(user)) {
            throw new SeatUnavailableException("Cannot release Seat " + seat.getSeatNumber() + ": currently held by " + seat.getLockedBy());
        }

        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setLockedBy(null);
        seat.setLockedAt(null);
        Seat savedSeat = seatRepository.save(seat);

        // 📢 Broadcast released seat to all connected browsers!
        messagingTemplate.convertAndSend("/topic/seats", savedSeat);
        return savedSeat;
    }

    // 🔄 Admin Action: Reset all seats for an event back to AVAILABLE
    @Transactional
    @CacheEvict(value = "eventSeats", allEntries = true)
    public List<Seat> resetEventSeats(Long eventId) {
        List<Seat> seats = seatRepository.findByEventIdOrderBySeatNumberAsc(eventId);
        for (Seat s : seats) {
            s.setStatus(SeatStatus.AVAILABLE);
            s.setLockedBy(null);
            s.setLockedAt(null);
            s.setBookedBy(null);
        }
        List<Seat> savedSeats = seatRepository.saveAll(seats);
        for (Seat s : savedSeats) {
            messagingTemplate.convertAndSend("/topic/seats", s);
        }
        return savedSeats;
    }

    // ⏰ Background TTL Lease Expiration Daemon (Runs every 10 seconds)
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 10000)
    @Transactional
    @CacheEvict(value = "eventSeats", allEntries = true)
    public void cleanupExpiredLocks() {
        // Automatically release any locks older than 5 minutes (cart abandonment lease expiry)
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusMinutes(5);
        List<Seat> expiredSeats = seatRepository.findByStatusAndLockedAtBefore(SeatStatus.LOCKED, cutoff);
        for (Seat s : expiredSeats) {
            System.out.println(">>> [TTL LEASE EXPIRED] Auto-releasing Seat " + s.getSeatNumber() + " (held by " + s.getLockedBy() + ")");
            s.setStatus(SeatStatus.AVAILABLE);
            s.setLockedBy(null);
            s.setLockedAt(null);
            Seat saved = seatRepository.save(s);
            messagingTemplate.convertAndSend("/topic/seats", saved);
        }
    }
}