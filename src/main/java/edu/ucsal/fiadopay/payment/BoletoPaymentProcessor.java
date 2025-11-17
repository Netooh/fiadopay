package edu.ucsal.fiadopay.payment;

import edu.ucsal.fiadopay.annotations.PaymentMethod;

@PaymentMethod(type = "BOLETO")
public class BoletoPaymentProcessor {

    public String process(double amount, String currency) {
        return "BOLETO generated, waiting payment";
    }
}
