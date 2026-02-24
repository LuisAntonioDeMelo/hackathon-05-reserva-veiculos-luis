package hackthon.fiap.luis.models;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record Sale(
        String saleId,
        String reservationId,
        String vehicleId,
        String clientId,
        String paymentCode,
        String paymentStatus,
        String status,
        double totalPrice,
        String createdAt,
        String updatedAt
) {
    public static Sale reserved(
            String saleId,
            String reservationId,
            String vehicleId,
            String clientId,
            String paymentCode,
            double totalPrice
    ) {
        String now = Instant.now().toString();
        return new Sale(
                saleId,
                reservationId,
                vehicleId,
                clientId,
                paymentCode,
                "PENDING",
                "RESERVED",
                totalPrice,
                now,
                now
        );
    }

    public Map<String, AttributeValue> toItem() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("saleId", AttributeValue.builder().s(saleId).build());
        item.put("reservationId", AttributeValue.builder().s(reservationId).build());
        item.put("vehicleId", AttributeValue.builder().s(vehicleId).build());
        item.put("clientId", AttributeValue.builder().s(clientId).build());
        item.put("paymentCode", AttributeValue.builder().s(paymentCode).build());
        item.put("paymentStatus", AttributeValue.builder().s(paymentStatus).build());
        item.put("status", AttributeValue.builder().s(status).build());
        item.put("totalPrice", AttributeValue.builder().n(String.valueOf(totalPrice)).build());
        item.put("createdAt", AttributeValue.builder().s(createdAt).build());
        item.put("updatedAt", AttributeValue.builder().s(updatedAt).build());
        return item;
    }
}
