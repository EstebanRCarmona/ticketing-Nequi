package co.com.nequi.ticketing.sqs.sender.adapter;

import co.com.nequi.ticketing.model.enums.GeneralMessage;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import co.com.nequi.ticketing.model.gateways.QueueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class SQSSenderAdapter implements QueueService {

    @Value("${aws.sqs.processing-queue-url}")
    private String processingQueueUrl;

    @Value("${aws.sqs.expiration-queue-url}")
    private String expirationQueueUrl;

    private final SqsAsyncClient sqsAsyncClient;
    private final ObjectMapper objectMapper;

    public SQSSenderAdapter(SqsAsyncClient sqsAsyncClient, ObjectMapper objectMapper) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> sendFinalizeOrderMessage(String orderId, String traceId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("eventType", "FINALIZE_ORDER");
        payload.put("orderId", orderId);
        payload.put("traceId", traceId);

        Map<String, MessageAttributeValue> attributes = Optional.ofNullable(traceId)
                .map(t -> Map.of("traceId", MessageAttributeValue.builder().dataType("String").stringValue(t).build()))
                .orElse(Map.of());

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .flatMap(json -> Mono.fromFuture(() -> sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(processingQueueUrl)
                        .messageBody(json)
                        .messageAttributes(attributes)
                        .build())))
                .then()
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_SQS_ERROR, e));
    }

    @Override
    public Mono<Void> sendExpireOrderMessage(String orderId, long deadlineAt, long delaySeconds) {
        int delay = (int) Math.min(Math.max(0, delaySeconds), 900);

        Map<String, Object> payload = new HashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("eventType", "EXPIRE_ORDER");
        payload.put("orderId", orderId);
        payload.put("deadlineAt", deadlineAt);

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .flatMap(json -> Mono.fromFuture(() -> sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(expirationQueueUrl)
                        .messageBody(json)
                        .delaySeconds(delay)
                        .build())))
                .then()
                .onErrorMap(e -> new TechnicalException(GeneralMessage.TECH_SQS_ERROR, e));
    }
}
