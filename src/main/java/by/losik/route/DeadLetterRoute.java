package by.losik.route;

import by.losik.config.AppConfig;
import by.losik.config.SqsConfig;
import by.losik.entity.EventMessageEntity;
import by.losik.repository.EventMessageRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class DeadLetterRoute extends RouteBuilder {

    private final AppConfig appConfig;
    private final SqsConfig sqsConfig;
    private final EventMessageRepository repository;
    private final ProducerTemplate template;

    @Inject
    public DeadLetterRoute(
            AppConfig appConfig,
            SqsConfig sqsConfig,
            EventMessageRepository repository,
            ProducerTemplate template) {
        this.appConfig = appConfig;
        this.sqsConfig = sqsConfig;
        this.repository = repository;
        this.template = template;
    }

    @Override
    public void configure() {
        from(String.format("timer:deadletter?period=%d", appConfig.getRetryPeriodMs()))
                .routeId("dead-letter-processor")
                .to("micrometer:timer:dlq.check?action=start")
                .log("Checking for messages that exceeded retry limit")
                .process(exchange -> {
                    repository.findMessagesExceedingRetryLimit(
                            appConfig.getMaxRetryAttempts(),
                            appConfig.getRetryQueryLimit(),
                            appConfig.getDlqTimeSpan()
                    ).subscribe().with(
                            messages -> Optional.ofNullable(messages)
                                    .filter(m -> !m.isEmpty())
                                    .ifPresent(m -> {
                                        Log.infof("Found %d messages to send to DLQ", m.size());
                                        m.forEach(entity -> sendToDeadLetterQueue(exchange, entity));
                                    }),
                            error -> Log.error("Failed to fetch messages for DLQ", error)
                    );
                })
                .onException(Exception.class)
                .handled(true)
                .process(exchange ->
                        log.error("DLQ route error: {}",
                                Optional.ofNullable(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
                                        .map(Exception::getMessage)
                                        .orElse("Unknown error"))
                )
                .to("micrometer:timer:dlq.check?action=stop");
    }

    private void sendToDeadLetterQueue(Exchange exchange, EventMessageEntity entity) {
        try {
            Map<String, MessageAttributeValue> attributes = new HashMap<>();
            attributes.put("original-message-id", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(entity.getMessageId())
                    .build());
            attributes.put("original-event-type", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(entity.getEventType())
                    .build());
            attributes.put("retry-count", MessageAttributeValue.builder()
                    .dataType("Number")
                    .stringValue(String.valueOf(entity.getRetryCount()))
                    .build());

            Optional.ofNullable(entity.getErrorMessage())
                    .ifPresent(errorMsg -> attributes.put("error-message", MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(errorMsg)
                            .build()));

            exchange.getMessage().setBody(entity.getPayload());
            exchange.getMessage().setHeader(Sqs2Constants.MESSAGE_ATTRIBUTES, attributes);

            template.send(String.format("aws2-sqs://%s", sqsConfig.getDlqQueueName()), exchange);

            log.info("Message sent to DLQ: {}", entity.getMessageId());

            repository.delete(entity.getMessageId(), entity.getEventType())
                    .subscribe().with(
                            done -> log.info("Deleted from main table: {}", entity.getMessageId()),
                            error -> log.error("Failed to delete from main table", error)
                    );

        } catch (Exception e) {
            log.error("Failed to send message to DLQ", e);
        }
    }
}