package com.travel_plan.payment_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.travel_plan.payment_service.domain.MethodType;
import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.domain.ProviderType;
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
class PaymentMethodRepositoryTest {

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savingPaymentMethodPopulatesGeneratedIdAndCreatedAt() {
        PaymentMethod method = paymentMethodRepository.save(newPaymentMethod(UUID.randomUUID(), true));
        entityManager.flush();

        assertThat(method.getId()).isNotNull();
        assertThat(method.getCreatedAt()).isNotNull();
    }

    @Test
    void reloadingPaymentMethodPreservesAllFields() {
        UUID ownerId = UUID.randomUUID();
        PaymentMethod method = paymentMethodRepository.save(newPaymentMethod(ownerId, true));
        entityManager.flush();
        entityManager.clear();

        PaymentMethod reloaded = paymentMethodRepository.findById(method.getId()).orElseThrow();

        assertThat(reloaded.getOwnerId()).isEqualTo(ownerId);
        assertThat(reloaded.getProvider()).isEqualTo(ProviderType.STRIPE);
        assertThat(reloaded.getType()).isEqualTo(MethodType.CARD);
        assertThat(reloaded.getBrand()).isEqualTo("visa");
        assertThat(reloaded.getLast4()).isEqualTo("4242");
        assertThat(reloaded.isDefault()).isTrue();
    }

    @Test
    void ownerCanHaveMultiplePaymentMethodsButOnlyOneMarkedDefault() {
        UUID ownerId = UUID.randomUUID();
        PaymentMethod defaultMethod = paymentMethodRepository.save(newPaymentMethod(ownerId, true));
        PaymentMethod secondaryMethod = paymentMethodRepository.save(newPaymentMethod(ownerId, false));
        entityManager.flush();
        entityManager.clear();

        var ownerMethods = paymentMethodRepository.findAllById(
                java.util.List.of(defaultMethod.getId(), secondaryMethod.getId()));

        assertThat(ownerMethods).extracting(PaymentMethod::isDefault).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void deletingAPaymentMethodWithNoAssociatedPaymentRemovesItEntirely() {
        PaymentMethod method = paymentMethodRepository.save(newPaymentMethod(UUID.randomUUID(), true));
        entityManager.flush();
        UUID methodId = method.getId();

        paymentMethodRepository.delete(method);
        entityManager.flush();

        assertThat(paymentMethodRepository.findById(methodId)).isEmpty();
    }

    private PaymentMethod newPaymentMethod(UUID ownerId, boolean isDefault) {
        return PaymentMethod.builder()
                .ownerId(ownerId)
                .provider(ProviderType.STRIPE)
                .type(MethodType.CARD)
                .providerToken("pm_card_visa")
                .brand("visa")
                .last4("4242")
                .isDefault(isDefault)
                .build();
    }
}
