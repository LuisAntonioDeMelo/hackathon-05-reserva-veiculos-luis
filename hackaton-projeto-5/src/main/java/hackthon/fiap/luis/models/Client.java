package hackthon.fiap.luis.models;

import hackthon.fiap.luis.common.SensitiveDataProtector;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
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
        String normalizedEmail = requireValue("email", normalizeEmail(email));
        String normalizedDocument = requireValue("documentNumber", normalizeDocument(documentNumber));
        String normalizedPaymentKey = requireValue("paymentKey", normalizePaymentKey(paymentKey));
        String normalizedAddress = requireValue("address", normalizeAddress(address));

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("clientId", AttributeValue.builder().s(clientId).build());
        item.put("fullName", AttributeValue.builder().s(fullName).build());
        item.put("emailEncrypted", AttributeValue.builder().s(SensitiveDataProtector.encrypt(normalizedEmail)).build());
        item.put("emailHash", AttributeValue.builder().s(SensitiveDataProtector.sha256(normalizedEmail)).build());
        item.put("documentNumberEncrypted", AttributeValue.builder().s(SensitiveDataProtector.encrypt(normalizedDocument)).build());
        item.put("documentNumberHash", AttributeValue.builder().s(SensitiveDataProtector.sha256(normalizedDocument)).build());
        item.put("documentNumberLast4", AttributeValue.builder().s(SensitiveDataProtector.last4(normalizedDocument)).build());
        item.put("paymentKeyEncrypted", AttributeValue.builder().s(SensitiveDataProtector.encrypt(normalizedPaymentKey)).build());
        item.put("paymentKeyHash", AttributeValue.builder().s(SensitiveDataProtector.sha256(normalizedPaymentKey.toLowerCase(Locale.ROOT))).build());
        item.put("addressEncrypted", AttributeValue.builder().s(SensitiveDataProtector.encrypt(normalizedAddress)).build());
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

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDocument(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String normalizePaymentKey(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeAddress(String value) {
        return value == null ? "" : value.trim();
    }

    private static String requireValue(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field '" + field + "' is invalid");
        }
        return value;
    }
}
