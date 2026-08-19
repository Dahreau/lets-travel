package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.service.TravelService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travels")
@RequiredArgsConstructor
public class TravelController {

    private final TravelService travelService;

    @GetMapping
    public List<TravelResponse> findAll() {
        return travelService.findAll();
    }

    @GetMapping("/{id}")
    public TravelResponse findById(@PathVariable UUID id) {
        return travelService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TravelResponse create(@Valid @RequestBody TravelRequest request) {
        return travelService.create(request);
    }

    @PutMapping("/{id}")
    public TravelResponse update(@PathVariable UUID id, @Valid @RequestBody TravelRequest request) {
        return travelService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        travelService.delete(id);
    }
}
