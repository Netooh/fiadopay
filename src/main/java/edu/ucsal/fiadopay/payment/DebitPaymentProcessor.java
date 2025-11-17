package edu.ucsal.fiadopay.payment;

import edu.ucsal.fiadopay.annotations.PaymentMethod;

@PaymentMethod(type = "DEBIT")
public class DebitPaymentProcessor {

    public String process(double amount, String currency) {
        return "DEBIT transaction approved (instant)";
    }
}
