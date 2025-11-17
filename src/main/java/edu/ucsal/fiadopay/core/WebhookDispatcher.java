package edu.ucsal.fiadopay.core;

import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.domain.WebhookDelivery;
import edu.ucsal.fiadopay.repo.WebhookDeliveryRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class WebhookDispatcher {

    private final AsyncExecutor asyncExecutor;
    private final WebhookDeliveryRepository deliveries;
    private final RestTemplate http = new RestTemplate();

    public WebhookDispatcher(AsyncExecutor asyncExecutor,
                             WebhookDeliveryRepository deliveries) {
        this.asyncExecutor = asyncExecutor;
        this.deliveries = deliveries;
    }

    /**
     * Coloca o webhook na fila para envio assíncrono.
     */
    public void enqueuePaymentEvent(Payment payment) {
        asyncExecutor.enqueue(() -> {
            try {
                sendPaymentWebhook(payment);
            } catch (Exception e) {
                System.err.println("[WEBHOOK] Error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Lógica principal de envio do webhook.
     */
    private void sendPaymentWebhook(Payment payment) {

        if (payment.getWebhookUrl() == null || payment.getWebhookUrl().isBlank()) {
            System.out.println("[WEBHOOK] Payment " + payment.getId() + " has no webhook URL. Skipping.");
            return;
        }

        try {
            // Corpo do webhook
            String json = """
                    {
                      "paymentId": "%s",
                      "status": "%s",
                      "amount": "%s",
                      "totalWithInterest": "%s",
                      "updatedAt": "%s"
                    }
                    """.formatted(
                    payment.getId(),
                    payment.getStatus(),
                    payment.getAmount(),
                    payment.getTotalWithInterest(),
                    payment.getUpdatedAt()
            );

            // Headers JSON
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(json, headers);

            // Envio POST
            http.exchange(
                    payment.getWebhookUrl(),
                    HttpMethod.POST,
                    request,
                    String.class
            );

            // Registrar sucesso
            recordDelivery(payment, true, null);

            System.out.println("[WEBHOOK] Delivered to " + payment.getWebhookUrl());

        } catch (Exception error) {
            recordDelivery(payment, false, error.getMessage());
            System.err.println("[WEBHOOK] FAILED: " + error.getMessage());
        }
    }

    /**
     * Persistência de tentativas de webhook para auditorias.
     */
    private void recordDelivery(Payment payment, boolean success, String error) {
        WebhookDelivery d = new WebhookDelivery();
        d.setPaymentId(payment.getId());
        d.setSuccess(success);
        d.setErrorMessage(error);
        d.setAt(java.time.Instant.now());
        deliveries.save(d);
    }
}
