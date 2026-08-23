package com.travel_plan.payment_service.service;

import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.exception.ForbiddenException;
import com.travel_plan.payment_service.exception.InvalidPaymentRequestException;
import com.travel_plan.payment_service.exception.PaymentMethodNotFoundException;
import com.travel_plan.payment_service.repository.PaymentMethodRepository;
import com.travel_plan.payment_service.security.AuthenticatedUser;
import com.travel_plan.payment_service.web.PaymentMethodRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final PaymentMethodRepository paymentMethodRepository;

    public PaymentMethodService(PaymentMethodRepository paymentMethodRepository) {
        this.paymentMethodRepository = paymentMethodRepository;
    }

    // ADMIN voit toutes les methodes de paiement, tout le monde d'autre ne voit que les siennes.
    @Transactional(readOnly = true)
    public List<PaymentMethod> findAll(AuthenticatedUser caller) {
        if (ADMIN_ROLE.equals(caller.role())) {
            return paymentMethodRepository.findAll();
        }
        return paymentMethodRepository.findByOwnerId(caller.userId());
    }

    @Transactional(readOnly = true)
    public PaymentMethod findById(UUID id, AuthenticatedUser caller) {
        PaymentMethod method = getOrThrow(id);
        requireOwnershipOrAdmin(method, caller);
        return method;
    }

    @Transactional
    public PaymentMethod create(PaymentMethodRequest request, AuthenticatedUser caller) {
        PaymentMethod method = PaymentMethod.builder()
                .ownerId(resolveOwnerId(request.ownerId(), caller))
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
    public PaymentMethod update(UUID id, PaymentMethodRequest request, AuthenticatedUser caller) {
        PaymentMethod method = getOrThrow(id);
        requireOwnershipOrAdmin(method, caller);
        method.setOwnerId(resolveOwnerId(request.ownerId(), caller));
        method.setProvider(request.provider());
        method.setType(request.type());
        method.setProviderToken(request.providerToken());
        method.setBrand(request.brand());
        method.setLast4(request.last4());
        method.setDefault(request.isDefault());
        return paymentMethodRepository.save(method);
    }

    @Transactional
    public void delete(UUID id, AuthenticatedUser caller) {
        PaymentMethod method = getOrThrow(id);
        requireOwnershipOrAdmin(method, caller);
        paymentMethodRepository.delete(method);
    }

    // TRAVELER/TRAVEL_MANAGER : force a son propre userId, toute valeur envoyee dans la requete
    // est ignoree. ADMIN : doit fournir explicitement ownerId, puisque son propre JWT n'en porte pas.
    private UUID resolveOwnerId(UUID requestedOwnerId, AuthenticatedUser caller) {
        if (!ADMIN_ROLE.equals(caller.role())) {
            return caller.userId();
        }
        if (requestedOwnerId == null) {
            throw new InvalidPaymentRequestException(
                    "ownerId is required when an admin creates or updates a payment method on behalf of a traveler");
        }
        return requestedOwnerId;
    }

    private void requireOwnershipOrAdmin(PaymentMethod method, AuthenticatedUser caller) {
        if (ADMIN_ROLE.equals(caller.role())) {
            return;
        }
        if (!method.getOwnerId().equals(caller.userId())) {
            throw new ForbiddenException("You can only manage your own payment methods");
        }
    }

    private PaymentMethod getOrThrow(UUID id) {
        return paymentMethodRepository.findById(id)
                .orElseThrow(() -> new PaymentMethodNotFoundException(id));
    }
}
