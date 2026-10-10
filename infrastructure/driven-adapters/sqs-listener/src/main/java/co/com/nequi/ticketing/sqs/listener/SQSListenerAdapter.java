package co.com.nequi.ticketing.sqs.listener;

import co.com.nequi.ticketing.usecase.ProcessOrderAsyncUseCase;
import co.com.nequi.ticketing.usecase.ReleaseExpiredReservationsUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Service
public class SQSListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(SQSListenerAdapter.class);

    @Value("${aws.sqs.processing-queue-url}")
    private String processingQueueUrl;

    @Value("${aws.sqs.expiration-queue-url}")
    private String expirationQueueUrl;

    private final SqsAsyncClient sqsAsyncClient;
    private final ObjectMapper objectMapper;
    private final ProcessOrderAsyncUseCase processOrderAsyncUseCase;
    private final ReleaseExpiredReservationsUseCase releaseExpiredReservationsUseCase;

    public SQSListenerAdapter(SqsAsyncClient sqsAsyncClient, ObjectMapper objectMapper, ProcessOrderAsyncUseCase processOrderAsyncUseCase, ReleaseExpiredReservationsUseCase releaseExpiredReservationsUseCase) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.objectMapper = objectMapper;
        this.processOrderAsyncUseCase = processOrderAsyncUseCase;
        this.releaseExpiredReservationsUseCase = releaseExpiredReservationsUseCase;
    }

    @Scheduled(fixedDelayString = "${aws.sqs.poll-interval-ms:5000}")
    public void listenProcessingQueue() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(processingQueueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(2)
                .messageAttributeNames("All")
                .build();

        Mono.fromFuture(() -> sqsAsyncClient.receiveMessage(request))
                .flatMapMany(response -> Flux.fromIterable(response.messages()))
                .flatMap(msg -> Mono.fromCallable(() -> objectMapper.readTree(msg.body()))
                        .flatMap(node -> {
                            String orderId = node.path("orderId").asText();
                            String traceId = node.path("traceId").asText(null);
                            return processOrderAsyncUseCase.execute(orderId, traceId, msg.messageId());
                        })
                        .then(Mono.fromFuture(() -> sqsAsyncClient.deleteMessage(
                                DeleteMessageRequest.builder()
                                        .queueUrl(processingQueueUrl)
                                        .receiptHandle(msg.receiptHandle())
                                        .build())))
                        .then()
                        .onErrorResume(e -> {
                            log.error("Error processing SQS message {}, will not delete for retry", msg.messageId(), e);
                            return Mono.empty();
                        }))
                .subscribe();
    }

    @Scheduled(fixedDelayString = "${aws.sqs.poll-interval-ms:5000}")
    public void listenExpirationQueue() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(expirationQueueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(2)
                .build();

        Mono.fromFuture(() -> sqsAsyncClient.receiveMessage(request))
                .flatMapMany(response -> Flux.fromIterable(response.messages()))
                .flatMap(msg -> Mono.fromCallable(() -> objectMapper.readTree(msg.body()))
                        .flatMap(node -> {
                            String orderId = node.path("orderId").asText();
                            long deadlineAt = node.path("deadlineAt").asLong(0);
                            return releaseExpiredReservationsUseCase.expireOrderById(orderId, deadlineAt, "SQS_EXPIRER");
                        })
                        .then(Mono.fromFuture(() -> sqsAsyncClient.deleteMessage(
                                DeleteMessageRequest.builder()
                                        .queueUrl(expirationQueueUrl)
                                        .receiptHandle(msg.receiptHandle())
                                        .build())))
                        .then()
                        .onErrorResume(e -> {
                            log.error("Error processing expiration SQS message {}, will not delete for retry", msg.messageId(), e);
                            return Mono.empty();
                        }))
                .subscribe();
    }
}
