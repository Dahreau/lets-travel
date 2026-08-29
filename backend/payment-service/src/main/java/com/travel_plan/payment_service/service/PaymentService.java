package com.travel_plan.payment_service.service;

import com.travel_plan.payment_service.client.TravelServiceClient;
import com.travel_plan.payment_service.client.TravelSummary;
import com.travel_plan.payment_service.domain.Payment;
import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.domain.PaymentStatus;
import com.travel_plan.payment_service.exception.ForbiddenException;
import com.travel_plan.payment_service.exception.InvalidPaymentRequestException;
import com.travel_plan.payment_service.exception.InvalidRefundException;
import com.travel_plan.payment_service.exception.PaymentMethodNotFoundException;
import com.travel_plan.payment_service.exception.PaymentNotFoundException;
import com.travel_plan.payment_service.provider.ChargeRequest;
import com.travel_plan.payment_service.provider.ChargeResult;
import com.travel_plan.payment_service.provider.PaymentProviderResolver;
import com.travel_plan.payment_service.repository.PaymentMethodRepository;
import com.travel_plan.payment_service.repository.PaymentRepository;
import com.travel_plan.payment_service.security.AuthenticatedUser;
import com.travel_plan.payment_service.web.PaymentRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentProviderResolver paymentProviderResolver;
    private final TravelServiceClient travelServiceClient;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentMethodRepository paymentMethodRepository,
            PaymentProviderResolver paymentProviderResolver,
            TravelServiceClient travelServiceClient) {
        this.paymentRepository = paymentRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentProviderResolver = paymentProviderResolver;
        this.travelServiceClient = travelServiceClient;
    }

    // Liste complete non filtree par proprietaire : reste ADMIN-only (voir SecurityConfig),
    // pas de changement de perimetre ici.
    @Transactional(readOnly = true)
    public List<Payment> findAll() {
        return paymentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Payment findById(UUID id, AuthenticatedUser caller) {
        Payment payment = getOrThrow(id);
        requireOwnershipOrAdmin(payment, caller);
        return payment;
    }

    @Transactional
    public Payment create(PaymentRequest request, AuthenticatedUser caller, String authorizationHeader) {
        UUID ownerId = resolveOwnerId(request.ownerId(), caller);

        PaymentMethod method = paymentMethodRepository.findById(request.paymentMethodId())
                .orElseThrow(() -> new PaymentMethodNotFoundException(request.paymentMethodId()));
        requireMethodOwnershipOrAdmin(method, caller);

        // Le montant vient de travel-service, jamais de la requete (voir
        // docs/nouveautes-vs-travel-plan.md) : faire confiance a un "amount" fourni par le client
        // permettait de payer n'importe quel prix pour n'importe quel voyage.
        TravelSummary travel = travelServiceClient.getPricedTravel(request.travelId(), authorizationHeader);

        ChargeRequest chargeRequest = new ChargeRequest(travel.price(), travel.currency(), method.getProviderToken());
        ChargeResult chargeResult = paymentProviderResolver.resolve(method.getProvider()).charge(chargeRequest);

        Payment payment = Payment.builder()
                .travelId(request.travelId())
                .ownerId(ownerId)
                .paymentMethod(method)
                .amount(travel.price())
                .currency(travel.currency().toUpperCase())
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

    // TRAVELER/TRAVEL_MANAGER : force a son propre userId, toute valeur envoyee dans la requete
    // est ignoree. ADMIN : doit fournir explicitement ownerId, puisque son propre JWT n'en porte pas.
    private UUID resolveOwnerId(UUID requestedOwnerId, AuthenticatedUser caller) {
        if (!ADMIN_ROLE.equals(caller.role())) {
            return caller.userId();
        }
        if (requestedOwnerId == null) {
            throw new InvalidPaymentRequestException(
                    "ownerId est obligatoire quand un admin cree un paiement pour le compte d'un traveler");
        }
        return requestedOwnerId;
    }

    private void requireOwnershipOrAdmin(Payment payment, AuthenticatedUser caller) {
        if (ADMIN_ROLE.equals(caller.role())) {
            return;
        }
        if (!payment.getOwnerId().equals(caller.userId())) {
            throw new ForbiddenException("Vous ne pouvez consulter que vos propres paiements");
        }
    }

    private void requireMethodOwnershipOrAdmin(PaymentMethod method, AuthenticatedUser caller) {
        if (ADMIN_ROLE.equals(caller.role())) {
            return;
        }
        if (!method.getOwnerId().equals(caller.userId())) {
            throw new ForbiddenException("Vous ne pouvez payer qu'avec vos propres moyens de paiement");
        }
    }

    private Payment getOrThrow(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
