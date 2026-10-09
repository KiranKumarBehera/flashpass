package com.kiran.flashpassengine.service;

import com.kiran.flashpassengine.exception.ResourceNotFoundException;
import com.kiran.flashpassengine.exception.SeatUnavailableException;
import com.kiran.flashpassengine.model.*;
import com.kiran.flashpassengine.repository.EventRepository;
import com.kiran.flashpassengine.repository.PaymentTransactionRepository;
import com.kiran.flashpassengine.repository.SeatRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final SeatService seatService;
    private final SimpMessagingTemplate messagingTemplate;

    public PaymentService(
            PaymentTransactionRepository paymentTransactionRepository,
            SeatRepository seatRepository,
            EventRepository eventRepository,
            SeatService seatService,
            SimpMessagingTemplate messagingTemplate) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
        this.seatService = seatService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    @CacheEvict(value = "eventSeats", allEntries = true)
    public PaymentChargeResponse processPayment(PaymentChargeRequest request) {
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            request.setIdempotencyKey(UUID.randomUUID().toString());
        }

        // 🛡️ 1. Idempotency Check (Prevents double charging on network retry / rapid click)
        Optional<PaymentTransaction> existingTxn = paymentTransactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingTxn.isPresent()) {
            PaymentTransaction tx = existingTxn.get();
            Seat seat = seatRepository.findById(tx.getSeatId()).orElse(null);
            return new PaymentChargeResponse(
                    tx.getStatus() == PaymentStatus.SUCCESS,
                    tx.getTransactionRef(),
                    tx.getIdempotencyKey(),
                    tx.getStatus(),
                    tx.getTotalAmount(),
                    "🛡️ Idempotent Replay: Transaction already verified. Zero duplicate charges.",
                    seat,
                    tx.getCompletedAt() != null ? tx.getCompletedAt() : tx.getCreatedAt()
            );
        }

        // 🔍 2. Validate Seat & Hold Status
        Seat seat = seatRepository.findById(request.getSeatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with ID: " + request.getSeatId()));

        if (seat.getStatus() != SeatStatus.LOCKED) {
            throw new SeatUnavailableException("Seat " + seat.getSeatNumber() + " must be in LOCKED state before processing payment. Current: " + seat.getStatus());
        }

        if (seat.getLockedBy() != null && request.getUser() != null 
                && !"ADMIN".equalsIgnoreCase(request.getUser()) 
                && !seat.getLockedBy().equalsIgnoreCase(request.getUser())) {
            throw new SeatUnavailableException("Seat " + seat.getSeatNumber() + " is currently held by " + seat.getLockedBy() + ", not " + request.getUser());
        }

        // Fetch Event Context
        Event event = null;
        if (request.getEventId() != null) {
            event = eventRepository.findById(request.getEventId()).orElse(null);
        } else if (seat.getEvent() != null) {
            event = seat.getEvent();
        }

        double basePrice = request.getAmount() != null ? request.getAmount() : (seat.getPrice() != null ? seat.getPrice() : 2500.0);
        double fee = Math.round(basePrice * 0.05 * 100.0) / 100.0; // 5% processing fee
        double total = basePrice + fee;

        String txnRef = "TXN_FP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        transaction.setTransactionRef(txnRef);
        transaction.setSeatId(seat.getId());
        transaction.setEventId(event != null ? event.getId() : (seat.getEvent() != null ? seat.getEvent().getId() : 0L));
        transaction.setEventName(event != null ? event.getName() : "Concert Tour");
        transaction.setSeatNumber(seat.getSeatNumber());
        transaction.setUsername(request.getUser() != null ? request.getUser() : "anonymous");
        transaction.setAmount(basePrice);
        transaction.setConvenienceFee(fee);
        transaction.setTotalAmount(total);
        transaction.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.CREDIT_CARD);
        transaction.setPaymentDetails(request.getPaymentDetails() != null ? request.getPaymentDetails() : "**** **** **** 4242");
        transaction.setCreatedAt(LocalDateTime.now());

        // ⚡ 3. Simulated Payment Rail Failure & Automated Compensating Rollback
        if (request.isSimulateFailure()) {
            transaction.setStatus(PaymentStatus.FAILED);
            transaction.setFailureReason("SIMULATED_DECLINE: Bank rejected charge due to insufficient balance / test trigger.");
            transaction.setCompletedAt(LocalDateTime.now());
            paymentTransactionRepository.save(transaction);

            // Automated Rollback: Return seat immediately to AVAILABLE pool
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setLockedBy(null);
            seat.setLockedAt(null);
            Seat rolledBackSeat = seatRepository.save(seat);

            messagingTemplate.convertAndSend("/topic/seats", rolledBackSeat);
            seatService.broadcastAnalyticsUpdate();

            return new PaymentChargeResponse(
                    false,
                    txnRef,
                    request.getIdempotencyKey(),
                    PaymentStatus.FAILED,
                    total,
                    "❌ Payment declined by issuing bank rail. Seat hold has been automatically released back to the public pool.",
                    rolledBackSeat,
                    LocalDateTime.now()
            );
        }

        // 💳 4. Payment Authorized Successfully -> Commit Booking
        transaction.setStatus(PaymentStatus.SUCCESS);
        transaction.setCompletedAt(LocalDateTime.now());
        paymentTransactionRepository.save(transaction);

        seat.setStatus(SeatStatus.BOOKED);
        seat.setBookedBy(request.getUser() != null ? request.getUser() : seat.getLockedBy());
        seat.setLockedBy(null);
        seat.setLockedAt(null);
        Seat bookedSeat = seatRepository.save(seat);

        // 📢 Real-Time WebSocket Broadcasts
        messagingTemplate.convertAndSend("/topic/seats", bookedSeat);
        seatService.broadcastAnalyticsUpdate();

        return new PaymentChargeResponse(
                true,
                txnRef,
                request.getIdempotencyKey(),
                PaymentStatus.SUCCESS,
                total,
                "✅ Payment Authorized & Settled! Holographic Ticket Pass issued.",
                bookedSeat,
                LocalDateTime.now()
        );
    }

    @Transactional
    public void recordRefundForSeat(Long seatId, String user) {
        Optional<PaymentTransaction> txnOpt = paymentTransactionRepository.findFirstBySeatIdAndStatusOrderByCreatedAtDesc(seatId, PaymentStatus.SUCCESS);
        if (txnOpt.isPresent()) {
            PaymentTransaction txn = txnOpt.get();
            txn.setStatus(PaymentStatus.REFUNDED);
            txn.setCompletedAt(LocalDateTime.now());
            paymentTransactionRepository.save(txn);
        }
    }

    public List<PaymentTransaction> getTransactionsByUser(String username) {
        return paymentTransactionRepository.findByUsernameOrderByCreatedAtDesc(username);
    }
}
