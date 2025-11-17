package edu.ucsal.fiadopay.core;
import edu.ucsal.fiadopay.annotations.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AnnotationScanner {

    private final Map<String, Class<?>> paymentMethods = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> antiFraudRules = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> webhooks = new ConcurrentHashMap<>();

    public void scan(String basePackage) {
        try {
            String path = basePackage.replace('.', '/');
            Enumeration<URL> resources = Thread.currentThread()
                    .getContextClassLoader()
                    .getResources(path);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                File directory = new File(resource.getFile());
                if (directory.exists()) {
                    findClasses(directory, basePackage);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void findClasses(File directory, String packageName) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                findClasses(file, packageName + "." + file.getName());
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." +
                        file.getName().replace(".class", "");
                try {
                    Class<?> clazz = Class.forName(className);
                    registerIfAnnotated(clazz);
                } catch (ClassNotFoundException ignored) {}
            }
        }
    }

    private void registerIfAnnotated(Class<?> clazz) {
        if (clazz.isAnnotationPresent(PaymentMethod.class)) {
            PaymentMethod annotation = clazz.getAnnotation(PaymentMethod.class);
            paymentMethods.put(annotation.type(), clazz);
        }

        if (clazz.isAnnotationPresent(AntiFraud.class)) {
            AntiFraud annotation = clazz.getAnnotation(AntiFraud.class);
            antiFraudRules.put(annotation.name(), clazz);
        }

        if (clazz.isAnnotationPresent(WebhookSink.class)) {
            WebhookSink annotation = clazz.getAnnotation(WebhookSink.class);
            webhooks.put(annotation.path(), clazz);
        }
    }

    // Getters
    public Map<String, Class<?>> getPaymentMethods() {
        return paymentMethods;
    }

    public Map<String, Class<?>> getAntiFraudRules() {
        return antiFraudRules;
    }

    public Map<String, Class<?>> getWebhooks() {
        return webhooks;
    }

    public void logResults() {
        System.out.println("=== Annotation Scanner Results ===");

        System.out.println("Payment Methods found: " + paymentMethods.keySet());
        System.out.println("AntiFraud Rules found: " + antiFraudRules.keySet());
        System.out.println("Webhooks found: " + webhooks.keySet());

        System.out.println("=== End of Annotation Scan ===");
    }

}