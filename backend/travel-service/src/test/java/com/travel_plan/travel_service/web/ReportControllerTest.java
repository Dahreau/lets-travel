package com.travel_plan.travel_service.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_plan.travel_service.domain.ReportedType;
import com.travel_plan.travel_service.exception.ApiExceptionHandler;
import com.travel_plan.travel_service.exception.ForbiddenException;
import com.travel_plan.travel_service.exception.InvalidReportRequestException;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.service.ReportService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReportControllerTest {

    private ReportService reportService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID travelId = UUID.randomUUID();
    private final UUID reporterId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        ReportController controller = new ReportController(reportService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void submitReturns201() throws Exception {
        ReportRequest request = new ReportRequest(ReportedType.MANAGER, managerId, "Cancelled last minute");
        when(reportService.submit(travelId, request, travelerAuth()))
                .thenReturn(new ReportResponse(
                        UUID.randomUUID(),
                        travelId,
                        "Test Travel",
                        reporterId,
                        ReportedType.MANAGER,
                        managerId,
                        "Cancelled last minute",
                        Instant.now(Clock.systemUTC())));

        mockMvc.perform(post("/api/travels/{travelId}/reports", travelId)
                        .principal(travelerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportedType").value("MANAGER"));
    }

    @Test
    void submitReturns400ForBlankReason() throws Exception {
        mockMvc.perform(post("/api/travels/{travelId}/reports", travelId)
                        .principal(travelerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportRequest(ReportedType.MANAGER, managerId, ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitReturns403WhenReporterDidNotParticipate() throws Exception {
        ReportRequest request = new ReportRequest(ReportedType.MANAGER, managerId, "reason");
        when(reportService.submit(travelId, request, travelerAuth()))
                .thenThrow(new ForbiddenException("Vous ne pouvez signaler que quelqu'un d'un voyage auquel vous etiez abonne"));

        mockMvc.perform(post("/api/travels/{travelId}/reports", travelId)
                        .principal(travelerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitReturns400WhenTargetInconsistent() throws Exception {
        ReportRequest request = new ReportRequest(ReportedType.MANAGER, UUID.randomUUID(), "reason");
        when(reportService.submit(travelId, request, travelerAuth()))
                .thenThrow(new InvalidReportRequestException("reportedId doit etre le manager de ce voyage"));

        mockMvc.perform(post("/api/travels/{travelId}/reports", travelId)
                        .principal(travelerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listAllReturnsReportsForAdmin() throws Exception {
        when(reportService.listAll())
                .thenReturn(List.of(new ReportResponse(
                        UUID.randomUUID(),
                        travelId,
                        "Test Travel",
                        reporterId,
                        ReportedType.MANAGER,
                        managerId,
                        "reason",
                        Instant.now(Clock.systemUTC()))));

        mockMvc.perform(get("/api/reports").principal(adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reportedType").value("MANAGER"));
    }

    private AuthenticatedUser travelerAuth() {
        return new AuthenticatedUser("traveler1", "TRAVELER", reporterId);
    }

    private Authentication travelerToken() {
        return new UsernamePasswordAuthenticationToken(
                travelerAuth(), null, List.of(new SimpleGrantedAuthority("ROLE_TRAVELER")));
    }

    private Authentication adminToken() {
        AuthenticatedUser admin = new AuthenticatedUser("admin", "ADMIN", null);
        return new UsernamePasswordAuthenticationToken(admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
