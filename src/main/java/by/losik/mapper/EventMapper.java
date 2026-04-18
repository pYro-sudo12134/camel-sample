package by.losik.mapper;

import by.losik.dto.SqsMessageDto;
import by.losik.entity.EventMessageEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA_CDI,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventMapper {

    @Mapping(target = "messageId", source = "id")
    @Mapping(target = "eventType", source = "type")
    @Mapping(target = "orderId", source = "payload.orderId")
    @Mapping(target = "retryCount", source = "metadata.retryCount")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "payload", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "processedAt", ignore = true)
    @Mapping(target = "errorMessage", ignore = true)
    EventMessageEntity toEntity(SqsMessageDto sqsMessage);

    @Mapping(target = "id", source = "messageId")
    @Mapping(target = "type", source = "eventType")
    @Mapping(target = "timestamp", source = "createdAt")
    @Mapping(target = "payload", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    SqsMessageDto toSqsMessageDto(EventMessageEntity entity);
}