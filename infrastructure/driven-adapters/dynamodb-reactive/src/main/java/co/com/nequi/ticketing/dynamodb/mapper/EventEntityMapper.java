package co.com.nequi.ticketing.dynamodb.mapper;

import co.com.nequi.ticketing.dynamodb.entity.EventEntity;
import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.enums.EventCategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface EventEntityMapper {

    @Mapping(target = "eventId", source = "id")
    @Mapping(target = "category", expression = "java(mapCategory(event.getCategory()))")
    @Mapping(target = "createdAt", expression = "java(mapInstant(event.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(mapInstant(event.getUpdatedAt()))")
    EventEntity toEntity(Event event);

    @Mapping(target = "id", source = "eventId")
    @Mapping(target = "category", expression = "java(parseCategory(entity.getCategory()))")
    @Mapping(target = "createdAt", expression = "java(parseInstant(entity.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(parseInstant(entity.getUpdatedAt()))")
    Event toDomain(EventEntity entity);

    default String mapCategory(EventCategory category) {
        return category != null ? category.name() : null;
    }

    default EventCategory parseCategory(String categoryStr) {
        return categoryStr != null ? EventCategory.valueOf(categoryStr) : null;
    }

    default String mapInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    default Instant parseInstant(String instantStr) {
        return instantStr != null ? Instant.parse(instantStr) : null;
    }
}
