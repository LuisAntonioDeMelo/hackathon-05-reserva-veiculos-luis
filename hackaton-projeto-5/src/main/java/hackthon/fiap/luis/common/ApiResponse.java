package hackthon.fiap.luis.common;

import java.util.Map;

public final class ApiResponse {
    private ApiResponse() {
    }

    public static Map<String, Object> ok(Object payload) {
        return build(200, payload);
    }

    public static Map<String, Object> created(Object payload) {
        return build(201, payload);
    }

    public static Map<String, Object> accepted(Object payload) {
        return build(202, payload);
    }

    public static Map<String, Object> badRequest(String message) {
        return build(400, Map.of("error", message));
    }

    public static Map<String, Object> notFound(String message) {
        return build(404, Map.of("error", message));
    }

    public static Map<String, Object> conflict(String message) {
        return build(409, Map.of("error", message));
    }

    public static Map<String, Object> serverError(String message) {
        return build(500, Map.of("error", message));
    }

    private static Map<String, Object> build(int statusCode, Object payload) {
        return Map.of(
                "statusCode", statusCode,
                "headers", Map.of(
                        "Content-Type", "application/json",
                        "Access-Control-Allow-Origin", "*"
                ),
                "body", JsonUtils.toJson(payload)
        );
    }
}
