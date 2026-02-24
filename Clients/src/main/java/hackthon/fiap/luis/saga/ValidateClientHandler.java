package hackthon.fiap.luis.saga;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import hackthon.fiap.luis.common.AwsClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.util.Map;

public class ValidateClientHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final DynamoDbClient dynamoDbClient = AwsClientFactory.dynamoDb();
    private final String clientsTable = resolveClientsTable();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String clientId = readClientId(input);

        Map<String, AttributeValue> client = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(clientsTable)
                .key(Map.of("clientId", AttributeValue.builder().s(clientId).build()))
                .build()).item();

        if (client == null || client.isEmpty()) {
            throw new IllegalStateException("Client does not exist");
        }

        if (client.get("status") != null && "INACTIVE".equals(client.get("status").s())) {
            throw new IllegalStateException("Client is inactive");
        }

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
