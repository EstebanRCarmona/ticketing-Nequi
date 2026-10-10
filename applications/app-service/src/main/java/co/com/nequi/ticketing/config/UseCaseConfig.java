package co.com.nequi.ticketing.config;

import co.com.nequi.ticketing.model.gateways.EventRepository;
import co.com.nequi.ticketing.model.gateways.IdempotencyRepository;
import co.com.nequi.ticketing.model.gateways.InventoryRepository;
import co.com.nequi.ticketing.model.gateways.OrderHistoryRepository;
import co.com.nequi.ticketing.model.gateways.PaymentGateway;
import co.com.nequi.ticketing.model.gateways.QueueService;
import co.com.nequi.ticketing.model.gateways.TicketOrderRepository;
import co.com.nequi.ticketing.usecase.CancelOrderUseCase;
import co.com.nequi.ticketing.usecase.ConfirmOrderUseCase;
import co.com.nequi.ticketing.usecase.CreateComplimentaryUseCase;
import co.com.nequi.ticketing.usecase.CreateEventUseCase;
import co.com.nequi.ticketing.usecase.CreateOrderUseCase;
import co.com.nequi.ticketing.usecase.GetEventUseCase;
import co.com.nequi.ticketing.usecase.GetOrderHistoryUseCase;
import co.com.nequi.ticketing.usecase.GetOrderUseCase;
import co.com.nequi.ticketing.usecase.ProcessOrderAsyncUseCase;
import co.com.nequi.ticketing.usecase.ReleaseExpiredReservationsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public CreateEventUseCase createEventUseCase(EventRepository eventRepository) {
        return new CreateEventUseCase(eventRepository);
    }

    @Bean
    public GetEventUseCase getEventUseCase(EventRepository eventRepository, InventoryRepository inventoryRepository) {
        return new GetEventUseCase(eventRepository, inventoryRepository);
    }

    @Bean
    public CreateOrderUseCase createOrderUseCase(EventRepository eventRepository, TicketOrderRepository ticketOrderRepository, IdempotencyRepository idempotencyRepository, QueueService queueService) {
        return new CreateOrderUseCase(eventRepository, ticketOrderRepository, idempotencyRepository, queueService);
    }

    @Bean
    public GetOrderUseCase getOrderUseCase(TicketOrderRepository ticketOrderRepository) {
        return new GetOrderUseCase(ticketOrderRepository);
    }

    @Bean
    public ConfirmOrderUseCase confirmOrderUseCase(TicketOrderRepository ticketOrderRepository, QueueService queueService) {
        return new ConfirmOrderUseCase(ticketOrderRepository, queueService);
    }

    @Bean
    public CancelOrderUseCase cancelOrderUseCase(TicketOrderRepository ticketOrderRepository) {
        return new CancelOrderUseCase(ticketOrderRepository);
    }

    @Bean
    public GetOrderHistoryUseCase getOrderHistoryUseCase(TicketOrderRepository ticketOrderRepository, OrderHistoryRepository orderHistoryRepository) {
        return new GetOrderHistoryUseCase(ticketOrderRepository, orderHistoryRepository);
    }

    @Bean
    public CreateComplimentaryUseCase createComplimentaryUseCase(EventRepository eventRepository, TicketOrderRepository ticketOrderRepository, IdempotencyRepository idempotencyRepository) {
        return new CreateComplimentaryUseCase(eventRepository, ticketOrderRepository, idempotencyRepository);
    }

    @Bean
    public ProcessOrderAsyncUseCase processOrderAsyncUseCase(TicketOrderRepository ticketOrderRepository, PaymentGateway paymentGateway) {
        return new ProcessOrderAsyncUseCase(ticketOrderRepository, paymentGateway);
    }

    @Bean
    public ReleaseExpiredReservationsUseCase releaseExpiredReservationsUseCase(TicketOrderRepository ticketOrderRepository) {
        return new ReleaseExpiredReservationsUseCase(ticketOrderRepository);
    }
}
