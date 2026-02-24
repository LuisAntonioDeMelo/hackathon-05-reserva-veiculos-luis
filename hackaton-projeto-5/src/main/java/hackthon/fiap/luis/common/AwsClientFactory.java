package hackthon.fiap.luis.common;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sfn.SfnClient;

import java.net.URI;

public final class AwsClientFactory {
    private AwsClientFactory() {
    }

    public static DynamoDbClient dynamoDb() {
        var builder = DynamoDbClient.builder().region(region());
        String endpoint = endpointOverride();
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint));
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    public static SfnClient stepFunctions() {
        var builder = SfnClient.builder().region(region());
        String endpoint = endpointOverride();
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint));
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    private static Region region() {
        String region = System.getenv("AWS_REGION");
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
        return Region.of(region);
    }

    private static String endpointOverride() {
        String endpoint = System.getenv("AWS_ENDPOINT_OVERRIDE");
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        return endpoint;
    }
}
