package edu.ucsal.fiadopay.core;

public class AppInitializer {

    public static void initialize() {
        AnnotationScanner scanner = new AnnotationScanner();
        scanner.scan("edu.ucsal.fiadopay");
        scanner.logResults();
    }

}