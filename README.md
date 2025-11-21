# Contexto Escolhido

O projeto simula um gateway de pagamentos completo, incluindo criação, processamento assíncrono, antifraude, cálculo de juros, idempotência e envio de webhooks. A proposta é aproximar o funcionamento de plataformas reais, mas em escala reduzida e com foco educacional.

**Decisões de Design**

O sistema foi estruturado com foco em clareza, extensibilidade e simulação fiel de um fluxo real. Entre as decisões principais estão:

Processamento separado da criação do pagamento.<br>
Uso de idempotência para garantir segurança nos pedidos.<br>
Persistência simples via Spring Data JPA.<br>
Webhook enviado automaticamente após o processamento.

**Anotações Criadas e Metadados**

Foram definidas anotações personalizadas para registrar regras antifraude.
Essas anotações carregam metadados como nome da regra e prioridade, permitindo que o sistema descubra e organize automaticamente todas as regras disponíveis.

**Mecanismo de Reflexão**

O AnnotationScanner percorre o pacote e identifica todas as classes anotadas com AntiFraudRule.
Durante o processamento, o PaymentProcessor instancia essas classes via reflexão e executa o método validate dinamicamente, independente da assinatura (double ou BigDecimal).
Esse mecanismo permite adicionar novas regras sem modificar nenhum trecho do núcleo do sistema.

**Threads e Execução Assíncrona**

O AsyncExecutor mantém uma fila interna usando threads dedicadas.
Isso garante que a API responda imediatamente ao criar um pagamento, enquanto o processamento roda em paralelo, simulando um ambiente de produção.
O envio de webhooks também utiliza essa fila.

**Padrões Aplicados**

Event-driven: criação separada do processamento.<br>
Worker Thread: execução paralela dos pagamentos.<br>
Strategy implícito nas regras antifraude descobertas por reflexão.<br>
Repository Pattern com Spring Data.<br>
Idempotent Request para garantir segurança ao criar pagamentos.

**Limites Conhecidos**

Não possui mecanismo de reenvio automático de webhooks.<br>
Antifraude depende fortemente de reflexão, que pode ter custo em aplicações de grande escala.<br>
O executor assíncrono é simples e não substitui um broker real como Kafka ou RabbitMQ.<br>
Processamento ainda não suporta execução distribuída entre múltiplas instâncias.

**Evidências (Prints)**

Fluxo de criação de merchant.

<img width="1095" height="692" alt="image" src="https://github.com/user-attachments/assets/c2bc3e54-6354-4a42-800a-a39e93e9ccbb" /><br>

Obtenção de token no Postman.

<img width="1087" height="685" alt="image" src="https://github.com/user-attachments/assets/c4064d28-53c9-4389-89dc-9bc9fa58333e" />

Criação de pagamento.

<img width="1080" height="681" alt="image" src="https://github.com/user-attachments/assets/d1873a99-19f3-4e0e-a181-a94bac898e28" />

Consulta do pagamento após processamento.

<img width="1066" height="689" alt="image" src="https://github.com/user-attachments/assets/568a77fa-69d4-46b0-8e3c-eba9099fc525" />

# FiadoPay Simulator (Spring Boot + H2)

Gateway de pagamento **FiadoPay** para a AVI/POOA.
Substitui PSPs reais com um backend em memória (H2).

## Rodar
```bash
./mvnw spring-boot:run
# ou
mvn spring-boot:run
```

H2 console: http://localhost:8080/h2  
Swagger UI: http://localhost:8080/swagger-ui.html

## Fluxo

1) **Cadastrar merchant**
```bash
curl -X POST http://localhost:8080/fiadopay/admin/merchants   -H "Content-Type: application/json"   -d '{"name":"MinhaLoja ADS","webhookUrl":"http://localhost:8081/webhooks/payments"}'
```

2) **Obter token**
```bash
curl -X POST http://localhost:8080/fiadopay/auth/token   -H "Content-Type: application/json"   -d '{"client_id":"<clientId>","client_secret":"<clientSecret>"}'
```

3) **Criar pagamento**
```bash
curl -X POST http://localhost:8080/fiadopay/gateway/payments   -H "Authorization: Bearer FAKE-<merchantId>"   -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000"   -H "Content-Type: application/json"   -d '{"method":"CARD","currency":"BRL","amount":250.50,"installments":12,"metadataOrderId":"ORD-123"}'
```

4) **Consultar pagamento**
```bash
curl http://localhost:8080/fiadopay/gateway/payments/<paymentId>
```
