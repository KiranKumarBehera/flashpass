package com.kiran.flashpassengine.repository;

import com.kiran.flashpassengine.model.PaymentStatus;
import com.kiran.flashpassengine.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentTransaction> findByTransactionRef(String transactionRef);
    Optional<PaymentTransaction> findFirstBySeatIdAndStatusOrderByCreatedAtDesc(Long seatId, PaymentStatus status);
    List<PaymentTransaction> findByUsernameOrderByCreatedAtDesc(String username);
}
