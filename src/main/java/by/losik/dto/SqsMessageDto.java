package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record SqsMessageDto(
        String id,
        String type,
        Long timestamp,
        @JsonProperty("data") PayloadDto payload,
        MetadataDto metadata
) {}