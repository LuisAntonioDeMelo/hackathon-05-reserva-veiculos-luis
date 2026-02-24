package hackthon.fiap.luis.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import hackthon.fiap.luis.common.ApiResponse;
import hackthon.fiap.luis.common.AwsClientFactory;
import hackthon.fiap.luis.common.DynamoItemMapper;
import hackthon.fiap.luis.common.HttpEventUtils;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.util.Map;

public class GetReservationHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final DynamoDbClient dynamoDbClient = AwsClientFactory.dynamoDb();
    private final String reservationsTable = System.getenv("RESERVATIONS_TABLE");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            String reservationId = HttpEventUtils.pathParam(event, "reservationId");
            if (reservationId == null) {
                return ApiResponse.badRequest("Path parameter 'reservationId' is required");
            }

            Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(reservationsTable)
                    .key(Map.of("reservationId", AttributeValue.builder().s(reservationId).build()))
                    .build()).item();

            if (item == null || item.isEmpty()) {
                return ApiResponse.notFound("Reservation not found");
            }
            return ApiResponse.ok(DynamoItemMapper.fromItem(item));
        } catch (Exception e) {
            context.getLogger().log("Get reservation error: " + e.getMessage());
            return ApiResponse.serverError("Could not load reservation");
        }
    }
}
