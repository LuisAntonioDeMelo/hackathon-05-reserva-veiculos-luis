package hackthon.fiap.luis.common;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

public final class DynamoItemMapper {
    private DynamoItemMapper() {
    }

    public static Map<String, Object> fromItem(Map<String, AttributeValue> item) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, AttributeValue> entry : item.entrySet()) {
            var value = entry.getValue();
            if (value.s() != null) {
                result.put(entry.getKey(), value.s());
            } else if (value.n() != null) {
                String n = value.n();
                if (n.contains(".")) {
                    result.put(entry.getKey(), Double.parseDouble(n));
                } else {
                    try {
                        result.put(entry.getKey(), Integer.parseInt(n));
                    } catch (NumberFormatException ex) {
                        result.put(entry.getKey(), Long.parseLong(n));
                    }
                }
            } else if (value.bool() != null) {
                result.put(entry.getKey(), value.bool());
            }
        }
        return result;
    }
}
