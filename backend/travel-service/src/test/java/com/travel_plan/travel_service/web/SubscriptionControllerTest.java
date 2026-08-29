package com.travel_plan.travel_service.web;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travel_plan.travel_service.domain.SubscriptionStatus;
import com.travel_plan.travel_service.exception.ApiExceptionHandler;
import com.travel_plan.travel_service.exception.DuplicateSubscriptionException;
import com.travel_plan.travel_service.exception.ForbiddenException;
import com.travel_plan.travel_service.exception.SubscriptionCutoffException;
import com.travel_plan.travel_service.exception.SubscriptionNotFoundException;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.service.SubscriptionService;
import java.time.Clock;
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

class SubscriptionControllerTest {

    private SubscriptionService subscriptionService;
    private MockMvc mockMvc;

    private final UUID travelId = UUID.randomUUID();
    private final UUID travelerId = UUID.randomUUID();
    private final UUID managerUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        subscriptionService = mock(SubscriptionService.class);
        SubscriptionController controller = new SubscriptionController(subscriptionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void subscribeReturns201() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        when(subscriptionService.subscribe(travelId, travelerAuth().user()))
                .thenReturn(new SubscriptionResponse(
                        subscriptionId,
                        travelId,
                        "Test Travel",
                        travelerId,
                        SubscriptionStatus.ACTIVE,
                        Instant.now(Clock.systemUTC()),
                        null));

        mockMvc.perform(post("/api/travels/{travelId}/subscriptions", travelId).principal(travelerAuth().token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.travelId").value(travelId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void subscribeReturns409WhenAlreadySubscribed() throws Exception {
        when(subscriptionService.subscribe(travelId, travelerAuth().user()))
                .thenThrow(new DuplicateSubscriptionException("Deja abonne a ce voyage"));

        mockMvc.perform(post("/api/travels/{travelId}/subscriptions", travelId).principal(travelerAuth().token()))
                .andExpect(status().isConflict());
    }

    @Test
    void unsubscribeReturns204() throws Exception {
        UUID subscriptionId = UUID.randomUUID();

        mockMvc.perform(delete("/api/travels/{travelId}/subscriptions/{subscriptionId}", travelId, subscriptionId)
                        .principal(travelerAuth().token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void unsubscribeReturns404WhenSubscriptionMissing() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        doThrow(new SubscriptionNotFoundException(subscriptionId))
                .when(subscriptionService)
                .unsubscribe(travelId, subscriptionId, travelerAuth().user());

        mockMvc.perform(delete("/api/travels/{travelId}/subscriptions/{subscriptionId}", travelId, subscriptionId)
                        .principal(travelerAuth().token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsubscribeReturns403WhenNotOwnSubscription() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        doThrow(new ForbiddenException("Vous ne pouvez annuler que votre propre abonnement"))
                .when(subscriptionService)
                .unsubscribe(travelId, subscriptionId, travelerAuth().user());

        mockMvc.perform(delete("/api/travels/{travelId}/subscriptions/{subscriptionId}", travelId, subscriptionId)
                        .principal(travelerAuth().token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unsubscribeReturns409WhenPastCutoff() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        doThrow(new SubscriptionCutoffException("Les abonnements ne peuvent plus etre annules moins de 3 jours avant le depart"))
                .when(subscriptionService)
                .unsubscribe(travelId, subscriptionId, travelerAuth().user());

        mockMvc.perform(delete("/api/travels/{travelId}/subscriptions/{subscriptionId}", travelId, subscriptionId)
                        .principal(travelerAuth().token()))
                .andExpect(status().isConflict());
    }

    @Test
    void listSubscribersReturnsSubscriberList() throws Exception {
        AuthenticatedManager manager = managerAuth();
        when(subscriptionService.listSubscribers(travelId, manager.user()))
                .thenReturn(List.of(new SubscriptionResponse(
                        UUID.randomUUID(),
                        travelId,
                        "Test Travel",
                        travelerId,
                        SubscriptionStatus.ACTIVE,
                        Instant.now(Clock.systemUTC()),
                        null)));

        mockMvc.perform(get("/api/travels/{travelId}/subscriptions", travelId).principal(manager.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].travelerId").value(travelerId.toString()));
    }

    @Test
    void listSubscribersReturns403WhenNotOwningManager() throws Exception {
        when(subscriptionService.listSubscribers(travelId, managerAuth().user()))
                .thenThrow(new ForbiddenException("Vous ne pouvez consulter les abonnes que de vos propres voyages"));

        mockMvc.perform(get("/api/travels/{travelId}/subscriptions", travelId).principal(managerAuth().token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void coTravelersReturnsOtherTravelerIds() throws Exception {
        UUID otherTravelerId = UUID.randomUUID();
        when(subscriptionService.coTravelerIds(travelId, travelerAuth().user())).thenReturn(List.of(otherTravelerId));

        mockMvc.perform(
                        get("/api/travels/{travelId}/subscriptions/co-travelers", travelId)
                                .principal(travelerAuth().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(otherTravelerId.toString()));
    }

    @Test
    void coTravelersReturns403WhenCallerNeverParticipated() throws Exception {
        when(subscriptionService.coTravelerIds(travelId, travelerAuth().user()))
                .thenThrow(new ForbiddenException("Vous ne pouvez consulter les co-voyageurs que d'un voyage auquel vous avez participe"));

        mockMvc.perform(
                        get("/api/travels/{travelId}/subscriptions/co-travelers", travelId)
                                .principal(travelerAuth().token()))
                .andExpect(status().isForbidden());
    }

    // AuthenticatedUser est un record : chaque appel a travelerAuth()/managerAuth() reconstruit
    // une instance egale (equals()) a celle capturee par le mock, donc Mockito matche correctement
    // meme sans reutiliser exactement le meme objet entre le stubbing et l'appel MockMvc.
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
