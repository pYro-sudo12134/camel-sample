package by.losik.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;

import java.net.URI;
import java.util.Optional;

@ApplicationScoped
public class EventBridgeConfig {

    private final String region;
    private final Optional<String> endpointOverride;
    private final Optional<String> accessKey;
    private final Optional<String> secretKey;

    public EventBridgeConfig(
            @ConfigProperty(name = "aws.region", defaultValue = "us-east-1") String region,
            @ConfigProperty(name = "aws.eventbridge.endpoint.override") Optional<String> endpointOverride,
            @ConfigProperty(name = "aws.access.key") Optional<String> accessKey,
            @ConfigProperty(name = "aws.secret.key") Optional<String> secretKey
    ) {
        this.region = region;
        this.endpointOverride = endpointOverride;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Produces
    @ApplicationScoped
    public EventBridgeAsyncClient eventBridgeAsyncClient() {
        var builder = EventBridgeAsyncClient.builder()
                .region(Region.of(region));

        endpointOverride.filter(e -> !e.isBlank())
                .map(URI::create)
                .ifPresent(builder::endpointOverride);

        if (accessKey.isPresent() && secretKey.isPresent() &&
                !accessKey.get().isBlank() && !secretKey.get().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey.get(), secretKey.get())
            ));
        }

        return builder.build();
    }
}