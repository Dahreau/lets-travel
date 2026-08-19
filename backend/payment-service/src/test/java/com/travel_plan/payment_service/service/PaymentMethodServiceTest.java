package com.travel_plan.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.payment_service.domain.MethodType;
import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.domain.ProviderType;
import com.travel_plan.payment_service.exception.PaymentMethodNotFoundException;
import com.travel_plan.payment_service.repository.PaymentMethodRepository;
import com.travel_plan.payment_service.web.PaymentMethodRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentMethodServiceTest {

    private final PaymentMethodRepository paymentMethodRepository = mock(PaymentMethodRepository.class);
    private final PaymentMethodService paymentMethodService = new PaymentMethodService(paymentMethodRepository);

    @Test
    void findAllDelegatesToRepository() {
        when(paymentMethodRepository.findAll()).thenReturn(List.of(PaymentMethod.builder().build()));

        assertThat(paymentMethodService.findAll()).hasSize(1);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(paymentMethodRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentMethodService.findById(id))
                .isInstanceOf(PaymentMethodNotFoundException.class);
    }

    @Test
    void createBuildsPaymentMethodFromRequest() {
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentMethod created = paymentMethodService.create(validRequest());

        assertThat(created.getProvider()).isEqualTo(ProviderType.STRIPE);
        assertThat(created.getType()).isEqualTo(MethodType.CARD);
        assertThat(created.getProviderToken()).isEqualTo("pm_card_visa");
        assertThat(created.getLast4()).isEqualTo("4242");
        assertThat(created.isDefault()).isTrue();
    }

    @Test
    void updateReplacesFieldsOnExistingPaymentMethod() {
        UUID id = UUID.randomUUID();
        PaymentMethod existing = PaymentMethod.builder()
                .id(id)
                .ownerId(UUID.randomUUID())
                .provider(ProviderType.STRIPE)
                .type(MethodType.CARD)
                .providerToken("pm_old")
                .isDefault(false)
                .build();
        when(paymentMethodRepository.findById(id)).thenReturn(Optional.of(existing));
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentMethod updated = paymentMethodService.update(id, validRequest());

        assertThat(updated.getProviderToken()).isEqualTo("pm_card_visa");
        assertThat(updated.isDefault()).isTrue();
    }

    @Test
    void deleteRemovesExistingPaymentMethod() {
        UUID id = UUID.randomUUID();
        PaymentMethod existing = PaymentMethod.builder().id(id).build();
        when(paymentMethodRepository.findById(id)).thenReturn(Optional.of(existing));

        paymentMethodService.delete(id);

        verify(paymentMethodRepository).delete(existing);
    }

    private PaymentMethodRequest validRequest() {
        return new PaymentMethodRequest(
                UUID.randomUUID(), ProviderType.STRIPE, MethodType.CARD, "pm_card_visa", "visa", "4242", true);
    }
}
