package edu.ucsal.fiadopay.payment;

import edu.ucsal.fiadopay.annotations.PaymentMethod;

@PaymentMethod(type = "PIX")
public class PixPaymentProcessor {

    public String process(double amount, String currency) {
        return "PIX payment confirmed instantly";
    }
}
