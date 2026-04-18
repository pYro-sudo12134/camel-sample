package by.losik.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;
import java.util.Optional;

@ApplicationScoped
public class SqsConfig {

    private final String region;
    private final Optional<String> endpointOverride;
    private final Optional<String> accessKey;
    private final Optional<String> secretKey;
    private final String queueName;

    public SqsConfig(
            @ConfigProperty(name = "sqs.region", defaultValue = "us-east-1") String region,
            @ConfigProperty(name = "sqs.endpoint.override") Optional<String> endpointOverride,
            @ConfigProperty(name = "sqs.access.key") Optional<String> accessKey,
            @ConfigProperty(name = "sqs.secret.key") Optional<String> secretKey,
            @ConfigProperty(name = "sqs.queue.name") String queueName
    ) {
        this.region = region;
        this.endpointOverride = endpointOverride;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.queueName = queueName;
    }

    @Produces
    @ApplicationScoped
    public SqsAsyncClient sqsAsyncClient() {
        var builder = SqsAsyncClient.builder()
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

    public String getQueueName() {
        return queueName;
    }
}