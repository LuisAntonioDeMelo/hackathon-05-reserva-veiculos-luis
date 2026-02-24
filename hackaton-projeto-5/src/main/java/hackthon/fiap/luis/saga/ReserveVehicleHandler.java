package hackthon.fiap.luis.saga;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import hackthon.fiap.luis.common.AwsClientFactory;
import hackthon.fiap.luis.models.Reservation;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReserveVehicleHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final DynamoDbClient dynamoDbClient = AwsClientFactory.dynamoDb();
    private final String vehiclesTable = System.getenv("VEHICLES_TABLE");
    private final String reservationsTable = System.getenv("RESERVATIONS_TABLE");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String vehicleId = String.valueOf(input.get("vehicleId"));
        String clientId = readClientId(input);
        String saleId = String.valueOf(input.get("saleId"));
        int ttlMinutes = readTtlMinutes(input);
        String reservationId = "res-" + UUID.randomUUID();

        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("vehicleId is required");
        }

        Map<String, String> names = new HashMap<>();
        names.put("#status", "status");
        names.put("#updatedAt", "updatedAt");
        names.put("#reservedAt", "reservedAt");
        names.put("#saleId", "saleId");
        names.put("#clientId", "clientId");
        names.put("#reservationId", "reservationId");

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":available", AttributeValue.builder().s("AVAILABLE").build());
        values.put(":reserved", AttributeValue.builder().s("RESERVED").build());
        values.put(":updatedAt", AttributeValue.builder().s(Instant.now().toString()).build());
        values.put(":reservedAt", AttributeValue.builder().s(Instant.now().toString()).build());
        values.put(":saleId", AttributeValue.builder().s(saleId).build());
        values.put(":clientId", AttributeValue.builder().s(clientId).build());
        values.put(":reservationId", AttributeValue.builder().s(reservationId).build());

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(vehiclesTable)
                .key(Map.of("vehicleId", AttributeValue.builder().s(vehicleId).build()))
                .conditionExpression("#status = :available")
                .updateExpression("SET #status = :reserved, #updatedAt = :updatedAt, #reservedAt = :reservedAt, #saleId = :saleId, #clientId = :clientId, #reservationId = :reservationId")
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build());

        Reservation reservation = Reservation.reserved(reservationId, saleId, vehicleId, clientId, ttlMinutes);
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(reservationsTable)
                .item(reservation.toItem())
                .conditionExpression("attribute_not_exists(reservationId)")
                .build());

        input.put("clientId", clientId);
        input.put("reservationId", reservationId);
        input.put("reservationStatus", "RESERVED");
        return input;
    }

    private String readClientId(Map<String, Object> input) {
        Object raw = input.get("clientId");
        if (raw == null) {
            raw = input.get("buyerId");
        }
        String clientId = raw == null ? null : String.valueOf(raw);
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId is required");
        }
        return clientId;
    }

    private int readTtlMinutes(Map<String, Object> input) {
        Object raw = input.get("reservationTtlMinutes");
        if (raw == null) {
            return 15;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(raw));
            if (parsed < 1 || parsed > 120) {
                return 15;
            }
            return parsed;
        } catch (NumberFormatException e) {
            return 15;
        }
    }
}
