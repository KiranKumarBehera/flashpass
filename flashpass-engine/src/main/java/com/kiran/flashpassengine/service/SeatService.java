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

    @Transactional
    @CacheEvict(value = "eventSeats", allEntries = true)
    public Seat lockSeat(Long seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with ID: " + seatId));

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new SeatUnavailableException("Seat " + seat.getSeatNumber() + " is already " + seat.getStatus());
        }

        seat.setStatus(SeatStatus.LOCKED);
        Seat savedSeat = seatRepository.save(seat);

        // 📢 Broadcast to all connected browsers in real-time!
        messagingTemplate.convertAndSend("/topic/seats", savedSeat);
        return savedSeat;
    }

    @Transactional
    @CacheEvict(value = "eventSeats", allEntries = true)
    public Seat bookSeat(Long seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with ID: " + seatId));

        if (seat.getStatus() != SeatStatus.LOCKED) {
            throw new SeatUnavailableException("Seat " + seat.getSeatNumber() + " must be LOCKED before booking.");
        }

        seat.setStatus(SeatStatus.BOOKED);
        Seat savedSeat = seatRepository.save(seat);

        // 📢 Broadcast confirmed booking to all connected browsers!
        messagingTemplate.convertAndSend("/topic/seats", savedSeat);
        return savedSeat;
    }

    @Transactional
    @CacheEvict(value = "eventSeats", allEntries = true)
    public Seat releaseSeat(Long seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with ID: " + seatId));

        if (seat.getStatus() == SeatStatus.BOOKED) {
            throw new SeatUnavailableException("Cannot release Seat " + seat.getSeatNumber() + " because it is already BOOKED.");
        }

        seat.setStatus(SeatStatus.AVAILABLE);
        Seat savedSeat = seatRepository.save(seat);

        // 📢 Broadcast released seat to all connected browsers!
        messagingTemplate.convertAndSend("/topic/seats", savedSeat);
        return savedSeat;
    }
}