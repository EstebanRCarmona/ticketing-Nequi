package co.com.nequi.ticketing.sqs.sender.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import co.com.nequi.ticketing.model.exception.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SQSSenderAdapterTest {

    private static final String PROCESSING_QUEUE_URL = "http://localhost:4566/000000000000/order-processing-queue";
    private static final String EXPIRATION_QUEUE_URL = "http://localhost:4566/000000000000/order-expiration-queue";
    private static final String ORDER_ID = "ord-1";
    private static final String TRACE_ID = "trace-1";
    private static final long DEADLINE_AT = 1700000000L;
    private static final long DELAY_SECONDS = 600L;

    @Mock private SqsAsyncClient sqsAsyncClient;

    private SQSSenderAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        adapter = new SQSSenderAdapter(sqsAsyncClient, new ObjectMapper());
        setField(adapter, "processingQueueUrl", PROCESSING_QUEUE_URL);
        setField(adapter, "expirationQueueUrl", EXPIRATION_QUEUE_URL);
    }

    private void setField(Object target, String fieldName, String value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ── sendFinalizeOrderMessage ──────────────────────────────────────────────

    @Test
    void shouldSendFinalizeOrderMessageSuccessfully() {
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("msg-1").build()));

        StepVerifier.create(adapter.sendFinalizeOrderMessage(ORDER_ID, TRACE_ID))
                .verifyComplete();
    }

    @Test
    void shouldSendFinalizeOrderMessageToProcessingQueue() {
        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        when(sqsAsyncClient.sendMessage(captor.capture()))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

        StepVerifier.create(adapter.sendFinalizeOrderMessage(ORDER_ID, TRACE_ID))
                .verifyComplete();

        SendMessageRequest sent = captor.getValue();
        assertThat(sent.queueUrl()).isEqualTo(PROCESSING_QUEUE_URL);
        assertThat(sent.messageBody()).contains("FINALIZE_ORDER");
        assertThat(sent.messageBody()).contains(ORDER_ID);
    }

    @Test
    void shouldSendFinalizeOrderMessageWithNullTraceId() {
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

        StepVerifier.create(adapter.sendFinalizeOrderMessage(ORDER_ID, null))
                .verifyComplete();
    }

    @Test
    void shouldWrapExceptionOnSendFinalizeOrderMessage() {
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("SQS error")));

        StepVerifier.create(adapter.sendFinalizeOrderMessage(ORDER_ID, TRACE_ID))
                .expectError(TechnicalException.class)
                .verify();
    }

    // ── sendExpireOrderMessage ────────────────────────────────────────────────

    @Test
    void shouldSendExpireOrderMessageSuccessfully() {
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("msg-2").build()));

        StepVerifier.create(adapter.sendExpireOrderMessage(ORDER_ID, DEADLINE_AT, DELAY_SECONDS))
                .verifyComplete();
    }

    @Test
    void shouldSendExpireOrderMessageToExpirationQueue() {
        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        when(sqsAsyncClient.sendMessage(captor.capture()))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

        StepVerifier.create(adapter.sendExpireOrderMessage(ORDER_ID, DEADLINE_AT, DELAY_SECONDS))
                .verifyComplete();

        SendMessageRequest sent = captor.getValue();
        assertThat(sent.queueUrl()).isEqualTo(EXPIRATION_QUEUE_URL);
        assertThat(sent.messageBody()).contains("EXPIRE_ORDER");
        assertThat(sent.messageBody()).contains(ORDER_ID);
        assertThat(sent.delaySeconds()).isEqualTo(600);
    }

    @Test
    void shouldCapDelayAt900Seconds() {
        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        when(sqsAsyncClient.sendMessage(captor.capture()))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

        StepVerifier.create(adapter.sendExpireOrderMessage(ORDER_ID, DEADLINE_AT, 9999L))
                .verifyComplete();

        assertThat(captor.getValue().delaySeconds()).isEqualTo(900);
    }

    @Test
    void shouldClampNegativeDelayToZero() {
        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        when(sqsAsyncClient.sendMessage(captor.capture()))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

        StepVerifier.create(adapter.sendExpireOrderMessage(ORDER_ID, DEADLINE_AT, -100L))
                .verifyComplete();

        assertThat(captor.getValue().delaySeconds()).isEqualTo(0);
    }

    @Test
    void shouldWrapExceptionOnSendExpireOrderMessage() {
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("SQS error")));

        StepVerifier.create(adapter.sendExpireOrderMessage(ORDER_ID, DEADLINE_AT, DELAY_SECONDS))
                .expectError(TechnicalException.class)
                .verify();
    }
}
