package co.com.nequi.ticketing.api.scheduler;

import co.com.nequi.ticketing.usecase.ReleaseExpiredReservationsUseCase;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpiredReservationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiredReservationScheduler.class);
    private final ReleaseExpiredReservationsUseCase releaseExpiredReservationsUseCase;

    @Scheduled(fixedDelayString = "${app.scheduler.expired-reservations-interval-ms:60000}")
    public void scheduleReservationRelease() {
        releaseExpiredReservationsUseCase.execute()
                .doOnNext(releasedOrder -> log.info("[SCHEDULER] Released expired reservation for order: {}", releasedOrder.getId()))
                .subscribe();
    }
}
