package hackthon.fiap.luis.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private JsonUtils() {
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialize payload", e);
        }
    }

    public static Map<String, Object> parseBody(Map<String, Object> event) {
        Object body = event.get("body");
        if (body == null) {
            return new HashMap<>();
        }
        if (body instanceof Map<?, ?> mapBody) {
            Map<String, Object> normalized = new HashMap<>();
            mapBody.forEach((k, v) -> normalized.put(String.valueOf(k), v));
            return normalized;
        }
        String json = String.valueOf(body);
        if (json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed JSON body", e);
        }
    }

    public static String requiredString(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new IllegalArgumentException("Field '" + key + "' is required");
        }
        return String.valueOf(raw).trim();
    }

    public static String optionalString(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isBlank() ? null : value;
    }

    public static int requiredInt(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (raw == null) {
            throw new IllegalArgumentException("Field '" + key + "' is required");
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Field '" + key + "' must be an integer");
        }
    }

    public static double requiredDouble(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (raw == null) {
            throw new IllegalArgumentException("Field '" + key + "' is required");
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Field '" + key + "' must be a number");
        }
    }

    public static boolean optionalBoolean(Map<String, Object> body, String key, boolean defaultValue) {
        Object raw = body.get(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }
}
