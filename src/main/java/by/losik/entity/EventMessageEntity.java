package by.losik.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class EventMessageEntity {
    private String messageId;
    private String eventType;
    private String orderId;
    private EventStatus status;
    private String payload;
    private String metadata;
    private Long createdAt;
    private Long processedAt;
    private Integer retryCount;
    private String errorMessage;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("message_id")
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    @DynamoDbSortKey
    @DynamoDbAttribute("event_type")
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    @DynamoDbSecondaryPartitionKey(indexNames = "order_id_index")
    @DynamoDbAttribute("order_id")
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    @DynamoDbSecondaryPartitionKey(indexNames = "status_created_at_index")
    @DynamoDbAttribute("status")
    public String getStatus() {
        return status != null ? status.getValue() : null;
    }
    public void setStatus(String status) {
        this.status = status != null ? EventStatus.fromValue(status) : null;
    }

    @DynamoDbSecondarySortKey(indexNames = "status_created_at_index")
    @DynamoDbAttribute("created_at")
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    @DynamoDbIgnore
    public EventStatus getStatusEnum() {
        return status;
    }
    public void setStatusEnum(EventStatus status) {
        this.status = status;
    }

    @DynamoDbAttribute("payload")
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    @DynamoDbAttribute("metadata")
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    @DynamoDbAttribute("processed_at")
    public Long getProcessedAt() { return processedAt; }
    public void setProcessedAt(Long processedAt) { this.processedAt = processedAt; }

    @DynamoDbAttribute("retry_count")
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    @DynamoDbAttribute("error_message")
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}