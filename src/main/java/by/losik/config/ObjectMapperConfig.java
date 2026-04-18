package by.losik.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ObjectMapperConfig {
    @Produces
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
