package hackthon.fiap.luis.common;

import java.util.Map;

public final class HttpEventUtils {
    private HttpEventUtils() {
    }

    @SuppressWarnings("unchecked")
    public static String pathParam(Map<String, Object> event, String key) {
        Object raw = event.get("pathParameters");
        if (!(raw instanceof Map<?, ?> pathParams)) {
            return null;
        }
        Object value = ((Map<String, Object>) pathParams).get(key);
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        return str.isBlank() ? null : str;
    }
}
