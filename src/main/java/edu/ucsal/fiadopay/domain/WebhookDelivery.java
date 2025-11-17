package edu.ucsal.fiadopay.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

@Entity
public class WebhookDelivery {

    @Id
    private String id = UUID.randomUUID().toString();

    private String paymentId;
    private boolean success;
    private String errorMessage;
    private Instant at;

    public String getId() { return id; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
}
