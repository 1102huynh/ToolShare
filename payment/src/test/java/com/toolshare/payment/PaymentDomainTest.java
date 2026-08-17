package com.toolshare.payment;

import com.toolshare.payment.domain.PaymentOrder;
import com.toolshare.payment.domain.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentDomainTest {

    @Test
    void initiated_payment_starts_in_initiated_state() {
        PaymentOrder payment = createPayment();

        assertEquals(PaymentStatus.INITIATED, payment.getStatus());
    }

    @Test
    void initiated_payment_can_reach_captured_through_authorize_and_capture() {
        PaymentOrder payment = createPayment();

        payment.authorize();
        assertEquals(PaymentStatus.AUTHORIZED, payment.getStatus());

        payment.capture();
        assertEquals(PaymentStatus.CAPTURED, payment.getStatus());
    }

    @Test
    void initiated_payment_can_fail() {
        PaymentOrder payment = createPayment();

        payment.fail();

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    void initiated_payment_can_be_cancelled() {
        PaymentOrder payment = createPayment();

        payment.cancel();

        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
    }

    @Test
    void authorized_payment_can_fail() {
        PaymentOrder payment = createPayment();
        payment.authorize();

        payment.fail();

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    void authorized_payment_can_be_cancelled() {
        PaymentOrder payment = createPayment();
        payment.authorize();

        payment.cancel();

        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
    }

    @Test
    void capture_is_rejected_before_authorization() {
        PaymentOrder payment = createPayment();

        IllegalStateException exception = assertThrows(IllegalStateException.class, payment::capture);

        assertEquals("Payment cannot transition from INITIATED to CAPTURED", exception.getMessage());
    }

    @Test
    void terminal_state_rejects_further_transitions() {
        PaymentOrder payment = createPayment();
        payment.authorize();
        payment.capture();

        IllegalStateException exception = assertThrows(IllegalStateException.class, payment::authorize);

        assertEquals("Payment cannot transition from CAPTURED to AUTHORIZED", exception.getMessage());
    }

    @Test
    void negative_amount_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> PaymentOrder.initiate(
                UUID.randomUUID(), UUID.randomUUID(), -1L, "VND", "MVP"
        ));
    }

    @Test
    void malformed_currency_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> PaymentOrder.initiate(
                UUID.randomUUID(), UUID.randomUUID(), 100_000L, "VNDX", "MVP"
        ));
    }

    private PaymentOrder createPayment() {
        return PaymentOrder.initiate(UUID.randomUUID(), UUID.randomUUID(), 100_000L, "VND", "MVP");
    }
}
