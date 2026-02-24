package hackthon.fiap.luis.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import hackthon.fiap.luis.common.ApiResponse;
import hackthon.fiap.luis.common.AwsClientFactory;
import hackthon.fiap.luis.common.DynamoItemMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.util.List;
import java.util.Map;

public class ListSoldVehiclesHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final DynamoDbClient dynamoDbClient = AwsClientFactory.dynamoDb();
    private final String vehiclesTable = System.getenv("VEHICLES_TABLE");
    private final String statusPriceIndex = System.getenv("STATUS_PRICE_INDEX");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            List<Map<String, Object>> vehicles = dynamoDbClient.query(QueryRequest.builder()
                            .tableName(vehiclesTable)
                            .indexName(statusPriceIndex)
                            .keyConditionExpression("#status = :status")
                            .expressionAttributeNames(Map.of("#status", "status"))
                            .expressionAttributeValues(Map.of(":status", AttributeValue.builder().s("SOLD").build()))
                            .scanIndexForward(true)
                            .build())
                    .items()
                    .stream()
                    .map(DynamoItemMapper::fromItem)
                    .toList();

            return ApiResponse.ok(Map.of("items", vehicles, "count", vehicles.size()));
        } catch (Exception e) {
            context.getLogger().log("List sold error: " + e.getMessage());
            return ApiResponse.serverError("Could not list sold vehicles");
        }
    }
}
