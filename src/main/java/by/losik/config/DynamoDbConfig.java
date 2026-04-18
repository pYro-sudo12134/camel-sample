package by.losik.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.net.URI;
import java.util.Optional;

@ApplicationScoped
public class DynamoDbConfig {

    private final String region;
    private final Optional<String> endpointOverride;
    private final Optional<String> accessKey;
    private final Optional<String> secretKey;

    public DynamoDbConfig(
            @ConfigProperty(name = "dynamodb.region", defaultValue = "us-east-1") String region,
            @ConfigProperty(name = "dynamodb.endpoint.override") Optional<String> endpointOverride,
            @ConfigProperty(name = "dynamodb.access.key") Optional<String> accessKey,
            @ConfigProperty(name = "dynamodb.secret.key") Optional<String> secretKey
    ) {
        this.region = region;
        this.endpointOverride = endpointOverride;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Produces
    @ApplicationScoped
    public DynamoDbAsyncClient dynamoDbAsyncClient() {
        var builder = DynamoDbAsyncClient.builder()
                .region(Region.of(region));

        endpointOverride
                .filter(e -> !e.isBlank())
                .map(URI::create)
                .ifPresent(builder::endpointOverride);

        if (accessKey.isPresent() && secretKey.isPresent() &&
                !accessKey.get().isBlank() && !secretKey.get().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey.get(), secretKey.get())
            ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    @Produces
    @ApplicationScoped
    public DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient(DynamoDbAsyncClient dynamoDbAsyncClient) {
        return DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(dynamoDbAsyncClient)
                .build();
    }
}