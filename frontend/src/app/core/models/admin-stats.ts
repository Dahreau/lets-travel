// feat/admin-dashboard-overview : classements globaux du dashboard Admin (docs/lets-travel_project.md,
// section Admin) - miroir des DTOs AdminManagerRankingResponse/AdminTravelRankingResponse (travel-service).
export interface AdminManagerRanking {
  managerId: string;
  travelCount: number;
  travelerCount: number;
  estimatedRevenue: number;
  averageRating: number | null;
  reportCount: number;
  performanceScore: number;
}

export interface AdminTravelRanking {
  travelId: string;
  title: string;
  managerId: string;
  activeSubscriberCount: number;
  revenue: number;
  averageRating: number | null;
}
