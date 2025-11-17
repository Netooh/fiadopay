package edu.ucsal.fiadopay.core.webhook;

import edu.ucsal.fiadopay.annotations.WebhookSink;

@WebhookSink(path = "/webhooks/payment-created")
public class PaymentCreatedWebhook {

    public void handle(String payload) {
        System.out.println("Webhook recebido (payment-created): " + payload);
    }
}
