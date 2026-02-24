package hackthon.fiap.luis.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import hackthon.fiap.luis.common.ApiResponse;
import hackthon.fiap.luis.common.AwsClientFactory;
import hackthon.fiap.luis.common.JsonUtils;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StartPurchaseSagaHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final SfnClient sfnClient = AwsClientFactory.stepFunctions();
    private final String stateMachineArn = System.getenv("STATE_MACHINE_ARN");

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            Map<String, Object> body = JsonUtils.parseBody(event);
            String vehicleId = JsonUtils.requiredString(body, "vehicleId");
            String clientId = JsonUtils.optionalString(body, "clientId");
            if (clientId == null) {
                clientId = JsonUtils.optionalString(body, "buyerId");
            }
            if (clientId == null) {
                throw new IllegalArgumentException("Field 'clientId' is required");
            }
            boolean customerCancelled = JsonUtils.optionalBoolean(body, "customerCancelled", false);
            int reservationTtlMinutes = readInt(body.get("reservationTtlMinutes"), 15);
            int maxPaymentChecks = readInt(body.get("maxPaymentChecks"), 6);

            String saleId = UUID.randomUUID().toString();
            Map<String, Object> input = new HashMap<>();
            input.put("saleId", saleId);
            input.put("vehicleId", vehicleId);
            input.put("clientId", clientId);
            input.put("customerCancelled", customerCancelled);
            input.put("reservationTtlMinutes", reservationTtlMinutes);
            input.put("maxPaymentChecks", maxPaymentChecks);
            input.put("paymentCheckAttempt", 0);
            input.put("requestedAt", Instant.now().toString());
            if (body.containsKey("paymentApproved")) {
                input.put("paymentApproved", JsonUtils.optionalBoolean(body, "paymentApproved", false));
            }

            String executionArn = sfnClient.startExecution(StartExecutionRequest.builder()
                            .stateMachineArn(stateMachineArn)
                            .name("sale-" + saleId.replace("-", ""))
                            .input(JsonUtils.toJson(input))
                            .build())
                    .executionArn();

            return ApiResponse.accepted(Map.of(
                    "saleId", saleId,
                    "executionArn", executionArn,
                    "message", "Purchase flow started"
            ));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            context.getLogger().log("Start purchase error: " + e.getMessage());
            return ApiResponse.serverError("Could not start purchase flow");
        }
    }

    private int readInt(Object raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(raw));
            if (parsed < 1) {
                return defaultValue;
            }
            return parsed;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
