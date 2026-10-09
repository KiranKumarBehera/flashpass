package com.kiran.flashpassengine.controller;

import com.kiran.flashpassengine.model.PaymentChargeRequest;
import com.kiran.flashpassengine.model.PaymentChargeResponse;
import com.kiran.flashpassengine.model.PaymentTransaction;
import com.kiran.flashpassengine.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 💳 Process Idempotent Ticket Payment with Automated Bank Simulation & Rollback
     */
    @PostMapping("/charge")
    public ResponseEntity<PaymentChargeResponse> chargePayment(
            @RequestBody PaymentChargeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {

        if ((request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) 
                && idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            request.setIdempotencyKey(idempotencyKeyHeader.trim());
        }

        PaymentChargeResponse response = paymentService.processPayment(request);
        if (!response.isSuccess()) {
            // Return 402 Payment Required for declined payments to adhere to HTTP standards
            return ResponseEntity.status(402).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 📋 Audit Trail: Retrieve payment history for authenticated user
     */
    @GetMapping("/history")
    public ResponseEntity<List<PaymentTransaction>> getPaymentHistory(@RequestParam String user) {
        return ResponseEntity.ok(paymentService.getTransactionsByUser(user));
    }
}
