package hackthon.fiap.luis.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import hackthon.fiap.luis.common.ApiResponse;
import hackthon.fiap.luis.common.AwsClientFactory;
import hackthon.fiap.luis.common.JsonUtils;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class PaymentCallbackHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final DynamoDbClient dynamoDbClient = AwsClientFactory.dynamoDb();
    private final String salesTable = System.getenv("SALES_TABLE");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            Map<String, Object> body = JsonUtils.parseBody(event);
            String saleId = JsonUtils.requiredString(body, "saleId");
            String paymentStatus = JsonUtils.requiredString(body, "paymentStatus").toUpperCase();
            String providerReference = JsonUtils.optionalString(body, "providerReference");

            if (!paymentStatus.equals("PAID") && !paymentStatus.equals("FAILED") && !paymentStatus.equals("CANCELLED")) {
                return ApiResponse.badRequest("paymentStatus must be PAID, FAILED or CANCELLED");
            }

            String internalStatus = paymentStatus.equals("PAID") ? "PAID" : "PAYMENT_FAILED";
            Map<String, String> names = new HashMap<>();
            names.put("#paymentStatus", "paymentStatus");
            names.put("#status", "status");
            names.put("#updatedAt", "updatedAt");
            names.put("#providerReference", "providerReference");

            Map<String, AttributeValue> values = new HashMap<>();
            values.put(":paymentStatus", AttributeValue.builder().s(paymentStatus).build());
            values.put(":status", AttributeValue.builder().s(internalStatus).build());
            values.put(":updatedAt", AttributeValue.builder().s(Instant.now().toString()).build());
            values.put(":providerReference", AttributeValue.builder().s(providerReference == null ? "N/A" : providerReference).build());

            String updateExpression = "SET #paymentStatus = :paymentStatus, #status = :status, #updatedAt = :updatedAt, #providerReference = :providerReference";
            if (paymentStatus.equals("PAID")) {
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

            return ApiResponse.ok(Map.of(
                    "saleId", saleId,
                    "paymentStatus", paymentStatus,
                    "status", internalStatus
            ));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            context.getLogger().log("Payment callback error: " + e.getMessage());
            return ApiResponse.serverError("Could not process payment callback");
        }
    }
}
