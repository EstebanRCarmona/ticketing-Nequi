package co.com.nequi.ticketing.dynamodb.mapper;

import co.com.nequi.ticketing.dynamodb.entity.IdempotencyEntity;
import co.com.nequi.ticketing.model.IdempotencyRecord;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface IdempotencyEntityMapper {
    IdempotencyEntity toEntity(IdempotencyRecord record);
    IdempotencyRecord toDomain(IdempotencyEntity entity);
}
