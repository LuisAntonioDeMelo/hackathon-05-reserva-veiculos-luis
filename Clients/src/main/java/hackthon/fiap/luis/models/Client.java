package hackthon.fiap.luis.models;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record Client(
        String clientId,
        String fullName,
        String email,
        String documentNumber,
        String paymentKey,
        String address,
        String status,
        String createdAt
) {
    public static Client newActive(
            String clientId,
            String fullName,
            String email,
            String documentNumber,
            String paymentKey,
            String address
    ) {
        return new Client(
                clientId,
                fullName,
                email,
                documentNumber,
                paymentKey,
                address,
                "ACTIVE",
                Instant.now().toString()
        );
    }

    public Map<String, AttributeValue> toItem() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("clientId", AttributeValue.builder().s(clientId).build());
        item.put("fullName", AttributeValue.builder().s(fullName).build());
        item.put("email", AttributeValue.builder().s(email).build());
        item.put("documentNumber", AttributeValue.builder().s(documentNumber).build());
        item.put("paymentKey", AttributeValue.builder().s(paymentKey).build());
        item.put("address", AttributeValue.builder().s(address).build());
        item.put("status", AttributeValue.builder().s(status).build());
        item.put("createdAt", AttributeValue.builder().s(createdAt).build());
        return item;
    }

    public Map<String, Object> toSummary() {
        return Map.of(
                "clientId", clientId,
                "fullName", fullName,
                "status", status
        );
    }
}
