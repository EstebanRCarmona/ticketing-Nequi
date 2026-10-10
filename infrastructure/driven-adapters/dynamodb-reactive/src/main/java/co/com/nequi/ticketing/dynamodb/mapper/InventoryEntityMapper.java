package co.com.nequi.ticketing.dynamodb.mapper;

import co.com.nequi.ticketing.dynamodb.entity.InventoryEntity;
import co.com.nequi.ticketing.model.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface InventoryEntityMapper {

    @Mapping(target = "updatedAt", expression = "java(mapInstant(inventory.getUpdatedAt()))")
    InventoryEntity toEntity(Inventory inventory);

    @Mapping(target = "updatedAt", expression = "java(parseInstant(entity.getUpdatedAt()))")
    Inventory toDomain(InventoryEntity entity);

    default String mapInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    default Instant parseInstant(String instantStr) {
        return instantStr != null ? Instant.parse(instantStr) : null;
    }
}
