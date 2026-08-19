package com.travel_plan.travel_service.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.travel_plan.travel_service.domain.Accommodation;
import com.travel_plan.travel_service.domain.AccommodationType;
import com.travel_plan.travel_service.domain.Activity;
import com.travel_plan.travel_service.domain.Destination;
import com.travel_plan.travel_service.domain.Transportation;
import com.travel_plan.travel_service.domain.TransportationType;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
import com.travel_plan.travel_service.exception.ApiExceptionHandler;
import com.travel_plan.travel_service.exception.TravelNotFoundException;
import com.travel_plan.travel_service.service.TravelService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TravelControllerTest {

    private TravelService travelService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        travelService = mock(TravelService.class);
        TravelController controller = new TravelController(travelService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void findAllReturnsAllTravels() throws Exception {
        when(travelService.findAll()).thenReturn(List.of(TravelResponse.from(newTravel("Iberian tour"))));

        mockMvc.perform(get("/api/travels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Iberian tour"))
                .andExpect(jsonPath("$[0].destinations[0].activities[0].name").value("Tram 28"))
                .andExpect(jsonPath("$[0].destinations[0].accommodation.name").value("Alfama Hostel"))
                .andExpect(jsonPath("$[0].transportations[0].provider").value("TAP"));
    }

    @Test
    void findByIdReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(travelService.findById(id)).thenThrow(new TravelNotFoundException(id));

        mockMvc.perform(get("/api/travels/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void findByIdReturns400ForNonUuidId() throws Exception {
        mockMvc.perform(get("/api/travels/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void createReturns201ForValidRequest() throws Exception {
        when(travelService.create(any(TravelRequest.class))).thenReturn(TravelResponse.from(newTravel("Iberian tour")));

        mockMvc.perform(post("/api/travels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest("Iberian tour"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Iberian tour"));
    }

    @Test
    void createReturns400WhenNoDestination() throws Exception {
        TravelRequest request = new TravelRequest(
                "Iberian tour",
                UUID.randomUUID(),
                LocalDate.of(2026, Month.SEPTEMBER, 1),
                LocalDate.of(2026, Month.SEPTEMBER, 10),
                TravelStatus.PLANNED,
                List.of(),
                List.of());

        mockMvc.perform(post("/api/travels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400WhenEndDateBeforeStartDate() throws Exception {
        TravelRequest request = new TravelRequest(
                "Iberian tour",
                UUID.randomUUID(),
                LocalDate.of(2026, Month.SEPTEMBER, 10),
                LocalDate.of(2026, Month.SEPTEMBER, 1),
                TravelStatus.PLANNED,
                List.of(new DestinationRequest(
                        "Lisbon", "Portugal",
                        LocalDate.of(2026, Month.SEPTEMBER, 1), LocalDate.of(2026, Month.SEPTEMBER, 5),
                        0, List.of(), null)),
                List.of());

        mockMvc.perform(post("/api/travels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400WhenDestinationDepartureBeforeArrival() throws Exception {
        TravelRequest request = new TravelRequest(
                "Iberian tour",
                UUID.randomUUID(),
                LocalDate.of(2026, Month.SEPTEMBER, 1),
                LocalDate.of(2026, Month.SEPTEMBER, 10),
                TravelStatus.PLANNED,
                List.of(new DestinationRequest(
                        "Lisbon", "Portugal",
                        LocalDate.of(2026, Month.SEPTEMBER, 5), LocalDate.of(2026, Month.SEPTEMBER, 1),
                        0, List.of(), null)),
                List.of());

        mockMvc.perform(post("/api/travels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400WhenAccommodationCheckOutBeforeCheckIn() throws Exception {
        AccommodationRequest accommodation = new AccommodationRequest(
                "Alfama Hostel", AccommodationType.HOSTEL, "Rua de Sao Miguel 10",
                LocalDate.of(2026, Month.SEPTEMBER, 5), LocalDate.of(2026, Month.SEPTEMBER, 1));
        TravelRequest request = new TravelRequest(
                "Iberian tour",
                UUID.randomUUID(),
                LocalDate.of(2026, Month.SEPTEMBER, 1),
                LocalDate.of(2026, Month.SEPTEMBER, 10),
                TravelStatus.PLANNED,
                List.of(new DestinationRequest(
                        "Lisbon", "Portugal",
                        LocalDate.of(2026, Month.SEPTEMBER, 1), LocalDate.of(2026, Month.SEPTEMBER, 5),
                        0, List.of(), accommodation)),
                List.of());

        mockMvc.perform(post("/api/travels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400WhenTransportationArrivalBeforeDeparture() throws Exception {
        TransportationRequest transportation = new TransportationRequest(
                TransportationType.FLIGHT, "Paris CDG", "Lisbon LIS",
                Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T08:00:00Z"), "TAP");
        TravelRequest request = new TravelRequest(
                "Iberian tour",
                UUID.randomUUID(),
                LocalDate.of(2026, Month.SEPTEMBER, 1),
                LocalDate.of(2026, Month.SEPTEMBER, 10),
                TravelStatus.PLANNED,
                List.of(new DestinationRequest(
                        "Lisbon", "Portugal",
                        LocalDate.of(2026, Month.SEPTEMBER, 1), LocalDate.of(2026, Month.SEPTEMBER, 5),
                        0, List.of(), null)),
                List.of(transportation));

        mockMvc.perform(post("/api/travels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400WhenActivityCostIsNegative() throws Exception {
        ActivityRequest activity = new ActivityRequest(
                "Tram 28", null, LocalDate.of(2026, Month.SEPTEMBER, 2), new BigDecimal("-3.50"));
        TravelRequest request = new TravelRequest(
                "Iberian tour",
                UUID.randomUUID(),
                LocalDate.of(2026, Month.SEPTEMBER, 1),
                LocalDate.of(2026, Month.SEPTEMBER, 10),
                TravelStatus.PLANNED,
                List.of(new DestinationRequest(
                        "Lisbon", "Portugal",
                        LocalDate.of(2026, Month.SEPTEMBER, 1), LocalDate.of(2026, Month.SEPTEMBER, 5),
                        0, List.of(activity), null)),
                List.of());

        mockMvc.perform(post("/api/travels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns409WhenDataIntegrityViolation() throws Exception {
        when(travelService.create(any(TravelRequest.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate accommodation"));

        mockMvc.perform(post("/api/travels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest("Iberian tour"))))
                .andExpect(status().isConflict());
    }

    @Test
    void updateReturns200ForValidRequest() throws Exception {
        UUID id = UUID.randomUUID();
        when(travelService.update(any(UUID.class), any(TravelRequest.class)))
                .thenReturn(TravelResponse.from(newTravel("Updated tour")));

        mockMvc.perform(put("/api/travels/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest("Updated tour"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated tour"));
    }

    @Test
    void updateReturns404WhenTravelMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(travelService.update(any(UUID.class), any(TravelRequest.class)))
                .thenThrow(new TravelNotFoundException(id));

        mockMvc.perform(put("/api/travels/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest("Iberian tour"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesExistingTravel() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/travels/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns404WhenTravelMissing() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new TravelNotFoundException(id)).when(travelService).delete(id);

        mockMvc.perform(delete("/api/travels/{id}", id)).andExpect(status().isNotFound());
    }

    private Travel newTravel(String title) {
        Travel travel = Travel.builder()
                .id(UUID.randomUUID())
                .title(title)
                .ownerId(UUID.randomUUID())
                .startDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .endDate(LocalDate.of(2026, Month.SEPTEMBER, 10))
                .status(TravelStatus.PLANNED)
                .build();

        Destination destination = Destination.builder()
                .id(UUID.randomUUID())
                .travel(travel)
                .city("Lisbon")
                .country("Portugal")
                .arrivalDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .departureDate(LocalDate.of(2026, Month.SEPTEMBER, 5))
                .orderIndex(0)
                .build();

        Activity activity = Activity.builder()
                .id(UUID.randomUUID())
                .destination(destination)
                .name("Tram 28")
                .date(LocalDate.of(2026, Month.SEPTEMBER, 2))
                .cost(new BigDecimal("3.50"))
                .build();
        destination.getActivities().add(activity);

        Accommodation accommodation = Accommodation.builder()
                .id(UUID.randomUUID())
                .destination(destination)
                .name("Alfama Hostel")
                .type(AccommodationType.HOSTEL)
                .address("Rua de Sao Miguel 10")
                .checkIn(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .checkOut(LocalDate.of(2026, Month.SEPTEMBER, 5))
                .build();
        destination.setAccommodation(accommodation);

        travel.getDestinations().add(destination);

        Transportation transportation = Transportation.builder()
                .id(UUID.randomUUID())
                .travel(travel)
                .type(TransportationType.FLIGHT)
                .fromLocation("Paris CDG")
                .toLocation("Lisbon LIS")
                .departureTime(Instant.parse("2026-09-01T08:00:00Z"))
                .arrivalTime(Instant.parse("2026-09-01T10:00:00Z"))
                .provider("TAP")
                .build();
        travel.getTransportations().add(transportation);

        return travel;
    }

    private TravelRequest validRequest(String title) {
        DestinationRequest destination = new DestinationRequest(
                "Lisbon",
                "Portugal",
                LocalDate.of(2026, Month.SEPTEMBER, 1),
                LocalDate.of(2026, Month.SEPTEMBER, 5),
                0,
                List.of(),
                null);
        return new TravelRequest(
                title,
                UUID.randomUUID(),
                LocalDate.of(2026, Month.SEPTEMBER, 1),
                LocalDate.of(2026, Month.SEPTEMBER, 10),
                TravelStatus.PLANNED,
                List.of(destination),
                List.of());
    }
}
