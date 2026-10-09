package com.kiran.flashpassengine.model;

import java.time.LocalDateTime;

public class PaymentChargeResponse {
    private boolean success;
    private String transactionRef;
    private String idempotencyKey;
    private PaymentStatus status;
    private Double amountCharged;
    private String message;
    private Seat seat;
    private LocalDateTime timestamp;

    public PaymentChargeResponse() {}

    public PaymentChargeResponse(boolean success, String transactionRef, String idempotencyKey, 
                                 PaymentStatus status, Double amountCharged, String message, 
                                 Seat seat, LocalDateTime timestamp) {
        this.success = success;
        this.transactionRef = transactionRef;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.amountCharged = amountCharged;
        this.message = message;
        this.seat = seat;
        this.timestamp = timestamp;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public Double getAmountCharged() { return amountCharged; }
    public void setAmountCharged(Double amountCharged) { this.amountCharged = amountCharged; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Seat getSeat() { return seat; }
    public void setSeat(Seat seat) { this.seat = seat; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
