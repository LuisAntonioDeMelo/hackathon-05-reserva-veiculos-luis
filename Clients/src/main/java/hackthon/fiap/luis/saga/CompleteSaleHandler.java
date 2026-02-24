package hackthon.fiap.luis.saga;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import hackthon.fiap.luis.common.AwsClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class CompleteSaleHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final DynamoDbClient dynamoDbClient = AwsClientFactory.dynamoDb();
    private final String vehiclesTable = System.getenv("VEHICLES_TABLE");
    private final String salesTable = System.getenv("SALES_TABLE");
    private final String reservationsTable = System.getenv("RESERVATIONS_TABLE");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String saleId = String.valueOf(input.get("saleId"));
        String vehicleId = String.valueOf(input.get("vehicleId"));
        String clientId = readClientId(input);
        String reservationId = readReservationId(input);
        String now = Instant.now().toString();

        Map<String, String> vehicleNames = new HashMap<>();
        vehicleNames.put("#status", "status");
        vehicleNames.put("#saleId", "saleId");
        vehicleNames.put("#soldAt", "soldAt");
        vehicleNames.put("#updatedAt", "updatedAt");
        vehicleNames.put("#clientId", "clientId");

        Map<String, AttributeValue> vehicleValues = new HashMap<>();
        vehicleValues.put(":reserved", AttributeValue.builder().s("RESERVED").build());
        vehicleValues.put(":sold", AttributeValue.builder().s("SOLD").build());
        vehicleValues.put(":saleId", AttributeValue.builder().s(saleId).build());
        vehicleValues.put(":now", AttributeValue.builder().s(now).build());
        vehicleValues.put(":clientId", AttributeValue.builder().s(clientId).build());

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(vehiclesTable)
                .key(Map.of("vehicleId", AttributeValue.builder().s(vehicleId).build()))
                .conditionExpression("#status = :reserved AND #saleId = :saleId")
                .updateExpression("SET #status = :sold, #soldAt = :now, #updatedAt = :now, #clientId = :clientId")
                .expressionAttributeNames(vehicleNames)
                .expressionAttributeValues(vehicleValues)
                .build());

        Map<String, String> saleNames = new HashMap<>();
        saleNames.put("#status", "status");
        saleNames.put("#updatedAt", "updatedAt");
        saleNames.put("#completedAt", "completedAt");

        Map<String, AttributeValue> saleValues = new HashMap<>();
        saleValues.put(":status", AttributeValue.builder().s("COMPLETED").build());
        saleValues.put(":now", AttributeValue.builder().s(now).build());

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(salesTable)
                .key(Map.of("saleId", AttributeValue.builder().s(saleId).build()))
                .updateExpression("SET #status = :status, #updatedAt = :now, #completedAt = :now")
                .expressionAttributeNames(saleNames)
                .expressionAttributeValues(saleValues)
                .build());

        Map<String, String> reservationNames = new HashMap<>();
        reservationNames.put("#status", "status");
        reservationNames.put("#updatedAt", "updatedAt");
        reservationNames.put("#confirmedAt", "confirmedAt");

        Map<String, AttributeValue> reservationValues = new HashMap<>();
        reservationValues.put(":status", AttributeValue.builder().s("CONFIRMED").build());
        reservationValues.put(":reserved", AttributeValue.builder().s("RESERVED").build());
        reservationValues.put(":awaitingPayment", AttributeValue.builder().s("AWAITING_PAYMENT").build());
        reservationValues.put(":now", AttributeValue.builder().s(now).build());

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(reservationsTable)
                .key(Map.of("reservationId", AttributeValue.builder().s(reservationId).build()))
                .conditionExpression("#status = :reserved OR #status = :awaitingPayment")
                .updateExpression("SET #status = :status, #updatedAt = :now, #confirmedAt = :now")
                .expressionAttributeNames(reservationNames)
                .expressionAttributeValues(reservationValues)
                .build());

        input.put("status", "COMPLETED");
        input.put("clientId", clientId);
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

    private String readReservationId(Map<String, Object> input) {
        Object raw = input.get("reservationId");
        String reservationId = raw == null ? null : String.valueOf(raw);
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("reservationId is required");
        }
        return reservationId;
    }
}
