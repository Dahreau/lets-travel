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
            throw new ForbiddenException("You can only report someone from a travel you were subscribed to");
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

    // MANAGER : reportedId doit etre le manager de CE travel (pas un manager quelconque).
    // TRAVELER : reportedId doit etre un autre abonne (actif ou annule) du MEME travel, pas
    // soi-meme - garantit que le signalement porte sur une interaction reelle.
    private void requireConsistentTarget(Travel travel, ReportRequest request, UUID reporterId) {
        if (request.reportedType() == ReportedType.MANAGER) {
            if (!travel.getManagerId().equals(request.reportedId())) {
                throw new InvalidReportRequestException("reportedId must be the manager of this travel");
            }
            return;
        }

        if (request.reportedId().equals(reporterId)) {
            throw new InvalidReportRequestException("You cannot report yourself");
        }
        if (!subscriptionRepository.existsByTravel_IdAndTravelerId(travel.getId(), request.reportedId())) {
            throw new InvalidReportRequestException("reportedId must be another traveler subscribed to this travel");
        }
    }

    private UUID requireTravelerId(AuthenticatedUser caller) {
        if (caller.userId() == null) {
            throw new InvalidReportRequestException("A linked user profile is required to submit a report");
        }
        return caller.userId();
    }

    private Travel getTravelOrThrow(UUID id) {
        return travelRepository.findById(id).orElseThrow(() -> new TravelNotFoundException(id));
    }
}
