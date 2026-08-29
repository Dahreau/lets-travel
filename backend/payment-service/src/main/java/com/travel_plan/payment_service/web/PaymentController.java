package com.travel_plan.payment_service.web;

import com.travel_plan.payment_service.domain.Payment;
import com.travel_plan.payment_service.security.AuthenticatedUser;
import com.travel_plan.payment_service.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<PaymentResponse> findAll() {
        return paymentService.findAll().stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public PaymentResponse findById(@PathVariable UUID id, Authentication authentication) {
        return PaymentResponse.from(paymentService.findById(id, principal(authentication)));
    }

    // Le header Authorization de l'appelant est propage tel quel vers travel-service (voir
    // client/TravelServiceClient) : meme JWT que travel-service valide deja pour ses routes GET.
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody PaymentRequest request,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        Payment created = paymentService.create(request, principal(authentication), authorizationHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(created));
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable UUID id) {
        return PaymentResponse.from(paymentService.refund(id));
    }

    private AuthenticatedUser principal(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
