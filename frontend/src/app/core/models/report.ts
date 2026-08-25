export type ReportedType = 'MANAGER' | 'TRAVELER';

export interface ReportRequest {
  reportedType: ReportedType;
  reportedId: string;
  reason: string;
}

// feat/admin-dashboard-overview : vue de moderation Admin (GET /api/reports).
export interface ReportResponse {
  id: string;
  travelId: string;
  reporterId: string;
  reportedType: ReportedType;
  reportedId: string;
  reason: string;
  createdAt: string;
}
