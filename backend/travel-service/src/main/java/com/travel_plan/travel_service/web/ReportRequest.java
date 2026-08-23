package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.domain.ReportedType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReportRequest(
        @NotNull ReportedType reportedType, @NotNull UUID reportedId, @NotBlank @Size(max = 2000) String reason) {
}
