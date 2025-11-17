package edu.ucsal.fiadopay.core.antifraud;


import edu.ucsal.fiadopay.annotations.AntiFraud;

@AntiFraud(name = "HighAmount", threshold = 1000.0)
public class HighAmountRule {

    public boolean validate(double amount) {
        return amount <= 1000.0; // reprova transações acima de 1000
    }
}
