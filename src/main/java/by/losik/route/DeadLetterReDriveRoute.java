package by.losik.route;

import by.losik.config.AppConfig;
import by.losik.config.SqsConfig;
import by.losik.dto.MetadataDto;
import by.losik.dto.PayloadDto;
import by.losik.dto.SqsMessageDto;
import by.losik.exception.QueueMessageReceiveException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DeadLetterReDriveRoute extends RouteBuilder {

    private final AppConfig appConfig;
    private final SqsConfig sqsConfig;
    private final ProducerTemplate template;
    private final ObjectMapper objectMapper;
    private final SqsAsyncClient sqsAsyncClient;

    @Inject
    public DeadLetterReDriveRoute(
            AppConfig appConfig,
            SqsConfig sqsConfig,
            ProducerTemplate template,
            ObjectMapper objectMapper,
            SqsAsyncClient sqsAsyncClient) {
        this.appConfig = appConfig;
        this.sqsConfig = sqsConfig;
        this.template = template;
        this.objectMapper = objectMapper;
        this.sqsAsyncClient = sqsAsyncClient;
    }

    @Override
    public void configure() {
        from(String.format("timer:dlq-redrive?period=%d", appConfig.getRetryPeriodMs()))
                .routeId("dlq-redrive")
                .to("micrometer:timer:dlq.redrive.check?action=start")
                .log("Checking DLQ for messages to redrive")
                .process(exchange -> {
                    ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                            .queueUrl(sqsConfig.getDlqQueueName())
                            .maxNumberOfMessages(appConfig.getMaxMessagePerPoll())
                            .waitTimeSeconds(appConfig.getWaitTimeSeconds())
                            .messageAttributeNames("All")
                            .build();

                    sqsAsyncClient.receiveMessage(receiveRequest)
                            .thenAccept(response -> Optional.ofNullable(response.messages())
                                    .ifPresent(messages -> {
                                        if (messages.isEmpty()) {
                                            return;
                                        }
                                        Log.infof("Found %d messages in DLQ to redrive", messages.size());
                                        messages.forEach(message -> redriveMessage(exchange, message));
                                    }))
                            .exceptionally(error -> {
                                Log.error("Failed to receive messages from DLQ", error);
                                throw new QueueMessageReceiveException(error);
                            });
                })
                .onException(Exception.class)
                .handled(true)
                .process(exchange ->
                        log.error("DLQ redrive error: {}",
                                Optional.ofNullable(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
                                        .map(Exception::getMessage)
                                        .orElse("Unknown error"))
                )
                .to("micrometer:timer:dlq.redrive.check?action=stop");
    }

    private void redriveMessage(Exchange exchange, Message message) {
        try {
            String body = message.body();

            int retryCount = Optional.ofNullable(message.messageAttributes())
                    .map(attrs -> attrs.get("retry-count"))
                    .map(MessageAttributeValue::stringValue)
                    .map(Integer::parseInt)
                    .orElse(0);

            String originalMessageId = Optional.ofNullable(message.messageAttributes())
                    .map(attrs -> attrs.get("original-message-id"))
                    .map(MessageAttributeValue::stringValue)
                    .orElse(UUID.randomUUID().toString());

            String originalEventType = Optional.ofNullable(message.messageAttributes())
                    .map(attrs -> attrs.get("original-event-type"))
                    .map(MessageAttributeValue::stringValue)
                    .orElse("unknown");

            PayloadDto payload = objectMapper.readValue(body, PayloadDto.class);

            SqsMessageDto sqsMessage = new SqsMessageDto(
                    originalMessageId,
                    originalEventType,
                    System.currentTimeMillis(),
                    payload,
                    new MetadataDto("camel.dlq.redrive", "1.0", retryCount + 1)
            );

            exchange.getMessage().setBody(objectMapper.writeValueAsString(sqsMessage));
            template.send(String.format("aws2-sqs://%s", sqsConfig.getQueueName()), exchange);

            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(sqsConfig.getDlqQueueName())
                    .receiptHandle(message.receiptHandle())
                    .build();

            sqsAsyncClient.deleteMessage(deleteRequest)
                    .whenComplete((deleteResponse, deleteError) ->
                            Optional.ofNullable(deleteError)
                                    .ifPresentOrElse(
                                            e -> Log.error("Failed to delete message from DLQ", e),
                                            () -> Log.infof("Message redriven and deleted from DLQ: %s (retry count: %d)", originalMessageId, retryCount + 1)
                                    )
                    );

        } catch (Exception e) {
            Log.error("Failed to redrive message", e);
        }
    }
}