package by.losik.route;

import by.losik.config.AppConfig;
import by.losik.config.SqsConfig;
import by.losik.dto.MetadataDto;
import by.losik.dto.SqsMessageDto;
import by.losik.entity.EventMessageEntity;
import by.losik.exception.SaveFailedException;
import by.losik.mapper.EventMapper;
import by.losik.repository.EventMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class SqsToEventBridgeRoute extends RouteBuilder {

    private final AppConfig appConfig;
    private final SqsConfig sqsConfig;
    private final EventMessageRepository repository;
    private final EventMapper mapper;
    private final ObjectMapper objectMapper;
    private final EventBridgeAsyncClient eventBridgeClient;

    @Inject
    public SqsToEventBridgeRoute(
            AppConfig appConfig,
            SqsConfig sqsConfig,
            EventMessageRepository repository,
            @Named("customEventMapper") EventMapper mapper,
            ObjectMapper objectMapper,
            EventBridgeAsyncClient eventBridgeClient) {
        this.appConfig = appConfig;
        this.sqsConfig = sqsConfig;
        this.repository = repository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.eventBridgeClient = eventBridgeClient;
    }

    @Override
    public void configure() {
        from(String.format("aws2-sqs://%s?concurrentConsumers=%d&maxMessagesPerPoll=%d&waitTimeSeconds=%d",
                sqsConfig.getQueueName(),
                appConfig.getCurrentConsumers(),
                appConfig.getMaxMessagePerPoll(),
                appConfig.getWaitTimeSeconds()))
                .routeId("sqs-to-eventbridge")
                .to("micrometer:timer:sqs.retry?action=start")
                .log("Received: ${body}")
                .unmarshal().json(SqsMessageDto.class)
                .process(exchange -> {
                    SqsMessageDto sqsDto = exchange.getIn().getBody(SqsMessageDto.class);

                    createEntity(sqsDto)
                            .flatMap(repository::save)
                            .subscribe().with(
                                    saved -> sendToEventBridge(saved, sqsDto),
                                    error -> {
                                        log.error("Failed to save: {}", error.getMessage());
                                        exchange.setException(new SaveFailedException(error));
                                    }
                            );
                })
                .onException(SaveFailedException.class)
                .handled(true)
                .process(exchange ->
                        log.error("Route error: {}",
                                Optional.ofNullable(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
                                        .map(Exception::getMessage)
                                        .orElse("Unknown error"))
                )
                .to("micrometer:timer:sqs.retry?action=stop");
    }

    private void sendToEventBridge(EventMessageEntity entity, SqsMessageDto sqsDto) {
        try {
            String detailJson = objectMapper.writeValueAsString(sqsDto.payload());
            String source = Optional.ofNullable(sqsDto.metadata())
                    .map(MetadataDto::source)
                    .orElse("camel.sqs");

            PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                    .source(source)
                    .detailType(sqsDto.type())
                    .detail(detailJson)
                    .time(Instant.now())
                    .eventBusName(appConfig.getEventBusName())
                    .build();

            PutEventsRequest request = PutEventsRequest.builder()
                    .entries(entry)
                    .build();

            eventBridgeClient.putEvents(request)
                    .whenComplete((response, error) -> Optional.ofNullable(error)
                            .ifPresentOrElse(
                                    e -> {
                                        log.error("EventBridge failed: {}", e.getMessage());
                                        markAsFailed(entity, e.getMessage());
                                    },
                                    () -> {
                                        log.info("EventBridge success: {}", entity.getMessageId());
                                        markAsProcessed(entity);
                                    }
                            ));
        } catch (Exception e) {
            log.error("Failed to prepare EventBridge event: {}", e.getMessage());
            markAsFailed(entity, e.getMessage());
        }
    }

    private void markAsProcessed(EventMessageEntity entity) {
        repository.markAsProcessed(entity.getMessageId(), entity.getEventType())
                .subscribe().with(
                        success -> log.info("Marked PROCESSED: {}", entity.getMessageId()),
                        error -> log.error("Failed to mark PROCESSED: {}", error.getMessage())
                );
    }

    private void markAsFailed(EventMessageEntity entity, String errorMessage) {
        repository.markAsFailed(entity.getMessageId(), entity.getEventType(), errorMessage)
                .subscribe().with(
                        success -> log.error("Marked FAILED: {}", entity.getMessageId()),
                        error -> log.error("Failed to mark FAILED: {}", error.getMessage())
                );
    }

    private Uni<EventMessageEntity> createEntity(SqsMessageDto sqsDto) {
        return Uni.createFrom().item(Unchecked.supplier(() -> {
            EventMessageEntity entity = mapper.toEntity(sqsDto);
            entity.setPayload(objectMapper.writeValueAsString(sqsDto.payload()));
            return entity;
        }));
    }
}