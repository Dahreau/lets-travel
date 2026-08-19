package com.travel_plan.payment_service.service;

import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.exception.PaymentMethodNotFoundException;
import com.travel_plan.payment_service.repository.PaymentMethodRepository;
import com.travel_plan.payment_service.web.PaymentMethodRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;

    public PaymentMethodService(PaymentMethodRepository paymentMethodRepository) {
        this.paymentMethodRepository = paymentMethodRepository;
    }

    @Transactional(readOnly = true)
    public List<PaymentMethod> findAll() {
        return paymentMethodRepository.findAll();
    }

    @Transactional(readOnly = true)
    public PaymentMethod findById(UUID id) {
        return getOrThrow(id);
    }

    @Transactional
    public PaymentMethod create(PaymentMethodRequest request) {
        PaymentMethod method = PaymentMethod.builder()
                .ownerId(request.ownerId())
                .provider(request.provider())
                .type(request.type())
                .providerToken(request.providerToken())
                .brand(request.brand())
                .last4(request.last4())
                .isDefault(request.isDefault())
                .build();
        return paymentMethodRepository.save(method);
    }

    @Transactional
    public PaymentMethod update(UUID id, PaymentMethodRequest request) {
        PaymentMethod method = getOrThrow(id);
        method.setOwnerId(request.ownerId());
        method.setProvider(request.provider());
        method.setType(request.type());
        method.setProviderToken(request.providerToken());
        method.setBrand(request.brand());
        method.setLast4(request.last4());
        method.setDefault(request.isDefault());
        return paymentMethodRepository.save(method);
    }

    @Transactional
    public void delete(UUID id) {
        PaymentMethod method = getOrThrow(id);
        paymentMethodRepository.delete(method);
    }

    private PaymentMethod getOrThrow(UUID id) {
        return paymentMethodRepository.findById(id)
                .orElseThrow(() -> new PaymentMethodNotFoundException(id));
    }
}
