package edu.ucsal.fiadopay.core;

import edu.ucsal.fiadopay.annotations.AntiFraud;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Component
public class PaymentProcessor {

    private final AsyncExecutor asyncExecutor;
    private final AnnotationScanner annotationScanner;
    private final PaymentRepository paymentRepository;
    private final WebhookDispatcher webhookDispatcher;

    public PaymentProcessor(AsyncExecutor asyncExecutor,
                            AnnotationScanner annotationScanner,
                            PaymentRepository paymentRepository,
                            WebhookDispatcher webhookDispatcher) {
        this.asyncExecutor = asyncExecutor;
        this.annotationScanner = annotationScanner;
        this.paymentRepository = paymentRepository;
        this.webhookDispatcher = webhookDispatcher;
    }

    public void submit(String paymentId) {
        asyncExecutor.enqueue(() -> {
            try {
                process(paymentId);
            } catch (Exception e) {
                System.err.println("Error processing payment " + paymentId + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Transactional
    public void process(String paymentId) {
        Optional<Payment> opt = paymentRepository.findById(paymentId);
        if (!opt.isPresent()) {
            System.err.println("Payment not found: " + paymentId);
            return;
        }

        Payment payment = opt.get();

        double amount = toDouble(payment.getAmount());
        int installments = payment.getInstallments();

        Map<String, Class<?>> antiFraudRules = annotationScanner.getAntiFraudRules();
        for (Map.Entry<String, Class<?>> entry : antiFraudRules.entrySet()) {
            Class<?> ruleClass = entry.getValue();
            try {
                Object ruleInstance = ruleClass.getDeclaredConstructor().newInstance();
                boolean ok = invokeValidate(ruleInstance, amount);
                if (!ok) {
                    System.out.println("AntiFraud rule '" + entry.getKey() + "' failed for payment " + paymentId);
                    setPaymentStatus(payment, PaymentStatus.DECLINED);
                    paymentRepository.save(payment);
                    webhookDispatcher.enqueuePaymentEvent(payment);
                    return;
                }
            } catch (NoSuchMethodException nsme) {
                System.out.println("AntiFraud rule " + entry.getKey() + " has no validate(...) method — skipping.");
            } catch (Exception e) {
                System.err.println("Error executing antifraud rule " + entry.getKey() + ": " + e.getMessage());
            }
        }

        BigDecimal totalWithInterest = calculateTotalWithInterest(payment.getAmount(), payment.getMonthlyInterest(), installments);
        payment.setTotalWithInterest(totalWithInterest);

        setPaymentStatus(payment, PaymentStatus.APPROVED);

        paymentRepository.save(payment);

        webhookDispatcher.enqueuePaymentEvent(payment);

        System.out.println("Payment " + paymentId + " processed successfully (approved).");
    }

    private boolean invokeValidate(Object ruleInstance, double amount) throws Exception {
        Class<?> cls = ruleInstance.getClass();
        try {
            Method m = cls.getMethod("validate", double.class);
            Object r = m.invoke(ruleInstance, amount);
            if (r instanceof Boolean) return (Boolean) r;
        } catch (NoSuchMethodException e) {
            try {
                Method m = cls.getMethod("validate", BigDecimal.class);
                Object r = m.invoke(ruleInstance, BigDecimal.valueOf(amount));
                if (r instanceof Boolean) return (Boolean) r;
            } catch (NoSuchMethodException ex) {
                // no validate found
                throw ex;
            }
        }
        return true;
    }

    private BigDecimal calculateTotalWithInterest(BigDecimal baseAmount, Double monthlyInterestPct, int installments) {
        if (baseAmount == null) return BigDecimal.ZERO;
        if (installments <= 1 || monthlyInterestPct == null || monthlyInterestPct <= 0.0) {
            return baseAmount;
        }

        double r = monthlyInterestPct / 100.0;
        double factor = Math.pow(1.0 + r, installments);
        BigDecimal total = baseAmount.multiply(BigDecimal.valueOf(factor));
        return total.setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private double toDouble(BigDecimal v) {
        if (v == null) return 0.0;
        return v.doubleValue();
    }

    private void setPaymentStatus(Payment payment, PaymentStatus status) {
        payment.setStatus(status);
    }

    public static class Payment {
        public BigDecimal getAmount() { return BigDecimal.ZERO; }
        public int getInstallments() { return 1; }
        public Double getMonthlyInterest() { return 0.0; }
        public void setTotalWithInterest(BigDecimal v) {}
        public void setStatus(PaymentStatus s) {}
    }
    public enum PaymentStatus { APPROVED, DECLINED, PENDING }
    public interface PaymentRepository {
        Optional<Payment> findById(String id);
        Payment save(Payment payment);
    }
    public interface WebhookDispatcher {
        void enqueuePaymentEvent(Payment payment);
    }
}