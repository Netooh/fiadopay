package edu.ucsal.fiadopay;

import edu.ucsal.fiadopay.core.AppInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FiadoPayApplication {
    public static void main(String[] args) {

        AppInitializer.initialize();

        SpringApplication.run(FiadoPayApplication.class, args);
    }
}
