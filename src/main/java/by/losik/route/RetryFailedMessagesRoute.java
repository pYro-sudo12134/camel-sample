package by.losik.route;

import by.losik.config.AppConfig;
import by.losik.entity.EventMessageEntity;
import by.losik.repository.EventMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class RetryFailedMessagesRoute extends RouteBuilder {

    private final AppConfig config;
    private final EventMessageRepository repository;
    private final ObjectMapper objectMapper;
    private final EventBridgeAsyncClient eventBridgeClient;

    @Inject
    public RetryFailedMessagesRoute(
            AppConfig appConfig,
            EventMessageRepository repository,
            ObjectMapper objectMapper,
            EventBridgeAsyncClient eventBridgeClient) {
        this.config = appConfig;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.eventBridgeClient = eventBridgeClient;
    }

    @Override
    public void configure() {
        from(String.format("timer:retry?period=%d", config.getRetryPeriodMs()))
                .routeId("retry-failed-messages")
                .to("micrometer:timer:sqs.receive?action=start")
                .log("Checking for failed messages to retry")
                .process(exchange -> retryFailedMessages())
                .onException(Exception.class)
                .handled(true)
                .process(exchange ->
                        log.error("Retry route error: {}",
                                Optional.ofNullable(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
                                        .map(Exception::getMessage)
                                        .orElse("Unknown error"))
                )
                .to("micrometer:timer:sqs.processing?action=stop");
    }

    private void retryFailedMessages() {
        repository.findFailedMessagesForRetry(
                        config.getMaxRetryAttempts(),
                        config.getRetryQueryLimit(),
                        config.getTimeSpan())
                .subscribe().with(
                        messages -> {
                            log.info("Found {} failed messages to retry", messages.size());
                            messages.forEach(this::retryMessage);
                        },
                        error -> log.error("Failed to fetch failed messages: {}", error.getMessage())
                );
    }

    private void retryMessage(EventMessageEntity entity) {
        log.info("Retrying message: {}", entity.getMessageId());

        createEventBridgeRequest(entity)
                .subscribe().with(
                        entry -> {
                            PutEventsRequest request = PutEventsRequest.builder()
                                    .entries(entry)
                                    .build();

                            eventBridgeClient.putEvents(request)
                                    .whenComplete((response, error) ->
                                            Optional.ofNullable(error)
                                                    .ifPresentOrElse(
                                                            e -> {
                                                                log.error("EventBridge failed: {}", e.getMessage());
                                                                handleRetryFailure(entity, e.getMessage());
                                                            },
                                                            () -> {
                                                                log.info("EventBridge success: {}", entity.getMessageId());
                                                                handleRetrySuccess(entity);
                                                            }
                                                    ));
                        },
                        error -> handleRetryFailure(entity, error.getMessage())
                );
    }

    private void handleRetrySuccess(EventMessageEntity entity) {
        log.info("Retry successful: {}", entity.getMessageId());
        repository.markAsProcessed(entity.getMessageId(), entity.getEventType())
                .subscribe().with(
                        success -> {
                        },
                        error -> log.error("Failed to update after retry: {}", error.getMessage())
                );
    }

    private void handleRetryFailure(EventMessageEntity entity, String errorMessage) {
        log.error("Retry failed for {}: {}", entity.getMessageId(), errorMessage);
        repository.markAsFailed(entity.getMessageId(), entity.getEventType(), errorMessage)
                .subscribe().with(
                        success -> {
                        },
                        error -> log.error("Failed to update error status: {}", error.getMessage())
                );
    }

    private Uni<PutEventsRequestEntry> createEventBridgeRequest(EventMessageEntity entity) {
        return Uni.createFrom().item(Unchecked.supplier(() -> {
            Object detail = objectMapper.readValue(entity.getPayload(), Object.class);
            String detailJson = objectMapper.writeValueAsString(detail);

            return PutEventsRequestEntry.builder()
                    .source("camel.retry")
                    .detailType(entity.getEventType())
                    .detail(detailJson)
                    .time(Instant.now())
                    .eventBusName(config.getEventBusName())
                    .build();
        }));
    }
}