package com.travel_plan.travel_service.service;

import com.travel_plan.travel_service.domain.Report;
import com.travel_plan.travel_service.domain.ReportedType;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.exception.ForbiddenException;
import com.travel_plan.travel_service.exception.InvalidReportRequestException;
import com.travel_plan.travel_service.exception.TravelNotFoundException;
import com.travel_plan.travel_service.repository.ReportRepository;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.web.ReportRequest;
import com.travel_plan.travel_service.web.ReportResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TravelRepository travelRepository;
    private final Clock clock;

    public ReportResponse submit(UUID travelId, ReportRequest request, AuthenticatedUser caller) {
        Travel travel = getTravelOrThrow(travelId);
        UUID reporterId = requireTravelerId(caller);

        if (!subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, reporterId)) {
            throw new ForbiddenException("Vous ne pouvez signaler que quelqu'un d'un voyage auquel vous etiez abonne");
        }

        requireConsistentTarget(travel, request, reporterId);

        Report report = Report.builder()
                .travel(travel)
                .reporterId(reporterId)
                .reportedType(request.reportedType())
                .reportedId(request.reportedId())
                .reason(request.reason())
                .createdAt(Instant.now(clock))
                .build();

        return ReportResponse.from(reportRepository.save(report));
    }

    // Reserve a l'Admin : moderation globale, tous travels confondus. Un Travel Manager ne
    // doit pas pouvoir consulter les signalements le concernant - voir SecurityConfig.
    public List<ReportResponse> listAll() {
        return reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ReportResponse::from)
                .toList();
    }

    // MANAGER : reportedId doit etre le manager de CE travel. TRAVELER : reportedId doit etre un
    // autre abonne du MEME travel, pas soi-meme.
    private void requireConsistentTarget(Travel travel, ReportRequest request, UUID reporterId) {
        if (request.reportedType() == ReportedType.MANAGER) {
            if (!travel.getManagerId().equals(request.reportedId())) {
                throw new InvalidReportRequestException("reportedId doit etre le manager de ce voyage");
            }
            return;
        }

        if (request.reportedId().equals(reporterId)) {
            throw new InvalidReportRequestException("Vous ne pouvez pas vous signaler vous-meme");
        }
        if (!subscriptionRepository.existsByTravel_IdAndTravelerId(travel.getId(), request.reportedId())) {
            throw new InvalidReportRequestException("reportedId doit etre un autre traveler abonne a ce voyage");
        }
    }

    private UUID requireTravelerId(AuthenticatedUser caller) {
        if (caller.userId() == null) {
            throw new InvalidReportRequestException("Un profil utilisateur lie est requis pour soumettre un signalement");
        }
        return caller.userId();
    }

    private Travel getTravelOrThrow(UUID id) {
        return travelRepository.findById(id).orElseThrow(() -> new TravelNotFoundException(id));
    }
}
