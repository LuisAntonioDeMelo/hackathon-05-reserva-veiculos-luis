package hackthon.fiap.luis.models;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record Vehicle(
        String vehicleId,
        String brand,
        String model,
        int year,
        String color,
        double price,
        String status,
        String createdAt,
        String updatedAt
) {
    public static Vehicle available(
            String vehicleId,
            String brand,
            String model,
            int year,
            String color,
            double price
    ) {
        String now = Instant.now().toString();
        return new Vehicle(
                vehicleId,
                brand,
                model,
                year,
                color,
                price,
                "AVAILABLE",
                now,
                now
        );
    }

    public Map<String, AttributeValue> toItem() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("vehicleId", AttributeValue.builder().s(vehicleId).build());
        item.put("brand", AttributeValue.builder().s(brand).build());
        item.put("model", AttributeValue.builder().s(model).build());
        item.put("year", AttributeValue.builder().n(String.valueOf(year)).build());
        item.put("color", AttributeValue.builder().s(color).build());
        item.put("price", AttributeValue.builder().n(String.valueOf(price)).build());
        item.put("status", AttributeValue.builder().s(status).build());
        item.put("createdAt", AttributeValue.builder().s(createdAt).build());
        item.put("updatedAt", AttributeValue.builder().s(updatedAt).build());
        return item;
    }
}
