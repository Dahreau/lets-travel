package com.travel_plan.travel_service.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travel_plan.travel_service.exception.ApiExceptionHandler;
import com.travel_plan.travel_service.exception.ForbiddenException;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.service.ManagerStatsService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ManagerStatsControllerTest {

    private ManagerStatsService managerStatsService;
    private MockMvc mockMvc;

    private final UUID managerUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        managerStatsService = mock(ManagerStatsService.class);
        ManagerStatsController controller = new ManagerStatsController(managerStatsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void myStatsReturnsOwnDashboardStats() throws Exception {
        AuthenticatedManager manager = managerAuth();
        UUID travelId = UUID.randomUUID();
        ManagerTravelStatsEntry entry = new ManagerTravelStatsEntry(travelId, "Trip", 3, 4.5, 2);
        when(managerStatsService.myStats(manager.user()))
                .thenReturn(new ManagerStatsResponse(2, 5, BigDecimal.valueOf(450), List.of(entry)));

        mockMvc.perform(get("/api/travels/managers/me/stats").principal(manager.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.travelCount").value(2))
                .andExpect(jsonPath("$.travelerCount").value(5))
                .andExpect(jsonPath("$.estimatedRevenue").value(450))
                .andExpect(jsonPath("$.travels[0].title").value("Trip"))
                .andExpect(jsonPath("$.travels[0].subscriberCount").value(3))
                .andExpect(jsonPath("$.travels[0].averageRating").value(4.5));
    }

    @Test
    void myStatsReturns403WhenCallerIsNotAManager() throws Exception {
        AuthenticatedManager traveler = travelerAuth();
        when(managerStatsService.myStats(traveler.user()))
                .thenThrow(new ForbiddenException("Seul un Travel Manager a un tableau de bord personnel"));

        mockMvc.perform(get("/api/travels/managers/me/stats").principal(traveler.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicStatsReturnsAggregatedStatsForAnyCaller() throws Exception {
        UUID targetManagerId = UUID.randomUUID();
        when(managerStatsService.publicStats(targetManagerId))
                .thenReturn(new ManagerPublicStatsResponse(3, 4.5, 1, List.of()));

        mockMvc.perform(get("/api/travels/managers/{managerId}/public-stats", targetManagerId)
                        .principal(travelerAuth().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.travelCount").value(3))
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.reportCount").value(1));
    }

    @Test
    void isMySubscriberReturnsSubscriberFlag() throws Exception {
        AuthenticatedManager manager = managerAuth();
        UUID travelerId = UUID.randomUUID();
        when(managerStatsService.isMySubscriber(manager.user(), travelerId)).thenReturn(true);

        mockMvc.perform(get("/api/travels/managers/me/subscribers/{travelerId}", travelerId)
                        .principal(manager.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriber").value(true));
    }

    @Test
    void isMySubscriberReturns403WhenCallerIsNotAManager() throws Exception {
        AuthenticatedManager traveler = travelerAuth();
        UUID travelerId = UUID.randomUUID();
        when(managerStatsService.isMySubscriber(traveler.user(), travelerId))
                .thenThrow(new ForbiddenException("Seul un Travel Manager peut verifier ses propres abonnes"));

        mockMvc.perform(get("/api/travels/managers/me/subscribers/{travelerId}", travelerId)
                        .principal(traveler.token()))
                .andExpect(status().isForbidden());
    }

    // Meme pattern que SubscriptionControllerTest : AuthenticatedUser est un record, donc
    // reconstruire une instance equals() suffit pour que Mockito matche le stubbing.
    private AuthenticatedManager travelerAuth() {
        AuthenticatedUser user = new AuthenticatedUser("traveler1", "TRAVELER", UUID.randomUUID());
        Authentication token =
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_TRAVELER")));
        return new AuthenticatedManager(user, token);
    }

    private AuthenticatedManager managerAuth() {
        AuthenticatedUser user = new AuthenticatedUser("manager1", "TRAVEL_MANAGER", managerUserId);
        Authentication token = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_TRAVEL_MANAGER")));
        return new AuthenticatedManager(user, token);
    }

    private record AuthenticatedManager(AuthenticatedUser user, Authentication token) {
    }
}
