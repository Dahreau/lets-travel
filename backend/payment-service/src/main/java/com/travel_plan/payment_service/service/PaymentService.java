package com.travel_plan.payment_service.service;

import com.travel_plan.payment_service.domain.Payment;
import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.domain.PaymentStatus;
import com.travel_plan.payment_service.exception.InvalidRefundException;
import com.travel_plan.payment_service.exception.PaymentMethodNotFoundException;
import com.travel_plan.payment_service.exception.PaymentNotFoundException;
import com.travel_plan.payment_service.provider.ChargeRequest;
import com.travel_plan.payment_service.provider.ChargeResult;
import com.travel_plan.payment_service.provider.PaymentProviderResolver;
import com.travel_plan.payment_service.repository.PaymentMethodRepository;
import com.travel_plan.payment_service.repository.PaymentRepository;
import com.travel_plan.payment_service.web.PaymentRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentProviderResolver paymentProviderResolver;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentMethodRepository paymentMethodRepository,
            PaymentProviderResolver paymentProviderResolver) {
        this.paymentRepository = paymentRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentProviderResolver = paymentProviderResolver;
    }

    @Transactional(readOnly = true)
    public List<Payment> findAll() {
        return paymentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Payment findById(UUID id) {
        return getOrThrow(id);
    }

    @Transactional
    public Payment create(PaymentRequest request) {
        PaymentMethod method = paymentMethodRepository.findById(request.paymentMethodId())
                .orElseThrow(() -> new PaymentMethodNotFoundException(request.paymentMethodId()));

        ChargeRequest chargeRequest = new ChargeRequest(request.amount(), request.currency(), method.getProviderToken());
        ChargeResult chargeResult = paymentProviderResolver.resolve(method.getProvider()).charge(chargeRequest);

        Payment payment = Payment.builder()
                .travelId(request.travelId())
                .ownerId(request.ownerId())
                .paymentMethod(method)
                .amount(request.amount())
                .currency(request.currency().toUpperCase())
                .provider(method.getProvider())
                .status(chargeResult.status())
                .providerReference(chargeResult.providerReference())
                .build();
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment refund(UUID id) {
        Payment payment = getOrThrow(id);
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new InvalidRefundException(id);
        }
        // Notifie reellement le fournisseur, si ca echoue, aucun changement d'etat local.
        paymentProviderResolver.resolve(payment.getProvider()).refund(payment.getProviderReference());
        payment.setStatus(PaymentStatus.REFUNDED);
        // saveAndFlush : @PreUpdate ne s'execute qu'au flush, sinon updatedAt renvoye est perime.
        return paymentRepository.saveAndFlush(payment);
    }

    private Payment getOrThrow(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
