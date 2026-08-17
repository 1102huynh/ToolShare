package com.toolshare.payment;

import com.toolshare.booking.domain.Booking;
import com.toolshare.booking.domain.BookingPolicy;
import com.toolshare.booking.infrastructure.persistence.BookingRepository;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.listing.domain.Category;
import com.toolshare.listing.domain.ListingLocation;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.CategoryRepository;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import com.toolshare.payment.application.PaymentProvider;
import com.toolshare.payment.application.PaymentService;
import com.toolshare.payment.domain.PaymentOrder;
import com.toolshare.payment.domain.PaymentStatus;
import com.toolshare.payment.domain.PaymentTransaction;
import com.toolshare.payment.domain.PaymentTransactionType;
import com.toolshare.payment.infrastructure.persistence.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the provider-failure paths of {@link PaymentService} — the always-
 * succeeds {@code MvpPaymentProviderAdapter} used by {@link PaymentServiceIntegrationTest}
 * can never reach these branches, so this class overrides the {@link PaymentProvider}
 * bean with {@link ConfigurablePaymentProvider} (a hand-written test double, matching
 * this codebase's existing no-mocking-framework convention) via a separate,
 * independently-cached Spring context.
 */
@SpringBootTest(classes = PaymentApplication.class)
@AutoConfigureTestDatabase
@Import(PaymentServiceProviderFailureIntegrationTest.FailingProviderConfig.class)
class PaymentServiceProviderFailureIntegrationTest {

    @TestConfiguration
    static class FailingProviderConfig {
        @Bean
        @Primary
        PaymentProvider paymentProvider() {
            return new ConfigurablePaymentProvider();
        }
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentProvider paymentProvider;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private IdentityAccountRepository identityAccountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ToolListingRepository toolListingRepository;

    @BeforeEach
    void resetProviderFailures() {
        configurableProvider().reset();
    }

    @Test
    @Transactional
    void provider_failure_during_create_intent_persists_a_failed_payment() {
        configurableProvider().failCreateIntent();
        Fixture fixture = createFixture();
        Booking booking = createBooking(fixture);

        PaymentOrder result = paymentService.createPaymentIntent(booking.getId(), fixture.renter.getId());

        assertEquals(PaymentStatus.FAILED, result.getStatus());
        List<PaymentTransaction> ledger = paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(result.getId());
        assertEquals(1, ledger.size());
        assertEquals(PaymentTransactionType.FAIL, ledger.get(0).getTransactionType());
    }

    @Test
    @Transactional
    void provider_failure_during_authorize_persists_a_failed_payment() {
        Fixture fixture = createFixture();
        Booking booking = createBooking(fixture);
        PaymentOrder created = paymentService.createPaymentIntent(booking.getId(), fixture.renter.getId());

        configurableProvider().failAuthorize();
        PaymentOrder result = paymentService.authorizePayment(created.getId());

        assertEquals(PaymentStatus.FAILED, result.getStatus());
        List<PaymentTransaction> ledger = paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(created.getId());
        assertEquals(2, ledger.size());
        assertEquals(PaymentTransactionType.FAIL, ledger.get(1).getTransactionType());
    }

    @Test
    @Transactional
    void provider_failure_during_capture_persists_a_failed_payment() {
        Fixture fixture = createFixture();
        Booking booking = createBooking(fixture);
        PaymentOrder created = paymentService.createPaymentIntent(booking.getId(), fixture.renter.getId());
        paymentService.authorizePayment(created.getId());

        configurableProvider().failCapture();
        PaymentOrder result = paymentService.capturePayment(created.getId());

        assertEquals(PaymentStatus.FAILED, result.getStatus());
        List<PaymentTransaction> ledger = paymentTransactionRepository.findByPaymentIdOrderByOccurredAtAsc(created.getId());
        assertEquals(3, ledger.size());
        assertEquals(PaymentTransactionType.FAIL, ledger.get(2).getTransactionType());
    }

    private ConfigurablePaymentProvider configurableProvider() {
        return (ConfigurablePaymentProvider) paymentProvider;
    }

    private Booking createBooking(Fixture fixture) {
        return bookingRepository.save(Booking.create(
                fixture.listing.getId(),
                fixture.renter.getId(),
                fixture.owner.getId(),
                fixture.start,
                fixture.start.plusDays(1),
                BookingPolicy.INSTANT
        ));
    }

    private Fixture createFixture() {
        IdentityAccount owner = createActiveAccount("owner-payment-failure-" + UUID.randomUUID() + "@example.com");
        IdentityAccount renter = createActiveAccount("renter-payment-failure-" + UUID.randomUUID() + "@example.com");

        Category category = categoryRepository.save(new Category("payment-failure-cat-" + UUID.randomUUID(), "Payment Failure Category"));

        ToolListing listing = new ToolListing(
                owner.getId(),
                category,
                new ListingLocation("2 Le Duan", null, "Ben Nghe", "District 1", "Ho Chi Minh City", "VN"),
                "Circular Saw",
                "Corded circular saw",
                150_000L,
                500_000L,
                "VND",
                true,
                List.of()
        );
        listing.submitForReview();
        listing.activate();
        ToolListing savedListing = toolListingRepository.save(listing);

        LocalDateTime start = LocalDateTime.now().plusDays(2).withMinute(0).withSecond(0).withNano(0);
        return new Fixture(savedListing, owner, renter, start);
    }

    private IdentityAccount createActiveAccount(String email) {
        IdentityAccount account = identityAccountRepository.save(IdentityAccount.register(email, "hashed-password"));
        account.markEmailVerified();
        account.activate();
        return identityAccountRepository.save(account);
    }

    private record Fixture(
            ToolListing listing,
            IdentityAccount owner,
            IdentityAccount renter,
            LocalDateTime start
    ) {
    }
}
