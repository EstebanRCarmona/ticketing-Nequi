package co.com.nequi.ticketing.dynamodb.adapter;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class MockPaymentGatewayAdapterTest {

    private final MockPaymentGatewayAdapter adapter = new MockPaymentGatewayAdapter();

    @Test
    void charge_withNormalRef_shouldReturnTrue() {
        StepVerifier.create(adapter.charge("ord-1", "tok_card_visa_123"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void charge_withRejectRef_shouldReturnFalse() {
        StepVerifier.create(adapter.charge("ord-1", "REJECT_PAYMENT"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void charge_withNullRef_shouldReturnTrue() {
        StepVerifier.create(adapter.charge("ord-1", null))
                .expectNext(true)
                .verifyComplete();
    }
}
