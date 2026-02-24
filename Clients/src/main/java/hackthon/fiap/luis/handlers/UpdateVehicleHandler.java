package hackthon.fiap.luis.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import hackthon.fiap.luis.common.ApiResponse;
import hackthon.fiap.luis.common.AwsClientFactory;
import hackthon.fiap.luis.common.HttpEventUtils;
import hackthon.fiap.luis.common.JsonUtils;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class UpdateVehicleHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final DynamoDbClient dynamoDbClient = AwsClientFactory.dynamoDb();
    private final String vehiclesTable = System.getenv("VEHICLES_TABLE");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            String vehicleId = HttpEventUtils.pathParam(event, "vehicleId");
            if (vehicleId == null) {
                return ApiResponse.badRequest("Path parameter 'vehicleId' is required");
            }

            Map<String, AttributeValue> existing = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(vehiclesTable)
                    .key(Map.of("vehicleId", AttributeValue.builder().s(vehicleId).build()))
                    .build()).item();
            if (existing == null || existing.isEmpty()) {
                return ApiResponse.notFound("Vehicle not found");
            }

            String status = existing.get("status").s();
            if ("SOLD".equals(status)) {
                return ApiResponse.conflict("Sold vehicles cannot be edited");
            }

            Map<String, Object> body = JsonUtils.parseBody(event);
            StringBuilder expression = new StringBuilder("SET updatedAt = :updatedAt");
            Map<String, String> names = new HashMap<>();
            Map<String, AttributeValue> values = new HashMap<>();
            values.put(":updatedAt", AttributeValue.builder().s(Instant.now().toString()).build());

            addStringField(body, "brand", expression, names, values);
            addStringField(body, "model", expression, names, values);
            addStringField(body, "color", expression, names, values);
            addNumberField(body, "year", expression, names, values, true);
            addNumberField(body, "price", expression, names, values, false);

            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(vehiclesTable)
                    .key(Map.of("vehicleId", AttributeValue.builder().s(vehicleId).build()))
                    .updateExpression(expression.toString())
                    .expressionAttributeNames(names)
                    .expressionAttributeValues(values)
                    .build());

            return ApiResponse.ok(Map.of("vehicleId", vehicleId, "message", "Vehicle updated"));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            context.getLogger().log("Update vehicle error: " + e.getMessage());
            return ApiResponse.serverError("Could not update vehicle");
        }
    }

    private void addStringField(
            Map<String, Object> body,
            String field,
            StringBuilder expression,
            Map<String, String> names,
            Map<String, AttributeValue> values
    ) {
        String value = JsonUtils.optionalString(body, field);
        if (value == null) {
            return;
        }
        String nameToken = "#" + field;
        String valueToken = ":" + field;
        expression.append(", ").append(nameToken).append(" = ").append(valueToken);
        names.put(nameToken, field);
        values.put(valueToken, AttributeValue.builder().s(value).build());
    }

    private void addNumberField(
            Map<String, Object> body,
            String field,
            StringBuilder expression,
            Map<String, String> names,
            Map<String, AttributeValue> values,
            boolean integer
    ) {
        Object raw = body.get(field);
        if (raw == null) {
            return;
        }
        String numberValue;
        try {
            numberValue = integer
                    ? String.valueOf(Integer.parseInt(String.valueOf(raw)))
                    : String.valueOf(Double.parseDouble(String.valueOf(raw)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Field '" + field + "' must be numeric");
        }
        String nameToken = "#" + field;
        String valueToken = ":" + field;
        expression.append(", ").append(nameToken).append(" = ").append(valueToken);
        names.put(nameToken, field);
        values.put(valueToken, AttributeValue.builder().n(numberValue).build());
    }
}
