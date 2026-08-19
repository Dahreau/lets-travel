package com.travel_plan.travel_service.service;

import com.travel_plan.travel_service.domain.Accommodation;
import com.travel_plan.travel_service.domain.Activity;
import com.travel_plan.travel_service.domain.Destination;
import com.travel_plan.travel_service.domain.Transportation;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.exception.TravelNotFoundException;
import com.travel_plan.travel_service.graph.TravelGraphSyncService;
import com.travel_plan.travel_service.repository.TravelRepository;
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

    private final TravelRepository travelRepository;
    private final TravelGraphSyncService graphSyncService;

    public List<TravelResponse> findAll() {
        return travelRepository.findAll().stream().map(TravelResponse::from).toList();
    }

    public TravelResponse findById(UUID id) {
        return TravelResponse.from(getOrThrow(id));
    }

    public TravelResponse create(TravelRequest request) {
        Travel travel = Travel.builder()
                .title(request.title())
                .ownerId(request.ownerId())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(request.status())
                .build();

        attachDestinations(travel, request.destinations());
        attachTransportations(travel, request.transportations());

        Travel saved = travelRepository.save(travel);
        graphSyncService.recordRoute(orderedDestinations(saved));
        return TravelResponse.from(saved);
    }

    public TravelResponse update(UUID id, TravelRequest request) {
        Travel travel = getOrThrow(id);
        List<Destination> oldRoute = orderedDestinations(travel);

        travel.setTitle(request.title());
        travel.setOwnerId(request.ownerId());
        travel.setStartDate(request.startDate());
        travel.setEndDate(request.endDate());
        travel.setStatus(request.status());

        attachDestinations(travel, request.destinations());
        attachTransportations(travel, request.transportations());

        // saveAndFlush : @PreUpdate ne s'execute qu'au flush, sinon updatedAt renvoye est perime.
        Travel saved = travelRepository.saveAndFlush(travel);

        graphSyncService.removeRoute(oldRoute);
        graphSyncService.recordRoute(orderedDestinations(saved));
        return TravelResponse.from(saved);
    }

    public void delete(UUID id) {
        Travel travel = getOrThrow(id);
        graphSyncService.removeRoute(orderedDestinations(travel));
        travelRepository.delete(travel);
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
