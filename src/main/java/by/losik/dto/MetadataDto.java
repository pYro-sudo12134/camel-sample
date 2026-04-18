package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record MetadataDto(
        String source,
        String version,
        @JsonProperty("retryCount") Integer retryCount
) {}
