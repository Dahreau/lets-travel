-- fix/audit-gaps (troubleshooting.md #40) : manager_id est le filtre de ManagerStatsService,
-- AdminStatsService (managerRankings/travelRankings) et TravelService.requireOwnershipOrAdmin,
-- mais n'avait jamais d'index contrairement a subscriptions.travel_id/traveler_id (V3),
-- feedbacks.travel_id (V5) et reports.travel_id/reported_id (V5).
CREATE INDEX idx_travels_manager_id ON travels(manager_id);
