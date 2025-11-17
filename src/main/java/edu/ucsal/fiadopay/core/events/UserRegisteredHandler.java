package edu.ucsal.fiadopay.core.events;

import edu.ucsal.fiadopay.annotations.EventHandler;

@EventHandler(event = "USER_REGISTERED")
public class UserRegisteredHandler {

    public void handle(String data) {
        System.out.println("Evento USER_REGISTERED recebido com dados: " + data);
    }
}
