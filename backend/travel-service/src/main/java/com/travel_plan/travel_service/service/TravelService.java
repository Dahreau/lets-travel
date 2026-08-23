package com.travel_plan.travel_service.service;

import com.travel_plan.travel_service.domain.Accommodation;
import com.travel_plan.travel_service.domain.Activity;
import com.travel_plan.travel_service.domain.Destination;
import com.travel_plan.travel_service.domain.Transportation;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.exception.ForbiddenException;
import com.travel_plan.travel_service.exception.InvalidTravelRequestException;
import com.travel_plan.travel_service.exception.TravelNotFoundException;
import com.travel_plan.travel_service.graph.TravelGraphSyncService;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.web.AccommodationRequest;
import com.travel_plan.travel_service.web.ActivityRequest;
import com.travel_plan.travel_service.web.DestinationRequest;
import com.travel_plan.travel_service.web.TransportationRequest;
import com.travel_plan.travel_service.web.TravelRequest;
import com.travel_plan.travel_service.web.TravelResponse;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TravelService {

    private static final String TRAVEL_MANAGER_ROLE = "TRAVEL_MANAGER";
    private static final String ADMIN_ROLE = "ADMIN";

    private final TravelRepository travelRepository;
    private final TravelGraphSyncService graphSyncService;

    public List<TravelResponse> findAll() {
        return travelRepository.findAll().stream().map(TravelResponse::from).toList();
    }

    public TravelResponse findById(UUID id) {
        return TravelResponse.from(getOrThrow(id));
    }

    public TravelResponse create(TravelRequest request, AuthenticatedUser caller) {
        Travel travel = Travel.builder()
                .title(request.title())
                .managerId(resolveManagerId(request.managerId(), caller))
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(request.status())
                .price(request.price())
                .currency(request.currency().toUpperCase())
                .build();

        attachDestinations(travel, request.destinations());
        attachTransportations(travel, request.transportations());

        Travel saved = travelRepository.save(travel);
        graphSyncService.recordRoute(orderedDestinations(saved));
        return TravelResponse.from(saved);
    }

    public TravelResponse update(UUID id, TravelRequest request, AuthenticatedUser caller) {
        Travel travel = getOrThrow(id);
        requireOwnershipOrAdmin(travel, caller);
        List<Destination> oldRoute = orderedDestinations(travel);

        travel.setTitle(request.title());
        travel.setManagerId(resolveManagerId(request.managerId(), caller));
        travel.setStartDate(request.startDate());
        travel.setEndDate(request.endDate());
        travel.setStatus(request.status());
        travel.setPrice(request.price());
        travel.setCurrency(request.currency().toUpperCase());

        attachDestinations(travel, request.destinations());
        attachTransportations(travel, request.transportations());

        // saveAndFlush : @PreUpdate ne s'execute qu'au flush, sinon updatedAt renvoye est perime.
        Travel saved = travelRepository.saveAndFlush(travel);

        graphSyncService.removeRoute(oldRoute);
        graphSyncService.recordRoute(orderedDestinations(saved));
        return TravelResponse.from(saved);
    }

    public void delete(UUID id, AuthenticatedUser caller) {
        Travel travel = getOrThrow(id);
        requireOwnershipOrAdmin(travel, caller);
        graphSyncService.removeRoute(orderedDestinations(travel));
        travelRepository.delete(travel);
    }

    // TRAVEL_MANAGER : force a son propre userId, toute valeur envoyee dans la requete est
    // ignoree (un manager ne peut pas s'attribuer/attribuer a un autre manager un voyage).
    // ADMIN : doit fournir explicitement managerId, puisque son propre JWT n'en porte pas.
    private UUID resolveManagerId(UUID requestedManagerId, AuthenticatedUser caller) {
        if (TRAVEL_MANAGER_ROLE.equals(caller.role())) {
            return caller.userId();
        }
        if (requestedManagerId == null) {
            throw new InvalidTravelRequestException(
                    "managerId is required when an admin creates or updates a travel on behalf of a manager");
        }
        return requestedManagerId;
    }

    private void requireOwnershipOrAdmin(Travel travel, AuthenticatedUser caller) {
        if (ADMIN_ROLE.equals(caller.role())) {
            return;
        }
        if (!travel.getManagerId().equals(caller.userId())) {
            throw new ForbiddenException("You can only manage your own travels");
        }
    }

    private Travel getOrThrow(UUID id) {
        return travelRepository.findById(id).orElseThrow(() -> new TravelNotFoundException(id));
    }

    private List<Destination> orderedDestinations(Travel travel) {
        return travel.getDestinations().stream()
                .sorted(Comparator.comparing(Destination::getOrderIndex))
                .toList();
    }

    private void attachDestinations(Travel travel, List<DestinationRequest> requests) {
        travel.getDestinations().clear();
        for (DestinationRequest request : requests) {
            Destination destination = Destination.builder()
                    .travel(travel)
                    .city(request.city())
                    .country(request.country())
                    .arrivalDate(request.arrivalDate())
                    .departureDate(request.departureDate())
                    .orderIndex(request.orderIndex())
                    .build();
            attachActivities(destination, request.activities());
            attachAccommodation(destination, request.accommodation());
            travel.getDestinations().add(destination);
        }
    }

    private void attachActivities(Destination destination, List<ActivityRequest> requests) {
        if (requests == null) {
            return;
        }
        for (ActivityRequest request : requests) {
            Activity activity = Activity.builder()
                    .destination(destination)
                    .name(request.name())
                    .description(request.description())
                    .date(request.date())
                    .cost(request.cost())
                    .build();
            destination.getActivities().add(activity);
        }
    }

    private void attachAccommodation(Destination destination, AccommodationRequest request) {
        if (request == null) {
            destination.setAccommodation(null);
            return;
        }
        Accommodation accommodation = Accommodation.builder()
                .destination(destination)
                .name(request.name())
                .type(request.type())
                .address(request.address())
                .checkIn(request.checkIn())
                .checkOut(request.checkOut())
                .build();
        destination.setAccommodation(accommodation);
    }

    private void attachTransportations(Travel travel, List<TransportationRequest> requests) {
        travel.getTransportations().clear();
        if (requests == null) {
            return;
        }
        for (TransportationRequest request : requests) {
            Transportation transportation = Transportation.builder()
                    .travel(travel)
                    .type(request.type())
                    .fromLocation(request.fromLocation())
                    .toLocation(request.toLocation())
                    .departureTime(request.departureTime())
                    .arrivalTime(request.arrivalTime())
                    .provider(request.provider())
                    .build();
            travel.getTransportations().add(transportation);
        }
    }
}
