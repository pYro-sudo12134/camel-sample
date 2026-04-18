package by.losik.config;

import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Getter
@Setter
public class AppConfig {
    private final String sqsQueueName;
    private final String eventBusName;
    private final long retryPeriodMs;
    private final int waitTimeSeconds;
    private final int maxRetryAttempts;
    private final int timeSpan;
    private final int retryQueryLimit;
    private final int currentConsumers;
    private final int maxMessagePerPoll;
    private final String dynamoTableName;
    private final String orderIdIndex;
    private final String statusCreatedAtIndex;
    private final String eventBusArn;
    private final String awsRegion;
    private final String awsAccountId;

    public AppConfig (
            @ConfigProperty(name = "aws.sqs.queue-name") String sqsQueueName,
            @ConfigProperty(name = "aws.eventbridge.bus-name", defaultValue = "default") String eventBusName,
            @ConfigProperty(name = "aws.eventbridge.bus-arn") String eventBusArn,
            @ConfigProperty(name = "aws.region") String awsRegion,
            @ConfigProperty(name = "aws.account-id") String awsAccountId,
            @ConfigProperty(name = "retry.period-ms", defaultValue = "300000") long retryPeriodMs,
            @ConfigProperty(name = "aws.sqs.poll.s", defaultValue = "10") int waitTimeSeconds,
            @ConfigProperty(name = "retry.max-attempts", defaultValue = "3") int maxRetryAttempts,
            @ConfigProperty(name = "dynamodb.table.query.span.s", defaultValue = "3600") int timeSpan,
            @ConfigProperty(name = "dynamodb.table.query.limit", defaultValue = "100") int retryQueryLimit,
            @ConfigProperty(name = "aws.sqs.consumer.amount", defaultValue = "5") int currentConsumers,
            @ConfigProperty(name = "aws.sqs.consumer.max-message-per-poll", defaultValue = "5") int maxMessagePerPoll,
            @ConfigProperty(name = "dynamodb.table.name", defaultValue = "events") String dynamoTableName,
            @ConfigProperty(name = "dynamodb.index.order-id", defaultValue = "order_id_index") String orderIdIndex,
            @ConfigProperty(name = "dynamodb.index.status-created-at", defaultValue = "status_created_at_index") String statusCreatedAtIndex
    ) {
        this.sqsQueueName = sqsQueueName;
        this.eventBusName = eventBusName;
        this.eventBusArn = eventBusArn;
        this.awsRegion = awsRegion;
        this.awsAccountId = awsAccountId;
        this.retryPeriodMs = retryPeriodMs;
        this.waitTimeSeconds = waitTimeSeconds;
        this.maxRetryAttempts = maxRetryAttempts;
        this.timeSpan = timeSpan;
        this.retryQueryLimit = retryQueryLimit;
        this.currentConsumers = currentConsumers;
        this.maxMessagePerPoll = maxMessagePerPoll;
        this.dynamoTableName = dynamoTableName;
        this.orderIdIndex = orderIdIndex;
        this.statusCreatedAtIndex = statusCreatedAtIndex;
    }
}