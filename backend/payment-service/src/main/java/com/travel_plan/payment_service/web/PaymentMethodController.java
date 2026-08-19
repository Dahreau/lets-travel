package com.travel_plan.payment_service.web;

import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.service.PaymentMethodService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    public PaymentMethodController(PaymentMethodService paymentMethodService) {
        this.paymentMethodService = paymentMethodService;
    }

    @GetMapping
    public List<PaymentMethodResponse> findAll() {
        return paymentMethodService.findAll().stream()
                .map(PaymentMethodResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public PaymentMethodResponse findById(@PathVariable UUID id) {
        return PaymentMethodResponse.from(paymentMethodService.findById(id));
    }

    @PostMapping
    public ResponseEntity<PaymentMethodResponse> create(@Valid @RequestBody PaymentMethodRequest request) {
        PaymentMethod created = paymentMethodService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentMethodResponse.from(created));
    }

    @PutMapping("/{id}")
    public PaymentMethodResponse update(@PathVariable UUID id, @Valid @RequestBody PaymentMethodRequest request) {
        return PaymentMethodResponse.from(paymentMethodService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        paymentMethodService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
