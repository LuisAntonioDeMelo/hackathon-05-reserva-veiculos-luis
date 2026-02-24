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

public class CancelSaleHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final DynamoDbClient dynamoDbClient = AwsClientFactory.dynamoDb();
    private final String vehiclesTable = System.getenv("VEHICLES_TABLE");
    private final String salesTable = System.getenv("SALES_TABLE");
    private final String reservationsTable = System.getenv("RESERVATIONS_TABLE");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String saleId = String.valueOf(input.get("saleId"));
        String vehicleId = String.valueOf(input.get("vehicleId"));
        String reservationId = readReservationId(input);
        String now = Instant.now().toString();
        String reason = "Saga compensation triggered";

        try {
            Map<String, String> vehicleNames = new HashMap<>();
            vehicleNames.put("#status", "status");
            vehicleNames.put("#saleId", "saleId");
            vehicleNames.put("#updatedAt", "updatedAt");

            Map<String, AttributeValue> vehicleValues = new HashMap<>();
            vehicleValues.put(":reserved", AttributeValue.builder().s("RESERVED").build());
            vehicleValues.put(":available", AttributeValue.builder().s("AVAILABLE").build());
            vehicleValues.put(":saleId", AttributeValue.builder().s(saleId).build());
            vehicleValues.put(":now", AttributeValue.builder().s(now).build());

            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(vehiclesTable)
                    .key(Map.of("vehicleId", AttributeValue.builder().s(vehicleId).build()))
                    .conditionExpression("#status = :reserved AND #saleId = :saleId")
                    .updateExpression("SET #status = :available, #updatedAt = :now REMOVE reservedAt, saleId, clientId")
                    .expressionAttributeNames(vehicleNames)
                    .expressionAttributeValues(vehicleValues)
                    .build());
        } catch (Exception ignored) {
            context.getLogger().log("Vehicle compensation skipped");
        }

        try {
            Map<String, String> reservationNames = new HashMap<>();
            reservationNames.put("#status", "status");
            reservationNames.put("#updatedAt", "updatedAt");
            reservationNames.put("#cancelledAt", "cancelledAt");
            reservationNames.put("#cancelReason", "cancelReason");

            Map<String, AttributeValue> reservationValues = new HashMap<>();
            reservationValues.put(":cancelled", AttributeValue.builder().s("CANCELLED").build());
            reservationValues.put(":reserved", AttributeValue.builder().s("RESERVED").build());
            reservationValues.put(":awaitingPayment", AttributeValue.builder().s("AWAITING_PAYMENT").build());
            reservationValues.put(":now", AttributeValue.builder().s(now).build());
            reservationValues.put(":reason", AttributeValue.builder().s(reason).build());

            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(reservationsTable)
                    .key(Map.of("reservationId", AttributeValue.builder().s(reservationId).build()))
                    .conditionExpression("#status = :reserved OR #status = :awaitingPayment")
                    .updateExpression("SET #status = :cancelled, #updatedAt = :now, #cancelledAt = :now, #cancelReason = :reason")
                    .expressionAttributeNames(reservationNames)
                    .expressionAttributeValues(reservationValues)
                    .build());
        } catch (Exception ignored) {
            context.getLogger().log("Reservation compensation skipped");
        }

        Map<String, String> saleNames = new HashMap<>();
        saleNames.put("#status", "status");
        saleNames.put("#updatedAt", "updatedAt");
        saleNames.put("#cancelReason", "cancelReason");

        Map<String, AttributeValue> saleValues = new HashMap<>();
        saleValues.put(":status", AttributeValue.builder().s("CANCELLED").build());
        saleValues.put(":now", AttributeValue.builder().s(now).build());
        saleValues.put(":reason", AttributeValue.builder().s(reason).build());

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(salesTable)
                .key(Map.of("saleId", AttributeValue.builder().s(saleId).build()))
                .updateExpression("SET #status = :status, #updatedAt = :now, #cancelReason = :reason")
                .expressionAttributeNames(saleNames)
                .expressionAttributeValues(saleValues)
                .build());

        input.put("status", "CANCELLED");
        return input;
    }

    private String readReservationId(Map<String, Object> input) {
        Object raw = input.get("reservationId");
        if (raw == null) {
            return null;
        }
        String reservationId = String.valueOf(raw);
        return reservationId.isBlank() ? null : reservationId;
    }
}
