package com.travel_plan.travel_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travel_plan.travel_service.domain.Report;
import com.travel_plan.travel_service.domain.ReportedType;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final TravelRepository travelRepository = mock(TravelRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);
    private final ReportService reportService =
            new ReportService(reportRepository, subscriptionRepository, travelRepository, clock);

    private final UUID travelId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID reporterId = UUID.randomUUID();
    private final UUID otherTravelerId = UUID.randomUUID();
    private final AuthenticatedUser reporter = new AuthenticatedUser("traveler1", "TRAVELER", reporterId);
    private final AuthenticatedUser admin = new AuthenticatedUser("admin", "ADMIN", null);

    @Test
    void submitCreatesReportAgainstManager() {
        Travel travel = travel();
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, reporterId)).thenReturn(true);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId(UUID.randomUUID());
            return report;
        });

        ReportResponse response = reportService.submit(
                travelId, new ReportRequest(ReportedType.MANAGER, managerId, "Cancelled last minute"), reporter);

        assertThat(response.reportedType()).isEqualTo(ReportedType.MANAGER);
        assertThat(response.reportedId()).isEqualTo(managerId);
        assertThat(response.reporterId()).isEqualTo(reporterId);
    }

    @Test
    void submitCreatesReportAgainstAnotherTraveler() {
        Travel travel = travel();
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, reporterId)).thenReturn(true);
        when(subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, otherTravelerId)).thenReturn(true);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId(UUID.randomUUID());
            return report;
        });

        ReportResponse response = reportService.submit(
                travelId, new ReportRequest(ReportedType.TRAVELER, otherTravelerId, "Disruptive behaviour"), reporter);

        assertThat(response.reportedType()).isEqualTo(ReportedType.TRAVELER);
        assertThat(response.reportedId()).isEqualTo(otherTravelerId);
    }

    @Test
    void submitThrowsWhenTravelMissing() {
        when(travelRepository.findById(travelId)).thenReturn(Optional.empty());
        ReportRequest request = new ReportRequest(ReportedType.MANAGER, managerId, "reason");

        assertThatThrownBy(() -> reportService.submit(travelId, request, reporter))
                .isInstanceOf(TravelNotFoundException.class);
    }

    @Test
    void submitThrowsWhenReporterDidNotParticipate() {
        Travel travel = travel();
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, reporterId)).thenReturn(false);
        ReportRequest request = new ReportRequest(ReportedType.MANAGER, managerId, "reason");

        assertThatThrownBy(() -> reportService.submit(travelId, request, reporter))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void submitThrowsWhenReportedManagerDoesNotMatchTravel() {
        Travel travel = travel();
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, reporterId)).thenReturn(true);
        ReportRequest request = new ReportRequest(ReportedType.MANAGER, UUID.randomUUID(), "reason");

        assertThatThrownBy(() -> reportService.submit(travelId, request, reporter))
                .isInstanceOf(InvalidReportRequestException.class);
    }

    @Test
    void submitThrowsWhenReportingSelf() {
        Travel travel = travel();
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, reporterId)).thenReturn(true);
        ReportRequest request = new ReportRequest(ReportedType.TRAVELER, reporterId, "reason");

        assertThatThrownBy(() -> reportService.submit(travelId, request, reporter))
                .isInstanceOf(InvalidReportRequestException.class);
    }

    @Test
    void submitThrowsWhenReportedTravelerNotSubscribedToSameTravel() {
        Travel travel = travel();
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, reporterId)).thenReturn(true);
        when(subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, otherTravelerId)).thenReturn(false);
        ReportRequest request = new ReportRequest(ReportedType.TRAVELER, otherTravelerId, "reason");

        assertThatThrownBy(() -> reportService.submit(travelId, request, reporter))
                .isInstanceOf(InvalidReportRequestException.class);
    }

    @Test
    void submitThrowsWhenCallerHasNoLinkedUserId() {
        Travel travel = travel();
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        ReportRequest request = new ReportRequest(ReportedType.MANAGER, managerId, "reason");

        assertThatThrownBy(() -> reportService.submit(travelId, request, admin))
                .isInstanceOf(InvalidReportRequestException.class);
    }

    @Test
    void listAllReturnsReportsNewestFirst() {
        when(reportRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(report(ReportedType.MANAGER, managerId)));

        assertThat(reportService.listAll()).hasSize(1);
    }

    private Travel travel() {
        return Travel.builder()
                .id(travelId)
                .title("Iberian tour")
                .managerId(managerId)
                .startDate(LocalDate.now(clock).minusDays(10))
                .endDate(LocalDate.now(clock).minusDays(3))
                .status(TravelStatus.PLANNED)
                .build();
    }

    private Report report(ReportedType type, UUID reportedId) {
        return Report.builder()
                .id(UUID.randomUUID())
                .travel(travel())
                .reporterId(reporterId)
                .reportedType(type)
                .reportedId(reportedId)
                .reason("reason")
                .createdAt(Instant.now(clock))
                .build();
    }
}
