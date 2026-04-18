package by.losik.config;

import by.losik.mapper.EventMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.mapstruct.factory.Mappers;

@ApplicationScoped
public class MapperConfig {

    @Produces
    @Named("customEventMapper")
    public EventMapper eventMapper() {
        return Mappers.getMapper(EventMapper.class);
    }
}
