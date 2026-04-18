package by.losik.repository;

import by.losik.annotation.Measured;
import by.losik.config.AppConfig;
import by.losik.entity.EventMessageEntity;
import by.losik.entity.EventStatus;
import by.losik.exception.NullEntityException;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@ApplicationScoped
public class EventMessageRepository {

    private final String orderIdIndex;
    private final String statusCreatedAtIndex;
    private final DynamoDbAsyncTable<EventMessageEntity> table;

    @Inject
    public EventMessageRepository(
            @NonNull DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient,
            @NonNull AppConfig appConfig) {
        this.orderIdIndex = appConfig.getOrderIdIndex();
        this.statusCreatedAtIndex = appConfig.getStatusCreatedAtIndex();
        this.table = dynamoDbEnhancedAsyncClient
                .table(appConfig.getDynamoTableName(),
                TableSchema.fromBean(EventMessageEntity.class));
    }

    @Measured(value = "dynamodb.save", tags = {"save"})
    public Uni<EventMessageEntity> save(EventMessageEntity entity) {
        return Optional.ofNullable(entity)
                .map(e -> {
                    e.setCreatedAt(Instant.now().toEpochMilli());
                    e.setStatusEnum(EventStatus.PENDING);
                    return Uni.createFrom()
                            .completionStage(() -> table.putItem(e))
                            .onItem().transform(ignore -> e)
                            .onFailure().invoke(error -> Log.error("Failed to save message: " + error.getMessage()));
                })
                .orElseThrow(NullEntityException::new);
    }

    @Measured(value = "dynamodb.mark_as_processed", tags = {"operation=update"})
    public Uni<EventMessageEntity> markAsProcessed(String messageId, String eventType) {
        return findById(messageId, eventType)
                .onItem().ifNotNull().transformToUni(entity -> {
                    entity.setStatusEnum(EventStatus.PROCESSED);
                    entity.setProcessedAt(Instant.now().toEpochMilli());
                    return update(entity);
                });
    }

    @Measured(value = "dynamodb.mark_as_failed", tags = {"operation=update"})
    public Uni<EventMessageEntity> markAsFailed(String messageId, String eventType, String errorMessage) {
        return findById(messageId, eventType)
                .onItem().ifNotNull().transformToUni(entity -> {
                    entity.setStatusEnum(EventStatus.FAILED);
                    entity.setErrorMessage(errorMessage);
                    entity.setRetryCount(Optional.ofNullable(entity.getRetryCount()).orElse(0) + 1);
                    return update(entity);
                });
    }

    @Measured(value = "dynamodb.find_by_id", tags = {"operation=select"})
    public Uni<EventMessageEntity> findById(String messageId, String eventType) {
        return Uni.createFrom()
                .completionStage(() ->
                        table.getItem(Key.builder()
                                .partitionValue(messageId)
                                .sortValue(eventType)
                                .build()));
    }

    public Uni<List<EventMessageEntity>> findByOrderId(String orderId, int limit) {
        return queryByIndex(orderIdIndex, orderId, limit);
    }

    public Uni<List<EventMessageEntity>> findByStatus(EventStatus status, int limit) {
        return queryByIndex(statusCreatedAtIndex, status.getValue(), limit);
    }

    public Uni<Void> delete(String messageId, String eventType) {
        return Uni.createFrom()
                .completionStage(() ->
                        table.deleteItem(Key.builder()
                                .partitionValue(messageId)
                                .sortValue(eventType)
                                .build()))
                .replaceWithVoid();
    }

    public Uni<EventMessageEntity> update(EventMessageEntity entity) {
        return Optional.ofNullable(entity)
                .map(e -> Uni.createFrom().completionStage(() -> table.updateItem(e)))
                .orElseThrow(NullEntityException::new);
    }

    private Uni<List<EventMessageEntity>> queryByIndex(String indexName, String partitionValue, int limit) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(partitionValue)
                        .build()))
                .limit(limit)
                .build();

        return Uni.createFrom()
                .completionStage(() -> {
                    CompletableFuture<List<EventMessageEntity>> future = new CompletableFuture<>();
                    var items = new ArrayList<EventMessageEntity>();

                    table.index(indexName).query(request)
                            .subscribe(page -> items.addAll(page.items()))
                            .whenComplete((result, error) ->
                                    Optional.ofNullable(error)
                                            .ifPresentOrElse(
                                                    future::completeExceptionally,
                                                    () -> future.complete(items)
                                            )
                            );

                    return future;
                });
    }

    @Measured(value = "dynamodb.find_by_status_and_date_range", tags = {"operation=select"})
    public Uni<List<EventMessageEntity>> findByStatusAndDateRange(EventStatus status, long fromTime, long toTime, int limit) {
        QueryConditional queryConditional = QueryConditional
                .sortBetween(Key.builder()
                                .partitionValue(status.getValue())
                                .sortValue(fromTime)
                                .build(),
                        Key.builder()
                                .partitionValue(status.getValue())
                                .sortValue(toTime)
                                .build());

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit)
                .scanIndexForward(true)
                .build();

        return Uni.createFrom()
                .completionStage(() -> {
                    CompletableFuture<List<EventMessageEntity>> future = new CompletableFuture<>();
                    var items = new ArrayList<EventMessageEntity>();

                    table.index(statusCreatedAtIndex).query(request)
                            .subscribe(page -> items.addAll(page.items()))
                            .whenComplete((result, error) ->
                                    Optional.ofNullable(error)
                                            .ifPresentOrElse(
                                                    future::completeExceptionally,
                                                    () -> future.complete(items)
                                            )
                            );

                    return future;
                });
    }

    @Measured(value = "dynamodb.find_failed_for_retry", tags = {"operation=select"})
    public Uni<List<EventMessageEntity>> findFailedMessagesForRetry(int maxRetryCount, int limit, int timeSpan) {
        long timeRange = Instant.now().minusSeconds(timeSpan).toEpochMilli();
        long now = Instant.now().toEpochMilli();

        return findByStatusAndDateRange(EventStatus.FAILED, timeRange, now, limit)
                .onItem().transform(entities -> entities.stream()
                        .filter(entity -> entity.getRetryCount() != null && entity.getRetryCount() < maxRetryCount)
                        .collect(Collectors.toList()));
    }
}