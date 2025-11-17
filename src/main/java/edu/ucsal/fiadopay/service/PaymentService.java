package edu.ucsal.fiadopay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsal.fiadopay.controller.PaymentRequest;
import edu.ucsal.fiadopay.controller.PaymentResponse;
import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.repo.MerchantRepository;
import edu.ucsal.fiadopay.repo.PaymentRepository;
import edu.ucsal.fiadopay.repo.WebhookDeliveryRepository;
import edu.ucsal.fiadopay.core.PaymentProcessor;
import edu.ucsal.fiadopay.core.WebhookDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {
    private final MerchantRepository merchants;
    private final PaymentRepository payments;
    private final WebhookDeliveryRepository deliveries;
    private final ObjectMapper objectMapper;
    private final PaymentProcessor paymentProcessor;
    private final WebhookDispatcher webhookDispatcher;

    @Value("${fiadopay.webhook-secret}") String secret;
    @Value("${fiadopay.processing-delay-ms}") long delay;
    @Value("${fiadopay.failure-rate}") double failRate;

    public PaymentService(MerchantRepository merchants,
                          PaymentRepository payments,
                          WebhookDeliveryRepository deliveries,
                          ObjectMapper objectMapper,
                          PaymentProcessor paymentProcessor,
                          WebhookDispatcher webhookDispatcher) {
        this.merchants = merchants;
        this.payments = payments;
        this.deliveries = deliveries;
        this.objectMapper = objectMapper;
        this.paymentProcessor = paymentProcessor;
        this.webhookDispatcher = webhookDispatcher;
    }

    private Merchant merchantFromAuth(String auth){
        if (auth == null || !auth.startsWith("Bearer FAKE-")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var raw = auth.substring("Bearer FAKE-".length());
        long id;
        try {
            id = Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var merchant = merchants.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (merchant.getStatus() != Merchant.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return merchant;
    }

    @Transactional
    public PaymentResponse createPayment(String auth, String idemKey, PaymentRequest req){
        var merchant = merchantFromAuth(auth);
        var mid = merchant.getId();

        if (idemKey != null) {
            var existing = payments.findByIdempotencyKeyAndMerchantId(idemKey, mid);
            if(existing.isPresent()) return toResponse(existing.get());
        }

        Double interest = null;
        BigDecimal total = req.amount();
        if ("CARD".equalsIgnoreCase(req.method()) && req.installments()!=null && req.installments()>1){
            interest = 1.0; // 1%/mês
            var base = new BigDecimal("1.01");
            var factor = base.pow(req.installments());
            total = req.amount().multiply(factor).setScale(2, RoundingMode.HALF_UP);
        }

        var payment = Payment.builder()
                .id("pay_"+UUID.randomUUID().toString().substring(0,8))
                .merchantId(mid)
                .method(req.method().toUpperCase())
                .amount(req.amount())
                .currency(req.currency())
                .installments(req.installments()==null?1:req.installments())
                .monthlyInterest(interest)
                .totalWithInterest(total)
                .status(Payment.Status.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .idempotencyKey(idemKey)
                .metadataOrderId(req.metadataOrderId())
                .build();

        payments.save(payment);

        // Delegar processamento assíncrono ao PaymentProcessor (usa AsyncExecutor internamente)
        paymentProcessor.submit(payment.getId());

        return toResponse(payment);
    }

    public PaymentResponse getPayment(String id){
        return toResponse(payments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Map<String,Object> refund(String auth, String paymentId){
        var merchant = merchantFromAuth(auth);
        var p = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!merchant.getId().equals(p.getMerchantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        p.setStatus(Payment.Status.REFUNDED);
        p.setUpdatedAt(Instant.now());
        payments.save(p);

        // Delegar envio de webhook ao dispatcher
        webhookDispatcher.enqueuePaymentEvent(p);

        return Map.of("id","ref_"+UUID.randomUUID(),"status","PENDING");
    }

    // NOTE: processAndWebhook / sendWebhook / tryDeliver foram removidos e delegados ao WebhookDispatcher.
    // Se precisar manter dados específicos de WebhookDelivery no Service, podemos adaptar, mas a responsabilidade
    // de entrega agora é do WebhookDispatcher.

    private PaymentResponse toResponse(Payment p){
        return new PaymentResponse(
                p.getId(), p.getStatus().name(), p.getMethod(),
                p.getAmount(), p.getInstallments(), p.getMonthlyInterest(),
                p.getTotalWithInterest()
        );
    }
}
