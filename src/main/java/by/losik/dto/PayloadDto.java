package by.losik.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record PayloadDto(
        String orderId,
        Double amount,
        String customerId,
        String currency
) {}
