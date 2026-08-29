package com.travel_plan.travel_service.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travel_plan.travel_service.domain.SubscriptionStatus;
import com.travel_plan.travel_service.exception.ApiExceptionHandler;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.service.TravelerStatsService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TravelerStatsControllerTest {

    private TravelerStatsService travelerStatsService;
    private MockMvc mockMvc;

    private final UUID travelerUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        travelerStatsService = mock(TravelerStatsService.class);
        TravelerStatsController controller = new TravelerStatsController(travelerStatsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void myStatsReturnsOwnDashboardStats() throws Exception {
        AuthenticatedTraveler traveler = travelerAuth();
        when(travelerStatsService.myStats(traveler.user())).thenReturn(new TravelerStatsResponse(5, 3, 1, 2));

        mockMvc.perform(get("/api/travels/travelers/me/stats").principal(traveler.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participationCount").value(5))
                .andExpect(jsonPath("$.feedbackCount").value(3))
                .andExpect(jsonPath("$.reportCount").value(1))
                .andExpect(jsonPath("$.cancellationCount").value(2));
    }

    @Test
    void mySubscriptionsReturnsOwnHistory() throws Exception {
        AuthenticatedTraveler traveler = travelerAuth();
        SubscriptionResponse subscription = new SubscriptionResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Travel",
                travelerUserId,
                SubscriptionStatus.ACTIVE,
                Instant.now(),
                null);
        when(travelerStatsService.mySubscriptions(traveler.user())).thenReturn(List.of(subscription));

        mockMvc.perform(get("/api/travels/travelers/me/subscriptions").principal(traveler.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].travelerId").value(travelerUserId.toString()));
    }

    // Meme pattern que ManagerStatsControllerTest : AuthenticatedUser est un record, donc
    // reconstruire une instance equals() suffit pour que Mockito matche le stubbing.
    private AuthenticatedTraveler travelerAuth() {
        AuthenticatedUser user = new AuthenticatedUser("traveler1", "TRAVELER", travelerUserId);
        Authentication token =
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_TRAVELER")));
        return new AuthenticatedTraveler(user, token);
    }

    private record AuthenticatedTraveler(AuthenticatedUser user, Authentication token) {
    }
}
