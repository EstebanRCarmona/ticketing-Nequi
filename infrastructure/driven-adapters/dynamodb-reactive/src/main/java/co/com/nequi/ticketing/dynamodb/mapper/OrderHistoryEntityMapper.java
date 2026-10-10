package co.com.nequi.ticketing.dynamodb.mapper;

import co.com.nequi.ticketing.dynamodb.entity.OrderHistoryEntity;
import co.com.nequi.ticketing.model.enums.ActorType;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.TicketOrder;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface OrderHistoryEntityMapper {

    @Mapping(target = "fromStatus", expression = "java(mapStatus(history.getFromStatus()))")
    @Mapping(target = "toStatus", expression = "java(mapStatus(history.getToStatus()))")
    @Mapping(target = "actor", expression = "java(mapActor(history.getActor()))")
    @Mapping(target = "occurredAt", expression = "java(mapInstant(history.getOccurredAt()))")
    OrderHistoryEntity toEntity(OrderHistory history);

    @Mapping(target = "fromStatus", expression = "java(parseStatus(entity.getFromStatus()))")
    @Mapping(target = "toStatus", expression = "java(parseStatus(entity.getToStatus()))")
    @Mapping(target = "actor", expression = "java(parseActor(entity.getActor()))")
    @Mapping(target = "occurredAt", expression = "java(parseInstant(entity.getOccurredAt()))")
    OrderHistory toDomain(OrderHistoryEntity entity);

    default OrderHistoryEntity toTransitionEntity(TicketOrder before, TicketOrder after, String reasonCode, String traceId, ActorType actor) {
        return OrderHistoryEntity.builder()
                .orderId(before.getId())
                .sequence(after.getVersion())
                .fromStatus(before.getStatus().name())
                .toStatus(after.getStatus().name())
                .reasonCode(reasonCode)
                .actor(actor.name())
                .quantity(before.getQuantity())
                .traceId(traceId)
                .occurredAt(after.getUpdatedAt().toString())
                .build();
    }

    default String mapStatus(TicketStatus status) {
        return status != null ? status.name() : null;
    }

    default TicketStatus parseStatus(String statusStr) {
        return statusStr != null ? TicketStatus.valueOf(statusStr) : null;
    }

    default String mapActor(ActorType actor) {
        return actor != null ? actor.name() : null;
    }

    default ActorType parseActor(String actorStr) {
        return actorStr != null ? ActorType.valueOf(actorStr) : null;
    }

    default String mapInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    default Instant parseInstant(String instantStr) {
        return instantStr != null ? Instant.parse(instantStr) : null;
    }
}
