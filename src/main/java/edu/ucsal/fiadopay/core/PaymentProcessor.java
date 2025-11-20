package edu.ucsal.fiadopay.core;

import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.repo.PaymentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Component
public class PaymentProcessor {

    private final AsyncExecutor asyncExecutor;
    private final AnnotationScanner annotationScanner;
    private final PaymentRepository payments;
    private final WebhookDispatcher webhookDispatcher;

    public PaymentProcessor(AsyncExecutor asyncExecutor,
                            AnnotationScanner annotationScanner,
                            PaymentRepository payments,
                            WebhookDispatcher webhookDispatcher) {

        this.asyncExecutor = asyncExecutor;
        this.annotationScanner = annotationScanner;
        this.payments = payments;
        this.webhookDispatcher = webhookDispatcher;
    }

    public void submit(String paymentId) {
        asyncExecutor.enqueue(() -> {
            try {
                Thread.sleep(2000);
                process(paymentId);
            } catch (Exception e) {
                System.err.println("Error processing payment " + paymentId + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Transactional
    public void process(String paymentId) {
        var opt = payments.findById(paymentId);
        if (opt.isEmpty()) {
            System.err.println("Payment not found: " + paymentId);
            return;
        }

        Payment p = opt.get();

        double amount = p.getAmount().doubleValue();
        int installments = p.getInstallments() == null ? 1 : p.getInstallments();

        Map<String, Class<?>> rules = annotationScanner.getAntiFraund();

        for (Map.Entry<String, Class<?>> entry : rules.entrySet()) {
            Class<?> clazz = entry.getValue();
            try {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                boolean ok = invokeValidate(instance, amount);

                if (!ok) {
                    System.out.println("[ANTI-FRAUD] Rule FAILED → " + entry.getKey());

                    p.setStatus(Payment.Status.DECLINED);
                    p.setUpdatedAt(java.time.Instant.now());
                    payments.save(p);

                    webhookDispatcher.enqueuePaymentEvent(p);
                    return;
                }

            } catch (Exception e) {
                System.err.println("Error executing antifraud rule " + entry.getKey() + ": " + e.getMessage());
            }
        }

        BigDecimal newTotal = calculateTotalWithInterest(
                p.getAmount(),
                p.getMonthlyInterest(),
                installments
        );

        p.setTotalWithInterest(newTotal);
        p.setStatus(Payment.Status.APPROVED);
        p.setUpdatedAt(java.time.Instant.now());

        payments.save(p);

        webhookDispatcher.enqueuePaymentEvent(p);

        System.out.println("[PROCESSOR] Payment " + paymentId + " approved.");
    }

    private boolean invokeValidate(Object rule, double amount) throws Exception {
        Class<?> c = rule.getClass();
        try {
            Method m = c.getMethod("validate", double.class);
            return (Boolean) m.invoke(rule, amount);
        } catch (NoSuchMethodException e) {
            Method m = c.getMethod("validate", BigDecimal.class);
            return (Boolean) m.invoke(rule, BigDecimal.valueOf(amount));
        }
    }

    private BigDecimal calculateTotalWithInterest(BigDecimal base, Double monthlyInterest, int installments) {
        if (installments <= 1 || monthlyInterest == null || monthlyInterest <= 0) {
            return base.setScale(2, RoundingMode.HALF_UP);
        }

        double r = monthlyInterest / 100.0;
        double factor = Math.pow(1 + r, installments);

        return base.multiply(BigDecimal.valueOf(factor))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
