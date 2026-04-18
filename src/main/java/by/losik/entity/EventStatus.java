package by.losik.entity;

import by.losik.exception.UnknownStatusException;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.EnumSet;

public enum EventStatus {
    PENDING("PENDING"),
    PROCESSED("PROCESSED"),
    FAILED("FAILED");
    private final String value;

    EventStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static EventStatus fromValue(String value) {
        return EnumSet.allOf(EventStatus.class).stream()
                .filter(status -> status.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new UnknownStatusException(value));
    }
}