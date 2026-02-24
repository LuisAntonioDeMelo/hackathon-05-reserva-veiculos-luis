package hackthon.fiap.luis.saga;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import hackthon.fiap.luis.common.AwsClientFactory;
import hackthon.fiap.luis.models.Sale;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class GeneratePaymentCodeHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final DynamoDbClient dynamoDbClient = AwsClientFactory.dynamoDb();
    private final String vehiclesTable = System.getenv("VEHICLES_TABLE");
    private final String salesTable = System.getenv("SALES_TABLE");
    private final String reservationsTable = System.getenv("RESERVATIONS_TABLE");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String saleId = String.valueOf(input.get("saleId"));
        String reservationId = String.valueOf(input.get("reservationId"));
        String vehicleId = String.valueOf(input.get("vehicleId"));
        String clientId = readClientId(input);

        Map<String, AttributeValue> vehicle = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(vehiclesTable)
                .key(Map.of("vehicleId", AttributeValue.builder().s(vehicleId).build()))
                .build()).item();
        if (vehicle == null || vehicle.isEmpty()) {
            throw new IllegalStateException("Vehicle not found");
        }

        String price = vehicle.get("price").n();
        String paymentCode = "PAY-" + saleId.replace("-", "").substring(0, 10).toUpperCase();
        Sale sale = Sale.reserved(
                saleId,
                reservationId,
                vehicleId,
                clientId,
                paymentCode,
                Double.parseDouble(price)
        );

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(salesTable)
                .item(sale.toItem())
                .build());

        Map<String, String> reservationNames = new HashMap<>();
        reservationNames.put("#status", "status");
        reservationNames.put("#updatedAt", "updatedAt");
        reservationNames.put("#paymentCode", "paymentCode");

        Map<String, AttributeValue> reservationValues = new HashMap<>();
        reservationValues.put(":reserved", AttributeValue.builder().s("RESERVED").build());
        reservationValues.put(":awaitingPayment", AttributeValue.builder().s("AWAITING_PAYMENT").build());
        reservationValues.put(":updatedAt", AttributeValue.builder().s(Instant.now().toString()).build());
        reservationValues.put(":paymentCode", AttributeValue.builder().s(paymentCode).build());

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(reservationsTable)
                .key(Map.of("reservationId", AttributeValue.builder().s(reservationId).build()))
                .conditionExpression("#status = :reserved")
                .updateExpression("SET #status = :awaitingPayment, #updatedAt = :updatedAt, #paymentCode = :paymentCode")
                .expressionAttributeNames(reservationNames)
                .expressionAttributeValues(reservationValues)
                .build());

        input.put("clientId", clientId);
        input.put("paymentCode", paymentCode);
        input.put("paymentStatus", "PENDING");
        input.put("totalPrice", Double.parseDouble(price));
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
}
