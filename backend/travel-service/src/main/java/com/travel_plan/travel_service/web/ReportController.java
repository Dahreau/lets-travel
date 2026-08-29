package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.service.ReportService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Pas de @RequestMapping de classe : POST est scope a un travel, GET est une vue de moderation
// globale ("/api/reports") - pas de prefixe commun exploitable.
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/api/travels/{travelId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse submit(
            @PathVariable UUID travelId, @Valid @RequestBody ReportRequest request, Authentication authentication) {
        return reportService.submit(travelId, request, principal(authentication));
    }

    // Reserve a l'Admin (voir SecurityConfig) - vue de moderation globale, tous travels confondus.
    @GetMapping("/api/reports")
    public List<ReportResponse> listAll() {
        return reportService.listAll();
    }

    private AuthenticatedUser principal(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
