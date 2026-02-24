package hackthon.fiap.luis.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import hackthon.fiap.luis.common.ApiResponse;
import hackthon.fiap.luis.common.AwsClientFactory;
import hackthon.fiap.luis.common.JsonUtils;
import hackthon.fiap.luis.models.Client;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;
import java.util.UUID;

public class CreateClientHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final DynamoDbClient dynamoDbClient = AwsClientFactory.dynamoDb();
    private final String clientsTable = resolveClientsTable();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            Map<String, Object> body = JsonUtils.parseBody(event);
            Client client = Client.newActive(
                    UUID.randomUUID().toString(),
                    JsonUtils.requiredString(body, "fullName"),
                    JsonUtils.requiredString(body, "email"),
                    JsonUtils.requiredString(body, "documentNumber"),
                    JsonUtils.requiredString(body, "paymentKey"),
                    JsonUtils.requiredString(body, "address")
            );

            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(clientsTable)
                    .item(client.toItem())
                    .build());

            return ApiResponse.created(client.toSummary());
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            context.getLogger().log("Create client error: " + e.getMessage());
            return ApiResponse.serverError("Could not create client");
        }
    }

    private String resolveClientsTable() {
        String clients = System.getenv("CLIENTS_TABLE");
        if (clients != null && !clients.isBlank()) {
            return clients;
        }
        String buyers = System.getenv("BUYERS_TABLE");
        if (buyers != null && !buyers.isBlank()) {
            return buyers;
        }
        throw new IllegalStateException("CLIENTS_TABLE is not configured");
    }
}
