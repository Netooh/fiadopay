package edu.ucsal.fiadopay.payment;

import edu.ucsal.fiadopay.annotations.PaymentMethod;

@PaymentMethod(type = "CARD")
public class CardPaymentProcessor {

    public String process(double amount, String currency) {
        return String.format("processed card payment: %.2f %s", amount, currency);
    }
}
