package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.domain.Report;
import com.travel_plan.travel_service.domain.ReportedType;
import java.time.Instant;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        UUID travelId,
        UUID reporterId,
        ReportedType reportedType,
        UUID reportedId,
        String reason,
        Instant createdAt) {

    public static ReportResponse from(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getTravel().getId(),
                report.getReporterId(),
                report.getReportedType(),
                report.getReportedId(),
                report.getReason(),
                report.getCreatedAt());
    }
}
