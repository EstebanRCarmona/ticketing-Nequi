package co.com.nequi.ticketing.api.mapper;

import co.com.nequi.ticketing.api.dto.responses.AvailabilityDTO;
import co.com.nequi.ticketing.api.dto.request.EventRequestDTO;
import co.com.nequi.ticketing.api.dto.responses.EventResponseDTO;
import co.com.nequi.ticketing.model.Event;
import co.com.nequi.ticketing.model.enums.EventCategory;
import co.com.nequi.ticketing.model.EventDetail;
import co.com.nequi.ticketing.model.Inventory;
import co.com.nequi.ticketing.model.Seat;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface EventDTOMapper {

    @Mapping(target = "category", expression = "java(parseCategory(dto.category()))")
    Event toDomain(EventRequestDTO dto);

    @Mapping(target = "eventId", source = "id")
    @Mapping(target = "category", expression = "java(mapCategory(event.getCategory()))")
    @Mapping(target = "availability", ignore = true)
    EventResponseDTO toResponseDTO(Event event);

    default EventResponseDTO toDetailResponseDTO(EventDetail detail) {
        Event event = detail.getEvent();
        Inventory inventory = detail.getInventory();

        AvailabilityDTO availabilityDTO = AvailabilityDTO.builder()
                .totalCapacity(inventory.getTotalCapacity())
                .available(inventory.getAvailable())
                .reserved(inventory.getReserved())
                .pendingConfirmation(inventory.getPendingConfirmation())
                .sold(inventory.getSold())
                .complimentary(inventory.getComplimentary())
                .asOf(inventory.getUpdatedAt() != null ? inventory.getUpdatedAt().toString() : Instant.now().toString())
                .build();

        return EventResponseDTO.builder()
                .eventId(event.getId())
                .name(event.getName())
                .eventDate(event.getEventDate())
                .venue(event.getVenue())
                .category(event.getCategory() != null ? event.getCategory().name() : null)
                .ticketPrice(event.getTicketPrice())
                .currency(event.getCurrency())
                .totalCapacity(event.getTotalCapacity())
                .availability(availabilityDTO)
                .availableSeatIds(detail.getAvailableSeats() == null ? java.util.List.of()
                    : detail.getAvailableSeats().stream().map(Seat::getSeatId).toList())
                .build();
    }

    default String mapCategory(EventCategory category) {
        return category != null ? category.name() : null;
    }

    default EventCategory parseCategory(String categoryStr) {
        return categoryStr != null ? EventCategory.valueOf(categoryStr) : EventCategory.CONCERT;
    }
}
