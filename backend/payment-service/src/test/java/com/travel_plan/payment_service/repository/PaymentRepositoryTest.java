package com.travel_plan.payment_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.travel_plan.payment_service.domain.MethodType;
import com.travel_plan.payment_service.domain.Payment;
import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.domain.PaymentStatus;
import com.travel_plan.payment_service.domain.ProviderType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void deletingPaymentMethodSetsPaymentMethodIdToNullInsteadOfDeletingPayment() {
        PaymentMethod method = paymentMethodRepository.save(newPaymentMethod());
        Payment payment = paymentRepository.save(newPayment(method));
        entityManager.flush();
        entityManager.clear();
        UUID paymentId = payment.getId();
        UUID methodId = method.getId();

        // Session propre requise avant le delete (sinon TransientPropertyValueException).
        PaymentMethod managedMethod = paymentMethodRepository.findById(methodId).orElseThrow();
        paymentMethodRepository.delete(managedMethod);
        entityManager.flush();
        entityManager.clear();

        Payment reloaded = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(reloaded.getPaymentMethod()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void updatingPaymentRefreshesUpdatedAt() {
        PaymentMethod method = paymentMethodRepository.save(newPaymentMethod());
        Payment payment = paymentRepository.save(newPayment(method));
        entityManager.flush();
        var firstUpdatedAt = payment.getUpdatedAt();

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        entityManager.flush();

        assertThat(payment.getUpdatedAt()).isAfterOrEqualTo(firstUpdatedAt);
    }

    private PaymentMethod newPaymentMethod() {
        return PaymentMethod.builder()
                .ownerId(UUID.randomUUID())
                .provider(ProviderType.STRIPE)
                .type(MethodType.CARD)
                .providerToken("pm_card_visa")
                .brand("visa")
                .last4("4242")
                .isDefault(true)
                .build();
    }

    private Payment newPayment(PaymentMethod method) {
        return Payment.builder()
                .travelId(UUID.randomUUID())
                .ownerId(method.getOwnerId())
                .paymentMethod(method)
                .amount(new BigDecimal("120.00"))
                .currency("EUR")
                .provider(ProviderType.STRIPE)
                .status(PaymentStatus.SUCCEEDED)
                .providerReference("pi_123")
                .build();
    }
}
