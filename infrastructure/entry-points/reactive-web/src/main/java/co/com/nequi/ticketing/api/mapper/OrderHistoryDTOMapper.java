package co.com.nequi.ticketing.api.mapper;

import co.com.nequi.ticketing.api.dto.responses.OrderHistoryResponseDTO;
import co.com.nequi.ticketing.model.enums.ActorType;
import co.com.nequi.ticketing.model.OrderHistory;
import co.com.nequi.ticketing.model.enums.TicketStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface OrderHistoryDTOMapper {

    @Mapping(target = "fromStatus", expression = "java(mapStatus(history.getFromStatus()))")
    @Mapping(target = "toStatus", expression = "java(mapStatus(history.getToStatus()))")
    @Mapping(target = "actor", expression = "java(mapActor(history.getActor()))")
    @Mapping(target = "occurredAt", expression = "java(mapInstant(history.getOccurredAt()))")
    OrderHistoryResponseDTO toResponseDTO(OrderHistory history);

    default String mapStatus(TicketStatus status) {
        return status != null ? status.name() : null;
    }

    default String mapActor(ActorType actor) {
        return actor != null ? actor.name() : null;
    }

    default String mapInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }
}
