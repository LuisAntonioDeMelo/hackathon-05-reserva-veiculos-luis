package hackthon.fiap.luis.models;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public record Reservation(
        String reservationId,
        String saleId,
        String vehicleId,
        String clientId,
        String status,
        String reservedAt,
        String expiresAt,
        String updatedAt
) {
    public static Reservation reserved(
            String reservationId,
            String saleId,
            String vehicleId,
            String clientId,
            int ttlMinutes
    ) {
        Instant now = Instant.now();
        return new Reservation(
                reservationId,
                saleId,
                vehicleId,
                clientId,
                "RESERVED",
                now.toString(),
                now.plus(ttlMinutes, ChronoUnit.MINUTES).toString(),
                now.toString()
        );
    }

    public Map<String, AttributeValue> toItem() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("reservationId", AttributeValue.builder().s(reservationId).build());
        item.put("saleId", AttributeValue.builder().s(saleId).build());
        item.put("vehicleId", AttributeValue.builder().s(vehicleId).build());
        item.put("clientId", AttributeValue.builder().s(clientId).build());
        item.put("status", AttributeValue.builder().s(status).build());
        item.put("reservedAt", AttributeValue.builder().s(reservedAt).build());
        item.put("expiresAt", AttributeValue.builder().s(expiresAt).build());
        item.put("updatedAt", AttributeValue.builder().s(updatedAt).build());
        return item;
    }
}
