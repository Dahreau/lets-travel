package com.travel_plan.travel_service.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_plan.travel_service.exception.ApiExceptionHandler;
import com.travel_plan.travel_service.exception.DuplicateFeedbackException;
import com.travel_plan.travel_service.exception.ForbiddenException;
import com.travel_plan.travel_service.exception.InvalidFeedbackRequestException;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.service.FeedbackService;
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

class FeedbackControllerTest {

    private FeedbackService feedbackService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID travelId = UUID.randomUUID();
    private final UUID travelerId = UUID.randomUUID();
    private final UUID managerUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        feedbackService = mock(FeedbackService.class);
        FeedbackController controller = new FeedbackController(feedbackService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void submitReturns201() throws Exception {
        when(feedbackService.submit(travelId, new FeedbackRequest(4, "Nice"), travelerAuth().user()))
                .thenReturn(new FeedbackResponse(
                        UUID.randomUUID(), travelId, travelerId, 4, "Nice", Instant.now(Clock.systemUTC())));

        mockMvc.perform(post("/api/travels/{travelId}/feedbacks", travelId)
                        .principal(travelerAuth().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FeedbackRequest(4, "Nice"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4));
    }

    @Test
    void submitReturns400ForRatingOutOfRange() throws Exception {
        mockMvc.perform(post("/api/travels/{travelId}/feedbacks", travelId)
                        .principal(travelerAuth().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FeedbackRequest(9, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitReturns403WhenNotParticipated() throws Exception {
        when(feedbackService.submit(travelId, new FeedbackRequest(4, null), travelerAuth().user()))
                .thenThrow(new ForbiddenException("You can only leave feedback on a travel you were subscribed to"));

        mockMvc.perform(post("/api/travels/{travelId}/feedbacks", travelId)
                        .principal(travelerAuth().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FeedbackRequest(4, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitReturns409WhenAlreadySubmitted() throws Exception {
        when(feedbackService.submit(travelId, new FeedbackRequest(4, null), travelerAuth().user()))
                .thenThrow(new DuplicateFeedbackException("Feedback already submitted for this travel"));

        mockMvc.perform(post("/api/travels/{travelId}/feedbacks", travelId)
                        .principal(travelerAuth().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FeedbackRequest(4, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void submitReturns400WhenTravelNotYetEnded() throws Exception {
        when(feedbackService.submit(travelId, new FeedbackRequest(4, null), travelerAuth().user()))
                .thenThrow(new InvalidFeedbackRequestException("Feedback can only be submitted after the travel has ended"));

        mockMvc.perform(post("/api/travels/{travelId}/feedbacks", travelId)
                        .principal(travelerAuth().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FeedbackRequest(4, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listForTravelReturnsFeedbackList() throws Exception {
        AuthenticatedManager manager = managerAuth();
        when(feedbackService.listForTravel(travelId, manager.user()))
                .thenReturn(List.of(new FeedbackResponse(
                        UUID.randomUUID(), travelId, travelerId, 5, "Amazing", Instant.now(Clock.systemUTC()))));

        mockMvc.perform(get("/api/travels/{travelId}/feedbacks", travelId).principal(manager.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rating").value(5));
    }

    @Test
    void listForTravelReturns403WhenNotOwningManager() throws Exception {
        when(feedbackService.listForTravel(travelId, managerAuth().user()))
                .thenThrow(new ForbiddenException("You can only view feedback for your own travels"));

        mockMvc.perform(get("/api/travels/{travelId}/feedbacks", travelId).principal(managerAuth().token()))
                .andExpect(status().isForbidden());
    }

    private AuthenticatedManager travelerAuth() {
        AuthenticatedUser user = new AuthenticatedUser("traveler1", "TRAVELER", travelerId);
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
