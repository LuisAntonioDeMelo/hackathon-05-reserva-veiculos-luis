package hackthon.fiap.luis.saga;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import hackthon.fiap.luis.common.AwsClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class CheckPaymentStatusHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private static final int DEFAULT_MAX_CHECKS = 6;

    private final DynamoDbClient dynamoDbClient = AwsClientFactory.dynamoDb();
    private final String salesTable = System.getenv("SALES_TABLE");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String saleId = String.valueOf(input.get("saleId"));
        int attempt = readInt(input.get("paymentCheckAttempt"), 0) + 1;
        int maxChecks = readInt(input.get("maxPaymentChecks"), DEFAULT_MAX_CHECKS);
        Boolean mockApproved = readNullableBoolean(input.get("paymentApproved"));

        Map<String, AttributeValue> saleItem = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(salesTable)
                .key(Map.of("saleId", AttributeValue.builder().s(saleId).build()))
                .build()).item();
        if (saleItem == null || saleItem.isEmpty()) {
            throw new IllegalStateException("Sale not found");
        }

        String currentPaymentStatus = saleItem.getOrDefault("paymentStatus", AttributeValue.builder().s("PENDING").build()).s();
        if ("PENDING".equals(currentPaymentStatus)) {
            if (Boolean.TRUE.equals(mockApproved)) {
                updateSaleStatus(saleId, "PAID", "PAID");
                currentPaymentStatus = "PAID";
            } else if (Boolean.FALSE.equals(mockApproved) || attempt >= maxChecks) {
                updateSaleStatus(saleId, "FAILED", "PAYMENT_TIMEOUT");
                currentPaymentStatus = "FAILED";
            }
        }

        boolean shouldRetry = "PENDING".equals(currentPaymentStatus) && attempt < maxChecks;
        input.put("paymentCheckAttempt", attempt);
        input.put("paymentStatus", currentPaymentStatus);
        input.put("shouldRetry", shouldRetry);
        return input;
    }

    private void updateSaleStatus(String saleId, String paymentStatus, String status) {
        Map<String, String> names = new HashMap<>();
        names.put("#paymentStatus", "paymentStatus");
        names.put("#status", "status");
        names.put("#updatedAt", "updatedAt");

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":paymentStatus", AttributeValue.builder().s(paymentStatus).build());
        values.put(":status", AttributeValue.builder().s(status).build());
        values.put(":updatedAt", AttributeValue.builder().s(Instant.now().toString()).build());

        String updateExpression = "SET #paymentStatus = :paymentStatus, #status = :status, #updatedAt = :updatedAt";
        if ("PAID".equals(paymentStatus)) {
            names.put("#paidAt", "paidAt");
            values.put(":paidAt", AttributeValue.builder().s(Instant.now().toString()).build());
            updateExpression += ", #paidAt = :paidAt";
        }

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(salesTable)
                .key(Map.of("saleId", AttributeValue.builder().s(saleId).build()))
                .updateExpression(updateExpression)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build());
    }

    private int readInt(Object raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Boolean readNullableBoolean(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }
}
