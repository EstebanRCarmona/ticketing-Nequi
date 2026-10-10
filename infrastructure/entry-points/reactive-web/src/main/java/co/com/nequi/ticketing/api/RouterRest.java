package co.com.nequi.ticketing.api;

import co.com.nequi.ticketing.api.handlers.EventHandler;
import co.com.nequi.ticketing.api.handlers.OrderHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterRest {

    @Bean
    public RouterFunction<ServerResponse> routerFunction(EventHandler eventHandler, OrderHandler orderHandler) {
        return route(POST("/api/v1/events"), eventHandler::createEvent)
                .andRoute(GET("/api/v1/events"), eventHandler::getAllEvents)
                .andRoute(GET("/api/v1/events/{eventId}"), eventHandler::getEventById)
                .andRoute(POST("/api/v1/events/{eventId}/complimentary"), eventHandler::createComplimentary)
                .andRoute(POST("/api/v1/orders"), orderHandler::createOrder)
                .andRoute(GET("/api/v1/orders/{orderId}"), orderHandler::getOrderById)
                .andRoute(POST("/api/v1/orders/{orderId}/confirm"), orderHandler::confirmOrder)
                .andRoute(POST("/api/v1/orders/{orderId}/cancel"), orderHandler::cancelOrder)
                .andRoute(GET("/api/v1/orders/{orderId}/history"), orderHandler::getOrderHistory);
    }
}
