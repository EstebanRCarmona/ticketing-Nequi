package co.com.nequi.ticketing.dynamodb.mapper;

import co.com.nequi.ticketing.dynamodb.entity.SeatEntity;
import co.com.nequi.ticketing.model.Seat;
import co.com.nequi.ticketing.model.enums.SeatStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface SeatEntityMapper {

    int SEAT_SHARD_COUNT = 16;

    @Mapping(target = "status", expression = "java(mapStatus(seat.getStatus()))")
    @Mapping(target = "seatKey", expression = "java(seat.getEventId() + \"#S#\" + seatShard(seat.getSeatId()))")
    @Mapping(target = "statusKey", expression = "java(seat.getEventId() + \"#\" + mapStatus(seat.getStatus()) + \"#S#\" + seatShard(seat.getSeatId()))")
    @Mapping(target = "updatedAt", expression = "java(mapInstant(seat.getUpdatedAt()))")
    SeatEntity toEntity(Seat seat);

    @Mapping(target = "status", expression = "java(parseStatus(entity.getStatus()))")
    @Mapping(target = "updatedAt", expression = "java(parseInstant(entity.getUpdatedAt()))")
    Seat toDomain(SeatEntity entity);

    default String mapStatus(SeatStatus status) {
        return status != null ? status.name() : null;
    }

    default SeatStatus parseStatus(String status) {
        return status != null ? SeatStatus.valueOf(status) : null;
    }

    default int seatShard(String seatId) {
        return Math.floorMod(seatId.hashCode(), SEAT_SHARD_COUNT);
    }

    default String mapInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    default Instant parseInstant(String instant) {
        return instant != null ? Instant.parse(instant) : null;
    }
}