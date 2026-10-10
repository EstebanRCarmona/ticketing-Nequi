package co.com.nequi.ticketing.sqs.listener;

import co.com.nequi.ticketing.usecase.ProcessOrderAsyncUseCase;
import co.com.nequi.ticketing.usecase.ReleaseExpiredReservationsUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SQSListenerAdapterTest {

    private static final String PROCESSING_QUEUE_URL = "http://localhost:4566/000000000000/order-processing-queue";
    private static final String EXPIRATION_QUEUE_URL = "http://localhost:4566/000000000000/order-expiration-queue";
    private static final String ORDER_ID = "ord-1";
    private static final String RECEIPT_HANDLE = "receipt-handle-1";
    private static final long DEADLINE_AT = 1700000000L;

    @Mock private SqsAsyncClient sqsAsyncClient;
    @Mock private ProcessOrderAsyncUseCase processOrderAsyncUseCase;
    @Mock private ReleaseExpiredReservationsUseCase releaseExpiredReservationsUseCase;

    private SQSListenerAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        adapter = new SQSListenerAdapter(sqsAsyncClient, new ObjectMapper(), processOrderAsyncUseCase, releaseExpiredReservationsUseCase);
        setField(adapter, "processingQueueUrl", PROCESSING_QUEUE_URL);
        setField(adapter, "expirationQueueUrl", EXPIRATION_QUEUE_URL);
    }

    private void setField(Object target, String fieldName, String value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Message buildMessage(String body) {
        return Message.builder().messageId("msg-1").receiptHandle(RECEIPT_HANDLE).body(body).build();
    }

    private void mockDeleteMessage() {
        when(sqsAsyncClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));
    }

    // ── listenProcessingQueue ─────────────────────────────────────────────────

    @Test
    void shouldProcessAndDeleteMessageFromProcessingQueue() throws InterruptedException {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"traceId\":\"trace-1\"}";
        Message msg = buildMessage(body);

        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(msg).build()));
        when(processOrderAsyncUseCase.execute(anyString(), anyString(), anyString())).thenReturn(Mono.empty());
        mockDeleteMessage();

        adapter.listenProcessingQueue();
        Thread.sleep(200);

        verify(processOrderAsyncUseCase).execute(ORDER_ID, "trace-1", "msg-1");
        verify(sqsAsyncClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldNotDeleteMessageWhenProcessingFails() throws InterruptedException {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"traceId\":\"trace-1\"}";
        Message msg = buildMessage(body);

        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(msg).build()));
        when(processOrderAsyncUseCase.execute(anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("processing error")));

        adapter.listenProcessingQueue();
        Thread.sleep(200);

        verify(sqsAsyncClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldHandleEmptyProcessingQueue() throws InterruptedException {
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ReceiveMessageResponse.builder().build()));

        adapter.listenProcessingQueue();
        Thread.sleep(200);

        verify(processOrderAsyncUseCase, never()).execute(anyString(), anyString(), anyString());
    }

    @Test
    void shouldHandleProcessingQueueReceiveError() throws InterruptedException {
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("SQS unavailable")));

        adapter.listenProcessingQueue();
        Thread.sleep(200);

        verify(processOrderAsyncUseCase, never()).execute(anyString(), anyString(), anyString());
    }

    @Test
    void shouldHandleNullTraceIdInProcessingMessage() throws InterruptedException {
        String body = "{\"orderId\":\"" + ORDER_ID + "\"}";
        Message msg = buildMessage(body);

        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(msg).build()));
        when(processOrderAsyncUseCase.execute(anyString(), any(), anyString())).thenReturn(Mono.empty());
        mockDeleteMessage();

        adapter.listenProcessingQueue();
        Thread.sleep(200);

        verify(processOrderAsyncUseCase).execute(ORDER_ID, null, "msg-1");
    }

    // ── listenExpirationQueue ─────────────────────────────────────────────────

    @Test
    void shouldProcessAndDeleteMessageFromExpirationQueue() throws InterruptedException {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"deadlineAt\":" + DEADLINE_AT + "}";
        Message msg = buildMessage(body);

        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(msg).build()));
        when(releaseExpiredReservationsUseCase.expireOrderById(anyString(), anyLong(), anyString())).thenReturn(Mono.empty());
        mockDeleteMessage();

        adapter.listenExpirationQueue();
        Thread.sleep(200);

        verify(releaseExpiredReservationsUseCase).expireOrderById(ORDER_ID, DEADLINE_AT, "SQS_EXPIRER");
        verify(sqsAsyncClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldNotDeleteMessageWhenExpirationFails() throws InterruptedException {
        String body = "{\"orderId\":\"" + ORDER_ID + "\",\"deadlineAt\":" + DEADLINE_AT + "}";
        Message msg = buildMessage(body);

        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(msg).build()));
        when(releaseExpiredReservationsUseCase.expireOrderById(anyString(), anyLong(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("expiration error")));

        adapter.listenExpirationQueue();
        Thread.sleep(200);

        verify(sqsAsyncClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldHandleEmptyExpirationQueue() throws InterruptedException {
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ReceiveMessageResponse.builder().build()));

        adapter.listenExpirationQueue();
        Thread.sleep(200);

        verify(releaseExpiredReservationsUseCase, never()).expireOrderById(anyString(), anyLong(), anyString());
    }

    @Test
    void shouldHandleExpirationQueueReceiveError() throws InterruptedException {
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("SQS unavailable")));

        adapter.listenExpirationQueue();
        Thread.sleep(200);

        verify(releaseExpiredReservationsUseCase, never()).expireOrderById(anyString(), anyLong(), anyString());
    }

    @Test
    void shouldUseMissingDeadlineAtAsZero() throws InterruptedException {
        String body = "{\"orderId\":\"" + ORDER_ID + "\"}";
        Message msg = buildMessage(body);

        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(msg).build()));
        when(releaseExpiredReservationsUseCase.expireOrderById(anyString(), anyLong(), anyString())).thenReturn(Mono.empty());
        mockDeleteMessage();

        adapter.listenExpirationQueue();
        Thread.sleep(200);

        verify(releaseExpiredReservationsUseCase).expireOrderById(ORDER_ID, 0L, "SQS_EXPIRER");
    }
}
