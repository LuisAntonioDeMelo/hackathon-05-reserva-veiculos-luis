package hackthon.fiap.luis.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import hackthon.fiap.luis.common.ApiResponse;
import hackthon.fiap.luis.common.AwsClientFactory;
import hackthon.fiap.luis.common.JsonUtils;
import hackthon.fiap.luis.models.Vehicle;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;
import java.util.UUID;

public class CreateVehicleHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final DynamoDbClient dynamoDbClient = AwsClientFactory.dynamoDb();
    private final String vehiclesTable = System.getenv("VEHICLES_TABLE");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            Map<String, Object> body = JsonUtils.parseBody(event);
            Vehicle vehicle = Vehicle.available(
                    UUID.randomUUID().toString(),
                    JsonUtils.requiredString(body, "brand"),
                    JsonUtils.requiredString(body, "model"),
                    JsonUtils.requiredInt(body, "year"),
                    JsonUtils.requiredString(body, "color"),
                    JsonUtils.requiredDouble(body, "price")
            );

            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(vehiclesTable)
                    .item(vehicle.toItem())
                    .build());

            return ApiResponse.created(Map.of(
                    "vehicleId", vehicle.vehicleId(),
                    "status", "AVAILABLE"
            ));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            context.getLogger().log("Create vehicle error: " + e.getMessage());
            return ApiResponse.serverError("Could not create vehicle");
        }
    }
}
